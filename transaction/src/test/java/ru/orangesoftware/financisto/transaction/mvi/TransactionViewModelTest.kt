// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.appstate.transform.testing.TransformTestScope
import androidx.appstate.transform.testing.transformTest
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.FIXED_NOW_MILLIS
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.defaultCatalog
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.fixedClock
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.multiCurrencyCatalog
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.sampleRecord
import ru.orangesoftware.financisto.transaction.port.TransactionPorts

class TransactionViewModelTest {

    private lateinit var repository: FakeTransactionRepository
    private lateinit var draftStore: FakeDraftSessionStore
    private lateinit var mediaPort: FakeReceiptMediaPort
    private lateinit var exchangeRatePort: FakeExchangeRatePort
    private lateinit var ports: TransactionPorts

    @Before
    fun setUp() {
        repository = FakeTransactionRepository(defaultCatalog())
        draftStore = FakeDraftSessionStore()
        mediaPort = FakeReceiptMediaPort()
        exchangeRatePort = FakeExchangeRatePort()
        ports = TransactionTestFakes.createPorts(repository, draftStore, exchangeRatePort, mediaPort)
    }

    private fun TransformTestScope.advanceAll() {
        advanceUntilIdle()
        advance()
        advanceUntilIdle()
    }

    private fun TransformTestScope.createViewModel(
        launchArgs: TransactionLaunchArgs,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): TransactionViewModel = TransactionViewModel(
        launchArgs = launchArgs,
        savedStateHandle = savedStateHandle,
        ports = ports,
        ioDispatcher = testDispatcher,
        clock = fixedClock,
        transformDispatcher = testDispatcher,
        stateScope = backgroundScope,
    ).also { advanceAll() }

    private fun TransformTestScope.dispatch(vm: TransactionViewModel, intent: TransactionIntent) {
        vm.onIntent(intent)
        advanceAll()
    }

    @Test
    fun blankNewSaveAndSaveAndNewWorkAndUseInjectedClock() = transformTest {
        val vm = createViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L, initialMode = TransactionMode.EXPENSE),
        )
        val initial = vm.uiState.value as TransactionUiState.Ready
        assertEquals(1L, initial.form.fromAccountId)
        assertEquals(FIXED_NOW_MILLIS, initial.form.dateTimeMillis)

        dispatch(vm, TransactionIntent.UpdatePrimaryAmount("25.50"))
        dispatch(vm, TransactionIntent.UpdateNote("Lunch"))
        dispatch(vm, TransactionIntent.SaveAndNewClicked)

        assertEquals(1, repository.savedCommands.size)
        assertEquals(-2550L, repository.savedCommands[0].amountCommandMinor())
        val afterReset = vm.uiState.value as TransactionUiState.Ready
        assertEquals(1L, afterReset.form.fromAccountId)
        assertEquals(0L, afterReset.form.primaryAmountMinor)
        assertEquals("", afterReset.form.note)
        assertTrue(vm.effects.first() is TransactionEffect.ShowMessage)

        dispatch(vm, TransactionIntent.UpdatePrimaryAmount("10.00"))
        dispatch(vm, TransactionIntent.SaveClicked)
        val finishEffect = vm.effects.first()
        assertTrue(finishEffect is TransactionEffect.SavedAndFinish)
        assertEquals(102L, (finishEffect as TransactionEffect.SavedAndFinish).transactionId)
    }

    @Test
    fun editHandlesNotFoundLegacyFallbackAndInactiveEntities() = transformTest {
        repository.editResult = TransactionEditLoadResult.NotFound(999L)
        val notFoundVm = createViewModel(launchArgs = TransactionLaunchArgs.Edit(999L))
        assertEquals(TransactionUiState.NotFound(999L), notFoundVm.uiState.value)

        repository.editResult = TransactionEditLoadResult.RequiresLegacyFallback(50L, isTransfer = false)
        val legacyVm = createViewModel(launchArgs = TransactionLaunchArgs.Edit(50L))
        assertEquals(TransactionUiState.LegacyRedirecting, legacyVm.uiState.value)
        assertTrue(legacyVm.effects.first() is TransactionEffect.OpenLegacyEditor)

        val inactiveAccount = AccountOption(
            id = 99L,
            title = "Closed Account",
            typeLabel = "CASH",
            currencyId = 1L,
            currencyCode = "USD",
            currencySymbol = "$",
            currencyDecimals = 2,
            balanceMinor = 0L,
            isActive = false,
        )
        repository.catalog = defaultCatalog().copy(
            accounts = defaultCatalog().accounts + inactiveAccount,
            payees = defaultCatalog().payees + PayeeOption(id = 300L, title = "Old Shop", isActive = false),
            projects = listOf(ProjectOption(id = 400L, title = "Archived Project", isActive = false)),
        )
        repository.editResult = TransactionEditLoadResult.Loaded(
            sampleRecord(id = 77L, fromAccountId = 99L, payeeId = 300L, projectId = 400L)
        )
        val loadedVm = createViewModel(launchArgs = TransactionLaunchArgs.Edit(77L))
        val ready = loadedVm.uiState.value as TransactionUiState.Ready
        assertEquals(99L, ready.form.fromAccountId)
        assertEquals(300L, ready.form.payeeId)
        assertEquals(400L, ready.form.projectId)
        assertFalse(ready.hasUnsavedChanges)
    }

    @Test
    fun aiDraftHandlesConsumedPromptAndStagedReceiptCleanupOnDiscardVsSave() = transformTest {
        draftStore.savedByDraftId["draft-consumed"] = 88L
        repository.editResult = TransactionEditLoadResult.Loaded(
            sampleRecord(id = 88L, attachedPicture = "staged_a.jpg")
        )
        val consumedVm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-consumed",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 1500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                    stagedReceiptFileName = "staged_a.jpg",
                )
            ),
        )
        val consumedState = consumedVm.uiState.value as TransactionUiState.Ready
        assertEquals(ActiveSheet.ConsumedDraftPrompt(88L), consumedState.activeSheet)
        assertFalse(consumedVm.hasUnsavedUserEdits())

        dispatch(consumedVm, TransactionIntent.CancelOrBackClicked)
        assertEquals(
            ActiveSheet.DiscardConfirmation,
            (consumedVm.uiState.value as TransactionUiState.Ready).activeSheet,
        )
        dispatch(consumedVm, TransactionIntent.ConfirmDiscard)
        assertTrue(mediaPort.deletedFiles.isEmpty())
        assertTrue(consumedVm.effects.first() is TransactionEffect.CloseCancelled)

        val saveVm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-fresh",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 2500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                    stagedReceiptFileName = "staged_initial.jpg",
                )
            ),
        )
        dispatch(saveVm, TransactionIntent.AttachCapturedPhotoFile("staged_replacement.jpg"))
        assertTrue(saveVm.hasUnsavedUserEdits())
        dispatch(saveVm, TransactionIntent.SaveClicked)

        assertEquals(listOf("staged_initial.jpg"), mediaPort.deletedFiles)
        assertEquals(101L, draftStore.savedByDraftId["draft-fresh"])
        assertEquals("staged_replacement.jpg", repository.savedCommands.last().attachedPicture)
    }

    @Test
    fun pinMaskAndSavedStateHandleRestoreWorkAcrossProcessDeath() = transformTest {
        val handle = SavedStateHandle()
        val vm1 = createViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
        )
        dispatch(vm1, TransactionIntent.UpdatePrimaryAmount("75.25"))
        dispatch(vm1, TransactionIntent.UpdateNote("Before process death"))

        vm1.onPinStateChanged(unlocked = false)
        advanceAll()
        assertEquals(TransactionUiState.PinLockedMask, vm1.uiState.value)
        vm1.onPinStateChanged(unlocked = true)
        advanceAll()
        assertEquals("Before process death", (vm1.uiState.value as TransactionUiState.Ready).form.note)

        val vm2 = createViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
        )
        val restored = vm2.uiState.value as TransactionUiState.Ready
        assertEquals(7525L, restored.form.primaryAmountMinor)
        assertEquals("Before process death", restored.form.note)
        assertTrue(restored.hasUnsavedChanges)
        assertNull(restored.activeSheet)
    }

    @Test
    fun openSavedTransactionUpdatesCurrentLaunchArgsForOpenInLegacy() = transformTest {
        draftStore.savedByDraftId["draft-consumed"] = 88L
        repository.editResult = TransactionEditLoadResult.Loaded(sampleRecord(id = 88L))

        val vm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-consumed",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 1500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                )
            ),
        )

        dispatch(vm, TransactionIntent.OpenSavedTransaction(88L))
        dispatch(vm, TransactionIntent.OpenInLegacyClicked)

        val effect = vm.effects.first()
        assertEquals(TransactionEffect.OpenLegacyEditor(TransactionLaunchArgs.Edit(88L)), effect)
    }

    @Test
    fun cleanupStagedReceiptsDeletesNewlyStagedFilesOnlyAndPreservesPersistedReceipt() = transformTest {
        repository.editResult = TransactionEditLoadResult.Loaded(
            sampleRecord(id = 77L, attachedPicture = "persisted.jpg")
        )
        val vm = createViewModel(launchArgs = TransactionLaunchArgs.Edit(77L))

        dispatch(vm, TransactionIntent.AttachCapturedPhotoFile("temp_1.jpg"))
        dispatch(vm, TransactionIntent.AttachCapturedPhotoFile("temp_2.jpg"))
        assertTrue(vm.hasUnsavedUserEdits())

        vm.cleanupStagedReceipts()
        advanceAll()
        assertEquals(listOf("temp_1.jpg", "temp_2.jpg"), mediaPort.deletedFiles)
        val ready = vm.uiState.value as TransactionUiState.Ready
        assertEquals("persisted.jpg", ready.form.attachedPictureFileName)
        assertTrue(ready.form.newlyStagedPictureFiles.isEmpty())

        vm.cleanupStagedReceipts()
        advanceAll()
        assertEquals(listOf("temp_1.jpg", "temp_2.jpg"), mediaPort.deletedFiles)
    }

    @Test
    fun consumedDraftDiscardPreservesCommittedReceiptAndDeletesReplacement() = transformTest {
        draftStore.savedByDraftId["draft-consumed-receipt"] = 88L
        val vm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-consumed-receipt",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 1500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                    stagedReceiptFileName = "committed_receipt.jpg",
                )
            ),
        )

        val initial = vm.uiState.value as TransactionUiState.Ready
        assertEquals("committed_receipt.jpg", initial.form.attachedPictureFileName)
        assertTrue(initial.form.newlyStagedPictureFiles.isEmpty())

        dispatch(vm, TransactionIntent.DuplicateDraftAnyway)
        dispatch(vm, TransactionIntent.AttachCapturedPhotoFile("new_replacement.jpg"))
        dispatch(vm, TransactionIntent.ConfirmDiscard)

        assertEquals(listOf("new_replacement.jpg"), mediaPort.deletedFiles)
        assertEquals(TransactionEffect.CloseCancelled, vm.effects.first())
    }

    @Test
    fun consumedDraftPromptSurvivesProcessDeathAndBlocksSaveUntilConfirmed() = transformTest {
        repository.catalog = multiCurrencyCatalog()
        repository.storedRates[1L to 2L] = 0.9
        draftStore.savedByDraftId["draft-recreated"] = 88L
        val draftArgs = TransactionLaunchArgs.AiDraft(
            TransactionDraft(
                draftId = "draft-recreated",
                mode = TransactionMode.TRANSFER,
                amountMinor = 10000L,
                fromAccountId = 1L,
                toAccountId = 2L,
                dateTimeMillis = FIXED_NOW_MILLIS,
            )
        )
        val handle = SavedStateHandle()
        val vm1 = createViewModel(draftArgs, handle)
        assertEquals(
            ActiveSheet.ConsumedDraftPrompt(88L),
            (vm1.uiState.value as TransactionUiState.Ready).activeSheet,
        )

        val vm2 = createViewModel(draftArgs, handle)
        assertEquals(
            ActiveSheet.ConsumedDraftPrompt(88L),
            (vm2.uiState.value as TransactionUiState.Ready).activeSheet,
        )

        dispatch(vm2, TransactionIntent.DismissSheet)
        dispatch(vm2, TransactionIntent.SaveClicked)
        assertTrue(repository.savedCommands.isEmpty())
        assertEquals(
            ActiveSheet.ConsumedDraftPrompt(88L),
            (vm2.uiState.value as TransactionUiState.Ready).activeSheet,
        )

        dispatch(vm2, TransactionIntent.DuplicateDraftAnyway)
        assertNull((vm2.uiState.value as TransactionUiState.Ready).activeSheet)

        val vm3 = createViewModel(draftArgs, handle)
        assertNull((vm3.uiState.value as TransactionUiState.Ready).activeSheet)
        dispatch(vm3, TransactionIntent.SaveClicked)
        assertEquals(1, repository.savedCommands.size)
        assertEquals(TransactionEffect.SavedAndFinish(101L), vm3.effects.first())
    }

    @Test
    fun postCommitCleanupAndRefreshFailuresNeverReportCommittedSaveAsRetryable() = transformTest {
        draftStore.failMarkDraftSavedMessage = "Prefs write failed"
        mediaPort.failDeleteMessage = "FS busy"

        val saveVm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-post-commit",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 2000L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                    stagedReceiptFileName = "unused.jpg",
                )
            ),
        )
        dispatch(saveVm, TransactionIntent.RemoveReceipt)
        dispatch(saveVm, TransactionIntent.SaveClicked)

        assertEquals(1, repository.savedCommands.size)
        assertEquals(TransactionEffect.SavedAndFinish(101L), saveVm.effects.first())

        val saveAndNewVm = createViewModel(launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L))
        repository.failLoadCatalogAfterFirstMessage = "Catalog reload failed"
        dispatch(saveAndNewVm, TransactionIntent.UpdatePrimaryAmount("15.00"))
        dispatch(saveAndNewVm, TransactionIntent.SaveAndNewClicked)

        assertEquals(2, repository.savedCommands.size)
        assertTrue(saveAndNewVm.effects.first() is TransactionEffect.ShowMessage)
        assertEquals(TransactionEffect.SavedAndFinish(102L), saveAndNewVm.effects.first())
    }

    @Test
    fun externalCategoryCreatedFromSplitHeaderPreservesSplitMode() = transformTest {
        val vm = createViewModel(launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L))
        dispatch(vm, TransactionIntent.UpdatePrimaryAmount("50.00"))
        dispatch(vm, TransactionIntent.ToggleSplitMode)
        val beforeSplits = (vm.uiState.value as TransactionUiState.Ready).form.splits
        assertEquals(1, beforeSplits.size)

        repository.catalog = defaultCatalog().copy(
            categories = defaultCatalog().categories + CategoryOption(
                id = 88L,
                parentId = 0L,
                title = "Snacks",
                fullPath = "Snacks",
                level = 1,
                isIncome = false,
            )
        )
        dispatch(vm, TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 88L))

        val ready = vm.uiState.value as TransactionUiState.Ready
        assertTrue(ready.form.isSplitMode)
        assertEquals(TransactionIds.SPLIT_CATEGORY_ID, ready.form.categoryId)
        assertEquals(beforeSplits, ready.form.splits)
        assertTrue(ready.catalog.categories.any { it.id == 88L })
    }

    @Test
    fun processDeathAfterOpenSavedTransactionFromConsumedDraftRestoresEditAndAllowsSave() = transformTest {
        draftStore.savedByDraftId["draft-open-saved"] = 88L
        repository.editResult = TransactionEditLoadResult.Loaded(
            sampleRecord(id = 88L, attachedPicture = "saved_88.jpg")
        )
        val draftArgs = TransactionLaunchArgs.AiDraft(
            TransactionDraft(
                draftId = "draft-open-saved",
                mode = TransactionMode.EXPENSE,
                amountMinor = 1500L,
                fromAccountId = 1L,
                dateTimeMillis = FIXED_NOW_MILLIS,
                stagedReceiptFileName = "draft_receipt.jpg",
            )
        )
        val handle = SavedStateHandle()
        val vm1 = createViewModel(draftArgs, handle)
        assertEquals(
            ActiveSheet.ConsumedDraftPrompt(88L),
            (vm1.uiState.value as TransactionUiState.Ready).activeSheet,
        )

        dispatch(vm1, TransactionIntent.OpenSavedTransaction(88L))
        dispatch(vm1, TransactionIntent.UpdateNote("Edited saved tx"))

        val vm2 = createViewModel(draftArgs, handle)
        val restored = vm2.uiState.value as TransactionUiState.Ready
        assertEquals(88L, restored.form.existingTransactionId)
        assertFalse(restored.form.isAiDraft)
        assertEquals("Edited saved tx", restored.form.note)
        assertEquals("saved_88.jpg", restored.form.initialPersistedPictureFileName)
        assertNull(restored.activeSheet)
        assertNull(restored.consumedDraftTransactionId)
        assertEquals(88L, repository.lastLoadCatalogEditingTransactionId)

        dispatch(vm2, TransactionIntent.OpenInLegacyClicked)
        assertEquals(
            TransactionEffect.OpenLegacyEditor(TransactionLaunchArgs.Edit(88L)),
            vm2.effects.first(),
        )

        dispatch(vm2, TransactionIntent.SaveClicked)
        assertEquals(1, repository.savedCommands.size)
        assertEquals(88L, repository.savedCommands.single().existingTransactionId)
        assertEquals(TransactionEffect.SavedAndFinish(101L), vm2.effects.first())
    }

    @Test
    fun loadErrorPublishesRecoverableStateAndRetryLoadRecoversPreservingSavedForm() = transformTest {
        val handle = SavedStateHandle()
        val initialVm = createViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
        )
        dispatch(initialVm, TransactionIntent.UpdateNote("Preserve across retry"))

        repository.failLoadCatalogMessage = "Database locked"
        val failingVm = createViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
        )
        assertEquals(TransactionUiState.LoadError("Database locked"), failingVm.uiState.value)

        dispatch(failingVm, TransactionIntent.SaveClicked)
        assertTrue(repository.savedCommands.isEmpty())

        repository.failLoadCatalogMessage = null
        dispatch(failingVm, TransactionIntent.RetryLoad)
        val recovered = failingVm.uiState.value as TransactionUiState.Ready
        assertEquals("Preserve across retry", recovered.form.note)
    }

    @Test
    fun reopeningConsumedDraftUsesSavedTransactionReceiptAndNeverDeletedDraftReceipt() = transformTest {
        draftStore.savedByDraftId["draft-replaced-receipt"] = 88L
        val draftArgs = TransactionLaunchArgs.AiDraft(
            TransactionDraft(
                draftId = "draft-replaced-receipt",
                mode = TransactionMode.EXPENSE,
                amountMinor = 1500L,
                fromAccountId = 1L,
                dateTimeMillis = FIXED_NOW_MILLIS,
                stagedReceiptFileName = "receipt_a_deleted.jpg",
            )
        )
        mediaPort.resolveFilePathHandler = { file -> if (file == "receipt_b_saved.jpg") "/tmp/$file" else null }
        repository.editResult = TransactionEditLoadResult.Loaded(
            sampleRecord(id = 88L, attachedPicture = "receipt_b_saved.jpg")
        )

        val vmWithReplacement = createViewModel(draftArgs)
        val readyWithB = vmWithReplacement.uiState.value as TransactionUiState.Ready
        assertEquals("receipt_b_saved.jpg", readyWithB.form.attachedPictureFileName)
        assertEquals("receipt_b_saved.jpg", readyWithB.form.initialPersistedPictureFileName)
        assertTrue(readyWithB.form.newlyStagedPictureFiles.isEmpty())

        repository.editResult = TransactionEditLoadResult.Loaded(sampleRecord(id = 88L, attachedPicture = null))
        val vmWithoutReceipt = createViewModel(draftArgs)
        assertNull((vmWithoutReceipt.uiState.value as TransactionUiState.Ready).form.attachedPictureFileName)

        repository.editResult = TransactionEditLoadResult.NotFound(88L)
        val vmNotFoundDeleted = createViewModel(draftArgs)
        assertNull((vmNotFoundDeleted.uiState.value as TransactionUiState.Ready).form.attachedPictureFileName)
    }

    @Test
    fun processDeathDuringPendingOpenSavedTransactionResumesEditInsteadOfDraft() = transformTest {
        draftStore.savedByDraftId["draft-mid-edit-load"] = 88L
        val draftArgs = TransactionLaunchArgs.AiDraft(
            TransactionDraft(
                draftId = "draft-mid-edit-load",
                mode = TransactionMode.EXPENSE,
                amountMinor = 1500L,
                fromAccountId = 1L,
                dateTimeMillis = FIXED_NOW_MILLIS,
            )
        )
        val handle = SavedStateHandle()
        val vm1 = createViewModel(draftArgs, handle)
        assertEquals(
            ActiveSheet.ConsumedDraftPrompt(88L),
            (vm1.uiState.value as TransactionUiState.Ready).activeSheet,
        )

        val editGate = CompletableDeferred<TransactionEditLoadResult>()
        repository.pendingEditDeferred = editGate
        dispatch(vm1, TransactionIntent.OpenSavedTransaction(88L))
        assertEquals(TransactionUiState.Loading, vm1.uiState.value)

        repository.pendingEditDeferred = null
        repository.editResult = TransactionEditLoadResult.Loaded(sampleRecord(id = 88L))
        val vm2 = createViewModel(draftArgs, handle)
        val restored = vm2.uiState.value as TransactionUiState.Ready
        assertEquals(88L, restored.form.existingTransactionId)
        assertFalse(restored.form.isAiDraft)
        assertNull(restored.activeSheet)
        assertNull(restored.consumedDraftTransactionId)
    }

    @Test
    fun openSavedTransactionPublishesLoadingAndBlocksDuplicateOrSaveWhilePending() = transformTest {
        draftStore.savedByDraftId["draft-block-pending"] = 88L
        val draftArgs = TransactionLaunchArgs.AiDraft(
            TransactionDraft(
                draftId = "draft-block-pending",
                mode = TransactionMode.EXPENSE,
                amountMinor = 1500L,
                fromAccountId = 1L,
                dateTimeMillis = FIXED_NOW_MILLIS,
            )
        )
        val vm = createViewModel(draftArgs)
        assertEquals(
            ActiveSheet.ConsumedDraftPrompt(88L),
            (vm.uiState.value as TransactionUiState.Ready).activeSheet,
        )

        val editGate = CompletableDeferred<TransactionEditLoadResult>()
        repository.pendingEditDeferred = editGate
        dispatch(vm, TransactionIntent.OpenSavedTransaction(88L))
        assertEquals(TransactionUiState.Loading, vm.uiState.value)

        dispatch(vm, TransactionIntent.DuplicateDraftAnyway)
        dispatch(vm, TransactionIntent.SaveClicked)
        dispatch(vm, TransactionIntent.UpdateNote("Stale edit"))
        assertEquals(TransactionUiState.Loading, vm.uiState.value)
        assertTrue(repository.savedCommands.isEmpty())

        editGate.complete(TransactionEditLoadResult.Loaded(sampleRecord(id = 88L)))
        advanceAll()
        val loaded = vm.uiState.value as TransactionUiState.Ready
        assertEquals(88L, loaded.form.existingTransactionId)
        assertEquals("Legacy note", loaded.form.note)
    }

    private fun SaveTransactionCommand.amountCommandMinor(): Long = fromAmountMinor
}
