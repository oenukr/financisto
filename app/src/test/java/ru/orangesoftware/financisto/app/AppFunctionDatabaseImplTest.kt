package ru.orangesoftware.financisto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder

class AppFunctionDatabaseImplTest : AbstractDbTest() {

    private lateinit var appDb: AppFunctionDatabaseImpl
    private lateinit var account1: Account
    private lateinit var account2: Account

    override fun setUp() {
        super.setUp()
        appDb = AppFunctionDatabaseImpl(db)
        val usd = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").create()
        account1 = AccountBuilder.withDb(db).title("Cash").currency(usd).create()
        account2 = AccountBuilder.withDb(db).title("Card").currency(usd).create()
    }

    @Test
    fun testGetAccounts() {
        val accounts = appDb.getAccounts()
        assertEquals(2, accounts.size)
        val titles = accounts.map { it.title }
        assertTrue(titles.contains("Cash"))
        assertTrue(titles.contains("Card"))
    }

    @Test
    fun testGetCategoriesAndPayees() {
        val initialCategoriesCount = appDb.getCategories().size
        val initialPayeesCount = appDb.getPayees().size

        // Create a category
        val category = Category().apply {
            title = "Food"
            type = ru.orangesoftware.financisto.model.CategoryEntity.TYPE_EXPENSE
        }
        db.saveOrUpdate(category)

        val categories = appDb.getCategories()
        assertEquals(initialCategoriesCount + 1, categories.size)
        assertTrue(categories.any { it.title == "Food" })

        // Create payee
        val payeeId = appDb.createPayee("Starbucks")
        assertTrue(payeeId > 0)

        val payees = appDb.getPayees()
        assertEquals(initialPayeesCount + 1, payees.size)
        assertTrue(payees.any { it.title == "Starbucks" })
    }

    @Test
    fun testInsertOrUpdateTransaction() {
        val category = Category().apply {
            title = "Food"
            type = ru.orangesoftware.financisto.model.CategoryEntity.TYPE_EXPENSE
        }
        db.saveOrUpdate(category)

        val payee = Payee().apply { title = "McDonalds" }
        val payeeId = db.saveOrUpdate(payee)

        val timestamp = System.currentTimeMillis()
        val txId = appDb.insertOrUpdateTransaction(
            fromAccountId = account1.id,
            toAccountId = null,
            categoryId = category.id,
            payeeId = payeeId,
            amount = -1500L,
            toAmount = null,
            note = "Burger",
            dateTime = timestamp
        )

        assertTrue(txId > 0L)

        val tx = db.load(Transaction::class.java, txId)
        assertNotNull(tx)
        assertEquals(account1.id, tx.fromAccountId)
        assertEquals(0L, tx.toAccountId)
        assertEquals(category.id, tx.categoryId)
        assertEquals(payeeId, tx.payeeId)
        assertEquals(-1500L, tx.fromAmount)
        assertEquals("Burger", tx.note)
        assertEquals(timestamp, tx.dateTime)
    }

    @Test
    fun testInsertOrUpdateTransfer() {
        val timestamp = System.currentTimeMillis()
        val txId = appDb.insertOrUpdateTransaction(
            fromAccountId = account1.id,
            toAccountId = account2.id,
            categoryId = null,
            payeeId = null,
            amount = -5000L,
            toAmount = 5000L,
            note = "ATM Deposit",
            dateTime = timestamp
        )

        assertTrue(txId > 0L)

        val tx = db.load(Transaction::class.java, txId)
        assertNotNull(tx)
        assertEquals(account1.id, tx.fromAccountId)
        assertEquals(account2.id, tx.toAccountId)
        assertEquals(-5000L, tx.fromAmount)
        assertEquals(5000L, tx.toAmount)
        assertEquals("ATM Deposit", tx.note)
        assertEquals(timestamp, tx.dateTime)
    }
}
