// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appstate.transform.transform
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateContainer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.orangesoftware.financisto.R

enum class AboutTab(
    @param:StringRes val titleRes: Int,
    val url: String,
) {
    WHATS_NEW(R.string.whats_new, "file:///android_asset/whatsnew.htm"),
    PRIVACY_POLICY(R.string.privacy_policy, "https://financisto.com/privacy.html"),
    LICENSE(R.string.license, "file:///android_asset/gpl-2.0-standalone.htm"),
    ABOUT(R.string.about, "file:///android_asset/about.htm"),
}

data class AboutUiState(
    val appVersion: String,
    val selectedTab: AboutTab = AboutTab.WHATS_NEW,
    val tabs: List<AboutTab> = AboutTab.entries,
)

@Suppress("InjectDispatcher")
class AboutViewModel(
    savedStateHandle: SavedStateHandle,
    private val appVersion: String,
    transformDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = stateScope ?: viewModelScope
    private val selectedTabOverride = mutableStateOf<AboutTab?>(null)

    private val container = SavedStateContainer().apply {
        savedStateHandle.get<Bundle>(KEY_TRANSFORM_CONTAINER)?.let(::restoreState)
        savedStateHandle.setSavedStateProvider(KEY_TRANSFORM_CONTAINER) { saveState() }
    }

    val uiState: State<AboutUiState> = transform(
        initialValue = AboutUiState(appVersion = appVersion),
        scope = scope,
        container = container,
        dispatcher = transformDispatcher,
    ) {
        var savedTabOrdinal by rememberSaveable { mutableIntStateOf(AboutTab.WHATS_NEW.ordinal) }
        val resolvedTab = selectedTabOverride.value
            ?: AboutTab.entries.getOrElse(savedTabOrdinal) { AboutTab.WHATS_NEW }
        SideEffect {
            if (savedTabOrdinal != resolvedTab.ordinal) {
                Snapshot.withMutableSnapshot {
                    savedTabOrdinal = resolvedTab.ordinal
                }
            }
        }
        AboutUiState(
            appVersion = appVersion,
            selectedTab = resolvedTab,
            tabs = AboutTab.entries,
        )
    }

    fun selectTab(tab: AboutTab) {
        Snapshot.withMutableSnapshot {
            selectedTabOverride.value = tab
        }
    }

    companion object {
        internal const val KEY_TRANSFORM_CONTAINER = "about_transform_container"
    }
}
