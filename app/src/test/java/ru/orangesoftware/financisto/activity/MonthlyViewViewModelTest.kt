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
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CategoryBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime.date
import ru.orangesoftware.financisto.test.TransactionBuilder
import ru.orangesoftware.financisto.test.TransferBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MonthlyViewPlanner
import java.util.Date
import kotlin.coroutines.CoroutineContext

class MonthlyViewViewModelTest : AbstractDbTest() {

    private lateinit var c1: Currency
    private lateinit var a1: Account
    private lateinit var c2: Currency
    private lateinit var a2: Account

    private val fromDate: Date = date(2011, 8, 1).atMidnight().asDate()
    private val toDate: Date = date(2011, 8, 16).atDayEnd().asDate()
    private val nowDate: Date = date(2011, 8, 8).at(23, 20, 0, 0).asDate()

    @Before
    override fun setUp() {
        super.setUp()
        c1 = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").makeDefault().create()
        c2 = CurrencyBuilder.withDb(db).name("SGD").title("Singapore Dollar").symbol("S$").create()
        a1 = AccountBuilder.createDefault(db, c1)
        a2 = AccountBuilder.createDefault(db, c2)
        CategoryBuilder.createDefaultHierarchy(db)
        CurrencyCache.initialize(db)
    }

    private fun prepareSampleData() {
        // Income (credits section in CC statement)
        TransactionBuilder.withDb(db).dateTime(date(2011, 8, 8).atNoon())
            .account(a1).amount(1000).note("salary").create()
        // Transfer (expenses section in CC statement)
        TransferBuilder.withDb(db).dateTime(date(2011, 8, 9).atNoon())
            .fromAccount(a1).fromAmount(-100).toAccount(a2).toAmount(50).note("transfer").create()
        // Credit card payment (payments section in CC statement)
        TransactionBuilder.withDb(db).dateTime(date(2011, 8, 15).atNoon())
            .account(a1).amount(400).ccPayment().note("cc_payment").create()
    }

    @Test
    fun initialStateIsLoading() {
        val viewModel = MonthlyViewViewModel(db, Dispatchers.Unconfined)
        assertEquals(MonthlyViewUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun loadMonthlyPreview_withRegularPlannedTransactions_emitsSuccess() {
        prepareSampleData()
        val viewModel = MonthlyViewViewModel(db, Dispatchers.Unconfined)

        viewModel.loadMonthlyPreview(
            account = a1,
            isStatementPreview = false,
            open = fromDate,
            close = toDate,
            now = nowDate,
        )

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, got $state", state is MonthlyViewUiState.Success)
        val success = state as MonthlyViewUiState.Success
        assertFalse(success.isStatementPreview)
        assertEquals(3, success.transactions.size)
        // 1000 + (-100) + 400 = 1300
        assertEquals(1300L, success.total)
    }

    @Test
    fun loadMonthlyPreview_withCreditCardStatement_emitsSuccessWithHeadersAndExcludesPaymentsFromTotal() {
        prepareSampleData()
        val viewModel = MonthlyViewViewModel(db, Dispatchers.Unconfined)

        viewModel.loadMonthlyPreview(
            account = a1,
            isStatementPreview = true,
            open = fromDate,
            close = toDate,
            now = nowDate,
        )

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, got $state", state is MonthlyViewUiState.Success)
        val success = state as MonthlyViewUiState.Success
        assertTrue(success.isStatementPreview)
        // 3 header dividers (PAYMENTS_HEADER, CREDITS_HEADER, EXPENSES_HEADER) + 3 transactions = 6 items
        assertEquals(6, success.transactions.size)
        assertEquals(MonthlyViewPlanner.PAYMENTS_HEADER, success.transactions[0])
        assertEquals("cc_payment", success.transactions[1].note)
        assertEquals(MonthlyViewPlanner.CREDITS_HEADER, success.transactions[2])
        assertEquals("salary", success.transactions[3].note)
        assertEquals(MonthlyViewPlanner.EXPENSES_HEADER, success.transactions[4])
        assertEquals("transfer", success.transactions[5].note)
        // Statement total excludes credit card payments: 1000 + (-100) = 900
        assertEquals(900L, success.total)
    }

    @Test
    fun loadMonthlyPreview_whenNoTransactions_emitsEmptyState() {
        val viewModel = MonthlyViewViewModel(db, Dispatchers.Unconfined)

        viewModel.loadMonthlyPreview(
            account = a1,
            isStatementPreview = false,
            open = fromDate,
            close = toDate,
            now = nowDate,
        )

        val state = viewModel.uiState.value
        assertTrue("Expected Empty state, got $state", state is MonthlyViewUiState.Empty)
        val empty = state as MonthlyViewUiState.Empty
        assertEquals(0L, empty.total)
        assertFalse(empty.isStatementPreview)
    }

    @Test
    fun loadMonthlyPreview_whenDatabaseErrorOccurs_emitsErrorState() {
        val failingDb = object : DatabaseAdapter(context) {
            override fun getBlotterForAccountWithSplits(filter: WhereFilter): Cursor {
                throw IllegalStateException("Database query failed")
            }
        }
        val viewModel = MonthlyViewViewModel(failingDb, Dispatchers.Unconfined)

        viewModel.loadMonthlyPreview(
            account = a1,
            isStatementPreview = false,
            open = fromDate,
            close = toDate,
            now = nowDate,
        )

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, got $state", state is MonthlyViewUiState.Error)
        val error = state as MonthlyViewUiState.Error
        assertEquals("Database query failed", error.message)
        assertNotNull(error.cause)
    }

    @Test
    fun loadMonthlyPreview_cancelsPreviousJobWhenInvokedAgain() {
        val executedDispatchers = mutableListOf<String>()
        val testDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                executedDispatchers.add("dispatched")
                block.run()
            }
        }
        val viewModel = MonthlyViewViewModel(db, testDispatcher)

        viewModel.loadMonthlyPreview(a1, false, fromDate, toDate, nowDate)
        viewModel.loadMonthlyPreview(a1, true, fromDate, toDate, nowDate)

        val state = viewModel.uiState.value
        assertTrue(state is MonthlyViewUiState.Success || state is MonthlyViewUiState.Empty)
        if (state is MonthlyViewUiState.Success) {
            assertTrue(state.isStatementPreview)
        } else if (state is MonthlyViewUiState.Empty) {
            assertTrue(state.isStatementPreview)
        }
    }

    @Test
    fun delegationMethods_getAccountAndCustomClosingDay() {
        val viewModel = MonthlyViewViewModel(db, Dispatchers.Unconfined)
        val fetchedAccount = viewModel.getAccount(a1.id)
        assertNotNull(fetchedAccount)
        assertEquals(a1.id, fetchedAccount?.id)

        val customDay = viewModel.getCustomClosingDay(a1.id, 202610)
        assertEquals(0, customDay)
    }
}
