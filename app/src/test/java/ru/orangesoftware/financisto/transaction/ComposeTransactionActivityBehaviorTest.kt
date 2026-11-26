// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Choreographer
import androidx.appstate.statestore.StateStore
import androidx.appstate.transform.testing.TransformTestScope
import androidx.appstate.transform.testing.transformTest
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.TRANSFORM_DISPATCHER
import ru.orangesoftware.financisto.appstate.AiDraftSessionKey
import ru.orangesoftware.financisto.appstate.AiDraftSessionState
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.TransactionBuilder
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
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeTransactionActivityBehaviorTest : AbstractDbTest() {

    private lateinit var account: Account
    private val transformDispatcherOverride = module {
        single<CoroutineDispatcher>(TRANSFORM_DISPATCHER) { Dispatchers.Main.immediate }
    }

    override fun setUp() {
        super.setUp()
        context.getSharedPreferences(FinancistoDraftSessionStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
        val koin = GlobalContext.get()
        Snapshot.withMutableSnapshot {
            koin.get<StateStore>().setState(AiDraftSessionKey, AiDraftSessionState())
        }
        koin.loadModules(listOf(transformDispatcherOverride), allowOverride = true)
        val koinDb = koin.get<ru.orangesoftware.financisto.db.DatabaseAdapter>()
        val usd = CurrencyBuilder.withDb(koinDb)
            .name("USD")
            .title("Dollar")
            .symbol("$")
            .makeDefault()
            .create()
        account = AccountBuilder.withDb(koinDb).title("Cash").currency(usd).create()
        CurrencyCache.initialize(koinDb)
    }

    override fun tearDown() {
        GlobalContext.get().unloadModules(listOf(transformDispatcherOverride))
        super.tearDown()
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
        flushAppStateAndLooper()
        assertTrue(activity.viewModel.hasUnsavedUserEdits())

        controller.newIntent(TransactionEntryRouter.createAiDraftIntent(context, incomingDraft))
        flushAppStateAndLooper()

        assertFalse(activity.isFinishing)
        assertEquals(incomingDraft, activity.pendingAiDraftPrompt)
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.destroy()
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun inProcessPendingDraftSurfacesReactivelyWhenEditorIsReady() {
        val draftOne = sampleDraft("draft-reactive-1", amountMinor = 1500L, note = "Coffee")
        val draftTwo = sampleDraft("draft-reactive-2", amountMinor = 4200L, note = "Dinner")
        val koinDraftStore = GlobalContext.get().get<FinancistoDraftSessionStore>()
        val factory = WindowRecomposerFactory { rootView ->
            rootView.createLifecycleAwareWindowRecomposer(
                coroutineContext = Dispatchers.Main.immediate + AndroidUiFrameClock(Choreographer.getInstance()),
            )
        }

        WindowRecomposerPolicy.withFactory(factory) {
            val controller = Robolectric.buildActivity(
                ComposeTransactionActivity::class.java,
                TransactionEntryRouter.createAiDraftIntent(context, draftOne),
            ).setup()
            try {
                val activity = controller.get()
                awaitReady(activity)
                ShadowLooper.idleMainLooper(32, TimeUnit.MILLISECONDS)

                assertTrue(koinDraftStore.savePendingDraft(draftTwo))
                repeat(4) {
                    Snapshot.sendApplyNotifications()
                    ShadowLooper.idleMainLooper(32, TimeUnit.MILLISECONDS)
                }

                assertFalse(activity.isFinishing)
                assertEquals(draftTwo, activity.pendingAiDraftPrompt)
            } finally {
                controller.destroy()
            }
        }
    }

    @Test
    fun surfacePendingAiDraftIfReadyGuardsLoadingLockedAndAlreadySavedDrafts() {
        runBlocking {
            val draftOne = sampleDraft("draft-guard-1", amountMinor = 1500L, note = "Coffee")
            val draftTwo = sampleDraft("draft-guard-2", amountMinor = 4200L, note = "Dinner")
            val savedDraft = sampleDraft("draft-guard-saved", amountMinor = 5300L, note = "Lunch")
            val koinDraftStore = GlobalContext.get().get<FinancistoDraftSessionStore>()
            val stateStore = GlobalContext.get().get<StateStore>()

            val controller = Robolectric.buildActivity(
                ComposeTransactionActivity::class.java,
                TransactionEntryRouter.createAiDraftIntent(context, draftOne),
            ).setup()
            val activity = controller.get()
            awaitReady(activity)

            activity.surfacePendingAiDraftIfReady(draftTwo, TransactionUiState.Loading, unlocked = true)
            assertNull(activity.pendingAiDraftPrompt)

            activity.surfacePendingAiDraftIfReady(draftTwo, TransactionUiState.PinLockedMask, unlocked = false)
            assertNull(activity.pendingAiDraftPrompt)

            koinDraftStore.markDraftSaved(savedDraft.draftId, 88L)
            Snapshot.withMutableSnapshot {
                stateStore.updateState(AiDraftSessionKey) { it.copy(pendingDraft = savedDraft) }
            }
            activity.surfacePendingAiDraftIfReady(savedDraft, activity.viewModel.uiState.value, unlocked = true)
            flushAppStateAndLooper()

            assertNull(activity.pendingAiDraftPrompt)
            assertNull(koinDraftStore.sessionState.value.pendingDraft)
            controller.destroy()
        }
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
        assertTrue(replacement.flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT != 0)
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
        assertEquals(
            context.getString(R.string.tx_notice_ai_draft_legacy_unsupported),
            ShadowToast.getTextOfLatestToast(),
        )
        controller.destroy()
    }

    @Test
    fun trySwitchToPendingAiDraftIsBlockedWhileSaveIsInFlight() {
        val draftOne = sampleDraft("draft-saving-1", amountMinor = 1500L, note = "Coffee")
        val draftTwo = sampleDraft("draft-saving-2", amountMinor = 4200L, note = "Dinner")
        val saveGate = CompletableDeferred<Long>()
        val catalogGate = CompletableDeferred<Unit>().apply { complete(Unit) }
        val draftStore = FinancistoDraftSessionStore(context, GlobalContext.get().get<StateStore>())
        val portsOverride = module {
            single {
                fakePorts(
                    catalogGate = catalogGate,
                    mediaPort = FakeReceiptMediaPort(),
                    saveGate = saveGate,
                    accountId = account.id,
                )
            }
        }
        val koin = GlobalContext.get()
        koin.loadModules(listOf(portsOverride), allowOverride = true)
        try {
            val controller = Robolectric.buildActivity(
                ComposeTransactionActivity::class.java,
                TransactionEntryRouter.createAiDraftIntent(context, draftOne),
            ).setup()
            val activity = controller.get()
            awaitReady(activity)

            activity.viewModel.onIntent(TransactionIntent.SaveClicked)
            flushAppStateAndLooper()
            assertTrue((activity.viewModel.uiState.value as TransactionUiState.Ready).isSaving)

            draftStore.savePendingDraft(draftTwo)
            controller.newIntent(TransactionEntryRouter.createAiDraftIntent(context, draftTwo))
            flushAppStateAndLooper()
            assertEquals(draftTwo, activity.pendingAiDraftPrompt)

            assertFalse(activity.trySwitchToPendingAiDraft(draftTwo, draftStore))
            assertFalse(activity.isFinishing)
            assertEquals(draftTwo, activity.pendingAiDraftPrompt)
            assertEquals(draftTwo, draftStore.peekPendingAutoOpenDraft())
            assertNull(shadowOf(activity).nextStartedActivity)

            saveGate.complete(42L)
            val deadline = System.currentTimeMillis() + 3_000L
            while (!activity.isFinishing && System.currentTimeMillis() < deadline) {
                flushAppStateAndLooper()
                Thread.sleep(10L)
            }
            flushAppStateAndLooper()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
            controller.destroy()
        } finally {
            koin.unloadModules(listOf(portsOverride))
        }
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
    fun dispatchIntentWhenReadyDeliversCapturedPhotoAfterInitialLoadCompletes() = transformTest {
        val catalogGate = CompletableDeferred<Unit>()
        val fakeMediaPort = FakeReceiptMediaPort()
        val viewModel = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = fakePorts(catalogGate, fakeMediaPort),
            ioDispatcher = testDispatcher,
            clock = FixedClock(1_700_000_000_000L),
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )

        assertTrue(viewModel.uiState.value is TransactionUiState.Loading)

        dispatchIntentWhenReady(
            scope = backgroundScope,
            viewModel = viewModel,
            receiptMediaPort = fakeMediaPort,
            intent = TransactionIntent.AttachCapturedPhotoFile("restored_photo.jpg"),
        )
        advanceAll()
        assertTrue(viewModel.uiState.value is TransactionUiState.Loading)

        catalogGate.complete(Unit)
        advanceAll()

        val ready = viewModel.uiState.value as? TransactionUiState.Ready
        assertNotNull(ready)
        assertEquals("restored_photo.jpg", ready?.form?.attachedPictureFileName)
        assertTrue(ready?.form?.newlyStagedPictureFiles?.contains("restored_photo.jpg") == true)
    }

    @Test
    fun dispatchIntentWhenReadyDeletesCapturedPhotoWhenInitialLoadEndsInNoAccounts() = transformTest {
        val catalogGate = CompletableDeferred<Unit>()
        val fakeMediaPort = FakeReceiptMediaPort()
        val viewModel = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(),
            savedStateHandle = SavedStateHandle(),
            ports = fakePorts(catalogGate, fakeMediaPort, includeAccount = false),
            ioDispatcher = testDispatcher,
            clock = FixedClock(1_700_000_000_000L),
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )

        dispatchIntentWhenReady(
            scope = backgroundScope,
            viewModel = viewModel,
            receiptMediaPort = fakeMediaPort,
            intent = TransactionIntent.AttachCapturedPhotoFile("orphan_photo.jpg"),
        )
        advanceAll()

        catalogGate.complete(Unit)
        advanceAll()

        assertTrue(viewModel.uiState.value is TransactionUiState.NoAccounts)
        assertEquals(listOf("orphan_photo.jpg"), fakeMediaPort.deletedFiles)
    }

    @Test
    fun dispatchIntentWhenReadyQueuesPhotoWhileSavingAndDispatchesAfterFailedSave() = transformTest {
        val catalogGate = CompletableDeferred<Unit>().apply { complete(Unit) }
        val saveGate = CompletableDeferred<Long>()
        val fakeMediaPort = FakeReceiptMediaPort()
        val viewModel = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = fakePorts(catalogGate, fakeMediaPort, saveGate = saveGate),
            ioDispatcher = testDispatcher,
            clock = FixedClock(1_700_000_000_000L),
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )
        advanceAll()
        viewModel.onIntent(TransactionIntent.UpdatePrimaryAmount("12.00"))
        viewModel.onIntent(TransactionIntent.SaveClicked)
        advanceAll()
        assertTrue((viewModel.uiState.value as TransactionUiState.Ready).isSaving)

        dispatchIntentWhenReady(
            scope = backgroundScope,
            viewModel = viewModel,
            receiptMediaPort = fakeMediaPort,
            intent = TransactionIntent.AttachCapturedPhotoFile("retry_photo.jpg"),
        )
        advanceAll()
        assertNull((viewModel.uiState.value as TransactionUiState.Ready).form.attachedPictureFileName)
        assertTrue(fakeMediaPort.deletedFiles.isEmpty())

        saveGate.completeExceptionally(IllegalStateException("Save failed"))
        advanceAll()

        val ready = viewModel.uiState.value as TransactionUiState.Ready
        assertFalse(ready.isSaving)
        assertEquals("retry_photo.jpg", ready.form.attachedPictureFileName)
        assertTrue(fakeMediaPort.deletedFiles.isEmpty())
    }

    @Test
    fun dispatchIntentWhenReadyDeletesQueuedPhotoWhenSaveAndNewResetsForm() = transformTest {
        val catalogGate = CompletableDeferred<Unit>().apply { complete(Unit) }
        val saveGate = CompletableDeferred<Long>()
        val fakeMediaPort = FakeReceiptMediaPort()
        val viewModel = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = fakePorts(catalogGate, fakeMediaPort, saveGate = saveGate),
            ioDispatcher = testDispatcher,
            clock = FixedClock(1_700_000_000_000L),
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )
        advanceAll()
        viewModel.onIntent(TransactionIntent.UpdatePrimaryAmount("25.00"))
        viewModel.onIntent(TransactionIntent.SaveAndNewClicked)
        advanceAll()
        assertTrue((viewModel.uiState.value as TransactionUiState.Ready).isSaving)

        dispatchIntentWhenReady(
            scope = backgroundScope,
            viewModel = viewModel,
            receiptMediaPort = fakeMediaPort,
            intent = TransactionIntent.AttachCapturedPhotoFile("stale_form_photo.jpg"),
        )
        advanceAll()

        saveGate.complete(101L)
        advanceAll()

        val ready = viewModel.uiState.value as TransactionUiState.Ready
        assertFalse(ready.isSaving)
        assertEquals(0L, ready.form.primaryAmountMinor)
        assertNull(ready.form.attachedPictureFileName)
        assertEquals(listOf("stale_form_photo.jpg"), fakeMediaPort.deletedFiles)
    }

    @Test
    fun dispatchIntentWhenReadyDeletesQueuedPhotoWhenScopeCancelsDuringSaveFinish() = transformTest {
        val catalogGate = CompletableDeferred<Unit>().apply { complete(Unit) }
        val saveGate = CompletableDeferred<Long>()
        val fakeMediaPort = FakeReceiptMediaPort()
        val viewModel = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = fakePorts(catalogGate, fakeMediaPort, saveGate = saveGate),
            ioDispatcher = testDispatcher,
            clock = FixedClock(1_700_000_000_000L),
            transformDispatcher = testDispatcher,
            stateScope = backgroundScope,
        )
        advanceAll()
        viewModel.onIntent(TransactionIntent.UpdatePrimaryAmount("30.00"))
        viewModel.onIntent(TransactionIntent.SaveClicked)
        advanceAll()

        val activityScope = CoroutineScope(Job() + testDispatcher)
        dispatchIntentWhenReady(
            scope = activityScope,
            viewModel = viewModel,
            receiptMediaPort = fakeMediaPort,
            intent = TransactionIntent.AttachCapturedPhotoFile("closing_photo.jpg"),
        )
        advanceAll()
        assertTrue(fakeMediaPort.deletedFiles.isEmpty())

        saveGate.complete(102L)
        activityScope.cancel()
        advanceAll()

        assertEquals(listOf("closing_photo.jpg"), fakeMediaPort.deletedFiles)
    }

    @Test
    fun createCameraCaptureFileDeletesLeakedFileOnCancellationBeforeResumption() = runTest {
        val callerDispatcher = ManualQueueDispatcher()
        val ioDispatcher = ManualQueueDispatcher()
        val tempFile = File.createTempFile("camera_cancel_test", ".jpg", context.cacheDir)
        tempFile.delete()

        var caughtCancellation = false
        val captureJob = launch(callerDispatcher) {
            try {
                createCameraCaptureFile(ioDispatcher) {
                    tempFile.writeBytes(byteArrayOf(0x01))
                    tempFile
                }
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        callerDispatcher.runAll()
        ioDispatcher.runAll()
        assertTrue(tempFile.exists())

        captureJob.cancel(CancellationException("Activity cancelled before resumption"))
        callerDispatcher.runAll()
        ioDispatcher.runAll()
        callerDispatcher.runAll()

        assertTrue(caughtCancellation)
        assertFalse(tempFile.exists())
    }

    @Test
    fun dismissPendingAiDraftDeletesOrphanedReceiptAndSkipsSavedOrActiveReceipt() {
        val draftStore = FinancistoDraftSessionStore(
            context,
            GlobalContext.get().get<StateStore>(),
            UnconfinedTestDispatcher(),
        )
        val fakeMediaPort = FakeReceiptMediaPort()
        val controller = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createNewIntent(context, account.id),
        ).setup()
        val activity = controller.get()
        awaitReady(activity)

        val orphanIncoming = sampleDraft("draft-dismiss-orphan", amountMinor = 1800L, note = "Lunch")
            .copy(stagedReceiptFileName = "orphan_keep_editing.jpg")
        draftStore.savePendingDraft(orphanIncoming)
        activity.pendingAiDraftPrompt = orphanIncoming
        activity.dismissPendingAiDraft(orphanIncoming, draftStore, fakeMediaPort)
        flushAppStateAndLooper()
        assertNull(activity.pendingAiDraftPrompt)
        assertNull(draftStore.peekPendingAutoOpenDraft())
        assertEquals(listOf("orphan_keep_editing.jpg"), fakeMediaPort.deletedFiles)

        val savedIncoming = sampleDraft("draft-dismiss-saved", amountMinor = 2200L, note = "Saved")
            .copy(stagedReceiptFileName = "saved_keep_editing.jpg")
        runBlocking { draftStore.markDraftSaved(savedIncoming.draftId, 88L) }
        draftStore.savePendingDraft(savedIncoming)
        activity.pendingAiDraftPrompt = savedIncoming
        activity.dismissPendingAiDraft(savedIncoming, draftStore, fakeMediaPort)
        flushAppStateAndLooper()
        assertNull(activity.pendingAiDraftPrompt)
        assertNull(draftStore.peekPendingAutoOpenDraft())
        assertEquals(listOf("orphan_keep_editing.jpg"), fakeMediaPort.deletedFiles)

        activity.viewModel.onIntent(TransactionIntent.AttachCapturedPhotoFile("active_attached.jpg"))
        flushAppStateAndLooper()
        val attachedIncoming = sampleDraft("draft-dismiss-attached", amountMinor = 1500L, note = "Same photo")
            .copy(stagedReceiptFileName = "active_attached.jpg")
        draftStore.savePendingDraft(attachedIncoming)
        activity.pendingAiDraftPrompt = attachedIncoming
        activity.dismissPendingAiDraft(attachedIncoming, draftStore, fakeMediaPort)
        flushAppStateAndLooper()
        assertNull(activity.pendingAiDraftPrompt)
        assertEquals(listOf("orphan_keep_editing.jpg"), fakeMediaPort.deletedFiles)
        controller.destroy()
    }

    @Test
    fun dismissPendingAiDraftSkipsReceiptMatchingInitialPersistedPicture() {
        val draftStore = FinancistoDraftSessionStore(
            context,
            GlobalContext.get().get<StateStore>(),
            UnconfinedTestDispatcher(),
        )
        val fakeMediaPort = FakeReceiptMediaPort()
        val koinDb = GlobalContext.get().get<ru.orangesoftware.financisto.db.DatabaseAdapter>()
        val existingTx = TransactionBuilder.withDb(koinDb).account(account).amount(-1200L).create()
        existingTx.attachedPicture = "persisted_initial.jpg"
        koinDb.insertOrUpdate(existingTx, emptyList())

        val editController = Robolectric.buildActivity(
            ComposeTransactionActivity::class.java,
            TransactionEntryRouter.createEditIntent(context, existingTx.id),
        ).setup()
        val editActivity = editController.get()
        awaitReady(editActivity)
        editActivity.viewModel.onIntent(TransactionIntent.RemoveReceipt)
        flushAppStateAndLooper()
        val readyEdit = editActivity.viewModel.uiState.value as TransactionUiState.Ready
        assertNull(readyEdit.form.attachedPictureFileName)
        assertEquals("persisted_initial.jpg", readyEdit.form.initialPersistedPictureFileName)

        val persistedIncoming = sampleDraft("draft-dismiss-persisted", amountMinor = 1200L, note = "Persisted photo")
            .copy(stagedReceiptFileName = "persisted_initial.jpg")
        draftStore.savePendingDraft(persistedIncoming)
        editActivity.pendingAiDraftPrompt = persistedIncoming
        editActivity.dismissPendingAiDraft(persistedIncoming, draftStore, fakeMediaPort)
        flushAppStateAndLooper()
        assertNull(editActivity.pendingAiDraftPrompt)
        assertTrue(fakeMediaPort.deletedFiles.isEmpty())
        editController.destroy()
    }

    private fun TransformTestScope.advanceAll() {
        advanceUntilIdle()
        advance()
        advanceUntilIdle()
    }

    private fun flushAppStateAndLooper() {
        Snapshot.sendApplyNotifications()
        ShadowLooper.idleMainLooper()
    }

    private fun awaitReady(activity: ComposeTransactionActivity) {
        val deadline = System.currentTimeMillis() + 3_000L
        while (activity.viewModel.uiState.value !is TransactionUiState.Ready &&
            System.currentTimeMillis() < deadline
        ) {
            flushAppStateAndLooper()
            Thread.sleep(10L)
        }
        flushAppStateAndLooper()
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
        saveGate: CompletableDeferred<Long>? = null,
        accountId: Long = 1L,
    ): TransactionPorts {
        val accounts = if (includeAccount) {
            listOf(
                AccountOption(
                    id = accountId,
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

                override suspend fun saveTransaction(command: SaveTransactionCommand): Long =
                    saveGate?.await() ?: 1L
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

    private class ManualQueueDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
            }
        }
    }
}
