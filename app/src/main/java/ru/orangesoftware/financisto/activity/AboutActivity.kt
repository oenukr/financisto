package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.Utils

class AboutActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinancistoTheme {
                AboutScreen()
            }
        }
    }

    @Composable
    fun FinancistoTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            content = content
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AboutScreen() {
        var state by remember { mutableIntStateOf(0) }
        val titles = listOf(
            R.string.whats_new,
            R.string.privacy_policy,
            R.string.license,
            R.string.about,
        )

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Financisto (${getAppVersion(this)})") })
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(selectedTabIndex = state) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(stringResource(id = title)) },
                            selected = state == index,
                            onClick = { state = index },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (state == index) {
                                            R.drawable.ic_tab_android_active
                                        } else {
                                            R.drawable.ic_tab_android_inactive
                                        }
                                    ),
                                    contentDescription = null
                                )
                            },
                        )
                    }
                }
                when(state) {
                    0 -> WhatsNewContent()
                    1 -> PrivacyPolicyContent()
                    2 -> LicenseContent()
                    3 -> AboutContent()
                }
            }
        }
    }

    @Composable
    fun WhatsNewContent() {
        val url = "file:///android_asset/whatsnew.htm"
        val state = rememberWebViewState(url = url)
        WebView(state)
    }

    @Composable
    fun PrivacyPolicyContent() {
        val url = "https://financisto.com/privacy.html"
        val state = rememberWebViewState(url = url)
        WebView(state)
    }

    @Composable
    fun LicenseContent() {
        val url = "file:///android_asset/gpl-2.0-standalone.htm"
        val state = rememberWebViewState(url = url)
        WebView(state)
    }

    @Composable
    fun AboutContent() {
        val url = "file:///android_asset/about.htm"
        val state = rememberWebViewState(url = url)
        WebView(state)
    }

    companion object {
        fun getAppVersion(context: Context): String {
            return try {
                "v. " + Utils.getPackageInfo(context)?.versionName.orEmpty()
            } catch (e: NameNotFoundException) {
                ""
            }
        }
    }
}
