// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appstate

import android.content.Context
import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.datastore.StateStorePreferences
import androidx.appstate.datastore.StateStoreSerializer
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.IOException

@Suppress("InjectDispatcher")
class AppStateSyncController(
    private val context: Context,
    private val stateStore: StateStore,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dataStoreFactory: ((CoroutineScope) -> DataStore<StateStorePreferences>)? = null,
    private val syncRunner: (suspend (StateStore, DataStore<StateStorePreferences>) -> Unit)? = null,
    private val retryDelayMs: Long? = null,
    private val maxRetryDelayMs: Long? = null,
) {
    private val lock = Any()
    private val restoreMutex = Mutex()
    private val restoredKeys = HashSet<String>()
    private var syncJob: Job? = null
    private var dataStoreScope: CoroutineScope? = null
    private var activeDataStore: DataStore<StateStorePreferences>? = null

    fun start() {
        synchronized(lock) {
            if (syncJob?.isActive == true) return
            val dataStore = getOrCreateDataStoreLocked()
            syncJob = applicationScope.launch(ioDispatcher) {
                runSyncLoop(dataStore)
            }
        }
    }

    suspend fun <T, K> ensureKeyRestored(key: K)
    where K : StateStoreKey<T>, K : PersistToDataStore<T> {
        val alreadyRestored = synchronized(lock) { key.name in restoredKeys }
        if (!alreadyRestored) {
            withContext(ioDispatcher) {
                restoreMutex.withLock {
                    val dataStore = synchronized(lock) {
                        if (key.name in restoredKeys) null else getOrCreateDataStoreLocked()
                    }
                    if (dataStore != null) {
                        restoreKeyFromDataStore(dataStore, key)
                        synchronized(lock) {
                            restoredKeys.add(key.name)
                        }
                    }
                }
            }
        }
    }

    suspend fun <T, K> persistKey(key: K, value: T)
    where K : StateStoreKey<T>, K : PersistToDataStore<T> {
        val dataStore = synchronized(lock) {
            getOrCreateDataStoreLocked()
        }
        val encodedValue = json.encodeToString(key.serializer, value)
        dataStore.updateData { current ->
            val currentBuffer = Buffer()
            StateStoreSerializer.writeTo(current, currentBuffer)
            val raw = currentBuffer.readUtf8()
            val updatedMap = if (raw.isBlank()) {
                mutableMapOf()
            } else {
                json.decodeFromString<Map<String, String>>(raw).toMutableMap()
            }
            updatedMap[key.name] = encodedValue
            val nextBuffer = Buffer().writeUtf8(json.encodeToString(updatedMap))
            StateStoreSerializer.readFrom(nextBuffer)
        }
        synchronized(lock) {
            restoredKeys.add(key.name)
        }
    }

    fun stop() {
        synchronized(lock) {
            syncJob?.cancel()
            syncJob = null
            dataStoreScope?.cancel()
            dataStoreScope = null
            activeDataStore = null
            restoredKeys.clear()
        }
    }

    private fun getOrCreateDataStoreLocked(): DataStore<StateStorePreferences> {
        activeDataStore?.let { return it }
        val scope = CoroutineScope(SupervisorJob(applicationScope.coroutineContext[Job]) + ioDispatcher)
        val created = dataStoreFactory?.invoke(scope) ?: createDefaultDataStore(scope)
        dataStoreScope = scope
        activeDataStore = created
        return created
    }

    private fun createDefaultDataStore(scope: CoroutineScope): DataStore<StateStorePreferences> {
        val path = context.dataStoreFile(DATA_STORE_FILE_NAME).absolutePath
        return DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, StateStoreSerializer) { path.toPath() },
            corruptionHandler = ReplaceFileCorruptionHandler { error ->
                Timber.e(error, "AppState DataStore corrupted; resetting to default state")
                StateStoreSerializer.defaultValue
            },
            scope = scope,
        )
    }

    private suspend fun <T, K> restoreKeyFromDataStore(
        dataStore: DataStore<StateStorePreferences>,
        key: K,
    ) where K : StateStoreKey<T>, K : PersistToDataStore<T> {
        stateStore.getState(key)
        val currentPrefs = dataStore.data.first()
        val buffer = Buffer()
        StateStoreSerializer.writeTo(currentPrefs, buffer)
        val raw = buffer.readUtf8()
        val storedJson = if (raw.isBlank()) {
            null
        } else {
            json.decodeFromString<Map<String, String>>(raw)[key.name]
        }
        if (storedJson != null) {
            try {
                val decoded = json.decodeFromString(key.serializer, storedJson)
                Snapshot.withMutableSnapshot {
                    if (stateStore.getState(key).value == key.defaultValue) {
                        stateStore.setState(key, decoded)
                    }
                }
            } catch (e: SerializationException) {
                Timber.w(e, "Failed to decode persisted AppState key: %s", key.name)
            }
        }
    }

    private suspend fun runSyncLoop(dataStore: DataStore<StateStorePreferences>) {
        var consecutiveFailures = 0
        val baseDelayMs = retryDelayMs ?: INITIAL_RETRY_DELAY_MS
        val capDelayMs = maxRetryDelayMs ?: MAX_RETRY_DELAY_MS
        while (true) {
            val recoveryNeeded = try {
                val runner = syncRunner
                if (runner != null) {
                    runner.invoke(stateStore, dataStore)
                } else {
                    ensureKeyRestored(AiDraftSessionKey)
                    ensureKeyRestored(BackupFolderUriKey)
                    stateStore.syncToDataStore(dataStore)
                }
                consecutiveFailures = 0
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: CorruptionException) {
                consecutiveFailures += 1
                Timber.e(e, "AppState DataStore corruption during sync (attempt %d)", consecutiveFailures)
                resetCorruptedStore(dataStore)
                true
            } catch (e: SerializationException) {
                consecutiveFailures += 1
                Timber.e(e, "AppState serialization failure during sync (attempt %d)", consecutiveFailures)
                resetCorruptedStore(dataStore)
                true
            } catch (e: IOException) {
                consecutiveFailures += 1
                Timber.e(e, "AppState DataStore I/O failure during sync (attempt %d)", consecutiveFailures)
                true
            }
            if (!recoveryNeeded) break
            val backoffMs = computeBackoffDelayMs(consecutiveFailures, baseDelayMs, capDelayMs)
            if (backoffMs > 0L) {
                delay(backoffMs)
            }
        }
    }

    private suspend fun resetCorruptedStore(dataStore: DataStore<StateStorePreferences>) {
        try {
            dataStore.updateData { StateStoreSerializer.defaultValue }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CorruptionException) {
            Timber.e(e, "Failed to reset corrupted AppState DataStore")
        } catch (e: IOException) {
            Timber.e(e, "I/O failure while resetting corrupted AppState DataStore")
        }
    }

    private companion object {
        const val DATA_STORE_FILE_NAME = "financisto_app_state.json"
        const val INITIAL_RETRY_DELAY_MS = 250L
        const val MAX_RETRY_DELAY_MS = 16_000L
        const val MAX_BACKOFF_SHIFT = 6
        val json = Json

        fun computeBackoffDelayMs(
            consecutiveFailures: Int,
            baseDelayMs: Long,
            maxDelayMs: Long,
        ): Long {
            if (baseDelayMs <= 0L || consecutiveFailures <= 0) return 0L
            val shift = (consecutiveFailures - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
            return (baseDelayMs shl shift).coerceAtMost(maxOf(baseDelayMs, maxDelayMs))
        }
    }
}
