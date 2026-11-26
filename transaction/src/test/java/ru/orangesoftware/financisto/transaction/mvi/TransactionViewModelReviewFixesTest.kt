// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.appstate.transform.testing.TransformTestScope
import androidx.appstate.transform.testing.transformTest
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.transaction.domain.TransactionActionRunner
import ru.orangesoftware.financisto.transaction.domain.TransactionValidationResult
import ru.orangesoftware.financisto.transaction.domain.TransactionValidator
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.FIXED_NOW_MILLIS
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.defaultCatalog
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.fixedClock
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.multiCurrencyCatalog
import ru.orangesoftware.financisto.transaction.port.TransactionPorts

class TransactionViewModelReviewFixesTest {

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
    fun directAndCalculatorSecondaryAmountEditsCancelPendingLiveRateFetch() = transformTest {
        repository.catalog = multiCurrencyCatalog()
        val firstGate = CompletableDeferred<Result<Double>>()
        val secondGate = CompletableDeferred<Result<Double>>()
        val thirdGate = CompletableDeferred<Result<Double>>()
        val queue = ArrayDeque(listOf(firstGate, secondGate, thirdGate))
        exchangeRatePort.handler = { _, _ -> queue.removeFirst().await() }

        val vm = createViewModel(launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L))
        dispatch(vm, TransactionIntent.UpdatePrimaryAmount("100.00"))
        dispatch(vm, TransactionIntent.SelectCurrency(2L))

        dispatch(vm, TransactionIntent.FetchLiveRate)
        dispatch(vm, TransactionIntent.UpdateSecondaryAmount("90.00"))
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isFetchingRate)
        firstGate.complete(Result.success(5.0))
        advanceAll()
        val afterDirect = vm.uiState.value as TransactionUiState.Ready
        assertEquals(9000L, afterDirect.form.secondaryAmountMinor)
        assertEquals(0.9, afterDirect.form.exchangeRate, 1e-6)

        dispatch(vm, TransactionIntent.FetchLiveRate)
        dispatch(vm, TransactionIntent.ApplyCalculatorResult(CalculatorTarget.SECONDARY_AMOUNT, 85.0))
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isFetchingRate)
        secondGate.complete(Result.success(4.0))
        advanceAll()
        val afterCalc = vm.uiState.value as TransactionUiState.Ready
        assertEquals(8500L, afterCalc.form.secondaryAmountMinor)
        assertEquals(0.85, afterCalc.form.exchangeRate, 1e-6)

        dispatch(vm, TransactionIntent.FetchLiveRate)
        dispatch(vm, TransactionIntent.UpdateNote("Unrelated edit"))
        dispatch(vm, TransactionIntent.UpdatePrimaryAmount("200.00"))
        thirdGate.complete(Result.success(1.5))
        advanceAll()
        val afterApplied = vm.uiState.value as TransactionUiState.Ready
        assertFalse(afterApplied.isFetchingRate)
        assertEquals(1.5, afterApplied.form.exchangeRate, 1e-6)
        assertEquals(30000L, afterApplied.form.secondaryAmountMinor)
        assertEquals("Unrelated edit", afterApplied.form.note)
    }

    @Test
    fun externalCategoryCreationStaysBoundToRequestingSplitAcrossSwitchAndDeletion() = transformTest {
        val vm = createViewModel(launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L))
        dispatch(vm, TransactionIntent.UpdatePrimaryAmount("100.00"))
        dispatch(vm, TransactionIntent.ToggleSplitMode)
        val splitAId = (vm.uiState.value as TransactionUiState.Ready).form.splits.single().localId
        val splitB = EditableSplit(localId = -2L, categoryId = 10L, amountMinor = 4000L, isIncome = false)
        dispatch(vm, TransactionIntent.SaveSplitItem(splitB))

        dispatch(vm, TransactionIntent.OpenEditSplitEditor(splitAId))
        dispatch(vm, TransactionIntent.OpenCategoryBrowser(forSplitLocalId = splitAId))
        dispatch(vm, TransactionIntent.OpenExternalCategoryCreator)
        assertEquals(TransactionEffect.LaunchExternalCategoryCreator, vm.effects.first())

        dispatch(vm, TransactionIntent.DismissSheet)
        dispatch(vm, TransactionIntent.DismissSheet)
        dispatch(vm, TransactionIntent.OpenEditSplitEditor(splitB.localId))
        dispatch(vm, TransactionIntent.UpdateNote("Unrelated edit"))

        repository.catalog = defaultCatalog().copy(
            categories = defaultCatalog().categories + incomeCategory(id = 77L, title = "Bonus"),
        )
        dispatch(vm, TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 77L))

        val afterSwitch = vm.uiState.value as TransactionUiState.Ready
        assertEquals(77L, afterSwitch.form.splits.first { it.localId == splitAId }.categoryId)
        assertEquals(10L, afterSwitch.form.splits.first { it.localId == splitB.localId }.categoryId)
        assertEquals(splitB.localId, afterSwitch.activeSplitEditor?.split?.localId)
        assertEquals(10L, afterSwitch.activeSplitEditor?.split?.categoryId)
        assertEquals("Unrelated edit", afterSwitch.form.note)

        dispatch(vm, TransactionIntent.DismissSheet)
        dispatch(vm, TransactionIntent.OpenEditSplitEditor(splitAId))
        dispatch(vm, TransactionIntent.OpenExternalCategoryCreator)
        assertEquals(TransactionEffect.LaunchExternalCategoryCreator, vm.effects.first())
        dispatch(vm, TransactionIntent.DismissSheet)
        dispatch(vm, TransactionIntent.RemoveSplitItem(splitAId))
        dispatch(vm, TransactionIntent.OpenEditSplitEditor(splitB.localId))

        repository.catalog = repository.catalog.copy(
            categories = repository.catalog.categories + incomeCategory(id = 78L, title = "Gift"),
        )
        dispatch(vm, TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 78L))
        val afterDelete = vm.uiState.value as TransactionUiState.Ready
        assertEquals(10L, afterDelete.activeSplitEditor?.split?.categoryId)
        assertEquals(10L, afterDelete.form.splits.single().categoryId)
        assertTrue(afterDelete.catalog.categories.any { it.id == 78L })
    }

    @Test
    fun externalCategoryCreationSplitTargetSurvivesProcessDeath() = transformTest {
        val handle = SavedStateHandle()
        val vm = createViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
        )
        dispatch(vm, TransactionIntent.UpdatePrimaryAmount("100.00"))
        dispatch(vm, TransactionIntent.ToggleSplitMode)
        val splitB = EditableSplit(localId = -2L, categoryId = 10L, amountMinor = 4000L, isIncome = false)
        dispatch(vm, TransactionIntent.SaveSplitItem(splitB))

        dispatch(vm, TransactionIntent.OpenEditSplitEditor(splitB.localId))
        dispatch(vm, TransactionIntent.OpenExternalCategoryCreator)
        assertEquals(TransactionEffect.LaunchExternalCategoryCreator, vm.effects.first())

        val recreated = createViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
        )
        repository.catalog = repository.catalog.copy(
            categories = repository.catalog.categories + incomeCategory(id = 79L, title = "Refund"),
        )
        dispatch(recreated, TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 79L))

        val afterDeath = recreated.uiState.value as TransactionUiState.Ready
        assertEquals(79L, afterDeath.form.splits.first { it.localId == splitB.localId }.categoryId)
        assertTrue(afterDeath.form.isSplitMode)
        assertEquals(TransactionIds.SPLIT_CATEGORY_ID, afterDeath.form.categoryId)
    }

    @Test
    fun aiDraftCommitRunsMarkDraftSavedOnMidSaveCancellationAndBlocksDuplicateInsert() = transformTest {
        val saveGate = CompletableDeferred<Long>()
        repository.pendingSaveDeferred = saveGate
        val draftForm = TransactionFormState(
            aiDraftId = "draft-noncancellable",
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountMinor = 2500L,
            primaryAmountInput = "25.00",
            isFromNewBlankLaunch = false,
        )
        val ready = TransactionUiState.Ready(
            form = draftForm,
            initialFormSnapshot = draftForm,
            catalog = defaultCatalog(),
            prefs = TransactionFormPreferences(),
        )
        val command = (
            TransactionValidator.validateAndBuildCommand(draftForm, ready.catalog)
                as TransactionValidationResult.Valid
            ).command

        val saveJob = launch(testDispatcher) {
            TransactionActionRunner.executeSave(
                ready = ready,
                command = command,
                saveAndNew = false,
                ports = ports,
                clock = fixedClock,
            )
        }
        advanceUntilIdle()
        assertEquals(1, repository.savedCommands.size)
        saveJob.cancel()
        saveGate.complete(501L)
        saveJob.join()
        assertEquals(501L, draftStore.savedByDraftId["draft-noncancellable"])

        repository.pendingSaveDeferred = null
        val racedVm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-raced",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 2500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                )
            ),
        )
        draftStore.savedByDraftId["draft-raced"] = 88L
        dispatch(racedVm, TransactionIntent.SaveClicked)

        val afterRaced = racedVm.uiState.value as TransactionUiState.Ready
        assertFalse(afterRaced.isSaving)
        assertEquals(1, repository.savedCommands.size)
        assertEquals(ActiveSheet.ConsumedDraftPrompt(88L), afterRaced.activeSheet)
    }

    @Test
    fun receiptStagingNotifiesErrorsForActiveSelectionOnlyAndKeepsLastSelection() = transformTest {
        val vm = createViewModel(launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L))

        mediaPort.stageHandler = { null }
        dispatch(vm, TransactionIntent.AttachReceiptFromUri("content://null"))
        assertTrue(vm.effects.first() is TransactionEffect.ShowMessage)
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isStagingReceipt)

        mediaPort.stageHandler = { throw IllegalStateException("Storage unreadable") }
        dispatch(vm, TransactionIntent.AttachReceiptFromUri("content://err"))
        assertEquals(TransactionEffect.ShowMessage("Storage unreadable"), vm.effects.first())
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isStagingReceipt)

        mediaPort.stageHandler = { uri ->
            if (uri == "content://first") {
                vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://second"))
                "receipt_a.jpg"
            } else {
                "receipt_b.jpg"
            }
        }
        dispatch(vm, TransactionIntent.AttachReceiptFromUri("content://first"))

        val finalReady = vm.uiState.value as TransactionUiState.Ready
        assertFalse(finalReady.isStagingReceipt)
        assertEquals("receipt_b.jpg", finalReady.form.attachedPictureFileName)
        assertEquals(listOf("receipt_b.jpg"), finalReady.form.newlyStagedPictureFiles)
        assertEquals(listOf("receipt_a.jpg"), mediaPort.deletedFiles)

        mediaPort.stageHandler = { uri ->
            if (uri == "content://cancelled-fail") {
                vm.onIntent(TransactionIntent.ClearFormClicked)
                null
            } else {
                "receipt_c.jpg"
            }
        }
        dispatch(vm, TransactionIntent.AttachReceiptFromUri("content://cancelled-fail"))
        dispatch(vm, TransactionIntent.RequestCameraPhoto)
        assertEquals(TransactionEffect.LaunchCameraCapture, vm.effects.first())
    }

    @Test
    fun untouchedAiDraftWithoutReceiptHasUnsavedChangesAndPromptsOnBack() = transformTest {
        val vm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-no-receipt",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 1500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                    stagedReceiptFileName = null,
                )
            ),
        )

        val ready = vm.uiState.value as TransactionUiState.Ready
        assertTrue(ready.hasUnsavedChanges)
        assertFalse(vm.hasUnsavedUserEdits())

        dispatch(vm, TransactionIntent.CancelOrBackClicked)
        assertEquals(
            ActiveSheet.DiscardConfirmation,
            (vm.uiState.value as TransactionUiState.Ready).activeSheet,
        )
        assertFalse(vm.hasUnsavedUserEdits())
    }

    @Test
    fun receiptAttachFailedEmitsMessageOnceOnInitialLoadAndNotOnRestore() = transformTest {
        val handle = SavedStateHandle()
        val args = TransactionLaunchArgs.AiDraft(
            TransactionDraft(
                draftId = "draft-receipt-attach-failed",
                mode = TransactionMode.EXPENSE,
                amountMinor = 1500L,
                fromAccountId = 1L,
                dateTimeMillis = FIXED_NOW_MILLIS,
                receiptAttachFailed = true,
            )
        )
        val vm1 = createViewModel(args, handle)
        assertTrue(vm1.uiState.value is TransactionUiState.Ready)
        val vm1Effects = mutableListOf<TransactionEffect>()
        val collect1 = launch(testDispatcher) { vm1.effects.collect { vm1Effects.add(it) } }
        advance()
        collect1.cancel()
        assertEquals(listOf(TransactionEffect.ShowMessage("Could not attach receipt image.")), vm1Effects)

        val vm2 = createViewModel(args, handle)
        assertTrue(vm2.uiState.value is TransactionUiState.Ready)
        val vm2Effects = mutableListOf<TransactionEffect>()
        val collect2 = launch(testDispatcher) { vm2.effects.collect { vm2Effects.add(it) } }
        advance()
        collect2.cancel()
        assertTrue(vm2Effects.isEmpty())

        val okVm = createViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-receipt-attach-ok",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 1500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                    receiptAttachFailed = false,
                )
            ),
        )
        val okEffects = mutableListOf<TransactionEffect>()
        val collectOk = launch(testDispatcher) { okVm.effects.collect { okEffects.add(it) } }
        advance()
        collectOk.cancel()
        assertTrue(okEffects.isEmpty())
    }

    private fun incomeCategory(id: Long, title: String): CategoryOption = CategoryOption(
        id = id,
        parentId = 0L,
        title = title,
        fullPath = title,
        level = 1,
        isIncome = true,
    )
}
