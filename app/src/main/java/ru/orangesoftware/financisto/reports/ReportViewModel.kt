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
import ru.orangesoftware.financisto.graph.GraphStyle
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

    fun initiateReport(intent: Intent, skipTransfers: Boolean, screenDensity: Float) {
        createReport(intent.extras, skipTransfers, screenDensity)
        viewModelScope.launch {
            _filter.emit(WhereFilter.fromIntent(intent))
        }
        if (intent.hasExtra(FILTER_INCOME_EXPENSE)) {
            viewModelScope.launch {
                _incomeExpenseState.emit(IncomeExpense.valueOf(intent.getStringExtra(FILTER_INCOME_EXPENSE) ?: IncomeExpense.BOTH.name))
            }
        }
        if (_filter.value.isEmpty) {
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
        saveFilter = true
    }

    fun saveFilter() {
        if (saveFilter) {
//            getPreferencesForReport(_currentReport.value?.reportType?.name.orEmpty(), context)
            preferences
                .also { _filter.value.toSharedPreferences(it) }
                .edit {
                    putString(FILTER_INCOME_EXPENSE, _incomeExpenseState.value.name)
                }
        }
    }

//    private fun getPreferencesForReport(reportName: String, context: Context): SharedPreferences =
//        context.getSharedPreferences("ReportActivity_${reportName}_DEFAULT", 0)

    fun createReport(extras: Bundle?, skipTransfers: Boolean, screenDensity: Float) {
        viewModelScope.launch {
            _currentReport.emit(ReportsListActivity.createReport(db, extras, skipTransfers, screenDensity))
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
//            val chartData = createPieChart()
            Intent(activity, PieChartActivity::class.java).apply {
//                putExtra(PieChartActivity.CHART_DATA, ArrayList(chartData))
                putExtra(PieChartActivity.CHART_TITLE, _currentReport.value?.reportType?.name.orEmpty())

                putExtra(EXTRA_REPORT_TYPE, _currentReport.value?.reportType?.name.orEmpty())
                putExtra(FILTER_INCOME_EXPENSE, _incomeExpenseState.value.name)
                _filter.value.toIntent(this)
            }.also { intent ->
                activity.startActivity(intent)
            }
        }
    }

//    private suspend fun createPieChart(): List<PieChartData> {
////        val renderer = DefaultRenderer()
////        renderer.labelsTextSize = context.resources.getDimension(R.dimen.report_labels_text_size)
////        renderer.legendTextSize = context.resources.getDimension(R.dimen.report_legend_text_size)
////        renderer.margins = intArrayOf(0, 0, 0, 0)
//        val report: ReportData = withContext(Dispatchers.IO) {
//            _currentReport.value?.getReportForChart(
//                db,
//                WhereFilter.copyOf(_filter.value),
//            ) ?: ReportData(emptyList(), Total.ZERO)
//        }
////        val series = CategorySeries("AAA")
////        val total: Long = abs(report.total.amount) + abs(report.total.balance)
//        val chartData = mutableListOf<PieChartData>()
//        val colors = generateColors(2 * report.units.size)
//        var index = 0
//        withContext(Dispatchers.Default) {
//            report.units.forEach { unit ->
//                chartData.add(PieChartData(
//                    data = unit.incomeExpense.income.toDouble(),
//                    color = colors[index++],
//                    partName = unit.name,
//                ))
//                chartData.add(PieChartData(
//                    data = unit.incomeExpense.expense.toDouble(),
//                    color = colors[index++],
//                    partName = unit.name,
//                ))
////                addSeries(
//////                    series,
//////                    renderer,
////                    unit.name,
////                    unit.incomeExpense.income,
////                    total,
////                    colors[index++],
////                )
////                addSeries(
//////                    series,
//////                    renderer,
////                    unit.name,
////                    unit.incomeExpense.expense,
////                    total,
////                    colors[index++],
////                )
//            }
////            renderer.isZoomButtonsVisible = true
////            renderer.isZoomEnabled = true
////            renderer.chartTitleTextSize = 20F
//        }
//        return chartData
//    }

//    private fun generateColors(n: Int): List<Color> {
//        var colors = mutableListOf<Color>()
//        if (n == 0) {
//            return colors
//        }
//
//        for (i in 0 until n) {
//            colors.add(
//                Color.hsv(
//                    hue = 360 * i.toFloat() / n.toFloat(),
//                    saturation = 0.75f,
//                    value = 0.85f,
//                    colorSpace = ColorSpaces.DisplayP3,
//                )
//            )
//        }
//        return colors
//    }

//    private fun addSeries(
////        series: CategorySeries,
////        renderer: DefaultRenderer,
//        name: String,
//        expense: BigDecimal,
//        total: Long,
//        color: Int,
//    ) {
//        val amount = expense.toLong()
//        if (amount != 0L && total != 0L) {
//            val percentage = 100 * abs(amount) /total
////            series.add((if (amount > 0) "+" else "-") + name + "($percentage%)", percentage.toDouble())
////            val seriesRenderer: SimpleSeriesRenderer = SimpleSeriesRenderer()
////            seriesRenderer.color = color
////            renderer.addSeriesRenderer(seriesRenderer)
//        }
//    }

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
                ReportViewModel(
                    db = DatabaseAdapter(context),
                    preferences = context.getSharedPreferences("ReportActivity_${reportName}_DEFAULT", 0),
                    screenDensity = screenDensity,
                    intent = intent,
                )
            }
        }
    }
}
