// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import ru.orangesoftware.financisto.transaction.port.TransactionPreferencesPort
import ru.orangesoftware.financisto.transaction.port.TransactionRepository
import ru.orangesoftware.financisto.utils.CurrencyCache
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeTransactionActivityBehaviorTest : AbstractDbTest() {

    private lateinit var account: Account

    override fun setUp() {
        super.setUp()
        context.getSharedPreferences(FinancistoDraftSessionStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
        val koinDb = GlobalContext.get().get<ru.orangesoftware.financisto.db.DatabaseAdapter>()
        val usd = CurrencyBuilder.withDb(koinDb)
            .name("USD")
            .title("Dollar")
            .symbol("$")
            .makeDefault()
            .create()
        account = AccountBuilder.withDb(koinDb).title("Cash").currency(usd).create()
        CurrencyCache.initialize(koinDb)
    }

    @Test
    fun onNewIntentWhileReviewingUnfinishedAiDraftPromptsInsteadOfReplacing() {
        val draftOne = sampleDraft("draft-one", amountMinor = 1500L, note = "Coffee")
        val draftTwo = sampleDraft("draft-two", amountMinor = 4200L, note = "Dinner")

        val controller = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createAiDraftIntent(context, draftOne),
        ).setup()
        val activity = controller.get()
        awaitReady(activity)

        controller.newIntent(TransactionEntryRouter.createAiDraftIntent(context, draftTwo))
        ShadowLooper.idleMainLooper()

        assertFalse(activity.isFinishing)
        assertEquals(draftTwo, activity.pendingAiDraftPrompt)
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.destroy()
    }

    @Test
    fun onNewIntentWithUnsavedManualEditsPromptsInsteadOfReplacing() {
        val incomingDraft = sampleDraft("draft-incoming", amountMinor = 2500L, note = "Lunch")
        val controller = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createNewIntent(context, account.id),
        ).setup()
        val activity = controller.get()
        awaitReady(activity)

        activity.viewModel.onIntent(TransactionIntent.UpdateNote("Manual unsaved edit"))
        ShadowLooper.idleMainLooper()
        assertTrue(activity.viewModel.hasUnsavedUserEdits())

        controller.newIntent(TransactionEntryRouter.createAiDraftIntent(context, incomingDraft))
        ShadowLooper.idleMainLooper()

        assertFalse(activity.isFinishing)
        assertEquals(incomingDraft, activity.pendingAiDraftPrompt)
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.destroy()
    }

    @Test
    fun onNewIntentOnUntouchedBlankNewReplacesImmediately() {
        val incomingDraft = sampleDraft("draft-clean-replace", amountMinor = 3100L, note = "Groceries")
        val controller = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createNewIntent(context, account.id),
        ).setup()
        val activity = controller.get()
        awaitReady(activity)
        assertFalse(activity.viewModel.hasUnsavedUserEdits())

        controller.newIntent(TransactionEntryRouter.createAiDraftIntent(context, incomingDraft))
        ShadowLooper.idleMainLooper()

        assertTrue(activity.isFinishing)
        val replacement = shadowOf(activity).nextStartedActivity
        assertNotNull(replacement)
        assertEquals(ComposeTransactionActivity::class.java.name, replacement.component?.className)
        val resolved = TransactionEntryRouter.resolveLaunchArgs(replacement)
        assertEquals(TransactionLaunchArgs.AiDraft(incomingDraft), resolved)
        controller.destroy()
    }

    @Test
    fun openInLegacyEffectForAiDraftKeepsReviewOpenAndDoesNotLaunchBlankLegacyForm() {
        val draft = sampleDraft("draft-no-legacy", amountMinor = 8900L, note = "Hotel")
        val controller = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createAiDraftIntent(context, draft),
        ).setup()
        val activity = controller.get()
        awaitReady(activity)

        activity.viewModel.onIntent(TransactionIntent.OpenInLegacyClicked)
        ShadowLooper.idleMainLooper()

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNotNull(ShadowToast.getTextOfLatestToast())
        controller.destroy()
    }

    @Test
    fun pendingCameraFileNameSurvivesSaveAndRestoreInstanceState() {
        val controller = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createNewIntent(context, account.id),
        ).setup()
        val activity = controller.get()
        activity.pendingCameraFileName = "camera_receipt_saved.jpg"

        val savedState = Bundle()
        controller.saveInstanceState(savedState)
        assertEquals(
            "camera_receipt_saved.jpg",
            savedState.getString(ComposeTransactionActivity.KEY_PENDING_CAMERA_FILE),
        )
        controller.destroy()

        val recreatedController = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createNewIntent(context, account.id),
        ).create(savedState)
        assertEquals("camera_receipt_saved.jpg", recreatedController.get().pendingCameraFileName)
        recreatedController.destroy()
    }

    @Test
    fun dispatchIntentWhenReadyDeliversCapturedPhotoAfterInitialLoadCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalogGate = CompletableDeferred<Unit>()
            val fakeMediaPort = FakeReceiptMediaPort()
            val viewModel = TransactionViewModel(
                launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
                savedStateHandle = SavedStateHandle(),
                ports = fakePorts(catalogGate, fakeMediaPort),
                ioDispatcher = dispatcher,
                clock = FixedClock(1_700_000_000_000L),
            )

            assertTrue(viewModel.uiState.value is TransactionUiState.Loading)

            dispatchIntentWhenReady(
                scope = this,
                viewModel = viewModel,
                receiptMediaPort = fakeMediaPort,
                intent = TransactionIntent.AttachCapturedPhotoFile("restored_photo.jpg"),
            )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is TransactionUiState.Loading)

            catalogGate.complete(Unit)
            advanceUntilIdle()

            val ready = viewModel.uiState.value as? TransactionUiState.Ready
            assertNotNull(ready)
            assertEquals("restored_photo.jpg", ready?.form?.attachedPictureFileName)
            assertTrue(ready?.form?.newlyStagedPictureFiles?.contains("restored_photo.jpg") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dispatchIntentWhenReadyDeletesCapturedPhotoWhenInitialLoadEndsInNoAccounts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalogGate = CompletableDeferred<Unit>()
            val fakeMediaPort = FakeReceiptMediaPort()
            val viewModel = TransactionViewModel(
                launchArgs = TransactionLaunchArgs.BlankNew(),
                savedStateHandle = SavedStateHandle(),
                ports = fakePorts(catalogGate, fakeMediaPort, includeAccount = false),
                ioDispatcher = dispatcher,
                clock = FixedClock(1_700_000_000_000L),
            )

            dispatchIntentWhenReady(
                scope = this,
                viewModel = viewModel,
                receiptMediaPort = fakeMediaPort,
                intent = TransactionIntent.AttachCapturedPhotoFile("orphan_photo.jpg"),
            )
            advanceUntilIdle()

            catalogGate.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is TransactionUiState.NoAccounts)
            assertEquals(listOf("orphan_photo.jpg"), fakeMediaPort.deletedFiles)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun awaitReady(activity: ComposeTransactionActivity) {
        val deadline = System.currentTimeMillis() + 3_000L
        while (activity.viewModel.uiState.value !is TransactionUiState.Ready &&
            System.currentTimeMillis() < deadline
        ) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10L)
        }
        ShadowLooper.idleMainLooper()
        assertTrue(
            "Expected ComposeTransactionActivity to reach Ready, got ${activity.viewModel.uiState.value}",
            activity.viewModel.uiState.value is TransactionUiState.Ready,
        )
    }

    private fun sampleDraft(draftId: String, amountMinor: Long, note: String): TransactionDraft {
        return TransactionDraft(
            draftId = draftId,
            mode = TransactionMode.EXPENSE,
            amountMinor = amountMinor,
            fromAccountId = account.id,
            note = note,
            dateTimeMillis = 1_700_000_000_000L,
        )
    }

    private fun fakePorts(
        catalogGate: CompletableDeferred<Unit>,
        mediaPort: ReceiptMediaPort,
        includeAccount: Boolean = true,
    ): TransactionPorts {
        val accounts = if (includeAccount) {
            listOf(
                AccountOption(
                    id = 1L,
                    title = "Cash",
                    typeLabel = "CASH",
                    currencyId = 1L,
                    currencyCode = "USD",
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    balanceMinor = 0L,
                )
            )
        } else {
            emptyList()
        }
        val catalog = TransactionCatalog(
            accounts = accounts,
            currencies = listOf(CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2)),
        )
        return TransactionPorts(
            repository = object : TransactionRepository {
                override suspend fun loadCatalog(editingTransactionId: Long?): TransactionCatalog {
                    catalogGate.await()
                    return catalog
                }

                override suspend fun loadForEdit(transactionId: Long): TransactionEditLoadResult =
                    TransactionEditLoadResult.NotFound(transactionId)

                override suspend fun getLatestExchangeRate(fromCurrencyId: Long, toCurrencyId: Long): Double? = null

                override suspend fun saveTransaction(command: SaveTransactionCommand): Long = 1L
            },
            preferencesPort = object : TransactionPreferencesPort {
                override fun readFormPreferences(): TransactionFormPreferences = TransactionFormPreferences()
            },
            draftSessionStore = object : DraftSessionStore {
                override suspend fun getSavedTransactionId(draftId: String): Long? = null
                override suspend fun markDraftSaved(draftId: String, transactionId: Long) = Unit
                override suspend fun acknowledgeDraftOpened(draftId: String) = Unit
                override suspend fun clearPendingDraft(draftId: String) = Unit
            },
            exchangeRatePort = object : ExchangeRatePort {
                override suspend fun fetchLiveRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double> =
                    Result.failure(IllegalStateException("unused"))
            },
            receiptMediaPort = mediaPort,
        )
    }

    private class FakeReceiptMediaPort : ReceiptMediaPort {
        val deletedFiles = mutableListOf<String>()
        override suspend fun stageFromUri(uriString: String): String? = null
        override fun resolveFilePath(fileName: String): String? = null
        override suspend fun deleteStagedFile(fileName: String) {
            deletedFiles.add(fileName)
        }
    }

    private class FixedClock(private val epochMillis: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis)
    }
}
