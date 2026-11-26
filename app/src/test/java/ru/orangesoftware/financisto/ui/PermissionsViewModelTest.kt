// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.ui

import android.net.Uri
import androidx.appstate.statestore.StateStore
import androidx.appstate.transform.testing.transformTest
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ru.orangesoftware.financisto.appstate.BackupFolderUriKey
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.persistance.PreferencesStore
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PermissionsViewModelTest {

    private lateinit var stateStore: StateStore
    private lateinit var preferencesStore: PreferencesStore

    @Before
    fun setUp() {
        val koin = GlobalContext.get()
        stateStore = koin.get()
        preferencesStore = koin.get()
        Snapshot.withMutableSnapshot {
            stateStore.setState(BackupFolderUriKey, Export.DEFAULT_EXPORT_PATH.absolutePath)
        }
    }

    @Test
    fun reactsToBackupFolderUriKeyUpdatesInStateStore() = transformTest {
        val grantedUri = "content://com.android.externalstorage.documents/tree/primary%3ABackups".toUri()
        val viewModel = PermissionsViewModel(
            preferencesStore = preferencesStore,
            hasPersistedUriPermission = { uri -> uri == grantedUri },
            ioDispatcher = testDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )

        advance()
        advanceUntilIdle()
        advance()
        assertFalse(viewModel.uiState.value.isStoragePermissionGranted)

        Snapshot.withMutableSnapshot {
            stateStore.setState(BackupFolderUriKey, grantedUri.toString())
        }
        advance()
        advanceUntilIdle()
        advance()

        assertEquals(grantedUri, viewModel.uiState.value.backupFolderUri)
        assertTrue(viewModel.uiState.value.isStoragePermissionGranted)
    }

    @Test
    fun onBackupFolderSelectedUpdatesPreferencesStoreAndPermissionState() = transformTest {
        val grantedUris = mutableSetOf<Uri>()
        val viewModel = PermissionsViewModel(
            preferencesStore = preferencesStore,
            hasPersistedUriPermission = { uri -> uri in grantedUris },
            ioDispatcher = testDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )

        advance()
        advanceUntilIdle()
        advance()
        assertFalse(viewModel.uiState.value.isStoragePermissionGranted)

        val selectedUri = "content://com.android.externalstorage.documents/tree/primary%3AFinancisto".toUri()
        grantedUris.add(selectedUri)
        viewModel.onBackupFolderSelected(selectedUri)
        advance()
        advanceUntilIdle()
        advance()

        assertEquals(selectedUri.toString(), stateStore.getState(BackupFolderUriKey).value)
        assertEquals(selectedUri, viewModel.uiState.value.backupFolderUri)
        assertTrue(viewModel.uiState.value.isStoragePermissionGranted)
    }

    @Test
    fun refreshPermissionsRechecksCurrentUriPermission() = transformTest {
        val folderUri = "content://com.android.externalstorage.documents/tree/primary%3ADownloads".toUri()
        Snapshot.withMutableSnapshot {
            stateStore.setState(BackupFolderUriKey, folderUri.toString())
        }
        var permissionGranted = false
        val viewModel = PermissionsViewModel(
            preferencesStore = preferencesStore,
            hasPersistedUriPermission = { uri -> uri == folderUri && permissionGranted },
            ioDispatcher = testDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )

        advance()
        advanceUntilIdle()
        advance()
        assertEquals(folderUri, viewModel.uiState.value.backupFolderUri)
        assertFalse(viewModel.uiState.value.isStoragePermissionGranted)

        permissionGranted = true
        viewModel.refreshPermissions()
        advance()
        advanceUntilIdle()
        advance()

        assertTrue(viewModel.uiState.value.isStoragePermissionGranted)
    }

    @Test
    fun preferencesStorePropagatesLegacyWriteFailureWithoutUpdatingStateStore() {
        val localStateStore = StateStore()
        val store = PreferencesStore(
            context = RuntimeEnvironment.getApplication(),
            stateStore = localStateStore,
        )
        val initialUri = localStateStore.getState(BackupFolderUriKey).value
        val failingUri = "content://com.android.externalstorage.documents/tree/primary%3AFailing".toUri()
        store.legacyFolderWriter = { throw IOException("Disk full") }

        val thrown = runCatching { store.updateBackupFolderUri(failingUri) }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertEquals(initialUri, localStateStore.getState(BackupFolderUriKey).value)
    }

    @Test
    fun preferencesStoreCommitsStateStoreAndLegacyDataStoreConsistently() {
        val targetUri = "content://com.android.externalstorage.documents/tree/primary%3AConsistent".toUri()
        preferencesStore.updateBackupFolderUri(targetUri)

        assertEquals(targetUri.toString(), stateStore.getState(BackupFolderUriKey).value)
        val persistedRx = preferencesStore.backupPreferencesRx.firstOrError().blockingGet()
        assertEquals(targetUri, persistedRx.folder)
    }

    @Test
    fun onBackupFolderSelectedCompletesWriteInApplicationScopeWhenViewModelScopeCancels() = transformTest {
        val localStateStore = StateStore()
        val localPreferencesStore = PreferencesStore(
            context = RuntimeEnvironment.getApplication(),
            stateStore = localStateStore,
        )
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        localPreferencesStore.legacyFolderWriter = { uriString ->
            started.countDown()
            assertTrue(release.await(2, TimeUnit.SECONDS))
            uriString
        }

        val viewModelScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val appScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val selectedUri = "content://com.android.externalstorage.documents/tree/primary%3ASurvived".toUri()
        val viewModel = PermissionsViewModel(
            preferencesStore = localPreferencesStore,
            hasPersistedUriPermission = { true },
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
            transformDispatcher = testDispatcher,
            stateScope = viewModelScope,
            applicationScope = appScope,
        )

        viewModel.onBackupFolderSelected(selectedUri)
        assertTrue(started.await(2, TimeUnit.SECONDS))
        viewModelScope.cancel()
        release.countDown()

        val deadlineMs = System.currentTimeMillis() + 2_000L
        while (localStateStore.getState(BackupFolderUriKey).value != selectedUri.toString() &&
            System.currentTimeMillis() < deadlineMs
        ) {
            Thread.sleep(10L)
        }
        assertEquals(selectedUri.toString(), localStateStore.getState(BackupFolderUriKey).value)
        appScope.cancel()
    }
}
