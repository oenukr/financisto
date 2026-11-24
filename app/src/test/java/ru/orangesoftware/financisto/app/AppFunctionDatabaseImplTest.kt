// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.app

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.IntentCompat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Shadows
import ru.orangesoftware.financisto.appfunctions.AppFunctionDatabase
import ru.orangesoftware.financisto.appfunctions.AppFunctionTransactionDraft
import ru.orangesoftware.financisto.appfunctions.TransactionSplit
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.CategoryEntity
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.transaction.ComposeTransactionActivity
import ru.orangesoftware.financisto.transaction.FinancistoDraftSessionStore
import ru.orangesoftware.financisto.transaction.TransactionEntryRouter
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionRepository
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.PicturesUtil
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import kotlin.time.Clock

class AppFunctionDatabaseImplTest : AbstractDbTest() {

    private lateinit var draftStore: FinancistoDraftSessionStore
    private lateinit var appDb: AppFunctionDatabaseImpl
    private lateinit var account1: Account
    private lateinit var account2: Account

    override fun setUp() {
        super.setUp()
        draftStore = FinancistoDraftSessionStore(context)
        appDb = AppFunctionDatabaseImpl(db, draftStore)
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
        assertTrue(accounts.all { it.currencyCode == "USD" && it.currencyId == account1.currency.id })
    }

    @Test
    fun testGetCategoriesAndPayees() {
        val initialCategoriesCount = appDb.getCategories().size
        val initialPayeesCount = appDb.getPayees().size

        val food = createCategory("Food")
        val groceries = createCategory("Groceries", parent = food)
        val salary = Category().apply {
            title = "Salary"
            type = CategoryEntity.TYPE_INCOME
        }.also { db.insertOrUpdate(it, emptyList()) }

        val categories = appDb.getCategories()
        assertEquals(initialCategoriesCount + 3, categories.size)
        val groceriesInfo = categories.first { it.id == groceries.id }
        assertEquals("Groceries", groceriesInfo.title)
        assertEquals("Food > Groceries", groceriesInfo.fullPath)
        assertEquals(food.id, groceriesInfo.parentId)
        assertFalse(groceriesInfo.isIncome)

        val salaryInfo = categories.first { it.id == salary.id }
        assertTrue(salaryInfo.isIncome)

        val payeeId = db.saveOrUpdate(Payee().apply { title = "Starbucks" })
        assertTrue(payeeId > 0)

        val payees = appDb.getPayees()
        assertEquals(initialPayeesCount + 1, payees.size)
        assertTrue(payees.any { it.title == "Starbucks" })
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
    fun testSaveReceiptPictureDeletesPartialFileWhenCopyFails() {
        val picturesDir = PicturesUtil.pictureFile("probe.jpg", false).parentFile
        requireNotNull(picturesDir)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()

        val uri = Uri.parse("content://ru.orangesoftware.financisto.test/broken_receipt.jpg")
        val failingStream = object : InputStream() {
            private var deliveredFirstByte = false
            override fun read(): Int {
                if (!deliveredFirstByte) {
                    deliveredFirstByte = true
                    return 0xFF
                }
                throw IOException("Simulated stream read failure")
            }
        }
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, failingStream)

        val savedFileName = appDb.saveReceiptPicture(uri)
        assertNull(savedFileName)

        val afterFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(beforeFiles, afterFiles)
    }

    @Test
    fun testGetCategoryHistoryIncludesNormalAndSplitTransactionsWithInheritedPayee() {
        val food = createCategory("Їжа")
        val groceries = createCategory("Продукти", parent = food)
        val gifts = createCategory("Подарунки")
        val silpoId = db.saveOrUpdate(Payee().apply { title = "Сільпо" })

        seedCategoryHistoryFixtures(groceries, gifts, silpoId)

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

    private fun seedCategoryHistoryFixtures(groceries: Category, gifts: Category, silpoId: Long) {
        db.insertOrUpdate(
            Transaction().apply {
                fromAccountId = account1.id
                categoryId = groceries.id
                payeeId = silpoId
                fromAmount = -12000L
                note = "Молоко і хліб"
                dateTime = 1_000_000L
            }
        )
        val splitParentId = db.insertOrUpdate(
            Transaction().apply {
                fromAccountId = account2.id
                categoryId = Category.SPLIT_CATEGORY_ID
                payeeId = silpoId
                fromAmount = -50000L
                note = "Чек Сільпо"
                dateTime = 2_000_000L
                splits = listOf(
                    Transaction().apply {
                        categoryId = groceries.id
                        fromAmount = -20000L
                        note = "Сир та сметана"
                    },
                    Transaction().apply {
                        categoryId = gifts.id
                        fromAmount = -30000L
                        note = "Подарунок"
                    },
                )
            }
        )
        db.db().execSQL("UPDATE transactions SET payee_id = 0 WHERE parent_id = ?", arrayOf(splitParentId))
        db.insertOrUpdate(
            Transaction().apply {
                fromAccountId = account1.id
                toAccountId = account2.id
                fromAmount = -5000L
                toAmount = 5000L
                note = "Переказ"
                dateTime = 3_000_000L
            }
        )
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
    }

    @Test
    fun testCreateTransactionReviewPendingIntentTargetsComposeActivityWithoutCompetingLaunch() {
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

        assertNull(Shadows.shadowOf(context as Application).nextStartedActivity)

        val savedIntent = Shadows.shadowOf(pendingIntent).savedIntent
        assertEquals(ComposeTransactionActivity::class.java.name, savedIntent.component?.className)
        assertTrue(savedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(savedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(savedIntent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertEquals(0, savedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)

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

        assertEquals(aiDraft, draftStore.consumePendingAutoOpenDraft())
        assertNull(draftStore.consumePendingAutoOpenDraft())
    }

    @Test
    fun testCreateTransactionReviewPendingIntentPropagatesReceiptAttachFailed() {
        val defaultIntent = Shadows.shadowOf(
            appDb.createTransactionReviewPendingIntent(
                sampleAppFunctionDraft(draftId = "draft-receipt-ok", amount = -1000L, note = "Receipt ok")
            )
        ).savedIntent
        val defaultDraft =
            (TransactionEntryRouter.resolveLaunchArgs(defaultIntent) as TransactionLaunchArgs.AiDraft).draft
        assertFalse(defaultDraft.receiptAttachFailed)
        assertEquals(defaultDraft, draftStore.consumePendingAutoOpenDraft())

        val failedIntent = Shadows.shadowOf(
            appDb.createTransactionReviewPendingIntent(
                sampleAppFunctionDraft(
                    draftId = "draft-receipt-failed",
                    amount = -1000L,
                    note = "Receipt failed",
                ).copy(receiptAttachFailed = true)
            )
        ).savedIntent
        val failedDraft =
            (TransactionEntryRouter.resolveLaunchArgs(failedIntent) as TransactionLaunchArgs.AiDraft).draft
        assertTrue(failedDraft.receiptAttachFailed)
        assertEquals(failedDraft, draftStore.consumePendingAutoOpenDraft())
    }

    @Test
    fun testCreateTransactionReviewPendingIntentDistinguishesHashCollidingDraftIds() {
        val draftIdA = "00000000-0000-4000-8000-00000500c004"
        val draftIdB = "00000000-0000-4000-8000-000000510170"
        assertEquals(draftIdA.hashCode(), draftIdB.hashCode())

        val pendingA = appDb.createTransactionReviewPendingIntent(
            sampleAppFunctionDraft(draftId = draftIdA, amount = -1000L, note = "Draft A")
        )
        val pendingB = appDb.createTransactionReviewPendingIntent(
            sampleAppFunctionDraft(draftId = draftIdB, amount = -2000L, note = "Draft B")
        )

        val intentA = Shadows.shadowOf(pendingA).savedIntent
        val intentB = Shadows.shadowOf(pendingB).savedIntent
        assertEquals("financisto://ai-draft/$draftIdA", intentA.data?.toString())
        assertEquals("financisto://ai-draft/$draftIdB", intentB.data?.toString())
        assertNotEquals(intentA.data, intentB.data)
        assertFalse(intentA.filterEquals(intentB))

        val argsA = TransactionEntryRouter.resolveLaunchArgs(intentA) as TransactionLaunchArgs.AiDraft
        val argsB = TransactionEntryRouter.resolveLaunchArgs(intentB) as TransactionLaunchArgs.AiDraft
        assertEquals(draftIdA, argsA.draft.draftId)
        assertEquals(draftIdB, argsB.draft.draftId)

        val updatedA = appDb.createTransactionReviewPendingIntent(
            sampleAppFunctionDraft(draftId = draftIdA, amount = -1500L, note = "Draft A Updated")
        )
        assertTrue(intentA.filterEquals(Shadows.shadowOf(updatedA).savedIntent))
    }

    @Test
    fun testCreateTransactionReviewPendingIntentScalesZeroDecimalSplitsAndTransfer() {
        val jpy = createJpyCurrency()
        val jpyAccount1 = AccountBuilder.withDb(db).title("JPY Cash").currency(jpy).create()
        val jpyAccount2 = AccountBuilder.withDb(db).title("JPY Card").currency(jpy).create()

        val splitIntent = Shadows.shadowOf(
            appDb.createTransactionReviewPendingIntent(
                sampleAppFunctionDraft("draft-jpy-split", amount = -100_000L, note = "JPY dinner").copy(
                    fromAccountId = jpyAccount1.id,
                    categoryId = Category.SPLIT_CATEGORY_ID,
                    splits = listOf(
                        TransactionSplit(categoryId = 10L, amount = -40_000L, note = "Food"),
                        TransactionSplit(categoryId = 20L, amount = -60_000L, note = "Drinks"),
                    ),
                )
            )
        ).savedIntent
        val splitDraft = (TransactionEntryRouter.resolveLaunchArgs(splitIntent) as TransactionLaunchArgs.AiDraft).draft
        assertEquals(1_000L, splitDraft.amountMinor)
        assertEquals(listOf(-400L, -600L), splitDraft.splits.map { it.amountMinor })

        val transferIntent = Shadows.shadowOf(
            appDb.createTransactionReviewPendingIntent(
                sampleAppFunctionDraft("draft-jpy-transfer", amount = -100_000L, note = "JPY transfer").copy(
                    fromAccountId = jpyAccount1.id,
                    toAccountId = jpyAccount2.id,
                    toAmount = 100_000L,
                )
            )
        ).savedIntent
        val transferDraft =
            (TransactionEntryRouter.resolveLaunchArgs(transferIntent) as TransactionLaunchArgs.AiDraft).draft
        assertEquals(1_000L, transferDraft.amountMinor)
        assertEquals(1_000L, transferDraft.toAmountMinor)
    }

    @Test
    fun testCreateTransactionReviewPendingIntentRebalancesSubUnitSplitRounding() {
        val jpy = createJpyCurrency()
        val jpyAccount = AccountBuilder.withDb(db).title("JPY Cash").currency(jpy).create()

        val rebalancedIntent = Shadows.shadowOf(
            appDb.createTransactionReviewPendingIntent(
                sampleAppFunctionDraft("draft-jpy-rebalance", amount = -300L, note = "JPY rounding").copy(
                    fromAccountId = jpyAccount.id,
                    categoryId = Category.SPLIT_CATEGORY_ID,
                    splits = listOf(
                        TransactionSplit(categoryId = 10L, amount = -150L, note = "S1"),
                        TransactionSplit(categoryId = 20L, amount = -150L, note = "S2"),
                    ),
                )
            )
        ).savedIntent
        val rebalancedDraft =
            (TransactionEntryRouter.resolveLaunchArgs(rebalancedIntent) as TransactionLaunchArgs.AiDraft).draft
        assertEquals(3L, rebalancedDraft.amountMinor)
        assertEquals(listOf(-2L, -1L), rebalancedDraft.splits.map { it.amountMinor })
    }

    private fun createJpyCurrency(): Currency {
        return Currency().apply {
            name = "JPY"
            title = "Japanese Yen"
            symbol = "¥"
            decimals = 0
        }.also {
            db.saveOrUpdate(it)
            CurrencyCache.initialize(db)
        }
    }

    @Test
    fun testSaveReceiptPictureRejectsFailingContentUriWithoutReadingLocalFile() {
        val localSecret = File.createTempFile("secret_receipt", ".txt", context.cacheDir).apply {
            writeText("sensitive-data")
        }
        val picturesDir = requireNotNull(PicturesUtil.pictureFile("probe.jpg", false).parentFile)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        try {
            val failingResolver = mock(ContentResolver::class.java)
            `when`(failingResolver.openInputStream(any())).thenThrow(SecurityException("Denied"))
            val wrappedContext = object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = failingResolver
            }
            val guardedAppDb = AppFunctionDatabaseImpl(DatabaseAdapter(wrappedContext), draftStore)

            val contentUri = Uri.parse("content://ru.orangesoftware.financisto.test${localSecret.absolutePath}")
            assertNull(guardedAppDb.saveReceiptPicture(contentUri))
            assertNull(appDb.saveReceiptPicture(contentUri))
            val afterFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
            assertEquals(beforeFiles, afterFiles)
        } finally {
            localSecret.delete()
        }
    }

    @Test
    fun testCreateTransactionReviewPendingIntentReturnsPendingIntentWhenFallbackCommitFails() {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.commit()).thenReturn(false)

        val failingStore = FinancistoDraftSessionStore(mockContext)
        val appDbWithFailingStore = AppFunctionDatabaseImpl(db, failingStore)
        val pendingIntent = appDbWithFailingStore.createTransactionReviewPendingIntent(
            sampleAppFunctionDraft(draftId = "draft-commit-fail", amount = -1000L, note = "Fallback fail")
        )
        assertNotNull(pendingIntent)
        val savedIntent = Shadows.shadowOf(pendingIntent).savedIntent
        assertEquals("financisto://ai-draft/draft-commit-fail", savedIntent.data?.toString())
    }

    private fun sampleAppFunctionDraft(
        draftId: String,
        amount: Long,
        note: String,
    ): AppFunctionTransactionDraft = AppFunctionTransactionDraft(
        draftId = draftId,
        fromAccountId = account1.id,
        toAccountId = null,
        categoryId = null,
        payeeId = null,
        payeeName = null,
        amount = amount,
        toAmount = null,
        isIncome = false,
        note = note,
        dateTime = 1_700_000_000_000L,
        attachedPicture = null,
    )

    @Test
    fun testKoinResolvesAppFunctionDatabaseAndTransactionPorts() {
        val koin = GlobalContext.get()
        assertNotNull(koin.get<AppFunctionDatabase>())
        assertNotNull(koin.get<FinancistoDraftSessionStore>())
        assertNotNull(koin.get<DraftSessionStore>())
        assertNotNull(koin.get<TransactionRepository>())
        assertNotNull(koin.get<ExchangeRatePort>())
        assertNotNull(koin.get<ReceiptMediaPort>())
        assertNotNull(koin.get<Clock>())
    }

    private fun createCategory(title: String, parent: Category? = null): Category {
        return Category().apply {
            this.title = title
            this.parent = parent
            this.type = CategoryEntity.TYPE_EXPENSE
        }.also { db.insertOrUpdate(it, emptyList()) }
    }
}
