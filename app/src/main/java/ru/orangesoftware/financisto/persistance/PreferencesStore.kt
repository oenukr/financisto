// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.persistance

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.appstate.statestore.StateStore
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rxjava3.rxPreferencesDataStore
import androidx.datastore.rxjava3.RxDataStore
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.appstate.BackupFolderUriKey
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.persistance.PreferencesStore.PreferencesKeys.BACKUP_FOLDER_URI
import ru.orangesoftware.financisto.persistance.PreferencesStore.PreferencesKeys.defaultPath
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class PreferencesStore(
    private val context: Context,
    private val stateStore: StateStore = StateStore(),
    private val applicationScope: CoroutineScope? = null,
) {
    private val Context.dataStoreRx: RxDataStore<Preferences> by rxPreferencesDataStore(name = "settings")
    private val seededFromLegacy = AtomicBoolean(false)

    private object PreferencesKeys {
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val defaultPath = Export.DEFAULT_EXPORT_PATH.absolutePath
    }

    val backupFolderUriState: State<String> = stateStore.getState(BackupFolderUriKey)

    @OptIn(ExperimentalCoroutinesApi::class)
    val backupPreferencesRx: Flowable<BackupPreferences> = context.dataStoreRx.data()
        .map { preferences ->
            val folder = preferences[BACKUP_FOLDER_URI] ?: defaultPath
            BackupPreferences(folder.toUri())
        }

    @SuppressLint("CheckResult")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun seedFromLegacyIfNeeded() {
        if (!seededFromLegacy.compareAndSet(false, true)) return
        context.dataStoreRx.data()
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { preferences ->
                    val legacyFolder = preferences[BACKUP_FOLDER_URI]
                    if (!legacyFolder.isNullOrBlank() && legacyFolder != defaultPath) {
                        val applySeed = {
                            Snapshot.withMutableSnapshot {
                                if (stateStore.getState(BackupFolderUriKey).value == defaultPath) {
                                    stateStore.setState(BackupFolderUriKey, legacyFolder)
                                }
                            }
                        }
                        if (applicationScope != null) {
                            applicationScope.launch { applySeed() }
                        } else {
                            applySeed()
                        }
                    }
                },
                { error ->
                    Timber.w(error, "Failed to seed backup folder URI from legacy DataStore")
                },
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun updateBackupFolderUri(folder: Uri) {
        val uriString = folder.toString()
        Snapshot.withMutableSnapshot {
            stateStore.setState(BackupFolderUriKey, uriString)
        }
        context.dataStoreRx.updateDataAsync { preferences ->
            val mutablePreferences = preferences.toMutablePreferences()
            mutablePreferences[BACKUP_FOLDER_URI] = uriString
            Single.just(mutablePreferences)
        }
    }
}

data class BackupPreferences(val folder: Uri)
