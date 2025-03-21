package ru.orangesoftware.financisto.reports

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
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
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.report.IncomeExpense
import ru.orangesoftware.financisto.report.Report
import ru.orangesoftware.financisto.report.ReportData
import java.math.BigDecimal
import kotlin.math.abs

data class ChartData(
    val data: Double,
    val color: Color,
    val partName: String,
)

class PieChartViewModel(
    private val db: DatabaseAdapter,
    private val preferences: SharedPreferences,
    private val screenDensity: Float,
    private val intent: Intent
) : ViewModel() {
    private val _currentReport = MutableStateFlow<Report?>(null)
//    val currentReport: StateFlow<Report?> = _currentReport
//        .onStart { initiateReport(intent, true, screenDensity) }
//        .stateIn(
//            viewModelScope,
//            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
//            null,
//        )

    private val _filter = MutableStateFlow<WhereFilter>(WhereFilter.empty())
//    val filter: StateFlow<WhereFilter> = _filter

    private var _incomeExpenseState = MutableStateFlow(IncomeExpense.BOTH)
//    val incomeExpenseState: StateFlow<IncomeExpense> = _incomeExpenseState

    private val _pieChartData = MutableStateFlow<List<ChartData>>(emptyList())
    val pieChartData: StateFlow<List<ChartData>> = _pieChartData
        .onStart {
            initiateReport(intent, true, screenDensity)
//            calculatePieData()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            emptyList(),
        )

    private fun initiateReport(intent: Intent, skipTransfers: Boolean, screenDensity: Float) {
        createReport(intent.extras, skipTransfers, screenDensity)
        val intentFilter = WhereFilter.fromIntent(intent)
        viewModelScope.launch {
            _filter.emit(intentFilter)
        }
        if (intent.hasExtra(FILTER_INCOME_EXPENSE)) {
            viewModelScope.launch {
                _incomeExpenseState.emit(IncomeExpense.valueOf(intent.getStringExtra(FILTER_INCOME_EXPENSE) ?: IncomeExpense.BOTH.name))
            }
        }
        if (_filter.value.isEmpty && intentFilter.isEmpty) {
            loadPrefsFilter(_currentReport.value?.reportType?.name.orEmpty())
        }

        updateReport()

        viewModelScope.launch {
            _pieChartData.emit(calculatePieData())
        }
    }

    private suspend fun calculatePieData(): List<ChartData> {
        val report: ReportData = withContext(Dispatchers.IO) {
            _currentReport.value?.getReportForChart(
                db,
                WhereFilter.copyOf(_filter.value),
            ) ?: ReportData(emptyList(), Total.ZERO)
        }
        val chartData = mutableListOf<ChartData>()
        val colors = generateColors(2 * report.units.size)
        var index = 0
        withContext(Dispatchers.Default) {
            report.units.forEach { unit ->
                chartData.add(
                    ChartData(
                        data = abs(unit.incomeExpense.income.toDouble()),
                        color = colors[index++],
                        partName = unit.name.plusSign(unit.incomeExpense.income),
                    )
                )
                chartData.add(
                    ChartData(
                        data = abs(unit.incomeExpense.expense.toDouble()),
                        color = colors[index++],
                        partName = unit.name.plusSign(unit.incomeExpense.expense),
                    )
                )
            }
        }
        return chartData
    }

    private fun String.plusSign(amount: BigDecimal): String {
        return if (amount.signum() >= 0) {
            this.plus(" $amount")
        } else {
            this.plus(" $amount")
        }
    }

    private fun generateColors(n: Int): List<Color> {
        var colors = mutableListOf<Color>()
        if (n == 0) {
            return colors
        }

        for (i in 0 until n) {
            colors.add(
                Color.hsv(
                    hue = 360 * i.toFloat() / n.toFloat(),
                    saturation = 0.75f,
                    value = 0.85f,
                    colorSpace = ColorSpaces.DisplayP3,
                )
            )
        }
        return colors
    }

    private fun loadPrefsFilter(reportName: String/*, context: Context*/) {
//        val preferences: SharedPreferences = getPreferencesForReport(reportName, context)
        viewModelScope.launch {
            _filter.emit(WhereFilter.fromSharedPreferences(preferences))
            _incomeExpenseState.emit(IncomeExpense.valueOf(
                preferences.getString(
                    FILTER_INCOME_EXPENSE,
                    IncomeExpense.BOTH.name,
                ) ?: IncomeExpense.BOTH.name
            ))
        }
    }

//    private fun getPreferencesForReport(reportName: String, context: Context): SharedPreferences =
//        context.getSharedPreferences("ReportActivity_${reportName}_DEFAULT", 0)

    fun updateReport() {
        viewModelScope.launch {
            val report = calculateReport()
//            withContext(Dispatchers.Default) {
//                _reportTotal.emit(report?.total ?: Total.ZERO)
//                _graphUnits.emit(report?.units ?: emptyList())
//            }
        }
    }

    private suspend fun calculateReport(): ReportData? {
        _currentReport.value?.setIncomeExpense(_incomeExpenseState.value)
        return withContext(Dispatchers.IO) {
            _currentReport.value?.getReport(db, WhereFilter.copyOf(_filter.value))
        }
    }

    fun createReport(extras: Bundle?, skipTransfers: Boolean, screenDensity: Float) {
        viewModelScope.launch {
            _currentReport.emit(ReportsListActivity.createReport(db, extras, skipTransfers, screenDensity))
        }
    }

    fun open() = db.open()

    fun close() = db.close()

    companion object {
//        @JvmField
//        val REPORT_NAME_KEY = object : CreationExtras.Key<String> {}
        @JvmField
        val INTENT_KEY = object : CreationExtras.Key<Intent> {}
        @JvmField
        val SCREEN_DENTITY_KEY = object : CreationExtras.Key<Float> {}

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = requireNotNull(this[APPLICATION_KEY]) { "Context is required" }
                val intent = requireNotNull(this[INTENT_KEY]) { "Intent is required" }
                val screenDensity = requireNotNull(this[SCREEN_DENTITY_KEY]) { "Screen density is required" }
                val reportName = requireNotNull(intent.getStringExtra(EXTRA_REPORT_TYPE)) { "Report name is required" }
                PieChartViewModel(
                    db = DatabaseAdapter(context),
                    preferences = context.getSharedPreferences("ReportActivity_${reportName}_DEFAULT", 0),
                    screenDensity = screenDensity,
                    intent = intent,
                )
            }
        }
    }
}
