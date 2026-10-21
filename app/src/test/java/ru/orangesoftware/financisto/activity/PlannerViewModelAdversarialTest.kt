// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.database.Cursor
import android.database.MatrixCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.filter.DateTimeCriteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlannerViewModelAdversarialTest : AbstractDbTest() {

    private lateinit var c1: Currency
    private lateinit var a1: Account

    @Before
    override fun setUp() {
        super.setUp()
        CurrencyCache.initialize(db)
        c1 = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").makeDefault().create()
        a1 = AccountBuilder.createDefault(db, c1)
    }

    @Test
    fun plannerViewModel_inFlightCancellation_raceCondition() {
        val pool = Executors.newFixedThreadPool(2)
        val testDispatcher = pool.asCoroutineDispatcher()

        val job1Started = CountDownLatch(1)
        val job1Proceed = CountDownLatch(1)

        val slowDb = object : DatabaseAdapter(context) {
            override fun getBlotter(filter: WhereFilter): Cursor {
                if (filter.dateTime?.longValue1 == 111L) {
                    job1Started.countDown()
                    job1Proceed.await(5, TimeUnit.SECONDS)
                }
                return MatrixCursor(arrayOf("_id", "from_account_id", "to_account_id", "from_amount", "to_amount", "datetime"))
            }

            override fun getAllScheduledTransactions(): ArrayList<ru.orangesoftware.financisto.model.TransactionInfo> {
                return ArrayList()
            }
        }

        try {
            val viewModel = PlannerViewModel(slowDb, testDispatcher)

            val filter1 = WhereFilter.empty()
            filter1.put(DateTimeCriteria(111L, 222L))

            val filter2 = WhereFilter.empty()
            filter2.put(DateTimeCriteria(333L, 444L))

            // Start Job 1 (slow)
            viewModel.loadPlannedTransactions(filter1)

            // Wait until Job 1 has actually entered getBlotter
            assertTrue("Job 1 did not start in time", job1Started.await(2, TimeUnit.SECONDS))

            // Now trigger Job 2 while Job 1 is still in flight
            viewModel.loadPlannedTransactions(filter2)

            // Give Job 2 time to complete on the other thread
            Thread.sleep(200)

            // Now unblock Job 1 so it finishes its execution
            job1Proceed.countDown()

            // Wait for Job 1 thread to finish
            Thread.sleep(300)

            val finalState = viewModel.uiState.value

            // Check if cancelled Job 1 overwrote Job 2's state!
            if (finalState is PlannerUiState.Empty) {
                assertEquals("Cancelled Job 1 overwrote Job 2 state!", filter2, finalState.filter)
            } else if (finalState is PlannerUiState.Success) {
                assertEquals("Cancelled Job 1 overwrote Job 2 state!", filter2, finalState.filter)
            } else {
                fail("Unexpected state: $finalState")
            }
        } finally {
            slowDb.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun monthlyViewViewModel_inFlightCancellation_raceCondition() {
        val pool = Executors.newFixedThreadPool(2)
        val testDispatcher = pool.asCoroutineDispatcher()

        val job1Started = CountDownLatch(1)
        val job1Proceed = CountDownLatch(1)

        val slowDb = object : DatabaseAdapter(context) {
            override fun getBlotterForAccountWithSplits(filter: WhereFilter): Cursor {
                if (filter.dateTime?.longValue1 == 1000L) {
                    job1Started.countDown()
                    job1Proceed.await(5, TimeUnit.SECONDS)
                }
                return MatrixCursor(arrayOf("_id", "from_account_id", "to_account_id", "from_amount", "to_amount", "datetime"))
            }

            override fun getAllScheduledTransactions(): ArrayList<ru.orangesoftware.financisto.model.TransactionInfo> {
                return ArrayList()
            }
        }

        try {
            val viewModel = MonthlyViewViewModel(slowDb, testDispatcher)

            val open1 = Date(1000L)
            val close1 = Date(2000L)

            val open2 = Date(3000L)
            val close2 = Date(4000L)

            // Start Job 1 with isStatementPreview = false
            viewModel.loadMonthlyPreview(a1, false, open1, close1, Date(1500L))

            assertTrue("Job 1 did not start", job1Started.await(2, TimeUnit.SECONDS))

            // Start Job 2 with isStatementPreview = true
            viewModel.loadMonthlyPreview(a1, true, open2, close2, Date(3500L))

            Thread.sleep(200)

            // Unblock Job 1
            job1Proceed.countDown()

            Thread.sleep(300)

            val finalState = viewModel.uiState.value

            if (finalState is MonthlyViewUiState.Empty) {
                assertTrue("Cancelled Job 1 overwrote Job 2 isStatementPreview state!", finalState.isStatementPreview)
            } else if (finalState is MonthlyViewUiState.Success) {
                assertTrue("Cancelled Job 1 overwrote Job 2 isStatementPreview state!", finalState.isStatementPreview)
            } else {
                fail("Unexpected state: $finalState")
            }
        } finally {
            slowDb.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun plannerViewModel_cancellationException_doesNotEmitErrorState() {
        val failingDb = object : DatabaseAdapter(context) {
            override fun getBlotter(filter: WhereFilter): Cursor {
                throw CancellationException("Simulated coroutine cancellation")
            }
        }
        val viewModel = PlannerViewModel(failingDb, Dispatchers.Unconfined)
        val filter = WhereFilter.empty()
        filter.put(DateTimeCriteria(0L, 1000L))

        try {
            viewModel.loadPlannedTransactions(filter, Date())
        } catch (e: CancellationException) {
            // In Dispatchers.Unconfined, rethrown exception may escape viewModelScope if not caught
        }

        val state = viewModel.uiState.value
        // State must NOT be PlannerUiState.Error
        assertTrue("CancellationException must not produce Error state, was $state", state !is PlannerUiState.Error)
    }

    @Test
    fun plannerViewModel_generalException_emitsErrorState() {
        val failingDb = object : DatabaseAdapter(context) {
            override fun getBlotter(filter: WhereFilter): Cursor {
                throw RuntimeException("Fatal disk error")
            }
        }
        val viewModel = PlannerViewModel(failingDb, Dispatchers.Unconfined)
        val filter = WhereFilter.empty()
        filter.put(DateTimeCriteria(0L, 1000L))

        viewModel.loadPlannedTransactions(filter, Date())

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, was $state", state is PlannerUiState.Error)
        val errorState = state as PlannerUiState.Error
        assertEquals("Fatal disk error", errorState.message)
        assertNotNull(errorState.cause)
    }
}
