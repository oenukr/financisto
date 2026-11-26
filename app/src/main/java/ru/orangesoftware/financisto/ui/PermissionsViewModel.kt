// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.ui

import android.net.Uri
import androidx.appstate.transform.transform
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.persistance.PreferencesStore
import timber.log.Timber
import java.io.IOException

data class PermissionsUiState(
    val backupFolderUri: Uri,
    val isStoragePermissionGranted: Boolean = false,
)

@Suppress("InjectDispatcher")
class PermissionsViewModel(
    private val preferencesStore: PreferencesStore,
    private val hasPersistedUriPermission: (Uri) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    transformDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateScope: CoroutineScope? = null,
    private val applicationScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = stateScope ?: viewModelScope
    private val refreshNonce = mutableIntStateOf(0)

    val uiState: State<PermissionsUiState> = transform(
        initialValue = PermissionsUiState(
            backupFolderUri = preferencesStore.backupFolderUriState.value.toUri(),
        ),
        scope = scope,
        dispatcher = transformDispatcher,
    ) {
        val folderUri = preferencesStore.backupFolderUriState.value.toUri()
        val nonce = refreshNonce.intValue
        var isGranted by remember { mutableStateOf(false) }

        LaunchedEffect(folderUri, nonce) {
            val granted = withContext(ioDispatcher) {
                hasPersistedUriPermission(folderUri)
            }
            Snapshot.withMutableSnapshot {
                isGranted = granted
            }
        }

        PermissionsUiState(
            backupFolderUri = folderUri,
            isStoragePermissionGranted = isGranted,
        )
    }

    fun onBackupFolderSelected(uri: Uri) {
        val writeScope = applicationScope ?: scope
        val writeJob = writeScope.launch(ioDispatcher) {
            try {
                preferencesStore.updateBackupFolderUri(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Timber.e(e, "Failed to persist backup folder URI: %s", uri)
            }
        }
        scope.launch(ioDispatcher) {
            writeJob.join()
            Snapshot.withMutableSnapshot {
                refreshNonce.intValue += 1
            }
        }
    }

    fun refreshPermissions() {
        Snapshot.withMutableSnapshot {
            refreshNonce.intValue += 1
        }
    }
}
