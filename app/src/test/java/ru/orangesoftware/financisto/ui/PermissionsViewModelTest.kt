// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.ui

import android.net.Uri
import androidx.appstate.statestore.StateStore
import androidx.appstate.transform.testing.transformTest
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import ru.orangesoftware.financisto.appstate.BackupFolderUriKey
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.persistance.PreferencesStore

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
}
