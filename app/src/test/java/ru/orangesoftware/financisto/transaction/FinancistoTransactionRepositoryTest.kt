// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.TransactionActivity
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
import ru.orangesoftware.financisto.transaction.model.DraftSplit
import ru.orangesoftware.financisto.transaction.model.SaveSplitCommand
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption

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
    fun loadCatalogIncludesActiveAndAssignedInactiveEntitiesAndRecentNotes() {
        runBlocking {
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
    }

    @Test
    fun loadForEditHandlesSplitNormalizationLegacyFallbacksAndMissingIds() {
        runBlocking {
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

            val templateTx = TransactionBuilder.withDb(db)
                .account(cash)
                .amount(-1000L)
                .makeTemplate()
                .create()
            assertTrue(repository.loadForEdit(templateTx.id) is TransactionEditLoadResult.RequiresLegacyFallback)

            val splitTransferParent = TransactionBuilder.withDb(db)
                .account(cash)
                .amount(-3000L)
                .withTransferSplit(card, -1000L, 1000L, "Move")
                .withSplit(normalCategory, -2000L, "Item")
                .create()
            assertTrue(
                repository.loadForEdit(splitTransferParent.id) is TransactionEditLoadResult.RequiresLegacyFallback
            )

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
    }

    @Test
    fun saveTransactionCreatesPendingEntitiesUpdatesBalancesPreservesMetadataAndSwitchesMode() {
        runBlocking {
            val categories = CategoryBuilder.createDefaultHierarchy(db)
            val cat = categories.getValue("A1")

            val createdId = repository.saveTransaction(
                SaveTransactionCommand(
                    existingTransactionId = TransactionIds.UNSAVED_TRANSACTION_ID,
                    mode = TransactionMode.EXPENSE,
                    fromAccountId = cash.id,
                    toAccountId = 0L,
                    categoryId = cat.id,
                    payeeId = 0L,
                    pendingPayeeName = "Fresh Market",
                    projectId = 0L,
                    pendingProjectName = "Household",
                    locationId = 0L,
                    fromAmountMinor = -2500L,
                    toAmountMinor = 0L,
                    originalCurrencyId = 0L,
                    originalFromAmountMinor = 0L,
                    note = "Veggies",
                    dateTimeMillis = 1_700_000_000_000L,
                    status = TransactionStatusOption.CLEARED,
                    attachedPicture = null,
                    isCCardPayment = false,
                    splits = emptyList(),
                )
            )
            val created = db.getTransaction(createdId)
            assertTrue(created.payeeId > 0L)
            assertTrue(created.projectId > 0L)
            assertEquals(-2500L, db.getAccount(cash.id)!!.totalAmount)
            assertEquals(TransactionStatus.CLEARED, created.status)

            created.blobKey = "blob-1"
            created.latitude = 50.4501
            created.longitude = 30.5234
            db.insertOrUpdate(created, emptyList())
            db.db().execSQL(
                "UPDATE transactions SET remote_key = ? WHERE _id = ?",
                arrayOf<Any>("sync-key-1", createdId),
            )

            repository.saveTransaction(
                SaveTransactionCommand(
                    existingTransactionId = createdId,
                    mode = TransactionMode.TRANSFER,
                    fromAccountId = cash.id,
                    toAccountId = card.id,
                    categoryId = 0L,
                    payeeId = created.payeeId,
                    pendingPayeeName = null,
                    projectId = created.projectId,
                    pendingProjectName = null,
                    locationId = 0L,
                    fromAmountMinor = -4000L,
                    toAmountMinor = 4000L,
                    originalCurrencyId = 0L,
                    originalFromAmountMinor = 0L,
                    note = "Converted to transfer",
                    dateTimeMillis = 1_700_000_100_000L,
                    status = TransactionStatusOption.PN,
                    attachedPicture = null,
                    isCCardPayment = false,
                    splits = listOf(SaveSplitCommand(cat.id, -4000L, "", 0L)).take(0),
                )
            )

            val updated = db.getTransaction(createdId)
            assertEquals("sync-key-1", updated.remoteKey)
            assertEquals("blob-1", updated.blobKey)
            assertEquals(50.4501, updated.latitude, 1e-4)
            assertEquals(30.5234, updated.longitude, 1e-4)
            assertEquals(card.id, updated.toAccountId)
            assertEquals(-4000L, db.getAccount(cash.id)!!.totalAmount)
            assertEquals(4000L, db.getAccount(card.id)!!.totalAmount)
        }
    }

    @Test
    fun draftSessionStoreAndEntryRouterHandleConsumedDraftsAndLegacyRoutes() {
        runBlocking {
            val store = FinancistoDraftSessionStore(context)
            assertNull(store.getSavedTransactionId("draft-abc"))
            store.markDraftSaved("draft-abc", 321L)
            assertEquals(321L, store.getSavedTransactionId("draft-abc"))

            val normalIntent = Intent()
            assertFalse(TransactionEntryRouter.shouldUseLegacyImmediately(normalIntent, db))

            val forceIntent = Intent().putExtra(TransactionEntryRouter.EXTRA_FORCE_LEGACY, true)
            assertTrue(TransactionEntryRouter.shouldUseLegacyImmediately(forceIntent, db))

            val balanceIntent = Intent().putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, 100L)
            assertTrue(TransactionEntryRouter.shouldUseLegacyImmediately(balanceIntent, db))

            val duplicateIntent = Intent().putExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, true)
            assertTrue(TransactionEntryRouter.shouldUseLegacyImmediately(duplicateIntent, db))

            val templateTx = TransactionBuilder.withDb(db).account(cash).amount(-500L).makeTemplate().create()
            val templateEditIntent = Intent().putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, templateTx.id)
            assertTrue(TransactionEntryRouter.shouldUseLegacyImmediately(templateEditIntent, db))

            val regularTx = TransactionBuilder.withDb(db).account(cash).amount(-500L).create()
            val regularEditIntent = Intent().putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, regularTx.id)
            assertFalse(TransactionEntryRouter.shouldUseLegacyImmediately(regularEditIntent, db))
            assertEquals(Category.NO_CATEGORY_ID, regularTx.categoryId)

            val pendingDraft = TransactionDraft(
                draftId = "draft-pending-roundtrip",
                mode = TransactionMode.EXPENSE,
                amountMinor = 4250L,
                fromAccountId = cash.id,
                pendingPayeeName = "Klarna Store",
                note = "Test receipt",
                dateTimeMillis = 1_700_000_123_456L,
                stagedReceiptFileName = "staged_receipt.jpg",
                splits = listOf(
                    DraftSplit(categoryId = 10L, amountMinor = -2000L, note = "Part 1"),
                    DraftSplit(categoryId = 20L, amountMinor = -2250L, note = "Part 2"),
                ),
            )
            store.savePendingDraft(pendingDraft)
            assertEquals(pendingDraft, store.consumePendingAutoOpenDraft())
            assertNull(store.consumePendingAutoOpenDraft())

            store.savePendingDraft(pendingDraft)
            store.acknowledgeDraftOpened(pendingDraft.draftId)
            assertNull(store.consumePendingAutoOpenDraft())

            store.savePendingDraft(pendingDraft)
            store.clearPendingDraft(pendingDraft.draftId)
            assertNull(store.consumePendingAutoOpenDraft())

            store.savePendingDraft(pendingDraft)
            assertTrue(TransactionEntryRouter.launchPendingAiDraftIfPresent(context))
            assertFalse(TransactionEntryRouter.launchPendingAiDraftIfPresent(context))

            store.savePendingDraft(pendingDraft)
            assertFalse(TransactionEntryRouter.launchPendingAiDraftIfPresent(context, pendingDraft.draftId))
            assertFalse(TransactionEntryRouter.launchPendingAiDraftIfPresent(context))

            val newerDraft = pendingDraft.copy(draftId = "draft-pending-newer", amountMinor = 1500L)
            store.savePendingDraft(newerDraft)
            assertTrue(TransactionEntryRouter.launchPendingAiDraftIfPresent(context, pendingDraft.draftId))
            assertFalse(TransactionEntryRouter.launchPendingAiDraftIfPresent(context, pendingDraft.draftId))
        }
    }
}
