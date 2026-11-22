// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.app

import android.net.Uri
import androidx.core.content.IntentCompat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows
import ru.orangesoftware.financisto.appfunctions.AppFunctionTransactionDraft
import ru.orangesoftware.financisto.appfunctions.TransactionSplit
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.CategoryEntity
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.transaction.ComposeTransactionActivity
import ru.orangesoftware.financisto.transaction.FinancistoDraftSessionStore
import ru.orangesoftware.financisto.transaction.TransactionEntryRouter
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.utils.PicturesUtil
import java.io.File
import java.time.LocalDateTime

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
    fun testGetAccountsFiltersInactiveAndMapsMetadata() {
        AccountBuilder.withDb(db).title("Closed Account").currency(account1.currency).inactive().create()

        val accounts = appDb.getAccounts()
        assertEquals(2, accounts.size)
        val titles = accounts.map { it.title }
        assertTrue(titles.contains("Cash"))
        assertTrue(titles.contains("Card"))
        assertFalse(titles.contains("Closed Account"))
        assertTrue(accounts.all { it.currencyCode == "USD" })
    }

    @Test
    fun testGetCategoriesAndPayees() {
        val initialCategoriesCount = appDb.getCategories().size
        val initialPayeesCount = appDb.getPayees().size

        val food = Category().apply {
            title = "Food"
            type = CategoryEntity.TYPE_EXPENSE
        }
        db.insertOrUpdate(food, emptyList())

        val groceries = Category().apply {
            title = "Groceries"
            parent = food
            type = CategoryEntity.TYPE_EXPENSE
        }
        db.insertOrUpdate(groceries, emptyList())

        val salary = Category().apply {
            title = "Salary"
            type = CategoryEntity.TYPE_INCOME
        }
        db.insertOrUpdate(salary, emptyList())

        val categories = appDb.getCategories()
        assertEquals(initialCategoriesCount + 3, categories.size)
        val groceriesInfo = categories.first { it.id == groceries.id }
        assertEquals("Groceries", groceriesInfo.title)
        assertEquals("Food > Groceries", groceriesInfo.fullPath)
        assertEquals(food.id, groceriesInfo.parentId)
        assertFalse(groceriesInfo.isIncome)

        val salaryInfo = categories.first { it.id == salary.id }
        assertTrue(salaryInfo.isIncome)

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
            type = CategoryEntity.TYPE_EXPENSE
        }
        db.insertOrUpdate(category, emptyList())

        val payee = Payee().apply { title = "McDonalds" }
        val payeeId = db.saveOrUpdate(payee)

        val timestamp = 1_700_000_000_000L
        val txId = appDb.insertOrUpdateTransaction(
            fromAccountId = account1.id,
            toAccountId = null,
            categoryId = category.id,
            payeeId = payeeId,
            amount = -1500L,
            toAmount = null,
            note = "Burger",
            dateTime = timestamp,
            attachedPicture = "receipt_123.jpg",
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
        assertEquals("receipt_123.jpg", tx.attachedPicture)
    }

    @Test
    fun testInsertOrUpdateTransfer() {
        val timestamp = 1_700_000_100_000L
        val txId = appDb.insertOrUpdateTransaction(
            fromAccountId = account1.id,
            toAccountId = account2.id,
            categoryId = null,
            payeeId = null,
            amount = -5000L,
            toAmount = 5000L,
            note = "ATM Deposit",
            dateTime = timestamp,
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

    @Test
    fun testInsertOrUpdateSplitTransaction() {
        val groceries = Category().apply {
            title = "Groceries"
            type = CategoryEntity.TYPE_EXPENSE
        }
        db.insertOrUpdate(groceries, emptyList())

        val gifts = Category().apply {
            title = "Gifts"
            type = CategoryEntity.TYPE_EXPENSE
        }
        db.insertOrUpdate(gifts, emptyList())

        val timestamp = 1_700_000_200_000L
        val txId = appDb.insertOrUpdateTransaction(
            fromAccountId = account2.id,
            toAccountId = null,
            categoryId = Category.SPLIT_CATEGORY_ID,
            payeeId = null,
            amount = -6000L,
            toAmount = null,
            note = "Groceries and birthday gift",
            dateTime = timestamp,
            attachedPicture = "split_receipt.jpg",
            splits = listOf(
                TransactionSplit(categoryId = groceries.id, amount = -3500L, note = "Milk and bread"),
                TransactionSplit(categoryId = gifts.id, amount = -2500L, note = "Birthday gift"),
            ),
        )

        assertTrue(txId > 0L)

        val parentTx = db.load(Transaction::class.java, txId)
        assertNotNull(parentTx)
        assertTrue(parentTx.isSplitParent)
        assertEquals(Category.SPLIT_CATEGORY_ID, parentTx.categoryId)
        assertEquals(-6000L, parentTx.fromAmount)
        assertEquals(timestamp, parentTx.dateTime)
        assertEquals("split_receipt.jpg", parentTx.attachedPicture)
        assertAccountTotal(account2, -6000L)

        val savedSplits = db.getSplitsForTransaction(txId)
        assertEquals(2, savedSplits.size)
        assertEquals(groceries.id, savedSplits[0].categoryId)
        assertEquals(-3500L, savedSplits[0].fromAmount)
        assertEquals("Milk and bread", savedSplits[0].note)
        assertEquals(gifts.id, savedSplits[1].categoryId)
        assertEquals(-2500L, savedSplits[1].fromAmount)
        assertEquals("Birthday gift", savedSplits[1].note)
    }

    @Test
    fun testSaveReceiptPictureAndParseExifDateTime() {
        val tempReceipt = File.createTempFile("test_receipt", ".jpg", context.cacheDir)
        val expectedBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        tempReceipt.writeBytes(expectedBytes)

        val savedFileName = appDb.saveReceiptPicture(Uri.fromFile(tempReceipt))
        assertNotNull(savedFileName)

        val storedFile = PicturesUtil.pictureFile(savedFileName.orEmpty(), false)
        assertTrue(storedFile.exists())
        assertArrayEquals(expectedBytes, storedFile.readBytes())
        storedFile.delete()
        tempReceipt.delete()

        assertEquals(
            LocalDateTime.of(2026, 11, 5, 14, 30, 15),
            AppFunctionDatabaseImpl.parseExifDateTime("2026:11:05 14:30:15"),
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 5, 14, 30, 0),
            AppFunctionDatabaseImpl.parseExifDateTime("2026:11:05 14:30"),
        )
        assertNull(AppFunctionDatabaseImpl.parseExifDateTime("invalid-date"))
        assertNull(AppFunctionDatabaseImpl.parseExifDateTime(null))
    }

    @Test
    fun testGetCategoryHistoryIncludesNormalAndSplitTransactionsWithInheritedPayee() {
        val food = createCategory("Їжа")
        val groceries = createCategory("Продукти", parent = food)
        val gifts = createCategory("Подарунки")
        val silpoId = appDb.createPayee("Сільпо")

        appDb.insertOrUpdateTransaction(
            fromAccountId = account1.id,
            toAccountId = null,
            categoryId = groceries.id,
            payeeId = silpoId,
            amount = -12000L,
            toAmount = null,
            note = "Молоко і хліб",
            dateTime = 1_000_000L,
        )

        val splitParentId = appDb.insertOrUpdateTransaction(
            fromAccountId = account2.id,
            toAccountId = null,
            categoryId = Category.SPLIT_CATEGORY_ID,
            payeeId = silpoId,
            amount = -50000L,
            toAmount = null,
            note = "Чек Сільпо",
            dateTime = 2_000_000L,
            splits = listOf(
                TransactionSplit(categoryId = groceries.id, amount = -20000L, note = "Сир та сметана"),
                TransactionSplit(categoryId = gifts.id, amount = -30000L, note = "Подарунок"),
            ),
        )
        db.db().execSQL("UPDATE transactions SET payee_id = 0 WHERE parent_id = ?", arrayOf(splitParentId))

        appDb.insertOrUpdateTransaction(account1.id, account2.id, null, null, -5000L, 5000L, "Переказ", 3_000_000L)
        db.insertOrUpdate(
            Transaction().apply {
                fromAccountId = account1.id
                categoryId = groceries.id
                fromAmount = -1000L
                note = "Шаблон"
                isTemplate = 1
                dateTime = 4_000_000L
            }
        )

        val history = appDb.getCategoryHistory()
        assertEquals(3, history.size)
        assertTrue(history.all { it.payeeId == silpoId })
        assertTrue(history.any { it.categoryId == groceries.id && it.note == "Сир та сметана" })
        assertTrue(history.any { it.categoryId == gifts.id && it.note == "Подарунок" })
        assertEquals(groceries.id, history[2].categoryId)
        assertEquals("Молоко і хліб", history[2].note)
        assertEquals(2, appDb.getCategoryHistory(limit = 2).size)
        assertTrue(appDb.getCategoryHistory(limit = 0).isEmpty())
    }

    @Test
    fun testCreateTransactionReviewPendingIntentTargetsComposeActivityWithAiDraft() {
        val pendingIntent = appDb.createTransactionReviewPendingIntent(
            AppFunctionTransactionDraft(
                draftId = "draft-123",
                fromAccountId = account1.id,
                toAccountId = null,
                categoryId = Category.SPLIT_CATEGORY_ID,
                payeeId = null,
                payeeName = "Fresh Market",
                amount = -4250L,
                toAmount = null,
                isIncome = false,
                note = "Weekly shop",
                dateTime = 1_700_000_000_000L,
                attachedPicture = "receipt_draft.jpg",
                splits = listOf(
                    TransactionSplit(categoryId = 10L, amount = -2000L, note = "Produce"),
                    TransactionSplit(categoryId = 20L, amount = -2250L, note = "Bakery"),
                ),
            )
        )

        val savedIntent = Shadows.shadowOf(pendingIntent).savedIntent
        assertEquals(ComposeTransactionActivity::class.java.name, savedIntent.component?.className)
        val launchArgs = IntentCompat.getParcelableExtra(
            savedIntent,
            TransactionEntryRouter.EXTRA_LAUNCH_ARGS,
            TransactionLaunchArgs::class.java,
        )
        assertTrue(launchArgs is TransactionLaunchArgs.AiDraft)
        val aiDraft = (launchArgs as TransactionLaunchArgs.AiDraft).draft
        assertEquals("draft-123", aiDraft.draftId)
        assertEquals(TransactionMode.EXPENSE, aiDraft.mode)
        assertEquals(4250L, aiDraft.amountMinor)
        assertEquals(account1.id, aiDraft.fromAccountId)
        assertNull(aiDraft.payeeId)
        assertEquals("Fresh Market", aiDraft.pendingPayeeName)
        assertEquals("Weekly shop", aiDraft.note)
        assertEquals("receipt_draft.jpg", aiDraft.stagedReceiptFileName)
        assertEquals(2, aiDraft.splits.size)
        assertEquals(10L, aiDraft.splits[0].categoryId)
        assertEquals(-2000L, aiDraft.splits[0].amountMinor)

        val draftStore = FinancistoDraftSessionStore(context)
        assertEquals(aiDraft, draftStore.consumePendingAutoOpenDraft())
        assertNull(draftStore.consumePendingAutoOpenDraft())
    }

    private fun createCategory(title: String, parent: Category? = null): Category {
        return Category().apply {
            this.title = title
            this.parent = parent
            this.type = CategoryEntity.TYPE_EXPENSE
        }.also { db.insertOrUpdate(it, emptyList()) }
    }
}
