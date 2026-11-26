// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.reports

import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import android.os.Bundle
import androidx.appstate.transform.transform
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.activity.ReportActivity.Companion.FILTER_INCOME_EXPENSE
import ru.orangesoftware.financisto.activity.ReportsListActivity
import ru.orangesoftware.financisto.activity.ReportsListActivity.EXTRA_REPORT_TYPE
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.graph.GraphUnit
import ru.orangesoftware.financisto.report.IncomeExpense
import ru.orangesoftware.financisto.report.Report
import ru.orangesoftware.financisto.report.ReportData
import timber.log.Timber
import kotlin.math.abs

data class ChartData(
    val data: Double,
    val color: Color,
    val partName: String,
)

data class PieChartLaunchParams(
    val reportTypeName: String,
    val chartTitle: String,
    val extras: Bundle?,
    val initialIntentFilter: WhereFilter,
    val explicitIncomeExpense: IncomeExpense?,
    val screenDensity: Float,
) {
    companion object {
        fun fromIntent(intent: Intent, screenDensity: Float): PieChartLaunchParams {
            val reportTypeName = intent.getStringExtra(EXTRA_REPORT_TYPE).orEmpty()
            val chartTitle = intent.getStringExtra(PieChartActivity.CHART_TITLE)
                ?: reportTypeName
            val explicitIncomeExpense = intent.getStringExtra(FILTER_INCOME_EXPENSE)
                ?.let { raw -> IncomeExpense.entries.firstOrNull { it.name == raw } }
            return PieChartLaunchParams(
                reportTypeName = reportTypeName,
                chartTitle = chartTitle,
                extras = intent.extras,
                initialIntentFilter = WhereFilter.fromIntent(intent),
                explicitIncomeExpense = explicitIncomeExpense,
                screenDensity = screenDensity,
            )
        }
    }
}

sealed interface PieChartUiState {
    data object Loading : PieChartUiState

    data class Empty(val title: String) : PieChartUiState

    data class Success(
        val title: String,
        val chartData: List<ChartData>,
    ) : PieChartUiState

    data class Error(
        val title: String,
        val message: String,
    ) : PieChartUiState
}

@Suppress("InjectDispatcher")
class PieChartViewModel(
    private val launchParams: PieChartLaunchParams,
    private val db: DatabaseAdapter,
    private val preferences: SharedPreferences,
    savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transformDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateScope: CoroutineScope? = null,
    private val reportFactory: (DatabaseAdapter, PieChartLaunchParams) -> Report = ::defaultCreatePieReport,
) : ViewModel() {

    private val usePrefs: Boolean = launchParams.initialIntentFilter.isEmpty

    private val initialFilter: WhereFilter = if (usePrefs) {
        WhereFilter.fromSharedPreferences(preferences)
    } else {
        WhereFilter.copyOf(launchParams.initialIntentFilter)
    }

    private val initialIncomeExpense: IncomeExpense =
        launchParams.explicitIncomeExpense ?: if (usePrefs) {
            readPieIncomeExpense(preferences)
        } else {
            IncomeExpense.BOTH
        }

    private val container = SavedStateContainer().apply {
        savedStateHandle.get<Bundle>(KEY_TRANSFORM_CONTAINER)?.let(::restoreState)
    }

    init {
        savedStateHandle.setSavedStateProvider(KEY_TRANSFORM_CONTAINER) {
            container.saveState()
        }
    }

    val uiState: State<PieChartUiState> = transform<PieChartUiState>(
        initialValue = PieChartUiState.Loading,
        scope = stateScope ?: viewModelScope,
        container = container,
        dispatcher = transformDispatcher,
    ) {
        var currentState by remember { mutableStateOf<PieChartUiState>(PieChartUiState.Loading) }

        LaunchedEffect(Unit) {
            try {
                val chartData = loadChartData()
                Snapshot.withMutableSnapshot {
                    currentState = if (chartData.isEmpty()) {
                        PieChartUiState.Empty(title = launchParams.chartTitle)
                    } else {
                        PieChartUiState.Success(
                            title = launchParams.chartTitle,
                            chartData = chartData,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLiteException) {
                Timber.e(e, "Failed to load pie chart %s", launchParams.reportTypeName)
                Snapshot.withMutableSnapshot {
                    currentState = PieChartUiState.Error(
                        title = launchParams.chartTitle,
                        message = e.localizedMessage ?: DEFAULT_ERROR_MESSAGE,
                    )
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Invalid pie chart configuration for %s", launchParams.reportTypeName)
                Snapshot.withMutableSnapshot {
                    currentState = PieChartUiState.Error(
                        title = launchParams.chartTitle,
                        message = e.localizedMessage ?: DEFAULT_ERROR_MESSAGE,
                    )
                }
            }
        }

        currentState
    }

    private suspend fun loadChartData(): List<ChartData> {
        val reportData: ReportData? = withContext(ioDispatcher) {
            val report = reportFactory(db, launchParams)
            report.setIncomeExpense(initialIncomeExpense)
            report.getReportForChart(db, WhereFilter.copyOf(initialFilter))
        }
        val units = reportData?.units.orEmpty()
        return withContext(transformDispatcher) {
            buildChartData(units)
        }
    }

    companion object {
        private const val KEY_TRANSFORM_CONTAINER = "pie_chart_transform_container"
        private const val DEFAULT_ERROR_MESSAGE = "Failed to load pie chart"
        private const val FULL_CIRCLE_DEGREES = 360f
        private const val DEFAULT_SATURATION = 0.75f
        private const val DEFAULT_VALUE = 0.85f

        private fun buildChartData(units: List<GraphUnit>): List<ChartData> {
            val colors = generateColors(2 * units.size)
            val chartData = ArrayList<ChartData>(2 * units.size)
            var index = 0
            units.forEach { unit ->
                chartData.add(
                    ChartData(
                        data = abs(unit.incomeExpense.income.toDouble()),
                        color = colors[index++],
                        partName = "${unit.name} ${unit.incomeExpense.income}",
                    ),
                )
                chartData.add(
                    ChartData(
                        data = abs(unit.incomeExpense.expense.toDouble()),
                        color = colors[index++],
                        partName = "${unit.name} ${unit.incomeExpense.expense}",
                    ),
                )
            }
            return chartData
        }

        private fun generateColors(n: Int): List<Color> {
            if (n == 0) return emptyList()
            return List(n) { i ->
                Color.hsv(
                    hue = FULL_CIRCLE_DEGREES * i.toFloat() / n.toFloat(),
                    saturation = DEFAULT_SATURATION,
                    value = DEFAULT_VALUE,
                    colorSpace = ColorSpaces.DisplayP3,
                )
            }
        }
    }
}

private fun readPieIncomeExpense(preferences: SharedPreferences): IncomeExpense {
    val raw = preferences.getString(FILTER_INCOME_EXPENSE, IncomeExpense.BOTH.name)
    return IncomeExpense.entries.firstOrNull { it.name == raw } ?: IncomeExpense.BOTH
}

private fun defaultCreatePieReport(db: DatabaseAdapter, params: PieChartLaunchParams): Report {
    val extras = params.extras ?: Bundle().apply {
        putString(EXTRA_REPORT_TYPE, params.reportTypeName)
    }
    return requireNotNull(
        ReportsListActivity.createReport(db, extras, true, params.screenDensity),
    ) {
        "Unsupported report type: ${params.reportTypeName}"
    }
}
