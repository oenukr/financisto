// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.Utils

class AboutActivity : ComponentActivity() {

    private val viewModel: AboutViewModel by viewModel {
        parametersOf(getAppVersion(this))
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState
            FinancistoTheme {
                AboutScreen(
                    uiState = uiState,
                    onTabSelected = viewModel::selectTab,
                )
            }
        }
    }

    @Composable
    fun AboutScreen(
        uiState: AboutUiState,
        onTabSelected: (AboutTab) -> Unit,
    ) {
        val selectedIndex = uiState.tabs.indexOf(uiState.selectedTab).coerceAtLeast(0)
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Financisto (${uiState.appVersion})") })
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(selectedTabIndex = selectedIndex) {
                    uiState.tabs.forEach { tab ->
                        val isSelected = uiState.selectedTab == tab
                        Tab(
                            text = { Text(stringResource(id = tab.titleRes)) },
                            selected = isSelected,
                            onClick = { onTabSelected(tab) },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (isSelected) {
                                            R.drawable.ic_tab_android_active
                                        } else {
                                            R.drawable.ic_tab_android_inactive
                                        }
                                    ),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
                key(uiState.selectedTab) {
                    WebView(rememberWebViewState(url = uiState.selectedTab.url))
                }
            }
        }
    }

    companion object {
        fun getAppVersion(context: Context): String {
            return try {
                "v. " + Utils.getPackageInfo(context)?.versionName.orEmpty()
            } catch (ignored: NameNotFoundException) {
                ""
            }
        }
    }
}
