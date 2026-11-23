// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appfunctions

import android.app.PendingIntent
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.orangesoftware.financisto.app.AppFunctionDatabaseImpl
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.CategoryEntity
import ru.orangesoftware.financisto.model.CategoryTree
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.transaction.FinancistoDraftSessionStore
import java.lang.reflect.Field
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TransactionAppFunctionsTest {

    private val db: AppFunctionDatabase = mock()
    private lateinit var appFunctions: TransactionAppFunctions

    @Before
    fun setUp() {
        ensureBundleEmptyInitialized()
        stopKoin()
        startKoin { modules(module { single { db } }) }
        appFunctions = TransactionAppFunctionService()
        setupDefaultFixtures(db)
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testOpenDraftExpense() {
        runBlocking {
            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
            val result = appFunctions.openDraft(
                amount = 15.50,
                accountName = "Cash",
                categoryName = "Food",
                payeeName = "Starbucks",
                note = "Coffee",
                isIncome = false,
            )
            val draft = captureDraft(db)
            assertTrue(draft.draftId.isNotBlank())
            assertSame(reviewIntent, result)
            assertEquals(1L, draft.fromAccountId)
            assertEquals(10L, draft.categoryId)
            assertEquals(100L, draft.payeeId)
            assertEquals("Starbucks", draft.payeeName)
            assertEquals(-1550L, draft.amount)
            assertFalse(draft.isIncome)
            assertEquals("Coffee", draft.note)
            assertNull(draft.attachedPicture)
        }
    }

    @Test
    fun testOpenDraftIncome() {
        runBlocking {
            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
            val result = appFunctions.openDraft(
                amount = 500.00,
                accountName = "Card",
                categoryName = "Salary",
                note = "Pay",
                isIncome = true,
            )
            val draft = captureDraft(db)
            assertSame(reviewIntent, result)
            assertEquals(2L, draft.fromAccountId)
            assertEquals(40L, draft.categoryId)
            assertEquals(50000L, draft.amount)
            assertTrue(draft.isIncome)
            assertEquals("Pay", draft.note)
        }
    }

    @Test
    fun testOpenDraftTransfer() {
        runBlocking {
            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
            val result = appFunctions.openDraft(50.00, "Cash", note = "ATM", toAccountName = "Card")
            val draft = captureDraft(db)
            assertSame(reviewIntent, result)
            assertEquals(1L, draft.fromAccountId)
            assertEquals(2L, draft.toAccountId)
            assertEquals(0L, draft.categoryId)
            assertEquals(-5000L, draft.amount)
            assertEquals(5000L, draft.toAmount)
            assertFalse(draft.isIncome)
            assertEquals("ATM", draft.note)
        }
    }

    @Test
    fun testOpenDraftDefersNewPayeeCreation() {
        runBlocking {
            appFunctions.openDraft(
                amount = 25.00,
                accountName = "Cash",
                payeeName = "McDonalds",
                note = "Lunch",
                isIncome = false,
            )
            val draft = captureDraft(db)
            assertEquals(1L, draft.fromAccountId)
            assertEquals(0L, draft.categoryId)
            assertNull(draft.payeeId)
            assertEquals("McDonalds", draft.payeeName)
            assertEquals(-2500L, draft.amount)
            assertEquals("Lunch", draft.note)
        }
    }

    @Test
    fun testOpenDraftAutoClassifiesCategoryWhenNull() {
        runBlocking {
            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)

            val byNoteResult = appFunctions.openDraft(
                amount = 18.00,
                accountName = "Cash",
                note = "Fresh milk and bread",
                isIncome = false,
            )
            assertSame(reviewIntent, byNoteResult)

            val byPayeeResult = appFunctions.openDraft(
                amount = 15.50,
                accountName = "Cash",
                payeeName = "Starbucks",
                note = "Coffee",
                isIncome = false,
            )
            assertSame(reviewIntent, byPayeeResult)

            val captor = argumentCaptor<AppFunctionTransactionDraft>()
            verify(db, times(2)).createTransactionReviewPendingIntent(captor.capture())
            assertEquals(11L, captor.firstValue.categoryId)
            assertEquals(-1800L, captor.firstValue.amount)
            assertEquals(10L, captor.secondValue.categoryId)
            assertEquals(100L, captor.secondValue.payeeId)
            assertEquals(-1550L, captor.secondValue.amount)
        }
    }

    @Test
    fun testOpenDraftAccountNotFoundThrowsElementNotFoundWithAvailableAccounts() {
        val ex = assertThrows(AppFunctionElementNotFoundException::class.java) {
            runBlocking { appFunctions.openDraft(amount = 25.00, accountName = "UnknownAccount", note = "Lunch") }
        }
        assertTrue(ex.message.orEmpty().contains("Cash"))
        assertTrue(ex.message.orEmpty().contains("Card"))
    }

    @Test
    fun testOpenDraftCategoryNotFoundThrowsElementNotFoundWithAvailableCategories() {
        val ex = assertThrows(AppFunctionElementNotFoundException::class.java) {
            runBlocking {
                appFunctions.openDraft(
                    amount = 25.00,
                    accountName = "Cash",
                    categoryName = "NonExistentCategory",
                    note = "Lunch",
                )
            }
        }
        assertTrue(ex.message.orEmpty().contains("Food > Groceries"))
        assertTrue(ex.message.orEmpty().contains("Gifts"))
    }

    @Test
    fun testOpenDraftFuzzyMatching() {
        runBlocking {
            appFunctions.openDraft(
                amount = 15.50,
                accountName = "Csh!",
                categoryName = "food",
                payeeName = "Starbuck",
                note = "Coffee",
                isIncome = false,
            )
            val draft = captureDraft(db)
            assertEquals(1L, draft.fromAccountId)
            assertEquals(10L, draft.categoryId)
            assertEquals(100L, draft.payeeId)
            assertEquals("Starbucks", draft.payeeName)
            assertEquals(-1550L, draft.amount)
        }
    }

    @Test
    fun testOpenDraftNegativeAmountExpense() {
        runBlocking {
            appFunctions.openDraft(
                amount = -15.50,
                accountName = "Cash",
                categoryName = "Food",
                payeeName = "Starbucks",
                note = "Coffee",
                isIncome = false,
            )
            val draft = captureDraft(db)
            assertEquals(-1550L, draft.amount)
        }
    }

    @Test
    fun testOpenDraftNegativeAmountTransfer() {
        runBlocking {
            appFunctions.openDraft(-50.00, "Cash", note = "ATM", toAccountName = "Card")
            val draft = captureDraft(db)
            assertEquals(1L, draft.fromAccountId)
            assertEquals(2L, draft.toAccountId)
            assertEquals(-5000L, draft.amount)
            assertEquals(5000L, draft.toAmount)
            assertEquals("ATM", draft.note)
        }
    }

    @Test
    fun testOpenDraftShortQueryNoFuzzyMatch() {
        whenever(db.getAccounts()).thenReturn(listOf(AccountInfo(1L, "Cash"), AccountInfo(3L, "C")))
        assertThrows(AppFunctionElementNotFoundException::class.java) {
            runBlocking { appFunctions.openDraft(amount = 10.0, accountName = "B", isIncome = false) }
        }
    }

    @Test
    fun testOpenDraftInvalidAmountThrowsInvalidArgument() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking { appFunctions.openDraft(amount = 0.0, accountName = "Cash") }
        }
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking { appFunctions.openDraft(amount = Double.NaN, accountName = "Cash") }
        }
    }

    @Test
    fun testOpenDraftTransferSameAccountThrowsInvalidArgument() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking { appFunctions.openDraft(amount = 20.0, accountName = "Cash", toAccountName = "Cash") }
        }
    }

    @Test
    fun testOpenDraftWithReceiptDateTimeAndImageAttachment() {
        runBlocking {
            val mockUri: Uri = mock()
            val receiptDate = LocalDateTime.of(2026, 11, 5, 14, 30, 0)
            val expectedMillis = receiptDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            whenever(db.saveReceiptPicture(mockUri)).thenReturn("20261105143000.jpg")

            appFunctions.openDraft(
                amount = 42.75,
                accountName = "Card",
                categoryName = "Food > Groceries",
                payeeName = null,
                note = "Weekly groceries",
                isIncome = false,
                transactionDateTime = receiptDate,
                receiptImageUri = mockUri,
            )

            val draft = captureDraft(db)
            assertEquals(2L, draft.fromAccountId)
            assertEquals(11L, draft.categoryId)
            assertNull(draft.payeeId)
            assertEquals(-4275L, draft.amount)
            assertEquals("Weekly groceries", draft.note)
            assertEquals(expectedMillis, draft.dateTime)
            assertEquals("20261105143000.jpg", draft.attachedPicture)
        }
    }

    @Test
    fun testOpenDraftWithSplitCategoriesGroceriesAndGifts() {
        runBlocking {
            val receiptDate = LocalDateTime.of(2026, 11, 6, 18, 15, 0)
            val expectedMillis = receiptDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val expectedSplits = listOf(
                TransactionSplit(categoryId = 11L, amount = -3500L, note = "Milk and bread"),
                TransactionSplit(categoryId = 30L, amount = -2500L, note = "Birthday gift"),
            )

            appFunctions.openDraft(
                amount = 60.00,
                accountName = "Card",
                note = "Supermarket checkout",
                isIncome = false,
                transactionDateTime = receiptDate,
                splits = listOf(
                    TransactionSplitInput(amount = 35.00, categoryName = "Food > Groceries", note = "Milk and bread"),
                    TransactionSplitInput(amount = 25.00, categoryName = "Gifts", note = "Birthday gift"),
                ),
            )

            val draft = captureDraft(db)
            assertEquals(2L, draft.fromAccountId)
            assertEquals(-1L, draft.categoryId)
            assertEquals(-6000L, draft.amount)
            assertEquals("Supermarket checkout", draft.note)
            assertEquals(expectedMillis, draft.dateTime)
            assertEquals(expectedSplits, draft.splits)
        }
    }

    @Test
    fun testOpenDraftSplitInfersCategoryFromNoteAndAdjustsOneCentRounding() {
        runBlocking {
            val expectedSplits = listOf(
                TransactionSplit(categoryId = 11L, amount = -3333L, note = "Fresh milk and bread"),
                TransactionSplit(categoryId = 30L, amount = -3333L, note = "Birthday gift toy"),
                TransactionSplit(categoryId = 20L, amount = -3334L, note = "Monthly rent share"),
            )

            appFunctions.openDraft(
                amount = 100.00,
                accountName = "Cash",
                splits = listOf(
                    TransactionSplitInput(amount = 33.33, categoryName = null, note = "Fresh milk and bread"),
                    TransactionSplitInput(amount = 33.33, categoryName = null, note = "Birthday gift toy"),
                    TransactionSplitInput(amount = 33.33, categoryName = null, note = "Monthly rent share"),
                ),
            )

            val draft = captureDraft(db)
            assertEquals(1L, draft.fromAccountId)
            assertEquals(-1L, draft.categoryId)
            assertEquals(-10000L, draft.amount)
            assertEquals(expectedSplits, draft.splits)
        }
    }

    @Test
    fun testOpenDraftSplitAmountMismatchThrowsInvalidArgument() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.openDraft(
                    amount = 60.00,
                    accountName = "Card",
                    splits = listOf(
                        TransactionSplitInput(amount = 20.00, categoryName = "Groceries"),
                        TransactionSplitInput(amount = 20.00, categoryName = "Gifts"),
                    ),
                )
            }
        }
    }

    @Test
    fun testOpenDraftSplitWithTransferThrowsInvalidArgument() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.openDraft(
                    amount = 60.00,
                    accountName = "Cash",
                    toAccountName = "Card",
                    splits = listOf(TransactionSplitInput(amount = 60.00, categoryName = "Groceries")),
                )
            }
        }
    }

    @Test
    fun testPrepareReceiptTransactionSingleCategoryByPayeeHistory() {
        runBlocking {
            val prep = appFunctions.prepareReceiptTransaction(merchantName = "Starbuck", receiptItems = "Latte")
            assertNull(prep.matchedAccount)
            assertEquals(2, prep.candidateAccounts.size)
            assertTrue(prep.requiresAccountSelection)
            assertEquals("Food", prep.suggestedCategory?.fullPath)
            assertEquals("HIGH", prep.categoryConfidence)
            assertFalse(prep.isSplitTransaction)
            assertFalse(prep.requiresCategoryConfirmation)
            assertEquals("Starbucks", prep.resolvedPayeeName)
        }
    }

    @Test
    fun testPrepareReceiptTransactionSingleCategoryByKeywordsAndExifDate() {
        runBlocking {
            val mockUri: Uri = mock()
            val exifDate = LocalDateTime.of(2026, 10, 20, 9, 15, 0)
            whenever(db.extractReceiptImageDateTime(mockUri)).thenReturn(exifDate)

            val prep = appFunctions.prepareReceiptTransaction(
                merchantName = "Local Market",
                receiptItems = "Milk, bread, and eggs",
                accountQuery = "Cash",
                receiptImageUri = mockUri,
            )

            assertEquals("Cash", prep.matchedAccount?.name)
            assertFalse(prep.requiresAccountSelection)
            assertEquals("Food > Groceries", prep.suggestedCategory?.fullPath)
            assertEquals("MEDIUM", prep.categoryConfidence)
            assertFalse(prep.isSplitTransaction)
            assertFalse(prep.requiresCategoryConfirmation)
            assertEquals(exifDate, prep.resolvedDateTime)
        }
    }

    @Test
    fun testPrepareReceiptTransactionAccountDefaultCategoryLowConfidence() {
        runBlocking {
            val prep = appFunctions.prepareReceiptTransaction(
                merchantName = "Unknown Vendor XYZ",
                receiptItems = "Misc item",
                accountQuery = "Card",
            )
            assertEquals("Card", prep.matchedAccount?.name)
            assertEquals("Rent", prep.suggestedCategory?.fullPath)
            assertEquals("LOW", prep.categoryConfidence)
            assertTrue(prep.requiresCategoryConfirmation)
        }
    }

    @Test
    fun testPrepareReceiptTransactionSplitLineItemsGroceriesAndGifts() {
        runBlocking {
            val receiptDate = LocalDateTime.of(2026, 11, 7, 12, 0, 0)
            val prep = appFunctions.prepareReceiptTransaction(
                merchantName = "Target",
                lineItems = listOf(
                    ReceiptLineItem(description = "Organic milk and bread", amount = 20.00),
                    ReceiptLineItem(description = "Fresh vegetables", amount = 15.00),
                    ReceiptLineItem(description = "Birthday gift toy", amount = 25.00),
                ),
                accountQuery = "Card",
                receiptDateTime = receiptDate,
            )

            assertTrue(prep.isSplitTransaction)
            assertNull(prep.suggestedCategory)
            assertTrue(prep.requiresCategoryConfirmation)
            assertFalse(prep.requiresAccountSelection)
            assertEquals(2, prep.suggestedSplits.size)

            val groceriesSplit = prep.suggestedSplits[0]
            assertEquals("Food > Groceries", groceriesSplit.category?.fullPath)
            assertEquals(35.00, groceriesSplit.amount ?: 0.0, 0.001)
            assertEquals("Organic milk and bread, Fresh vegetables", groceriesSplit.note)

            val giftsSplit = prep.suggestedSplits[1]
            assertEquals("Gifts", giftsSplit.category?.fullPath)
            assertEquals(25.00, giftsSplit.amount ?: 0.0, 0.001)
            assertEquals("Birthday gift toy", giftsSplit.note)
        }
    }

    @Test
    fun testPrepareAndAddUkrainianCategoriesFromHistoryAndMorphologicalPrefix() {
        runBlocking {
            setupUkrainianFixtures(db)

            val directPrep = appFunctions.prepareReceiptTransaction(
                merchantName = "Сільпо",
                receiptItems = "Закупівля продуктів додому",
                accountQuery = "Cash",
            )
            assertEquals("Їжа > Продукти", directPrep.suggestedCategory?.fullPath)
            assertEquals("HIGH", directPrep.categoryConfidence)

            val splitPrep = appFunctions.prepareReceiptTransaction(
                merchantName = "Сільпо",
                lineItems = listOf(
                    ReceiptLineItem(description = "Молоко та сметана", amount = 120.0),
                    ReceiptLineItem(description = "Святковий подарунок", amount = 380.0),
                ),
                accountQuery = "Card",
            )
            assertTrue(splitPrep.isSplitTransaction)
            assertEquals(2, splitPrep.suggestedSplits.size)
            assertEquals("Їжа > Продукти", splitPrep.suggestedSplits[0].category?.fullPath)
            assertEquals("HIGH", splitPrep.suggestedSplits[0].categoryConfidence)
            assertEquals("Подарунки", splitPrep.suggestedSplits[1].category?.fullPath)
            assertEquals("HIGH", splitPrep.suggestedSplits[1].categoryConfidence)

            val expectedSplits = listOf(
                TransactionSplit(categoryId = 101L, amount = -12000L, note = "Молоко та сметана"),
                TransactionSplit(categoryId = 102L, amount = -38000L, note = "Святковий подарунок"),
            )

            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
            val txResult = appFunctions.openDraft(
                amount = 500.0,
                accountName = "Card",
                payeeName = "Сільпо",
                note = "Чек Сільпо",
                splits = listOf(
                    TransactionSplitInput(amount = 120.0, categoryName = null, note = "Молоко та сметана"),
                    TransactionSplitInput(amount = 380.0, categoryName = null, note = "Святковий подарунок"),
                ),
            )
            val draft = captureDraft(db)
            assertSame(reviewIntent, txResult)
            assertEquals(2L, draft.fromAccountId)
            assertEquals(-1L, draft.categoryId)
            assertEquals(300L, draft.payeeId)
            assertEquals(-50000L, draft.amount)
            assertEquals("Чек Сільпо", draft.note)
            assertEquals(expectedSplits, draft.splits)
        }
    }

    @Test
    fun testMixedLanguageCustomCategoriesAndSemanticCategoryHint() {
        runBlocking {
            setupMixedLanguageFixtures(db)

            val fuelByHistoryAndPayee = appFunctions.prepareReceiptTransaction(
                merchantName = "OKKO",
                receiptItems = "Дизель 20л",
                accountQuery = "Card",
            )
            assertEquals("Auto > Benzin", fuelByHistoryAndPayee.suggestedCategory?.fullPath)
            assertEquals("HIGH", fuelByHistoryAndPayee.categoryConfidence)

            val schoolByMorphology = appFunctions.prepareReceiptTransaction(
                merchantName = "Книгарня",
                receiptItems = "Підручники для школи",
                accountQuery = "Cash",
            )
            assertEquals("Kids / Школа", schoolByMorphology.suggestedCategory?.fullPath)
            assertEquals("HIGH", schoolByMorphology.categoryConfidence)

            val splitMixed = appFunctions.prepareReceiptTransaction(
                merchantName = "Mall",
                lineItems = listOf(
                    ReceiptLineItem(description = "Зошити для навчання", amount = 15.0),
                    ReceiptLineItem(description = "Geschenk für Anna", amount = 35.0),
                    ReceiptLineItem(description = "Aral Tankstelle", amount = 50.0, categoryHint = "Benzin"),
                ),
                accountQuery = "Card",
            )
            assertTrue(splitMixed.isSplitTransaction)
            assertEquals(3, splitMixed.suggestedSplits.size)
            assertEquals("Kids / Школа", splitMixed.suggestedSplits[0].category?.fullPath)
            assertEquals("MEDIUM", splitMixed.suggestedSplits[0].categoryConfidence)
            assertEquals("Geschenke", splitMixed.suggestedSplits[1].category?.fullPath)
            assertEquals("HIGH", splitMixed.suggestedSplits[1].categoryConfidence)
            assertEquals("Auto > Benzin", splitMixed.suggestedSplits[2].category?.fullPath)
            assertEquals("HIGH", splitMixed.suggestedSplits[2].categoryConfidence)
        }
    }

    @Test
    fun testNullIsIncomeDefaultsToExpenseClassificationWhileTrueSelectsIncome() {
        runBlocking {
            val prepExpenseDefault = appFunctions.prepareReceiptTransaction(
                receiptItems = "Salary",
                accountQuery = "Cash",
                isIncome = null,
            )
            assertNull(prepExpenseDefault.suggestedCategory)
            assertEquals("NONE", prepExpenseDefault.categoryConfidence)
            assertTrue(prepExpenseDefault.candidateCategories.none { it.isIncome })

            val prepSplitExpenseDefault = appFunctions.prepareReceiptTransaction(
                lineItems = listOf(
                    ReceiptLineItem(description = "Milk", amount = 10.0),
                    ReceiptLineItem(description = "Salary", amount = 5.0),
                ),
                accountQuery = "Cash",
                isIncome = null,
            )
            assertTrue(prepSplitExpenseDefault.suggestedSplits.none { it.category?.isIncome == true })

            val prepIncome = appFunctions.prepareReceiptTransaction(
                receiptItems = "Salary",
                accountQuery = "Cash",
                isIncome = true,
            )
            assertEquals("Salary", prepIncome.suggestedCategory?.fullPath)
            assertEquals("HIGH", prepIncome.categoryConfidence)

            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
            val addExpenseDefault = appFunctions.openDraft(
                amount = 20.0,
                accountName = "Cash",
                note = "Salary",
                isIncome = null,
            )
            val draft = captureDraft(db)
            assertSame(reviewIntent, addExpenseDefault)
            assertEquals(1L, draft.fromAccountId)
            assertEquals(0L, draft.categoryId)
            assertEquals(-2000L, draft.amount)
            assertFalse(draft.isIncome)
            assertEquals("Salary", draft.note)
        }
    }
}

class CategoryClassifierAndDatabaseAdapterTest {

    @Test
    fun testAppFunctionDatabaseImplWithMockAdapterAndExifParser() {
        val mockAdapter = createMockDatabaseAdapter()
        val mockContext: Context = mock()
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mock())
        val impl = AppFunctionDatabaseImpl(mockAdapter, FinancistoDraftSessionStore(mockContext))

        val accounts = impl.getAccounts()
        assertEquals(1, accounts.size)
        assertEquals("Checking", accounts[0].title)
        assertEquals("USD", accounts[0].currencyCode)
        assertEquals(1L, accounts[0].currencyId)

        val categories = impl.getCategories()
        assertEquals(2, categories.size)
        assertEquals("Food", categories[0].fullPath)
        assertEquals("Food > Groceries", categories[1].fullPath)
        assertEquals(10L, categories[1].parentId)

        assertEquals(
            LocalDateTime.of(2026, 11, 5, 14, 30, 15),
            AppFunctionDatabaseImpl.parseExifDateTime("2026:11:05 14:30:15"),
        )
        assertNull(AppFunctionDatabaseImpl.parseExifDateTime("invalid"))
        assertTrue(impl.getCategoryHistory(0).isEmpty())
        val history = impl.getCategoryHistory(10)
        assertEquals(2, history.size)
        assertEquals(HistoricalCategorySignal(categoryId = 11L, payeeId = 300L, note = "Сир та сметана"), history[0])
        assertEquals(HistoricalCategorySignal(categoryId = 10L, payeeId = 0L, note = ""), history[1])
    }

    @Test
    fun testCategoryClassifierUnicodeMorphologicalAndPayeeHistoryFallback() {
        assertTrue(CategoryClassifier.tokensMatch("Продукти", "продуктів"))
        assertTrue(CategoryClassifier.tokensMatch("Geschenke", "Geschenk"))
        assertTrue(CategoryClassifier.tokensMatch("Groceries", "Grocery"))
        assertTrue(CategoryClassifier.tokensMatch("Подарунки", "подарунок"))
        assertTrue(CategoryClassifier.tokensMatch("Школа", "школи"))
        assertTrue(CategoryClassifier.tokensMatch("Cafés", "Cafe"))
        assertFalse(CategoryClassifier.tokensMatch("Food", "Foot"))
        assertFalse(CategoryClassifier.tokensMatch("Rent", "Restaurant"))

        val categories = listOf(
            CategoryInfo(id = 0L, title = "No Category", fullPath = "No Category", isIncome = false),
            CategoryInfo(id = 10L, title = "Їжа > Продукти", fullPath = "Їжа > Продукти", isIncome = false),
            CategoryInfo(id = 15L, title = "Подарунки", fullPath = "Подарунки", isIncome = false),
            CategoryInfo(id = 20L, title = "Зарплата", fullPath = "Зарплата", isIncome = true),
        )
        val payees = listOf(
            PayeeInfo(id = 50L, title = "АТБ", lastCategoryId = 0L),
            PayeeInfo(id = 60L, title = "Café Crème", lastCategoryId = 10L),
        )
        assertEquals(60L, CategoryClassifier.findBestMatch(payees, "Cafe Creme", exactOnly = true) { it.title }?.id)
        assertNull(CategoryClassifier.findCategoryMatch(categories, "No Category", isIncome = false))

        val history = listOf(
            HistoricalCategorySignal(categoryId = 10L, payeeId = 50L, note = "Хліб"),
            HistoricalCategorySignal(categoryId = 10L, payeeId = 50L, note = "Вода"),
            HistoricalCategorySignal(categoryId = 15L, payeeId = 0L, note = "Пакет біля АТБ"),
        )
        val payeeFreqResult = CategoryClassifier.classify(
            categories = categories,
            payees = payees,
            merchantName = "АТБ",
            receiptItems = "Серветки",
            isIncome = false,
            history = history,
        )
        assertEquals(10L, payeeFreqResult.category?.id)
        assertEquals(CategoryMatchConfidence.HIGH, payeeFreqResult.confidence)

        val unrestrictedResult = CategoryClassifier.classify(
            categories = categories,
            payees = payees,
            receiptItems = "Зарплата",
            isIncome = null,
            history = history,
        )
        assertEquals(20L, unrestrictedResult.category?.id)
    }

    @Test
    fun testAmbiguousTiedAccountMatchesRequireSelectionAndPreserveUniqueMatches() {
        val db: AppFunctionDatabase = mock()
        setupDefaultFixtures(db)
        val tiedAccounts = listOf(
            AccountInfo(id = 1L, title = "Cash", currencyCode = "USD", type = "CASH"),
            AccountInfo(id = 2L, title = "Card 1", currencyCode = "USD", type = "CARD"),
            AccountInfo(id = 3L, title = "Card 2", currencyCode = "USD", type = "CARD"),
        )
        whenever(db.getAccounts()).thenReturn(tiedAccounts)
        assertEquals(
            listOf(2L, 3L),
            CategoryClassifier.findBestMatches(tiedAccounts, "Card") { it.title }.map { it.id },
        )
        val normalizedAccounts = listOf(AccountInfo(4L, "My Card"), AccountInfo(5L, "My-Card"))
        assertEquals(
            listOf(4L, 5L),
            CategoryClassifier.findBestMatches(normalizedAccounts, "MyCard") { it.title }.map { it.id },
        )

        ensureBundleEmptyInitialized()
        stopKoin()
        startKoin { modules(module { single { db } }) }
        try {
            val service = TransactionAppFunctionService()
            runBlocking {
                val ambiguousPrep = service.prepareReceiptTransaction(accountQuery = "Card")
                assertNull(ambiguousPrep.matchedAccount)
                assertTrue(ambiguousPrep.requiresAccountSelection)

                val uniquePrep = service.prepareReceiptTransaction(accountQuery = "Csh")
                assertEquals("Cash", uniquePrep.matchedAccount?.name)
                assertFalse(uniquePrep.requiresAccountSelection)

                val srcEx = assertThrows(AppFunctionInvalidArgumentException::class.java) {
                    runBlocking { service.openDraft(amount = 10.0, accountName = "Card") }
                }
                assertTrue(srcEx.message.orEmpty().contains("Card 1"))
                assertTrue(srcEx.message.orEmpty().contains("Card 2"))

                val dstEx = assertThrows(AppFunctionInvalidArgumentException::class.java) {
                    runBlocking {
                        service.openDraft(amount = 10.0, accountName = "Cash", toAccountName = "Card")
                    }
                }
                assertTrue(dstEx.message.orEmpty().contains("Card 1"))

                val reviewIntent: PendingIntent = mock()
                whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
                val transfer = service.openDraft(amount = 15.0, accountName = "Csh", toAccountName = "Card 2")
                val draft = captureDraft(db)
                assertSame(reviewIntent, transfer)
                assertEquals(1L, draft.fromAccountId)
                assertEquals(3L, draft.toAccountId)
                assertEquals(-1500L, draft.amount)
                assertEquals(1500L, draft.toAmount)
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testStoragePrecisionValidationRejectsSubCentAmounts() {
        val db: AppFunctionDatabase = mock()
        setupDefaultFixtures(db)
        ensureBundleEmptyInitialized()
        stopKoin()
        startKoin { modules(module { single { db } }) }
        try {
            val service = TransactionAppFunctionService()
            assertThrows(AppFunctionInvalidArgumentException::class.java) {
                runBlocking { service.openDraft(amount = 10.125, accountName = "Cash") }
            }
            assertThrows(AppFunctionInvalidArgumentException::class.java) {
                runBlocking {
                    service.openDraft(
                        amount = 10.01,
                        accountName = "Cash",
                        splits = listOf(
                            TransactionSplitInput(amount = 10.004, categoryName = "Food"),
                            TransactionSplitInput(amount = 0.004, categoryName = "Gifts"),
                        ),
                    )
                }
            }
            assertThrows(AppFunctionInvalidArgumentException::class.java) {
                runBlocking {
                    service.prepareReceiptTransaction(
                        lineItems = listOf(ReceiptLineItem(description = "Weighted", amount = 2.345)),
                    )
                }
            }
            assertThrows(AppFunctionInvalidArgumentException::class.java) {
                runBlocking {
                    service.prepareReceiptTransaction(
                        lineItems = listOf(ReceiptLineItem(description = "Free", amount = 0.0)),
                    )
                }
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testSplitRemainderAdjustmentNeverCreatesZeroOrNegativeSplit() {
        val db: AppFunctionDatabase = mock()
        setupDefaultFixtures(db)
        ensureBundleEmptyInitialized()
        stopKoin()
        startKoin { modules(module { single { db } }) }
        try {
            val service = TransactionAppFunctionService()
            assertThrows(AppFunctionInvalidArgumentException::class.java) {
                runBlocking {
                    service.openDraft(
                        amount = 0.01,
                        accountName = "Cash",
                        splits = listOf(
                            TransactionSplitInput(amount = 0.01, categoryName = "Food"),
                            TransactionSplitInput(amount = 0.01, categoryName = "Gifts"),
                        ),
                    )
                }
            }
            val expectedSplits = listOf(
                TransactionSplit(categoryId = 10L, amount = -99L, note = "Lunch"),
                TransactionSplit(categoryId = 30L, amount = -1L, note = "Bag"),
            )
            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
            val adjustedResult = runBlocking {
                service.openDraft(
                    amount = 1.00,
                    accountName = "Cash",
                    splits = listOf(
                        TransactionSplitInput(amount = 1.00, categoryName = "Food", note = "Lunch"),
                        TransactionSplitInput(amount = 0.01, categoryName = "Gifts", note = "Bag"),
                    ),
                )
            }
            val draft = captureDraft(db)
            assertSame(reviewIntent, adjustedResult)
            assertEquals(-100L, draft.amount)
            assertEquals(expectedSplits, draft.splits)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun testOpenDraftAccountIdsResolveDuplicatesAndPreserveNameAmbiguity() {
        val db: AppFunctionDatabase = mock()
        setupDefaultFixtures(db)
        whenever(db.getAccounts()).thenReturn(
            listOf(
                AccountInfo(id = 10L, title = "Wallet", currencyCode = "USD", type = "CASH", currencyId = 1L),
                AccountInfo(id = 11L, title = "Wallet", currencyCode = "EUR", type = "CASH", currencyId = 2L),
            )
        )
        ensureBundleEmptyInitialized()
        stopKoin()
        startKoin { modules(module { single { db } }) }
        try {
            val service = TransactionAppFunctionService()
            val dupEx = assertThrows(AppFunctionInvalidArgumentException::class.java) {
                runBlocking { service.openDraft(amount = 10.0, accountName = "Wallet") }
            }
            assertTrue(dupEx.message.orEmpty().contains("id=10"))
            assertTrue(dupEx.message.orEmpty().contains("id=11"))
            assertThrows(AppFunctionElementNotFoundException::class.java) {
                runBlocking { service.openDraft(amount = 10.0, accountId = 999L) }
            }
            assertThrows(AppFunctionInvalidArgumentException::class.java) {
                runBlocking { service.openDraft(amount = 10.0) }
            }
            val reviewIntent: PendingIntent = mock()
            whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(reviewIntent)
            val byId = runBlocking { service.openDraft(amount = 10.0, categoryName = "Food", accountId = 11L) }
            assertSame(reviewIntent, byId)
            val transferByIds = runBlocking {
                service.openDraft(amount = 25.0, accountId = 10L, toAccountId = 11L)
            }
            assertSame(reviewIntent, transferByIds)
            val captor = argumentCaptor<AppFunctionTransactionDraft>()
            verify(db, times(2)).createTransactionReviewPendingIntent(captor.capture())
            assertEquals(11L, captor.firstValue.fromAccountId)
            assertEquals(10L, captor.firstValue.categoryId)
            assertEquals(-1000L, captor.firstValue.amount)
            assertEquals(10L, captor.secondValue.fromAccountId)
            assertEquals(11L, captor.secondValue.toAccountId)
            assertEquals(-2500L, captor.secondValue.amount)
            assertNull(captor.secondValue.toAmount)
        } finally {
            stopKoin()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testOpenDraftCrossCurrencyTransferLeavesToAmountNullAndUsesInjectedClock() {
        val db: AppFunctionDatabase = mock()
        setupDefaultFixtures(db)
        whenever(db.getAccounts()).thenReturn(
            listOf(
                AccountInfo(id = 1L, title = "USD Cash", currencyCode = "USD", type = "CASH", currencyId = 1L),
                AccountInfo(id = 2L, title = "USD Bank", currencyCode = "USD", type = "BANK", currencyId = 1L),
                AccountInfo(id = 3L, title = "EUR Bank", currencyCode = "EUR", type = "BANK", currencyId = 2L),
                AccountInfo(id = 4L, title = "Custom USD", currencyCode = "USD", type = "BANK", currencyId = 9L),
            )
        )
        val fixedInstant = Instant.parse("2026-11-22T10:15:30Z")
        val fixedClock = object : Clock {
            override fun now(): Instant = fixedInstant
        }
        ensureBundleEmptyInitialized()
        stopKoin()
        startKoin {
            modules(
                module {
                    single { db }
                    single<Clock> { fixedClock }
                }
            )
        }
        try {
            val service = TransactionAppFunctionService()
            runBlocking {
                service.openDraft(amount = 50.0, accountId = 1L, toAccountId = 3L, note = "FX transfer")
                service.openDraft(amount = 50.0, accountId = 1L, toAccountId = 2L, note = "Same currency")
                service.openDraft(amount = 50.0, accountId = 1L, toAccountId = 4L, note = "Different currencyId")
            }
            val captor = argumentCaptor<AppFunctionTransactionDraft>()
            verify(db, times(3)).createTransactionReviewPendingIntent(captor.capture())

            val fxDraft = captor.allValues[0]
            assertEquals(-5000L, fxDraft.amount)
            assertNull(fxDraft.toAmount)
            assertEquals(fixedInstant.toEpochMilliseconds(), fxDraft.dateTime)
            assertEquals(fxDraft.draftId, Uuid.parse(fxDraft.draftId).toString())

            val sameCurrencyDraft = captor.allValues[1]
            assertEquals(-5000L, sameCurrencyDraft.amount)
            assertEquals(5000L, sameCurrencyDraft.toAmount)

            val differentIdDraft = captor.allValues[2]
            assertNull(differentIdDraft.toAmount)
        } finally {
            stopKoin()
        }
    }
}

private fun setupDefaultFixtures(db: AppFunctionDatabase) {
    whenever(db.getAccounts()).thenReturn(
        listOf(
            AccountInfo(id = 1L, title = "Cash", currencyCode = "USD", type = "CASH", currencyId = 1L),
            AccountInfo(
                id = 2L,
                title = "Card",
                currencyCode = "USD",
                type = "CARD",
                lastCategoryId = 20L,
                currencyId = 1L,
            ),
        )
    )
    whenever(db.getCategories()).thenReturn(
        listOf(
            CategoryInfo(id = 10L, title = "Food", fullPath = "Food", isIncome = false),
            CategoryInfo(
                id = 11L,
                title = "Groceries",
                fullPath = "Food > Groceries",
                isIncome = false,
                parentId = 10L,
            ),
            CategoryInfo(id = 20L, title = "Rent", fullPath = "Rent", isIncome = false),
            CategoryInfo(id = 30L, title = "Gifts", fullPath = "Gifts", isIncome = false),
            CategoryInfo(id = 40L, title = "Salary", fullPath = "Salary", isIncome = true),
        )
    )
    whenever(db.getPayees()).thenReturn(listOf(PayeeInfo(id = 100L, title = "Starbucks", lastCategoryId = 10L)))
    whenever(db.getCategoryHistory()).thenReturn(
        listOf(
            HistoricalCategorySignal(categoryId = 11L, note = "Milk"),
            HistoricalCategorySignal(categoryId = 11L, note = "Vegetables"),
        )
    )
    whenever(db.createTransactionReviewPendingIntent(any())).thenReturn(mock())
}

private fun setupUkrainianFixtures(db: AppFunctionDatabase) {
    whenever(db.getCategories()).thenReturn(
        listOf(
            CategoryInfo(id = 100L, title = "Їжа", fullPath = "Їжа", isIncome = false),
            CategoryInfo(
                id = 101L,
                title = "Продукти",
                fullPath = "Їжа > Продукти",
                isIncome = false,
                parentId = 100L,
            ),
            CategoryInfo(id = 102L, title = "Подарунки", fullPath = "Подарунки", isIncome = false),
        )
    )
    whenever(db.getPayees()).thenReturn(listOf(PayeeInfo(id = 300L, title = "Сільпо", lastCategoryId = 0L)))
    whenever(db.getCategoryHistory()).thenReturn(
        listOf(HistoricalCategorySignal(categoryId = 101L, payeeId = 300L, note = "Молоко, сметана, сир"))
    )
}

private fun setupMixedLanguageFixtures(db: AppFunctionDatabase) {
    whenever(db.getCategories()).thenReturn(
        listOf(
            CategoryInfo(id = 200L, title = "Auto", fullPath = "Auto", isIncome = false),
            CategoryInfo(
                id = 201L,
                title = "Benzin",
                fullPath = "Auto > Benzin",
                isIncome = false,
                parentId = 200L,
            ),
            CategoryInfo(id = 202L, title = "Kids / Школа", fullPath = "Kids / Школа", isIncome = false),
            CategoryInfo(id = 203L, title = "Geschenke", fullPath = "Geschenke", isIncome = false),
        )
    )
    whenever(db.getPayees()).thenReturn(listOf(PayeeInfo(id = 400L, title = "OKKO", lastCategoryId = 0L)))
    whenever(db.getCategoryHistory()).thenReturn(
        listOf(
            HistoricalCategorySignal(categoryId = 201L, payeeId = 400L, note = "Дизель Pulls 40л"),
            HistoricalCategorySignal(categoryId = 201L, payeeId = 400L, note = "А-95 паливо"),
            HistoricalCategorySignal(categoryId = 202L, payeeId = 0L, note = "Зошити та ручки"),
        )
    )
}

private fun captureDraft(db: AppFunctionDatabase): AppFunctionTransactionDraft {
    val captor = argumentCaptor<AppFunctionTransactionDraft>()
    verify(db).createTransactionReviewPendingIntent(captor.capture())
    return captor.firstValue
}

private fun createMockDatabaseAdapter(): DatabaseAdapter {
    val mockAdapter: DatabaseAdapter = mock()
    val usd = Currency().apply {
        id = 1L
        name = "USD"
    }
    val activeAcc = Account().apply {
        id = 1L
        title = "Checking"
        currency = usd
        type = "BANK"
        isActive = true
    }
    val inactiveAcc = Account().apply {
        id = 2L
        title = "Closed"
        isActive = false
    }
    whenever(mockAdapter.allAccountsList).thenReturn(listOf(activeAcc, inactiveAcc))

    val groceries = Category().apply {
        id = 11L
        title = "Groceries"
        level = 2
        type = CategoryEntity.TYPE_EXPENSE
    }
    val food = Category().apply {
        id = 10L
        title = "Food"
        level = 1
        type = CategoryEntity.TYPE_EXPENSE
        addChild(groceries)
    }
    val tree = CategoryTree<Category>().apply {
        add(Category().apply { id = Category.NO_CATEGORY_ID })
        add(Category().apply { id = Category.SPLIT_CATEGORY_ID })
        add(food)
    }
    whenever(mockAdapter.getCategoriesTree(false)).thenReturn(tree)
    val cursor: Cursor = mock()
    whenever(cursor.moveToNext()).thenReturn(true, true, false)
    whenever(cursor.getLong(0)).thenReturn(11L, 10L)
    whenever(cursor.getLong(1)).thenReturn(300L, -1L)
    whenever(cursor.getString(2)).thenReturn("Сир та сметана", null)
    val sqlDb: SQLiteDatabase = mock()
    whenever(sqlDb.rawQuery(any(), eq(arrayOf("10")))).thenReturn(cursor)
    whenever(mockAdapter.db()).thenReturn(sqlDb)
    return mockAdapter
}

private fun ensureBundleEmptyInitialized() {
    if (Bundle.EMPTY != null) return
    runCatching {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val emptyField = Bundle::class.java.getDeclaredField("EMPTY")
        val base = unsafeClass.getMethod("staticFieldBase", Field::class.java).invoke(theUnsafe, emptyField)
        val offset = unsafeClass.getMethod("staticFieldOffset", Field::class.java)
            .invoke(theUnsafe, emptyField) as Long
        unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
            .invoke(theUnsafe, base, offset, Bundle())
    }
}
