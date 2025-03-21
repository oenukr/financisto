package ru.orangesoftware.financisto.reports

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.legend.FlowLegend
import io.github.koalaplot.core.legend.LegendLocation
import io.github.koalaplot.core.pie.CircularLabelPositionProvider
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.pie.PieLabelPlacement
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import ru.orangesoftware.financisto.reports.PieChartViewModel.Companion.INTENT_KEY
import ru.orangesoftware.financisto.reports.PieChartViewModel.Companion.SCREEN_DENTITY_KEY
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import kotlin.math.abs

class PieChartActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyPreferences.switchLocale(newBase))
    }

    @OptIn(ExperimentalKoalaPlotApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
            val context = LocalContext.current
            val screenDensity = LocalDensity.current

            val pieChartViewModel: PieChartViewModel = viewModel(
                factory = PieChartViewModel.Factory,
                extras = MutableCreationExtras(defaultViewModelCreationExtras).apply {
                    set(APPLICATION_KEY, application)
                    set(SCREEN_DENTITY_KEY, screenDensity.density)
                    set(INTENT_KEY, intent)
                },
            )

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> pieChartViewModel.open()
                        Lifecycle.Event.ON_PAUSE -> PinProtection.lock(context)
                        Lifecycle.Event.ON_RESUME -> PinProtection.unlock(context)
                        Lifecycle.Event.ON_STOP -> pieChartViewModel.close()
                        else -> Unit
                    }
                }

                // Add the observer to the lifecycle
                lifecycleOwner.lifecycle.addObserver(observer)

                // When the effect leaves the Composition, remove the observer
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

//            LaunchedEffect("calculations") {
//                pieChartViewModel.initiateReport(intent, true, screenDensity.density)
//            }

            val chartData = pieChartViewModel.pieChartData.collectAsStateWithLifecycle()

            if (chartData.value.isNotEmpty()) {
                ChartLayout(
                    modifier = Modifier.fillMaxSize(),
                    legend = {
                        FlowLegend(
                            itemCount = chartData.value.size,
                            symbol = { i ->
                                Symbol(
                                    modifier = Modifier.size(2.dp),
                                    fillBrush = SolidColor(chartData.value[i].color)
                                )
                            },
                            label = { i ->
                                Text(chartData.value[i].partName)
                            },
                            modifier = Modifier
                                .padding(2.dp)
                                .border(1.dp, Color.Black)
                                .padding(2.dp)
                        )
                    },
                    legendLocation = LegendLocation.BOTTOM,
                ) {
                    PieChart(
                        values = chartData.value.map { abs(it.data.toFloat()) },
                        labelPositionProvider = CircularLabelPositionProvider(
                            labelSpacing = 1.0F,
                            labelPlacement = PieLabelPlacement.External,
                        ),
                    )
                }
            } else {
                Text(text = "No data", style = TextStyle(color = Color.Black))
            }

            intent.extras?.let {
                val chartTitle = it.getString(CHART_TITLE) ?: ""
                setTitle(chartTitle)
            }
        }
    }

    companion object {
        const val CHART_DATA = "chart_data"
        const val CHART_TITLE = "chart_title"
    }
}
