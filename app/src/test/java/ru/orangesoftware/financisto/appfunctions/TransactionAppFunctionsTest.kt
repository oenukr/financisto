package ru.orangesoftware.financisto.appfunctions

import androidx.appfunctions.AppFunctionContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TransactionAppFunctionsTest {

    private val db: AppFunctionDatabase = mock()
    private val context: AppFunctionContext = mock()
    private lateinit var appFunctions: TransactionAppFunctions

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            modules(module {
                single { db }
            })
        }
        appFunctions = TransactionAppFunctions()

        // Mock accounts, categories, and payees lookup
        whenever(db.getAccounts()).thenReturn(
            listOf(
                AccountInfo(1L, "Cash"),
                AccountInfo(2L, "Card")
            )
        )
        whenever(db.getCategories()).thenReturn(
            listOf(
                CategoryInfo(10L, "Food"),
                CategoryInfo(20L, "Rent")
            )
        )
        whenever(db.getPayees()).thenReturn(
            listOf(
                PayeeInfo(100L, "Starbucks")
            )
        )
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testAddTransactionExpense() {
        runBlocking {
            whenever(db.insertOrUpdateTransaction(
                fromAccountId = eq(1L),
                toAccountId = eq(null),
                categoryId = eq(10L),
                payeeId = eq(100L),
                amount = eq(-1550L), // 15.50 * 100 * -1
                toAmount = eq(null),
                note = eq("Coffee"),
                dateTime = any()
            )).thenReturn(1000L)

            val result = appFunctions.addTransaction(
                context = context,
                amount = 15.50,
                accountName = "Cash",
                categoryName = "Food",
                payeeName = "Starbucks",
                note = "Coffee",
                isIncome = false,
                toAccountName = null
            )

            assertEquals(1000L, result)
            verify(db).insertOrUpdateTransaction(
                fromAccountId = eq(1L),
                toAccountId = eq(null),
                categoryId = eq(10L),
                payeeId = eq(100L),
                amount = eq(-1550L),
                toAmount = eq(null),
                note = eq("Coffee"),
                dateTime = any()
            )
        }
    }

    @Test
    fun testAddTransactionIncome() {
        runBlocking {
            whenever(db.insertOrUpdateTransaction(
                fromAccountId = eq(2L),
                toAccountId = eq(null),
                categoryId = eq(20L),
                payeeId = eq(0L), // empty payee
                amount = eq(50000L), // 500.00 * 100 * 1
                toAmount = eq(null),
                note = eq("Rent return"),
                dateTime = any()
            )).thenReturn(1001L)

            val result = appFunctions.addTransaction(
                context = context,
                amount = 500.00,
                accountName = "Card",
                categoryName = "Rent",
                payeeName = null,
                note = "Rent return",
                isIncome = true,
                toAccountName = null
            )

            assertEquals(1001L, result)
        }
    }

    @Test
    fun testAddTransactionTransfer() {
        runBlocking {
            whenever(db.insertOrUpdateTransaction(
                fromAccountId = eq(1L),
                toAccountId = eq(2L),
                categoryId = eq(0L),
                payeeId = eq(0L),
                amount = eq(-5000L), // Deducted from source: 50.00 * 100 * -1
                toAmount = eq(5000L),  // Added to destination
                note = eq("ATM"),
                dateTime = any()
            )).thenReturn(1002L)

            val result = appFunctions.addTransaction(
                context = context,
                amount = 50.00,
                accountName = "Cash",
                categoryName = null,
                payeeName = null,
                note = "ATM",
                isIncome = null,
                toAccountName = "Card"
            )

            assertEquals(1002L, result)
        }
    }

    @Test
    fun testAddTransactionCreatePayee() {
        runBlocking {
            // Mock creating a payee
            whenever(db.createPayee("McDonalds")).thenReturn(200L)

            whenever(db.insertOrUpdateTransaction(
                fromAccountId = eq(1L),
                toAccountId = eq(null),
                categoryId = eq(0L),
                payeeId = eq(200L), // Newly created payee
                amount = eq(-2500L),
                toAmount = eq(null),
                note = eq("Lunch"),
                dateTime = any()
            )).thenReturn(1003L)

            val result = appFunctions.addTransaction(
                context = context,
                amount = 25.00,
                accountName = "Cash",
                categoryName = null,
                payeeName = "McDonalds",
                note = "Lunch",
                isIncome = false,
                toAccountName = null
            )

            assertEquals(1003L, result)
            verify(db).createPayee("McDonalds")
        }
    }

    @Test
    fun testAddTransactionAccountNotFound() {
        runBlocking {
            val result = appFunctions.addTransaction(
                context = context,
                amount = 25.00,
                accountName = "UnknownAccount",
                categoryName = null,
                payeeName = null,
                note = "Lunch",
                isIncome = false,
                toAccountName = null
            )

            assertEquals(-1L, result)
        }
    }

    @Test
    fun testAddTransactionFuzzyMatching() {
        runBlocking {
            whenever(db.insertOrUpdateTransaction(
                fromAccountId = eq(1L),
                toAccountId = eq(null),
                categoryId = eq(10L),
                payeeId = eq(100L),
                amount = eq(-1550L),
                toAmount = eq(null),
                note = eq("Coffee"),
                dateTime = any()
            )).thenReturn(1000L)

            // 1. Test "Csh!" typo for "Cash", "food" (casing) for "Food", "Starbuck" typo for "Starbucks"
            val result = appFunctions.addTransaction(
                context = context,
                amount = 15.50,
                accountName = "Csh!",      // matches "Cash" (Levenshtein/Punctuation)
                categoryName = "food",     // matches "Food" (Casing)
                payeeName = "Starbuck",    // matches "Starbucks" (Levenshtein)
                note = "Coffee",
                isIncome = false,
                toAccountName = null
            )

            assertEquals(1000L, result)
            verify(db).insertOrUpdateTransaction(
                fromAccountId = eq(1L),
                toAccountId = eq(null),
                categoryId = eq(10L),
                payeeId = eq(100L),
                amount = eq(-1550L),
                toAmount = eq(null),
                note = eq("Coffee"),
                dateTime = any()
            )
        }
    }
}
