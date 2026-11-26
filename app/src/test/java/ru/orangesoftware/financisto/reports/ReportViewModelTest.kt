// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.reports

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_FIRST_USER
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import androidx.appstate.transform.testing.transformTest
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import ru.orangesoftware.financisto.activity.ReportActivity.Companion.FILTER_INCOME_EXPENSE
import ru.orangesoftware.financisto.activity.ReportsListActivity.EXTRA_REPORT_TYPE
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.filter.Criteria
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
class ReportViewModelTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var db: DatabaseAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("ReportViewModelTestPrefs", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        db = mock(DatabaseAdapter::class.java)
    }

    @Test
    fun initialLoadLoadsReportDataAndClearsLoading() = transformTest {
        val report = mock(Report::class.java)
        val unit = sampleUnit(id = 1L, name = "Food", income = 0L, expense = 500L)
        val total = Total(Currency.EMPTY, false)
        whenever(report.getReport(eq(db), any())).thenReturn(ReportData(listOf(unit), total))

        val viewModel = ReportViewModel(
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

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertSame(report, state.currentReport)
        assertEquals(listOf(unit), state.graphUnits)
        assertEquals(total, state.total)
        assertEquals(IncomeExpense.BOTH, state.incomeExpense)
        verify(report).setIncomeExpense(IncomeExpense.BOTH)
        verify(report).getReport(eq(db), any())
    }

    @Test
    fun updateFilterReloadsReportAndPersistsWhenLaunchedWithoutFilter() = transformTest {
        val report = mock(Report::class.java)
        val initialUnit = sampleUnit(id = 1L, name = "All", income = 100L, expense = 0L)
        val filteredUnit = sampleUnit(id = 2L, name = "Filtered", income = 200L, expense = 0L)
        whenever(report.getReport(eq(db), any())).thenAnswer { invocation ->
            val passedFilter = invocation.getArgument<WhereFilter>(1)
            val units = if (passedFilter.isEmpty) listOf(initialUnit) else listOf(filteredUnit)
            ReportData(units, Total.ZERO)
        }

        val viewModel = ReportViewModel(
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
        assertEquals(listOf(initialUnit), viewModel.uiState.value.graphUnits)

        val resultIntent = Intent().apply {
            WhereFilter.empty().eq("category_id", "7").toIntent(this)
        }
        viewModel.updateFilter(RESULT_OK, resultIntent)
        advance()
        advanceUntilIdle()
        advance()

        val updatedState = viewModel.uiState.value
        assertFalse(updatedState.isLoading)
        assertEquals("7", updatedState.filter.get("category_id")?.stringValue)
        assertEquals(listOf(filteredUnit), updatedState.graphUnits)

        val persisted = WhereFilter.fromSharedPreferences(preferences)
        assertEquals("7", persisted.get("category_id")?.stringValue)

        viewModel.updateFilter(RESULT_CANCELED, null)
        advance()
        advanceUntilIdle()
        advance()
        assertEquals("7", viewModel.uiState.value.filter.get("category_id")?.stringValue)

        viewModel.updateFilter(RESULT_FIRST_USER, null)
        advance()
        advanceUntilIdle()
        advance()
        assertTrue(viewModel.uiState.value.filter.isEmpty)
        assertEquals(listOf(initialUnit), viewModel.uiState.value.graphUnits)
    }

    @Test
    fun toggleIncomeExpenseCancelsInFlightLoadAndPersistsSelection() = transformTest {
        val ioDispatcher = ManualIoDispatcher()
        val observedIncomeExpenses = mutableListOf<IncomeExpense>()
        val report = mock(Report::class.java)
        whenever(report.setIncomeExpense(any())).thenAnswer { invocation ->
            observedIncomeExpenses.add(invocation.getArgument(0))
            Unit
        }
        whenever(report.getReport(eq(db), any())).thenAnswer {
            val latest = observedIncomeExpenses.last()
            val unit = sampleUnit(
                id = latest.ordinal.toLong() + 1L,
                name = latest.name,
                income = 100L,
                expense = 50L,
            )
            ReportData(listOf(unit), Total.ZERO)
        }

        val viewModel = ReportViewModel(
            launchParams = defaultLaunchParams(),
            db = db,
            preferences = preferences,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = ioDispatcher,
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
            reportFactory = { _, _ -> report },
        )

        // Start initial load on transformDispatcher; it suspends on ioDispatcher.
        advance()
        advanceUntilIdle()
        advance()
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(observedIncomeExpenses.isEmpty())
        assertEquals(1, ioDispatcher.pendingCount())

        // Toggle before the in-flight load finishes on ioDispatcher.
        viewModel.toggleIncomeExpense()
        advance()
        advanceUntilIdle()
        advance()
        assertEquals(IncomeExpense.EXPENSE, viewModel.uiState.value.incomeExpense)
        assertEquals(IncomeExpense.EXPENSE.name, preferences.getString(FILTER_INCOME_EXPENSE, null))

        // Drain ioDispatcher and transformDispatcher; only the non-cancelled EXPENSE load runs.
        ioDispatcher.runAll()
        advance()
        advanceUntilIdle()
        advance()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(IncomeExpense.EXPENSE, finalState.incomeExpense)
        assertEquals(listOf(IncomeExpense.EXPENSE), observedIncomeExpenses)
        assertEquals("EXPENSE", finalState.graphUnits.single().name)
    }

    @Test
    fun loadsInitialFilterAndIncomeExpenseFromPreferencesWhenIntentFilterIsEmpty() = transformTest {
        WhereFilter.empty().eq("project_id", "42").toSharedPreferences(preferences)
        preferences.edit(commit = true) {
            putString(FILTER_INCOME_EXPENSE, IncomeExpense.INCOME.name)
        }

        val report = mock(Report::class.java)
        whenever(report.getReport(eq(db), any())).thenReturn(ReportData(emptyList(), Total.ZERO))

        val viewModel = ReportViewModel(
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

        val state = viewModel.uiState.value
        assertEquals("42", state.filter.get("project_id")?.stringValue)
        assertEquals(IncomeExpense.INCOME, state.incomeExpense)
        verify(report).setIncomeExpense(IncomeExpense.INCOME)
    }

    @Test
    fun doesNotOverwritePreferencesWhenLaunchedWithExplicitIntentFilter() = transformTest {
        WhereFilter.empty().eq("project_id", "99").toSharedPreferences(preferences)
        val report = mock(Report::class.java)
        whenever(report.getReport(eq(db), any())).thenReturn(ReportData(emptyList(), Total.ZERO))

        val explicitFilter = WhereFilter.empty().apply { put(Criteria.eq("category_id", "5")) }
        val viewModel = ReportViewModel(
            launchParams = defaultLaunchParams(
                initialIntentFilter = explicitFilter,
                explicitIncomeExpense = IncomeExpense.SUMMARY,
            ),
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

        viewModel.toggleIncomeExpense()
        advance()
        advanceUntilIdle()
        advance()

        val persisted = WhereFilter.fromSharedPreferences(preferences)
        assertEquals("99", persisted.get("project_id")?.stringValue)
        assertNull(preferences.getString(FILTER_INCOME_EXPENSE, null))
    }

    @Test
    fun refreshReQueriesReport() = transformTest {
        val report = mock(Report::class.java)
        whenever(report.getReport(eq(db), any())).thenReturn(ReportData(emptyList(), Total.ZERO))

        val viewModel = ReportViewModel(
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

        viewModel.refresh()
        advance()
        advanceUntilIdle()
        advance()

        verify(report, times(2)).getReport(eq(db), any())
    }

    @Test
    fun sqlExceptionUpdatesErrorMessageAndClearsLoading() = transformTest {
        val report = mock(Report::class.java)
        whenever(report.getReport(eq(db), any())).thenThrow(SQLiteException("Query failed"))

        val viewModel = ReportViewModel(
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

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Query failed", state.errorMessage)
    }

    @Test
    fun createPieChartIntentIncludesReportTypeFilterAndIncomeExpense() = transformTest {
        val report = mock(Report::class.java)
        whenever(report.getReport(eq(db), any())).thenReturn(ReportData(emptyList(), Total.ZERO))

        val filter = WhereFilter.empty().eq("category_id", "11")
        val viewModel = ReportViewModel(
            launchParams = defaultLaunchParams(
                reportTypeName = ReportType.BY_CATEGORY.name,
                initialIntentFilter = filter,
                explicitIncomeExpense = IncomeExpense.EXPENSE,
            ),
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

        val pieIntent = viewModel.createPieChartIntent(context)
        assertEquals(ReportType.BY_CATEGORY.name, pieIntent.getStringExtra(EXTRA_REPORT_TYPE))
        assertEquals(ReportType.BY_CATEGORY.name, pieIntent.getStringExtra(PieChartActivity.CHART_TITLE))
        assertEquals(IncomeExpense.EXPENSE.name, pieIntent.getStringExtra(FILTER_INCOME_EXPENSE))
        assertEquals("11", WhereFilter.fromIntent(pieIntent).get("category_id")?.stringValue)
    }

    private fun defaultLaunchParams(
        reportTypeName: String = ReportType.BY_CATEGORY.name,
        initialIntentFilter: WhereFilter = WhereFilter.empty(),
        explicitIncomeExpense: IncomeExpense? = null,
    ): ReportLaunchParams = ReportLaunchParams(
        reportTypeName = reportTypeName,
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
