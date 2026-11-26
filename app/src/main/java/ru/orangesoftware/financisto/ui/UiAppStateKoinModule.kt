// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.ui

import android.net.Uri
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.orangesoftware.financisto.activity.AboutViewModel
import ru.orangesoftware.financisto.app.TRANSFORM_DISPATCHER

val uiAppStateModule = module {
    viewModel { (appVersion: String) ->
        AboutViewModel(
            savedStateHandle = get(),
            appVersion = appVersion,
            transformDispatcher = get(TRANSFORM_DISPATCHER),
        )
    }
    viewModel {
        val appContext = androidContext()
        val hasPersistedUriPermission: (Uri) -> Boolean = { uri ->
            appContext.contentResolver.persistedUriPermissions.any { it.uri == uri }
        }
        PermissionsViewModel(
            preferencesStore = get(),
            hasPersistedUriPermission = hasPersistedUriPermission,
            ioDispatcher = get(),
            transformDispatcher = get(TRANSFORM_DISPATCHER),
            applicationScope = get(),
        )
    }
}
