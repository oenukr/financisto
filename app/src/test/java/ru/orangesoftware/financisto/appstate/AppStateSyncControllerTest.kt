// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appstate

import android.content.Context
import androidx.appstate.datastore.StateStoreSerializer
import androidx.appstate.statestore.StateStore
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateSyncControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mock(Context::class.java)

    @Test
    fun syncRecoversFromCorruptionExceptionAndRetriesSync() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appJob = SupervisorJob()
        val storeFile = tempFolder.newFile("app_state_corrupt.json")
        var attempts = 0
        var resetObserved = false
        val controller = AppStateSyncController(
            context = context,
            stateStore = StateStore(),
            applicationScope = CoroutineScope(appJob + dispatcher),
            ioDispatcher = dispatcher,
            dataStoreFactory = { scope ->
                DataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, StateStoreSerializer) {
                        storeFile.absolutePath.toPath()
                    },
                    scope = scope,
                )
            },
            syncRunner = { _, dataStore ->
                attempts += 1
                when (attempts) {
                    1 -> {
                        dataStore.updateData {
                            it
                        }
                        throw CorruptionException("corrupt")
                    }
                    else -> {
                        dataStore.updateData { current ->
                            resetObserved = current == StateStoreSerializer.defaultValue
                            current
                        }
                    }
                }
            },
            retryDelayMs = 0L,
        )

        try {
            controller.start()
            advanceUntilIdle()

            assertEquals(2, attempts)
            assertTrue(resetObserved)
        } finally {
            controller.stop()
            appJob.cancel()
        }
    }

    @Test
    fun syncRetriesOnIoExceptionAndStopsWhenHealthy() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appJob = SupervisorJob()
        val storeFile = tempFolder.newFile("app_state_io.json")
        var attempts = 0
        val controller = AppStateSyncController(
            context = context,
            stateStore = StateStore(),
            applicationScope = CoroutineScope(appJob + dispatcher),
            ioDispatcher = dispatcher,
            dataStoreFactory = { scope ->
                DataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, StateStoreSerializer) {
                        storeFile.absolutePath.toPath()
                    },
                    scope = scope,
                )
            },
            syncRunner = { _, _ ->
                attempts += 1
                if (attempts < 3) {
                    throw IOException("transient write error")
                }
            },
            retryDelayMs = 0L,
        )

        try {
            controller.start()
            advanceUntilIdle()

            assertEquals(3, attempts)
        } finally {
            controller.stop()
            appJob.cancel()
        }
    }

    @Test
    fun syncPropagatesCancellationExceptionWithoutRetrying() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storeFile = tempFolder.newFile("app_state_cancel.json")
        val parentJob = SupervisorJob()
        var attempts = 0
        val controller = AppStateSyncController(
            context = context,
            stateStore = StateStore(),
            applicationScope = CoroutineScope(parentJob + dispatcher),
            ioDispatcher = dispatcher,
            dataStoreFactory = { scope ->
                DataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, StateStoreSerializer) {
                        storeFile.absolutePath.toPath()
                    },
                    scope = scope,
                )
            },
            syncRunner = { _, _ ->
                attempts += 1
                throw CancellationException("cancelled")
            },
            retryDelayMs = 0L,
        )

        controller.start()
        advanceUntilIdle()

        assertEquals(1, attempts)
        assertFalse(parentJob.isCancelled)
        controller.stop()
        parentJob.cancel()
    }

    @Test
    fun syncContinuesRetryingPastFiveFailuresWithCappedBackoffUntilRecovery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appJob = SupervisorJob()
        val storeFile = tempFolder.newFile("app_state_backoff.json")
        var attempts = 0
        val attemptTimesMs = mutableListOf<Long>()
        val controller = AppStateSyncController(
            context = context,
            stateStore = StateStore(),
            applicationScope = CoroutineScope(appJob + dispatcher),
            ioDispatcher = dispatcher,
            dataStoreFactory = { scope ->
                DataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, StateStoreSerializer) {
                        storeFile.absolutePath.toPath()
                    },
                    scope = scope,
                )
            },
            syncRunner = { _, _ ->
                attempts += 1
                attemptTimesMs += testScheduler.currentTime
                if (attempts < 8) {
                    throw IOException("transient storage failure #$attempts")
                }
            },
            retryDelayMs = 100L,
            maxRetryDelayMs = 400L,
        )

        try {
            controller.start()
            advanceUntilIdle()

            assertEquals(8, attempts)
            // Backoff intervals: 100, 200, 400, 400, 400, 400, 400 ms -> total 2300 ms
            assertEquals(listOf(0L, 100L, 300L, 700L, 1100L, 1500L, 1900L, 2300L), attemptTimesMs)
        } finally {
            controller.stop()
            appJob.cancel()
        }
    }

    @Test
    fun ensureKeyRestoredPopulatesStateStoreBeforeReturning() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appJob = SupervisorJob()
        val storeFile = tempFolder.newFile("app_state_restore.json")
        val initialController = AppStateSyncController(
            context = context,
            stateStore = StateStore(),
            applicationScope = CoroutineScope(appJob + dispatcher),
            ioDispatcher = dispatcher,
            dataStoreFactory = { scope ->
                DataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, StateStoreSerializer) {
                        storeFile.absolutePath.toPath()
                    },
                    scope = scope,
                )
            },
        )
        initialController.persistKey(
            AiDraftSessionKey,
            AiDraftSessionState(
                savedHistory = listOf(SavedDraftEntry(draftId = "draft-cold-restore", transactionId = 505L)),
            ),
        )
        initialController.stop()

        val restoredStore = StateStore()
        val restoredController = AppStateSyncController(
            context = context,
            stateStore = restoredStore,
            applicationScope = CoroutineScope(appJob + dispatcher),
            ioDispatcher = dispatcher,
            dataStoreFactory = { scope ->
                DataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, StateStoreSerializer) {
                        storeFile.absolutePath.toPath()
                    },
                    scope = scope,
                )
            },
        )
        try {
            restoredController.ensureKeyRestored(AiDraftSessionKey)
            val restoredState = restoredStore.getState(AiDraftSessionKey).value
            assertEquals(listOf(SavedDraftEntry("draft-cold-restore", 505L)), restoredState.savedHistory)
        } finally {
            restoredController.stop()
            appJob.cancel()
        }
    }
}
