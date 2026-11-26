// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.annotation.SuppressLint
import androidx.appstate.transform.testing.transformTest
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.orangesoftware.financisto.R

@RunWith(RobolectricTestRunner::class)
class AboutViewModelTest {

    @Test
    fun defaultStateUsesWhatsNewTabAndAppVersion() = transformTest {
        val viewModel = AboutViewModel(
            savedStateHandle = SavedStateHandle(),
            appVersion = "v. 1.8.3",
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )
        advance()

        val state = viewModel.uiState.value
        assertEquals("v. 1.8.3", state.appVersion)
        assertEquals(AboutTab.WHATS_NEW, state.selectedTab)
        assertEquals(AboutTab.entries, state.tabs)
        assertEquals("file:///android_asset/whatsnew.htm", state.selectedTab.url)
    }

    @Test
    fun selectTabUpdatesSelectedTabAndUrl() = transformTest {
        val viewModel = AboutViewModel(
            savedStateHandle = SavedStateHandle(),
            appVersion = "v. 1.8.3",
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )
        advance()

        viewModel.selectTab(AboutTab.PRIVACY_POLICY)
        advance()
        assertEquals(AboutTab.PRIVACY_POLICY, viewModel.uiState.value.selectedTab)
        assertEquals("https://financisto.com/privacy.html", viewModel.uiState.value.selectedTab.url)

        viewModel.selectTab(AboutTab.LICENSE)
        advance()
        assertEquals(AboutTab.LICENSE, viewModel.uiState.value.selectedTab)
        assertEquals("file:///android_asset/gpl-2.0-standalone.htm", viewModel.uiState.value.selectedTab.url)

        viewModel.selectTab(AboutTab.ABOUT)
        advance()
        assertEquals(AboutTab.ABOUT, viewModel.uiState.value.selectedTab)
        assertEquals("file:///android_asset/about.htm", viewModel.uiState.value.selectedTab.url)
    }

    @Test
    fun tabMetadataMatchesExpectedUrlsAndStringResources() {
        assertEquals(R.string.whats_new, AboutTab.WHATS_NEW.titleRes)
        assertEquals("file:///android_asset/whatsnew.htm", AboutTab.WHATS_NEW.url)

        assertEquals(R.string.privacy_policy, AboutTab.PRIVACY_POLICY.titleRes)
        assertEquals("https://financisto.com/privacy.html", AboutTab.PRIVACY_POLICY.url)

        assertEquals(R.string.license, AboutTab.LICENSE.titleRes)
        assertEquals("file:///android_asset/gpl-2.0-standalone.htm", AboutTab.LICENSE.url)

        assertEquals(R.string.about, AboutTab.ABOUT.titleRes)
        assertEquals("file:///android_asset/about.htm", AboutTab.ABOUT.url)
    }

    @SuppressLint("RestrictedApi")
    @Test
    fun savedStateHandleRestoresSelectedTabViaSavedStateContainer() = transformTest {
        val originalHandle = SavedStateHandle()
        val viewModel1 = AboutViewModel(
            savedStateHandle = originalHandle,
            appVersion = "v. 1.8.3",
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )
        advance()

        viewModel1.selectTab(AboutTab.LICENSE)
        advance()
        advanceUntilIdle()
        advance()
        assertEquals(AboutTab.LICENSE, viewModel1.uiState.value.selectedTab)

        val savedBundle = originalHandle.savedStateProvider().saveState()
        val restoredHandle = SavedStateHandle.createHandle(savedBundle, null)

        val viewModel2 = AboutViewModel(
            savedStateHandle = restoredHandle,
            appVersion = "v. 1.8.3",
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )
        advance()

        assertEquals(AboutTab.LICENSE, viewModel2.uiState.value.selectedTab)
    }
}
