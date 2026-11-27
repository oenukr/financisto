// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.reports

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import androidx.appstate.transform.testing.transformTest
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import ru.orangesoftware.financisto.activity.ReportActivity.Companion.FILTER_INCOME_EXPENSE
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.graph.GraphStyle
import ru.orangesoftware.financisto.graph.GraphUnit
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.report.IncomeExpense
import ru.orangesoftware.financisto.report.Report
import ru.orangesoftware.financisto.report.ReportData
import ru.orangesoftware.financisto.report.ReportType
import java.math.BigDecimal
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
class PieChartViewModelTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var db: DatabaseAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("PieChartViewModelTestPrefs", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        db = mock(DatabaseAdapter::class.java)
    }

    @Test
    fun transitionsFromLoadingToSuccessAndQueriesChartOnce() = transformTest {
        val ioDispatcher = ManualIoDispatcher()
        val report = mock(Report::class.java)
        val unit1 = sampleUnit(id = 1L, name = "Salary", income = 1000L, expense = 0L)
        val unit2 = sampleUnit(id = 2L, name = "Groceries", income = 0L, expense = 250L)
        whenever(report.getReportForChart(eq(db), any())).thenReturn(
            ReportData(listOf(unit1, unit2), Total.ZERO),
        )

        val viewModel = PieChartViewModel(
            launchParams = defaultLaunchParams(
                chartTitle = "BY_CATEGORY",
                explicitIncomeExpense = IncomeExpense.BOTH,
            ),
            db = db,
            preferences = preferences,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = ioDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
            reportFactory = { _, _ -> report },
        )

        assertEquals(PieChartUiState.Loading, viewModel.uiState.value)

        // Advance transformDispatcher so LaunchedEffect starts and suspends on ioDispatcher.
        advance()
        advanceUntilIdle()
        advance()
        assertEquals(PieChartUiState.Loading, viewModel.uiState.value)
        assertEquals(1, ioDispatcher.pendingCount())

        ioDispatcher.runAll()
        advance()
        advanceUntilIdle()
        advance()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got $state", state is PieChartUiState.Success)
        val success = state as PieChartUiState.Success
        assertEquals("BY_CATEGORY", success.title)
        assertEquals(4, success.chartData.size)
        assertEquals(4, success.chartData.map { it.color }.distinct().size)
        assertEquals(1000.0, success.chartData[0].data, 0.001)
        assertEquals("Salary 1000", success.chartData[0].partName)
        assertEquals(0.0, success.chartData[1].data, 0.001)
        assertEquals("Salary 0", success.chartData[1].partName)
        assertEquals(0.0, success.chartData[2].data, 0.001)
        assertEquals("Groceries 0", success.chartData[2].partName)
        assertEquals(250.0, success.chartData[3].data, 0.001)
        assertEquals("Groceries -250", success.chartData[3].partName)

        verify(report).setIncomeExpense(IncomeExpense.BOTH)
        verify(report, times(1)).getReportForChart(eq(db), any())
        verify(report, never()).getReport(eq(db), any())
    }

    @Test
    fun emitsEmptyWhenReportReturnsNoUnits() = transformTest {
        val report = mock(Report::class.java)
        whenever(report.getReportForChart(eq(db), any())).thenReturn(
            ReportData(emptyList(), Total.ZERO),
        )

        val viewModel = PieChartViewModel(
            launchParams = defaultLaunchParams(chartTitle = "BY_PAYEE"),
            db = db,
            preferences = preferences,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = testDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
            reportFactory = { _, _ -> report },
        )

        advance()
        advanceUntilIdle()
        advance()

        assertEquals(PieChartUiState.Empty(title = "BY_PAYEE"), viewModel.uiState.value)
        verify(report, times(1)).getReportForChart(eq(db), any())
        verify(report, never()).getReport(eq(db), any())
    }

    @Test
    fun emitsErrorWhenChartQueryThrowsSqlException() = transformTest {
        val report = mock(Report::class.java)
        whenever(report.getReportForChart(eq(db), any()))
            .thenThrow(SQLiteException("Chart query failed"))

        val viewModel = PieChartViewModel(
            launchParams = defaultLaunchParams(chartTitle = "BY_LOCATION"),
            db = db,
            preferences = preferences,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = testDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
            reportFactory = { _, _ -> report },
        )

        advance()
        advanceUntilIdle()
        advance()

        assertEquals(
            PieChartUiState.Error(title = "BY_LOCATION", message = "Chart query failed"),
            viewModel.uiState.value,
        )
    }

    @Test
    fun loadsPreferencesWhenLaunchedWithoutExplicitFilter() = transformTest {
        WhereFilter.empty().eq("account_id", "3").toSharedPreferences(preferences)
        preferences.edit(commit = true) {
            putString(FILTER_INCOME_EXPENSE, IncomeExpense.EXPENSE.name)
        }

        val report = mock(Report::class.java)
        whenever(report.getReportForChart(eq(db), any())).thenAnswer { invocation ->
            val passedFilter = invocation.getArgument<WhereFilter>(1)
            assertEquals("3", passedFilter.get("account_id")?.stringValue)
            ReportData(emptyList(), Total.ZERO)
        }

        val viewModel = PieChartViewModel(
            launchParams = defaultLaunchParams(),
            db = db,
            preferences = preferences,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = testDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
            reportFactory = { _, _ -> report },
        )

        advance()
        advanceUntilIdle()
        advance()

        verify(report).setIncomeExpense(IncomeExpense.EXPENSE)
        verify(report, times(1)).getReportForChart(eq(db), any())
    }

    private fun defaultLaunchParams(
        reportTypeName: String = ReportType.BY_CATEGORY.name,
        chartTitle: String = ReportType.BY_CATEGORY.name,
        initialIntentFilter: WhereFilter = WhereFilter.empty(),
        explicitIncomeExpense: IncomeExpense? = null,
    ): PieChartLaunchParams = PieChartLaunchParams(
        reportTypeName = reportTypeName,
        chartTitle = chartTitle,
        extras = null,
        initialIntentFilter = initialIntentFilter,
        explicitIncomeExpense = explicitIncomeExpense,
        screenDensity = 1.0f,
    )

    private fun sampleUnit(
        id: Long,
        name: String,
        income: Long,
        expense: Long,
    ): GraphUnit {
        val style = GraphStyle.Builder(1.0f).build()
        return GraphUnit(id, name, Currency.EMPTY, style).apply {
            if (income != 0L) addAmount(BigDecimal(income), false)
            if (expense != 0L) addAmount(BigDecimal(-expense), false)
            flatten(IncomeExpense.BOTH)
        }
    }

    private class ManualIoDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun pendingCount(): Int = tasks.size

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }
}
