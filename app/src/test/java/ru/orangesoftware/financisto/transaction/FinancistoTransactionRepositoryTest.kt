// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.robolectric.Shadows
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.TransactionActivity
import ru.orangesoftware.financisto.activity.TransferActivity
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.AttributeBuilder
import ru.orangesoftware.financisto.test.CategoryBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.ProjectBuilder
import ru.orangesoftware.financisto.test.TransactionBuilder
import ru.orangesoftware.financisto.transaction.domain.TransactionValidationResult
import ru.orangesoftware.financisto.transaction.domain.TransactionValidator
import ru.orangesoftware.financisto.transaction.model.DraftSplit
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.SaveSplitCommand
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState

class FinancistoTransactionRepositoryTest : AbstractDbTest() {

    private lateinit var repository: FinancistoTransactionRepository
    private lateinit var cash: Account
    private lateinit var card: Account

    override fun setUp() {
        super.setUp()
        repository = FinancistoTransactionRepository(context, db)
        val usd = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").create()
        cash = AccountBuilder.withDb(db).title("Cash").currency(usd).create()
        card = AccountBuilder.withDb(db).title("Card").currency(usd).create()
    }

    @Test
    fun loadCatalogIncludesActiveAndAssignedInactiveEntitiesAndRecentNotes() = runTest {
        val inactiveAcc = AccountBuilder.withDb(db)
            .title("Closed Card")
            .currency(cash.currency)
            .inactive()
            .create()
        val activePayee = Payee().apply {
            title = "Active Store"
            isActive = true
        }.also { db.saveOrUpdate(it) }
        val inactivePayee = Payee().apply {
            title = "Old Store"
            isActive = false
        }.also { db.saveOrUpdate(it) }
        val activeProj = ProjectBuilder.withDb(db).title("Active Proj").setActive().create()
        val inactiveProj = ProjectBuilder.withDb(db).title("Old Proj").create()

        val tx = Transaction().apply {
            fromAccountId = inactiveAcc.id
            payeeId = inactivePayee.id
            projectId = inactiveProj.id
            fromAmount = -1200L
            note = "Archived purchase"
            dateTime = 1_700_000_000_000L
        }
        val txId = db.insertOrUpdate(tx, emptyList())

        val blankCatalog = repository.loadCatalog(editingTransactionId = null)
        assertFalse(blankCatalog.accounts.any { it.id == inactiveAcc.id })
        assertTrue(blankCatalog.payees.any { it.id == activePayee.id })
        assertFalse(blankCatalog.payees.any { it.id == inactivePayee.id })
        assertTrue(blankCatalog.projects.any { it.id == activeProj.id })
        assertFalse(blankCatalog.projects.any { it.id == inactiveProj.id })
        assertTrue(blankCatalog.recentNotes.contains("Archived purchase"))

        val editCatalog = repository.loadCatalog(editingTransactionId = txId)
        assertTrue(editCatalog.accounts.any { it.id == inactiveAcc.id && !it.isActive })
        assertTrue(editCatalog.payees.any { it.id == inactivePayee.id && !it.isActive })
        assertTrue(editCatalog.projects.any { it.id == inactiveProj.id && !it.isActive })
    }

    @Test
    fun loadForEditHandlesSplitNormalizationLegacyFallbacksAndMissingIds() = runTest {
        val categories = CategoryBuilder.createDefaultHierarchy(db)
        val normalCategory = categories.getValue("A1")
        val attributedCategory = categories.getValue("AA1")

        assertEquals(TransactionEditLoadResult.NotFound(999999L), repository.loadForEdit(999999L))

        val splitParent = TransactionBuilder.withDb(db)
            .account(cash)
            .amount(-5000L)
            .note("Parent split")
            .withSplit(normalCategory, -2000L, "Part 1")
            .withSplit(categories.getValue("A2"), -3000L, "Part 2")
            .create()
        val splitChildId = db.getSplitsForTransaction(splitParent.id).first().id
        val normalizedResult = repository.loadForEdit(splitChildId)
        assertTrue(normalizedResult is TransactionEditLoadResult.Loaded)
        val loadedSplit = (normalizedResult as TransactionEditLoadResult.Loaded).record
        assertEquals(splitParent.id, loadedSplit.id)
        assertEquals(2, loadedSplit.splits.size)

        val templateTx = TransactionBuilder.withDb(db).account(cash).amount(-1000L).makeTemplate().create()
        assertTrue(repository.loadForEdit(templateTx.id) is TransactionEditLoadResult.RequiresLegacyFallback)

        val splitTransferParent = TransactionBuilder.withDb(db)
            .account(cash)
            .amount(-3000L)
            .withTransferSplit(card, -1000L, 1000L, "Move")
            .withSplit(normalCategory, -2000L, "Item")
            .create()
        assertTrue(repository.loadForEdit(splitTransferParent.id) is TransactionEditLoadResult.RequiresLegacyFallback)

        val attrCategoryTx = TransactionBuilder.withDb(db)
            .account(cash)
            .category(attributedCategory)
            .amount(-1500L)
            .create()
        assertTrue(repository.loadForEdit(attrCategoryTx.id) is TransactionEditLoadResult.RequiresLegacyFallback)

        val customAttr = AttributeBuilder.withDb(db).createTextAttribute("Custom")
        val directAttrTx = Transaction().apply {
            fromAccountId = cash.id
            categoryId = normalCategory.id
            fromAmount = -900L
        }
        val directAttrTxId = db.insertOrUpdate(
            directAttrTx,
            listOf(AttributeBuilder.attributeValue(customAttr, "abc")),
        )
        assertTrue(repository.loadForEdit(directAttrTxId) is TransactionEditLoadResult.RequiresLegacyFallback)
    }

    @Test
    fun saveTransactionCreatesPendingEntitiesPreservesMetadataAndSwitchesMode() = runTest {
        val cat = CategoryBuilder.createDefaultHierarchy(db).getValue("A1")
        val createdId = repository.saveTransaction(
            baseSaveCommand(
                categoryId = cat.id,
                pendingPayeeName = "Fresh Market",
                pendingProjectName = "Household",
                fromAmountMinor = -2500L,
                note = "Veggies",
                status = TransactionStatusOption.CLEARED,
            )
        )
        val created = db.getTransaction(createdId)
        assertTrue(created.payeeId > 0L)
        assertTrue(created.projectId > 0L)
        assertEquals(-2500L, requireNotNull(db.getAccount(cash.id)).totalAmount)
        assertEquals(TransactionStatus.CLEARED, created.status)

        created.blobKey = "blob-1"
        created.latitude = 50.4501
        created.longitude = 30.5234
        db.insertOrUpdate(created, emptyList())
        db.db().execSQL("UPDATE transactions SET remote_key = ? WHERE _id = ?", arrayOf<Any>("sync-key-1", createdId))

        repository.saveTransaction(
            baseSaveCommand(
                existingTransactionId = createdId,
                mode = TransactionMode.TRANSFER,
                toAccountId = card.id,
                payeeId = created.payeeId,
                projectId = created.projectId,
                fromAmountMinor = -4000L,
                toAmountMinor = 4000L,
                note = "Converted to transfer",
                dateTimeMillis = 1_700_000_100_000L,
                status = TransactionStatusOption.PN,
            )
        )

        val updated = db.getTransaction(createdId)
        assertEquals("sync-key-1", updated.remoteKey)
        assertEquals("blob-1", updated.blobKey)
        assertEquals(50.4501, updated.latitude, 1e-4)
        assertEquals(30.5234, updated.longitude, 1e-4)
        assertEquals(card.id, updated.toAccountId)
        assertEquals(-4000L, requireNotNull(db.getAccount(cash.id)).totalAmount)
        assertEquals(4000L, requireNotNull(db.getAccount(card.id)).totalAmount)
    }

    @Test
    fun saveTransactionRejectsDeletedExistingTransactionWithoutCreatingEntitiesOrInserting() = runTest {
        val cat = CategoryBuilder.createDefaultHierarchy(db).getValue("A1")
        val existingId = repository.saveTransaction(
            baseSaveCommand(categoryId = cat.id, fromAmountMinor = -1500L, note = "To be deleted")
        )
        db.deleteTransaction(existingId)

        val payeesBefore = db.allPayeeList.map { it.title }.toSet()
        val projectsBefore = db.getAllProjectsList(false).map { it.title }.toSet()
        val txCountBefore = db.db().rawQuery("SELECT COUNT(*) FROM transactions", null).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }

        try {
            repository.saveTransaction(
                baseSaveCommand(
                    existingTransactionId = existingId,
                    categoryId = cat.id,
                    pendingPayeeName = "Ghost Payee",
                    pendingProjectName = "Ghost Project",
                    fromAmountMinor = -2200L,
                    note = "Edited after delete",
                )
            )
            fail("Expected IllegalStateException when saving edit for deleted transaction")
        } catch (expected: IllegalStateException) {
            assertEquals("Transaction $existingId no longer exists", expected.message)
        }

        assertEquals(payeesBefore, db.allPayeeList.map { it.title }.toSet())
        assertEquals(projectsBefore, db.getAllProjectsList(false).map { it.title }.toSet())
        val txCountAfter = db.db().rawQuery("SELECT COUNT(*) FROM transactions", null).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }
        assertEquals(txCountBefore, txCountAfter)
        assertEquals(0L, requireNotNull(db.getAccount(cash.id)).totalAmount)
    }

    @Test
    fun saveAndReloadForeignCurrencySplitsPreservesOriginalAndExactAccountAmounts() = runTest {
        val eur = CurrencyBuilder.withDb(db).name("EUR").title("Euro").symbol("€").create()
        val categories = CategoryBuilder.createDefaultHierarchy(db)
        val catA1 = categories.getValue("A1")
        val catA2 = categories.getValue("A2")

        val savedId = repository.saveTransaction(
            baseSaveCommand(
                categoryId = TransactionIds.SPLIT_CATEGORY_ID,
                fromAmountMinor = -11000L,
                originalCurrencyId = eur.id,
                originalFromAmountMinor = -10000L,
                note = "Euro trip split",
                splits = listOf(
                    SaveSplitCommand(catA1.id, -3666L, "A", 0L, originalFromAmountMinor = -3333L),
                    SaveSplitCommand(catA2.id, -3666L, "B", 0L, originalFromAmountMinor = -3333L),
                    SaveSplitCommand(catA1.id, -3668L, "C", 0L, originalFromAmountMinor = -3334L),
                ),
            )
        )

        val persistedSplits = db.getSplitsForTransaction(savedId)
        assertEquals(3, persistedSplits.size)
        assertTrue(persistedSplits.all { it.originalCurrencyId == eur.id })
        assertEquals(listOf(-3333L, -3333L, -3334L), persistedSplits.map { it.originalFromAmount })
        assertEquals(listOf(-3666L, -3666L, -3668L), persistedSplits.map { it.fromAmount })
        assertEquals(-11000L, persistedSplits.sumOf { it.fromAmount })
        assertEquals(-11000L, requireNotNull(db.getAccount(cash.id)).totalAmount)

        val reloaded = repository.loadForEdit(savedId)
        assertTrue(reloaded is TransactionEditLoadResult.Loaded)
        val record = (reloaded as TransactionEditLoadResult.Loaded).record
        assertEquals(eur.id, record.originalCurrencyId)
        assertEquals(-10000L, record.originalFromAmountMinor)
        assertEquals(-11000L, record.fromAmountMinor)
        assertEquals(listOf(3333L, 3333L, 3334L), record.splits.map { it.amountMinor })
        assertTrue(record.splits.all { !it.isIncome })
    }

    @Test
    fun saveForeignCurrencyEqualSplitsAbsorbsRoundingRemainderEndToEnd() = runTest {
        val eur = CurrencyBuilder.withDb(db).name("EUR").title("Euro").symbol("€").create()
        val categories = CategoryBuilder.createDefaultHierarchy(db)
        val catA1 = categories.getValue("A1")
        val catA2 = categories.getValue("A2")
        val catalog = repository.loadCatalog(editingTransactionId = null)

        // 3 equal €10.00 splits (€30.00 total) converting to $31.00:
        // naive per-split rounding yields -$10.33 * 3 = -$30.99 (1-cent drift).
        // Largest-remainder allocation must persist -$10.34, -$10.33, -$10.33 summing to exact -$31.00.
        val form = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = cash.id,
            originalCurrencyId = eur.id,
            primaryAmountInput = "30.00",
            primaryAmountMinor = 3000L,
            secondaryAmountInput = "31.00",
            secondaryAmountMinor = 3100L,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            isSplitMode = true,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = catA1.id, amountMinor = 1000L, note = "S1"),
                EditableSplit(localId = -2L, categoryId = catA2.id, amountMinor = 1000L, note = "S2"),
                EditableSplit(localId = -3L, categoryId = catA1.id, amountMinor = 1000L, note = "S3"),
            ),
            dateTimeMillis = 1_700_000_000_000L,
        )
        val validation = TransactionValidator.validateAndBuildCommand(form, catalog)
        assertTrue(validation is TransactionValidationResult.Valid)
        val command = (validation as TransactionValidationResult.Valid).command

        val savedId = repository.saveTransaction(command)
        val persistedParent = db.getTransaction(savedId)
        val persistedSplits = db.getSplitsForTransaction(savedId)

        assertEquals(-3100L, persistedParent.fromAmount)
        assertEquals(-3000L, persistedParent.originalFromAmount)
        assertEquals(listOf(-1000L, -1000L, -1000L), persistedSplits.map { it.originalFromAmount })
        assertEquals(listOf(-1034L, -1033L, -1033L), persistedSplits.map { it.fromAmount })
        assertEquals(persistedParent.fromAmount, persistedSplits.sumOf { it.fromAmount })
    }

    @Test
    fun draftSessionStoreSerializesRoundTripAndEnforcesBoundedHistory() = runTest {
        val store = FinancistoDraftSessionStore(context)
        val richDraft = TransactionDraft(
            draftId = "draft-full-roundtrip",
            mode = TransactionMode.TRANSFER,
            amountMinor = 4250L,
            fromAccountId = cash.id,
            toAccountId = card.id,
            originalCurrencyId = 2L,
            originalAmountMinor = 3900L,
            toAmountMinor = 4250L,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            payeeId = 7L,
            pendingPayeeName = "Klarna Store",
            projectId = 3L,
            locationId = 4L,
            dateTimeMillis = 1_700_000_123_456L,
            status = TransactionStatusOption.CLEARED,
            note = "Test receipt",
            isCCardPayment = true,
            stagedReceiptFileName = "staged_receipt.jpg",
            splits = listOf(
                DraftSplit(categoryId = 10L, categoryName = "Food", amountMinor = -2000L, note = "P1", projectId = 1L),
                DraftSplit(categoryId = 20L, categoryName = "Gifts", amountMinor = -2250L, note = "P2"),
            ),
        )
        store.savePendingDraft(richDraft)
        assertEquals(richDraft, store.peekPendingAutoOpenDraft())
        assertEquals(richDraft, store.consumePendingAutoOpenDraft())
        assertNull(store.consumePendingAutoOpenDraft())

        val rawPrefs = context.getSharedPreferences(FinancistoDraftSessionStore.PREFS_NAME, Context.MODE_PRIVATE)
        rawPrefs.edit(commit = true) { putString(FinancistoDraftSessionStore.KEY_PENDING_DRAFT, "{corrupt-json") }
        assertNull(store.consumePendingAutoOpenDraft())
        assertFalse(rawPrefs.contains(FinancistoDraftSessionStore.KEY_PENDING_DRAFT))

        val totalDrafts = FinancistoDraftSessionStore.MAX_SAVED_DRAFTS + 5
        for (index in 1..totalDrafts) {
            store.markDraftSaved("draft-$index", index.toLong() * 10L)
        }
        for (evicted in 1..5) {
            assertNull(store.getSavedTransactionId("draft-$evicted"))
        }
        for (retained in 6..totalDrafts) {
            assertEquals(retained.toLong() * 10L, store.getSavedTransactionId("draft-$retained"))
        }
        assertEquals(setOf(FinancistoDraftSessionStore.KEY_SAVED_DRAFTS), rawPrefs.all.keys)
    }

    @Test
    fun entryRouterDetectsLegacyFallbackIntents() {
        assertFalse(TransactionEntryRouter.shouldUseLegacyImmediately(Intent(), db))
        assertTrue(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(TransactionEntryRouter.EXTRA_FORCE_LEGACY, true),
                db,
            )
        )
        assertTrue(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, 100L),
                db,
            )
        )
        assertTrue(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(TransactionActivity.AMOUNT_EXTRA, 5000L),
                db,
            )
        )
        assertTrue(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(TransactionActivity.AMOUNT_EXTRA, -5000L),
                db,
            )
        )
        assertTrue(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(TransactionActivity.AMOUNT_EXTRA, 0L),
                db,
            )
        )
        assertTrue(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, true),
                db,
            )
        )
        val templateTx = TransactionBuilder.withDb(db).account(cash).amount(-500L).makeTemplate().create()
        assertTrue(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, templateTx.id),
                db,
            )
        )
        val regularTx = TransactionBuilder.withDb(db).account(cash).amount(-500L).create()
        assertFalse(
            TransactionEntryRouter.shouldUseLegacyImmediately(
                Intent().putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, regularTx.id),
                db,
            )
        )
        assertEquals(Category.NO_CATEGORY_ID, regularTx.categoryId)
    }

    @Test
    fun entryRouterBlocksLegacyEditorForAiDraftsAndPreservesBlankAndEdit() {
        val pendingDraft = TransactionDraft(
            draftId = "draft-legacy-guard",
            mode = TransactionMode.EXPENSE,
            amountMinor = 4250L,
            fromAccountId = cash.id,
            dateTimeMillis = 1_700_000_123_456L,
        )
        assertNull(
            TransactionEntryRouter.createLegacyEditorIntent(context, TransactionLaunchArgs.AiDraft(pendingDraft))
        )
        assertNull(
            TransactionEntryRouter.createLegacyEditorIntent(
                context,
                TransactionLaunchArgs.AiDraft(pendingDraft.copy(mode = TransactionMode.TRANSFER)),
                fallbackIsTransfer = true,
            )
        )

        val blankLegacy = TransactionEntryRouter.createLegacyEditorIntent(
            context,
            TransactionLaunchArgs.BlankNew(accountId = cash.id, initialMode = TransactionMode.TRANSFER),
        )
        assertNotNull(blankLegacy)
        assertEquals(TransferActivity::class.java.name, blankLegacy?.component?.className)
        assertTrue(blankLegacy?.getBooleanExtra(TransactionEntryRouter.EXTRA_FORCE_LEGACY, false) == true)
        assertEquals(cash.id, blankLegacy?.getLongExtra(AbstractTransactionActivity.ACCOUNT_ID_EXTRA, -1L))

        val editLegacy = TransactionEntryRouter.createLegacyEditorIntent(
            context,
            TransactionLaunchArgs.Edit(77L),
            fallbackIsTransfer = false,
        )
        assertNotNull(editLegacy)
        assertEquals(TransactionActivity::class.java.name, editLegacy?.component?.className)
        assertEquals(77L, editLegacy?.getLongExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, -1L))
    }

    @Test
    fun entryRouterHandlesPendingDraftConsumptionAndFlags() = runTest {
        val store = FinancistoDraftSessionStore(context)
        val pendingDraft = TransactionDraft(
            draftId = "draft-router-test",
            mode = TransactionMode.EXPENSE,
            amountMinor = 4250L,
            fromAccountId = cash.id,
            dateTimeMillis = 1_700_000_123_456L,
        )
        val aiIntent = TransactionEntryRouter.createAiDraftIntent(context, pendingDraft)
        assertNotNull(aiIntent.component)
        assertTrue(aiIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(aiIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(aiIntent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertEquals(0, aiIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)

        store.savePendingDraft(pendingDraft)
        store.clearPendingAutoOpenDraft("different-draft-id")
        assertEquals(pendingDraft, store.peekPendingAutoOpenDraft())
        store.clearPendingAutoOpenDraft(pendingDraft.draftId)
        assertNull(store.peekPendingAutoOpenDraft())

        store.savePendingDraft(pendingDraft)
        store.acknowledgeDraftOpened(pendingDraft.draftId)
        assertNull(store.consumePendingAutoOpenDraft())

        store.savePendingDraft(pendingDraft)
        store.clearPendingDraft(pendingDraft.draftId)
        assertNull(store.consumePendingAutoOpenDraft())

        val shadowApp = Shadows.shadowOf(context as Application)
        while (shadowApp.nextStartedActivity != null) {
            // Drain any prior started activities from setup.
        }

        store.savePendingDraft(pendingDraft)
        assertTrue(TransactionEntryRouter.launchPendingAiDraftIfPresent(context, draftSessionStore = store))
        val firstLaunched = shadowApp.nextStartedActivity
        assertNotNull(firstLaunched)
        assertEquals(
            TransactionLaunchArgs.AiDraft(pendingDraft),
            TransactionEntryRouter.resolveLaunchArgs(firstLaunched),
        )
        assertFalse(TransactionEntryRouter.launchPendingAiDraftIfPresent(context, draftSessionStore = store))
        assertNull(shadowApp.nextStartedActivity)

        store.savePendingDraft(pendingDraft)
        assertFalse(
            TransactionEntryRouter.launchPendingAiDraftIfPresent(context, pendingDraft.draftId, store)
        )
        assertNull(shadowApp.nextStartedActivity)
        val newerDraft = pendingDraft.copy(draftId = "draft-router-newer", amountMinor = 1500L)
        store.savePendingDraft(newerDraft)
        assertTrue(
            TransactionEntryRouter.launchPendingAiDraftIfPresent(context, pendingDraft.draftId, store)
        )
        val launchedNewer = shadowApp.nextStartedActivity
        assertNotNull(launchedNewer)
        assertEquals(
            TransactionLaunchArgs.AiDraft(newerDraft),
            TransactionEntryRouter.resolveLaunchArgs(launchedNewer),
        )
    }

    private fun baseSaveCommand(
        existingTransactionId: Long = TransactionIds.UNSAVED_TRANSACTION_ID,
        mode: TransactionMode = TransactionMode.EXPENSE,
        toAccountId: Long = 0L,
        categoryId: Long = 0L,
        payeeId: Long = 0L,
        pendingPayeeName: String? = null,
        projectId: Long = 0L,
        pendingProjectName: String? = null,
        fromAmountMinor: Long = -1000L,
        toAmountMinor: Long = 0L,
        originalCurrencyId: Long = 0L,
        originalFromAmountMinor: Long = 0L,
        note: String = "",
        dateTimeMillis: Long = 1_700_000_000_000L,
        status: TransactionStatusOption = TransactionStatusOption.UR,
        splits: List<SaveSplitCommand> = emptyList(),
    ): SaveTransactionCommand = SaveTransactionCommand(
        existingTransactionId = existingTransactionId,
        mode = mode,
        fromAccountId = cash.id,
        toAccountId = toAccountId,
        categoryId = categoryId,
        payeeId = payeeId,
        pendingPayeeName = pendingPayeeName,
        projectId = projectId,
        pendingProjectName = pendingProjectName,
        locationId = 0L,
        fromAmountMinor = fromAmountMinor,
        toAmountMinor = toAmountMinor,
        originalCurrencyId = originalCurrencyId,
        originalFromAmountMinor = originalFromAmountMinor,
        note = note,
        dateTimeMillis = dateTimeMillis,
        status = status,
        attachedPicture = null,
        isCCardPayment = false,
        splits = splits,
    )
}
