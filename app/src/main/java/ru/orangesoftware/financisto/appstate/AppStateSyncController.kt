// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appstate

import android.content.Context
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppStateSyncController(
    private val context: Context,
    private val stateStore: StateStore,
    private val applicationScope: CoroutineScope,
) {
    private val lock = Any()
    private var syncJob: Job? = null

    fun start() {
        synchronized(lock) {
            if (syncJob?.isActive == true) return
            val path = context.dataStoreFile(DATA_STORE_FILE_NAME).absolutePath
            syncJob = applicationScope.launch {
                stateStore.syncToDataStore(
                    path = path,
                    coroutineScope = applicationScope,
                )
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            syncJob?.cancel()
            syncJob = null
        }
    }

    private companion object {
        const val DATA_STORE_FILE_NAME = "financisto_app_state.json"
    }
}
