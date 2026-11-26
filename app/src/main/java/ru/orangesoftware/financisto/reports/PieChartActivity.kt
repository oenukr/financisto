// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.reports

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.legend.FlowLegend
import io.github.koalaplot.core.legend.LegendLocation
import io.github.koalaplot.core.pie.CircularLabelPositionProvider
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.pie.PieLabelPlacement
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import kotlin.math.abs

class PieChartActivity : ComponentActivity() {

    private val pieChartViewModel: PieChartViewModel by viewModel {
        parametersOf(buildLaunchParams())
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyPreferences.switchLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
            val context = LocalContext.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> PinProtection.lock(context)
                        Lifecycle.Event.ON_RESUME -> PinProtection.unlock(context)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val uiState by pieChartViewModel.uiState
            val title = when (val state = uiState) {
                PieChartUiState.Loading -> intent.getStringExtra(CHART_TITLE).orEmpty()
                is PieChartUiState.Empty -> state.title
                is PieChartUiState.Success -> state.title
                is PieChartUiState.Error -> state.title
            }

            LaunchedEffect(title) {
                if (title.isNotEmpty()) {
                    setTitle(title)
                }
            }

            FinancistoTheme {
                PieChartContent(uiState = uiState)
            }
        }
    }

    private fun buildLaunchParams(): PieChartLaunchParams =
        PieChartLaunchParams.fromIntent(intent, resources.displayMetrics.density)

    @Composable
    private fun PieChartContent(uiState: PieChartUiState) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (uiState) {
                PieChartUiState.Loading -> CenteredContent { CircularProgressIndicator() }

                is PieChartUiState.Empty -> CenteredContent {
                    Text(
                        text = stringResource(id = R.string.no_data),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                is PieChartUiState.Error -> CenteredContent {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is PieChartUiState.Success -> PieChartSuccess(chartData = uiState.chartData)
            }
        }
    }

    @Composable
    private fun CenteredContent(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }

    @OptIn(ExperimentalKoalaPlotApi::class)
    @Composable
    private fun PieChartSuccess(chartData: List<ChartData>) {
        ChartLayout(
            modifier = Modifier.fillMaxSize(),
            legend = {
                FlowLegend(
                    itemCount = chartData.size,
                    symbol = { i ->
                        Symbol(
                            modifier = Modifier.size(2.dp),
                            fillBrush = SolidColor(chartData[i].color),
                        )
                    },
                    label = { i ->
                        Text(chartData[i].partName)
                    },
                    modifier = Modifier
                        .padding(2.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .padding(2.dp),
                )
            },
            legendLocation = LegendLocation.BOTTOM,
        ) {
            PieChart(
                values = chartData.map { abs(it.data.toFloat()) },
                labelPositionProvider = CircularLabelPositionProvider(
                    labelSpacing = 1.0F,
                    labelPlacement = PieLabelPlacement.External,
                ),
            )
        }
    }

    companion object {
        const val CHART_DATA = "chart_data"
        const val CHART_TITLE = "chart_title"
    }
}
