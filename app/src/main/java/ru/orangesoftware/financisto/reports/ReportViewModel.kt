package ru.orangesoftware.financisto.reports

import android.app.Activity
import android.app.Activity.RESULT_FIRST_USER
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

class ReportViewModel(
    private val db: DatabaseAdapter,
    private val preferences: SharedPreferences,
    private val screenDensity: Float,
    private val intent: Intent,
) : ViewModel() {
    private val _currentReport = MutableStateFlow<Report?>(null)
    val currentReport: StateFlow<Report?> = _currentReport
        .onStart { initiateReport(intent, true, screenDensity) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            null,
        )

    private val _filter = MutableStateFlow<WhereFilter>(WhereFilter.empty())
    val filter: StateFlow<WhereFilter> = _filter

    private val _reportTotal = MutableStateFlow(Total.ZERO)
    val reportTotal: StateFlow<Total> = _reportTotal

    private val _graphUnits = MutableStateFlow<List<GraphUnit>?>(emptyList())
    val graphUnits: StateFlow<List<GraphUnit>?> = _graphUnits

    private var _incomeExpenseState = MutableStateFlow(IncomeExpense.BOTH)
    val incomeExpenseState: StateFlow<IncomeExpense> = _incomeExpenseState

    private var saveFilter: Boolean = false

    fun open() = db.open()

    fun close() = db.close()

    fun onGraphRowClicked(id: Long, context: Context) {
        val intent = _currentReport.value?.createActivityIntent(
            context,
            db,
            WhereFilter.copyOf(_filter.value),
            id,
        )
        context.startActivity(intent)
    }

    private fun initiateReport(intent: Intent, skipTransfers: Boolean, screenDensity: Float) {
        createReport(intent.extras, skipTransfers, screenDensity)
        val intentFilter = WhereFilter.fromIntent(intent)
        viewModelScope.launch {
            _filter.emit(intentFilter)
        }
        if (intent.hasExtra(FILTER_INCOME_EXPENSE)) {
            viewModelScope.launch {
                _incomeExpenseState.emit(
                    IncomeExpense.valueOf(
                        intent.getStringExtra(
                            FILTER_INCOME_EXPENSE
                        ) ?: IncomeExpense.BOTH.name
                    )
                )
            }
        }
        if (_filter.value.isEmpty && intentFilter.isEmpty) {
            loadPrefsFilter(/*_currentReport.value?.reportType?.name.orEmpty()*/)
        }

        updateReport()
    }

    fun updateFilter(resultCode: Int, resultData: Intent?) {
        viewModelScope.launch {
            _filter.emit(
                when (resultCode) {
                    RESULT_FIRST_USER -> WhereFilter.empty()
                    RESULT_OK -> resultData?.let(WhereFilter::fromIntent) ?: WhereFilter.empty()
                    else -> _filter.value
                }
            )
        }
        saveFilter()
        updateReport()
    }

    fun toggleIncomeExpense() {
        val values: Array<IncomeExpense> = IncomeExpense.entries.toTypedArray()
        val nextIndex: Int = _incomeExpenseState.value.ordinal + 1
        viewModelScope.launch {
            _incomeExpenseState.emit(if (nextIndex > values.lastIndex) values[0] else values[nextIndex])
        }
        saveFilter()
        updateReport()
    }

    private fun loadPrefsFilter() {
        viewModelScope.launch {
            _filter.emit(WhereFilter.fromSharedPreferences(preferences))
            _incomeExpenseState.emit(
                IncomeExpense.valueOf(
                    preferences.getString(
                        FILTER_INCOME_EXPENSE,
                        IncomeExpense.BOTH.name,
                    ) ?: IncomeExpense.BOTH.name
                )
            )
        }
        saveFilter = true
    }

    fun saveFilter() {
        if (saveFilter) {
            preferences
                .also { _filter.value.toSharedPreferences(it) }
                .edit {
                    putString(FILTER_INCOME_EXPENSE, _incomeExpenseState.value.name)
                }
        }
    }

    fun createReport(extras: Bundle?, skipTransfers: Boolean, screenDensity: Float) {
        viewModelScope.launch {
            _currentReport.emit(
                ReportsListActivity.createReport(
                    db,
                    extras,
                    skipTransfers,
                    screenDensity
                )
            )
        }
    }

    fun updateReport() {
        viewModelScope.launch {
            val report = calculateReport()
            withContext(Dispatchers.Default) {
                _reportTotal.emit(report?.total ?: Total.ZERO)
                _graphUnits.emit(report?.units ?: emptyList())
            }
        }
    }

    private suspend fun calculateReport(): ReportData? {
        _currentReport.value?.setIncomeExpense(_incomeExpenseState.value)
        return withContext(Dispatchers.IO) {
            _currentReport.value?.getReport(db, WhereFilter.copyOf(_filter.value))
        }
    }

    fun showPieChart(activity: Activity) {
        viewModelScope.launch {
            Intent(activity, PieChartActivity::class.java).apply {
                putExtra(
                    PieChartActivity.CHART_TITLE,
                    _currentReport.value?.reportType?.name.orEmpty()
                )

                putExtra(EXTRA_REPORT_TYPE, _currentReport.value?.reportType?.name.orEmpty())
                putExtra(FILTER_INCOME_EXPENSE, _incomeExpenseState.value.name)
                _filter.value.toIntent(this)
            }.also { intent ->
                activity.startActivity(intent)
            }
        }
    }

    companion object {
        @JvmField
        val INTENT_KEY = object : CreationExtras.Key<Intent> {}

        @JvmField
        val SCREEN_DENTITY_KEY = object : CreationExtras.Key<Float> {}

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = requireNotNull(this[APPLICATION_KEY]) { "Context is required" }
                val intent = requireNotNull(this[INTENT_KEY]) { "Intent is required" }
                val screenDensity =
                    requireNotNull(this[SCREEN_DENTITY_KEY]) { "Screen density is required" }
                val reportName =
                    requireNotNull(intent.getStringExtra(EXTRA_REPORT_TYPE)) { "Report name is required" }
                ReportViewModel(
                    db = DatabaseAdapter(context),
                    preferences = context.getSharedPreferences(
                        "ReportActivity_${reportName}_DEFAULT",
                        0
                    ),
                    screenDensity = screenDensity,
                    intent = intent,
                )
            }
        }
    }
}
