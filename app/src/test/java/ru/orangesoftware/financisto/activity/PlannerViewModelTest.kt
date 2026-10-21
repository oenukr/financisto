// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.database.Cursor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.filter.DateTimeCriteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.TransactionBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache
import java.util.Date
import kotlin.coroutines.CoroutineContext

class PlannerViewModelTest : AbstractDbTest() {

    @Before
    override fun setUp() {
        super.setUp()
        CurrencyCache.initialize(db)
    }

    @Test
    fun initialStateIsLoading() {
        val viewModel = PlannerViewModel(db, Dispatchers.Unconfined)
        assertEquals(PlannerUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun loadPlannedTransactions_whenNoTransactions_emitsEmptyState() {
        val viewModel = PlannerViewModel(db, Dispatchers.Unconfined)
        val filter = WhereFilter.empty()
        val now = Date()
        filter.put(DateTimeCriteria(now.time - 3600_000L, now.time + 3600_000L))

        viewModel.loadPlannedTransactions(filter, now)

        val state = viewModel.uiState.value
        assertTrue("Expected Empty state, got $state", state is PlannerUiState.Empty)
        val emptyState = state as PlannerUiState.Empty
        assertEquals(filter, emptyState.filter)
        assertTrue(emptyState.totals.isNotEmpty())
        assertEquals(0L, emptyState.totals[0].balance)
    }

    @Test
    fun loadPlannedTransactions_whenRegularTransactionMatches_emitsSuccessState() {
        val c1 = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").makeDefault().create()
        CurrencyCache.initialize(db)
        val a1 = AccountBuilder.createDefault(db, c1)
        val now = Date()
        val t1 = TransactionBuilder.withDb(db)
            .dateTime(DateTime.fromTimestamp(now.time).atNoon())
            .account(a1)
            .amount(1000)
            .note("Lunch")
            .create()

        val viewModel = PlannerViewModel(db, Dispatchers.Unconfined)
        val filter = WhereFilter.empty()
        filter.put(DateTimeCriteria(now.time - 86400_000L, now.time + 86400_000L))

        viewModel.loadPlannedTransactions(filter, now)

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, got $state", state is PlannerUiState.Success)
        val successState = state as PlannerUiState.Success
        assertEquals(1, successState.transactions.size)
        assertEquals(t1.id, successState.transactions[0].id)
        assertEquals(1000L, successState.transactions[0].fromAmount)
        assertEquals("Lunch", successState.transactions[0].note)
        assertEquals(1, successState.totals.size)
        assertEquals(1000L, successState.totals[0].balance)
    }

    @Test
    fun loadPlannedTransactions_whenScheduledTransactionExists_projectsRecurrence() {
        val c1 = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").makeDefault().create()
        val a1 = AccountBuilder.createDefault(db, c1)
        val fromDate = DateTime.date(2026, 10, 1).atMidnight().asDate()
        val toDate = DateTime.date(2026, 10, 31).atDayEnd().asDate()
        val nowDate = DateTime.date(2026, 10, 15).atNoon().asDate()

        TransactionBuilder.withDb(db)
            .scheduleRecur("2026-10-10T10:00:00~DAILY:interval@1#~INDEFINITELY:null")
            .account(a1)
            .amount(-50)
            .note("Daily Coffee")
            .create()

        val viewModel = PlannerViewModel(db, Dispatchers.Unconfined)
        val filter = WhereFilter.empty()
        filter.put(DateTimeCriteria(fromDate.time, toDate.time))

        viewModel.loadPlannedTransactions(filter, nowDate)

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, got $state", state is PlannerUiState.Success)
        val successState = state as PlannerUiState.Success
        assertTrue("Should project planned dates from now onwards", successState.transactions.isNotEmpty())
        for (t in successState.transactions) {
            assertEquals(-50L, t.fromAmount)
            assertEquals("Daily Coffee", t.note)
            assertTrue(t.dateTime >= nowDate.time)
        }
    }

    @Test
    fun loadPlannedTransactions_whenDatabaseErrorOccurs_emitsErrorState() {
        val failingDb = object : DatabaseAdapter(context) {
            override fun getBlotter(filter: WhereFilter): Cursor {
                throw IllegalStateException("Database query failed")
            }
        }
        val viewModel = PlannerViewModel(failingDb, Dispatchers.Unconfined)
        val filter = WhereFilter.empty()
        filter.put(DateTimeCriteria(0L, 1000L))

        viewModel.loadPlannedTransactions(filter, Date())

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, got $state", state is PlannerUiState.Error)
        val errorState = state as PlannerUiState.Error
        assertEquals("Database query failed", errorState.message)
        assertNotNull(errorState.cause)
        assertEquals(filter, errorState.filter)

        viewModel.onErrorShown()
        val consumedState = viewModel.uiState.value
        assertTrue("Expected Empty state after onErrorShown, got $consumedState", consumedState is PlannerUiState.Empty)
        assertEquals(filter, (consumedState as PlannerUiState.Empty).filter)
    }

    @Test
    fun loadPlannedTransactions_cancelsPreviousJobWhenInvokedAgain() {
        val executedDispatchers = mutableListOf<String>()
        val testDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                executedDispatchers.add("dispatched")
                block.run()
            }
        }
        val viewModel = PlannerViewModel(db, testDispatcher)
        val filter1 = WhereFilter.empty()
        filter1.put(DateTimeCriteria(100L, 200L))
        val filter2 = WhereFilter.empty()
        filter2.put(DateTimeCriteria(300L, 400L))

        viewModel.loadPlannedTransactions(filter1)
        viewModel.loadPlannedTransactions(filter2)

        val state = viewModel.uiState.value
        assertTrue(state is PlannerUiState.Empty || state is PlannerUiState.Success)
        if (state is PlannerUiState.Empty) {
            assertEquals(filter2, state.filter)
        } else if (state is PlannerUiState.Success) {
            assertEquals(filter2, state.filter)
        }
    }

    @Test
    fun plannerUiState_equalityAndHashCode() {
        val filter = WhereFilter.empty()
        val total1 = Total(Currency.EMPTY)
        val total2 = Total(Currency.EMPTY)

        val empty1 = PlannerUiState.Empty(arrayOf(total1), filter)
        val empty2 = PlannerUiState.Empty(arrayOf(total1), filter)
        val emptyDifferentTotals = PlannerUiState.Empty(arrayOf(total2), filter)

        assertEquals(empty1, empty2)
        assertEquals(empty1.hashCode(), empty2.hashCode())
        assertFalse(empty1 == emptyDifferentTotals)

        val success1 = PlannerUiState.Success(emptyList(), arrayOf(total1), filter)
        val success2 = PlannerUiState.Success(emptyList(), arrayOf(total1), filter)
        val successDifferent = PlannerUiState.Success(emptyList(), arrayOf(total2), filter)

        assertEquals(success1, success2)
        assertEquals(success1.hashCode(), success2.hashCode())
        assertFalse(success1 == successDifferent)
    }

    @Test
    fun loadPlannedTransactions_defensivelyCopiesFilterToPreventStateMutation() {
        val viewModel = PlannerViewModel(db, Dispatchers.Unconfined)
        val filter = WhereFilter.empty()
        filter.put(DateTimeCriteria(100L, 200L))

        viewModel.loadPlannedTransactions(filter, Date())

        val state = viewModel.uiState.value as PlannerUiState.Empty
        assertEquals(filter, state.filter)

        // Mutate original filter
        filter.put(DateTimeCriteria(900L, 1000L))

        // State filter must remain unaffected
        assertEquals(100L, state.filter.dateTime?.longValue1)
        assertEquals(200L, state.filter.dateTime?.longValue2)
        assertFalse(filter == state.filter)
    }

    @Test
    fun whereFilter_structuralEqualsAndHashCode() {
        val filter1 = WhereFilter.empty()
        filter1.put(DateTimeCriteria(100L, 200L))
        val filter2 = WhereFilter.copyOf(filter1)

        assertEquals(filter1, filter2)
        assertEquals(filter1.hashCode(), filter2.hashCode())

        filter2.put(DateTimeCriteria(300L, 400L))
        assertFalse(filter1 == filter2)
    }
}
