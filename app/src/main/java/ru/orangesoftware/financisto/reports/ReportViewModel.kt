// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.reports

import android.app.Activity.RESULT_FIRST_USER
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import android.os.Bundle
import androidx.appstate.transform.transform
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.content.edit
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
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.report.IncomeExpense
import ru.orangesoftware.financisto.report.Report
import ru.orangesoftware.financisto.report.ReportData
import timber.log.Timber

data class ReportLaunchParams(
    val reportTypeName: String,
    val extras: Bundle?,
    val initialIntentFilter: WhereFilter,
    val explicitIncomeExpense: IncomeExpense?,
    val screenDensity: Float,
) {
    companion object {
        fun fromIntent(intent: Intent, screenDensity: Float): ReportLaunchParams {
            val reportTypeName = intent.getStringExtra(EXTRA_REPORT_TYPE).orEmpty()
            val explicitIncomeExpense = intent.getStringExtra(FILTER_INCOME_EXPENSE)
                ?.let { raw -> IncomeExpense.entries.firstOrNull { it.name == raw } }
            return ReportLaunchParams(
                reportTypeName = reportTypeName,
                extras = intent.extras,
                initialIntentFilter = WhereFilter.fromIntent(intent),
                explicitIncomeExpense = explicitIncomeExpense,
                screenDensity = screenDensity,
            )
        }
    }
}

data class ReportUiState(
    val isLoading: Boolean = true,
    val currentReport: Report? = null,
    val filter: WhereFilter = WhereFilter.empty(),
    val incomeExpense: IncomeExpense = IncomeExpense.BOTH,
    val graphUnits: List<GraphUnit> = emptyList(),
    val total: Total = Total.ZERO,
    val errorMessage: String? = null,
)

@Suppress("InjectDispatcher")
class ReportViewModel(
    private val launchParams: ReportLaunchParams,
    private val db: DatabaseAdapter,
    private val preferences: SharedPreferences,
    savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transformDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateScope: CoroutineScope? = null,
    private val reportFactory: (DatabaseAdapter, ReportLaunchParams) -> Report = ::defaultCreateReport,
) : ViewModel() {

    private val saveFilter: Boolean = launchParams.initialIntentFilter.isEmpty

    private val filterState = mutableStateOf(
        if (saveFilter) {
            WhereFilter.fromSharedPreferences(preferences)
        } else {
            WhereFilter.copyOf(launchParams.initialIntentFilter)
        },
    )

    private val incomeExpenseState = mutableStateOf(
        launchParams.explicitIncomeExpense ?: if (saveFilter) {
            readIncomeExpense(preferences)
        } else {
            IncomeExpense.BOTH
        },
    )

    private val refreshNonce = mutableIntStateOf(0)

    private val container = SavedStateContainer().apply {
        savedStateHandle.get<Bundle>(KEY_TRANSFORM_CONTAINER)?.let(::restoreState)
    }

    init {
        savedStateHandle.setSavedStateProvider(KEY_TRANSFORM_CONTAINER) {
            container.saveState()
        }
    }

    val uiState: State<ReportUiState> = transform(
        initialValue = ReportUiState(
            isLoading = true,
            filter = filterState.value,
            incomeExpense = incomeExpenseState.value,
        ),
        scope = stateScope ?: viewModelScope,
        container = container,
        dispatcher = transformDispatcher,
    ) {
        val currentFilter = filterState.value
        val currentIncomeExpense = incomeExpenseState.value
        val nonce = refreshNonce.intValue

        var isLoading by remember { mutableStateOf(true) }
        var currentReport by remember { mutableStateOf<Report?>(null) }
        var graphUnits by remember { mutableStateOf<List<GraphUnit>>(emptyList()) }
        var total by remember { mutableStateOf(Total.ZERO) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(currentFilter, currentIncomeExpense, nonce) {
            Snapshot.withMutableSnapshot {
                isLoading = true
                errorMessage = null
            }
            try {
                val (report, reportData) = loadReportData(currentFilter, currentIncomeExpense)
                Snapshot.withMutableSnapshot {
                    currentReport = report
                    graphUnits = reportData?.units.orEmpty()
                    total = reportData?.total ?: Total.ZERO
                    isLoading = false
                    errorMessage = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLiteException) {
                Timber.e(e, "Failed to load report %s", launchParams.reportTypeName)
                Snapshot.withMutableSnapshot {
                    isLoading = false
                    errorMessage = e.localizedMessage ?: DEFAULT_ERROR_MESSAGE
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Invalid report configuration for %s", launchParams.reportTypeName)
                Snapshot.withMutableSnapshot {
                    isLoading = false
                    errorMessage = e.localizedMessage ?: DEFAULT_ERROR_MESSAGE
                }
            }
        }

        ReportUiState(
            isLoading = isLoading,
            currentReport = currentReport,
            filter = currentFilter,
            incomeExpense = currentIncomeExpense,
            graphUnits = graphUnits,
            total = total,
            errorMessage = errorMessage,
        )
    }

    private suspend fun loadReportData(
        filter: WhereFilter,
        incomeExpense: IncomeExpense,
    ): Pair<Report, ReportData?> = withContext(ioDispatcher) {
        val report = reportFactory(db, launchParams)
        report.setIncomeExpense(incomeExpense)
        val data = report.getReport(db, WhereFilter.copyOf(filter))
        report to data
    }

    fun updateFilter(resultCode: Int, resultData: Intent?) {
        val updatedFilter = when (resultCode) {
            RESULT_FIRST_USER -> WhereFilter.empty()
            RESULT_OK -> resultData?.let(WhereFilter::fromIntent) ?: WhereFilter.empty()
            else -> filterState.value
        }
        Snapshot.withMutableSnapshot {
            filterState.value = updatedFilter
        }
        persistFilterIfEnabled(updatedFilter, incomeExpenseState.value)
    }

    fun toggleIncomeExpense() {
        val values = IncomeExpense.entries
        val nextIndex = (incomeExpenseState.value.ordinal + 1) % values.size
        val nextIncomeExpense = values[nextIndex]
        Snapshot.withMutableSnapshot {
            incomeExpenseState.value = nextIncomeExpense
        }
        persistFilterIfEnabled(filterState.value, nextIncomeExpense)
    }

    fun refresh() {
        Snapshot.withMutableSnapshot {
            refreshNonce.intValue += 1
        }
    }

    fun createRowClickIntent(id: Long, context: Context): Intent? {
        return uiState.value.currentReport?.createActivityIntent(
            context,
            db,
            WhereFilter.copyOf(filterState.value),
            id,
        )
    }

    fun createPieChartIntent(context: Context): Intent {
        val reportTypeName = uiState.value.currentReport?.reportType?.name
            ?: launchParams.reportTypeName
        return Intent(context, PieChartActivity::class.java).apply {
            putExtra(PieChartActivity.CHART_TITLE, reportTypeName)
            putExtra(EXTRA_REPORT_TYPE, reportTypeName)
            putExtra(FILTER_INCOME_EXPENSE, incomeExpenseState.value.name)
            filterState.value.toIntent(this)
        }
    }

    private fun persistFilterIfEnabled(filter: WhereFilter, incomeExpense: IncomeExpense) {
        if (!saveFilter) return
        filter.toSharedPreferences(preferences)
        preferences.edit {
            putString(FILTER_INCOME_EXPENSE, incomeExpense.name)
        }
    }

    companion object {
        private const val KEY_TRANSFORM_CONTAINER = "report_transform_container"
        private const val DEFAULT_ERROR_MESSAGE = "Failed to load report"
    }
}

private fun readIncomeExpense(preferences: SharedPreferences): IncomeExpense {
    val raw = preferences.getString(FILTER_INCOME_EXPENSE, IncomeExpense.BOTH.name)
    return IncomeExpense.entries.firstOrNull { it.name == raw } ?: IncomeExpense.BOTH
}

private fun defaultCreateReport(db: DatabaseAdapter, params: ReportLaunchParams): Report {
    val extras = params.extras ?: Bundle().apply {
        putString(EXTRA_REPORT_TYPE, params.reportTypeName)
    }
    return requireNotNull(
        ReportsListActivity.createReport(db, extras, true, params.screenDensity),
    ) {
        "Unsupported report type: ${params.reportTypeName}"
    }
}
