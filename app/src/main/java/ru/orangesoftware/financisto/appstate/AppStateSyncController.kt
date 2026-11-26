// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appstate

import android.content.Context
import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.datastore.StateStorePreferences
import androidx.appstate.datastore.StateStoreSerializer
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import androidx.appstate.statestore.StateStoreKey
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
import kotlinx.coroutines.launch
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
) {
    private val lock = Any()
    private var syncJob: Job? = null
    private var dataStoreScope: CoroutineScope? = null
    private var activeDataStore: DataStore<StateStorePreferences>? = null

    fun start() {
        synchronized(lock) {
            if (syncJob?.isActive == true) return
            val dataStore = getOrCreateDataStoreLocked()
            syncJob = applicationScope.launch {
                runSyncLoop(dataStore)
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
    }

    fun stop() {
        synchronized(lock) {
            syncJob?.cancel()
            syncJob = null
            dataStoreScope?.cancel()
            dataStoreScope = null
            activeDataStore = null
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

    private suspend fun runSyncLoop(dataStore: DataStore<StateStorePreferences>) {
        var consecutiveFailures = 0
        val delayMs = retryDelayMs ?: RETRY_DELAY_MS
        while (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            val recoveryNeeded = try {
                syncRunner?.invoke(stateStore, dataStore) ?: stateStore.syncToDataStore(dataStore)
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
            if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES && delayMs > 0L) {
                delay(delayMs)
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
        const val MAX_CONSECUTIVE_FAILURES = 5
        const val RETRY_DELAY_MS = 250L
        val json = Json
    }
}
