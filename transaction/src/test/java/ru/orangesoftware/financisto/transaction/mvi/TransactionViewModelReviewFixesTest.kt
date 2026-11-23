// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelReviewFixesTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeTransactionRepository
    private lateinit var draftStore: FakeDraftSessionStore
    private lateinit var mediaPort: FakeReceiptMediaPort
    private lateinit var exchangeRatePort: FakeExchangeRatePort
    private lateinit var ports: TransactionPorts

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeTransactionRepository(defaultCatalog())
        draftStore = FakeDraftSessionStore()
        mediaPort = FakeReceiptMediaPort()
        exchangeRatePort = FakeExchangeRatePort()
        ports = TransactionTestFakes.createPorts(repository, draftStore, exchangeRatePort, mediaPort)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun directAndCalculatorSecondaryAmountEditsCancelPendingLiveRateFetch() = runTest {
        repository.catalog = multiCurrencyCatalog()
        val firstGate = CompletableDeferred<Result<Double>>()
        val secondGate = CompletableDeferred<Result<Double>>()
        val thirdGate = CompletableDeferred<Result<Double>>()
        val queue = ArrayDeque(listOf(firstGate, secondGate, thirdGate))
        exchangeRatePort.handler = { _, _ -> queue.removeFirst().await() }

        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("100.00"))
        vm.onIntent(TransactionIntent.SelectCurrency(2L))

        vm.onIntent(TransactionIntent.FetchLiveRate)
        vm.onIntent(TransactionIntent.UpdateSecondaryAmount("90.00"))
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isFetchingRate)
        firstGate.complete(Result.success(5.0))
        val afterDirect = vm.uiState.value as TransactionUiState.Ready
        assertEquals(9000L, afterDirect.form.secondaryAmountMinor)
        assertEquals(0.9, afterDirect.form.exchangeRate, 1e-6)

        vm.onIntent(TransactionIntent.FetchLiveRate)
        vm.onIntent(TransactionIntent.ApplyCalculatorResult(CalculatorTarget.SECONDARY_AMOUNT, 85.0))
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isFetchingRate)
        secondGate.complete(Result.success(4.0))
        val afterCalc = vm.uiState.value as TransactionUiState.Ready
        assertEquals(8500L, afterCalc.form.secondaryAmountMinor)
        assertEquals(0.85, afterCalc.form.exchangeRate, 1e-6)

        vm.onIntent(TransactionIntent.FetchLiveRate)
        vm.onIntent(TransactionIntent.UpdateNote("Unrelated edit"))
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("200.00"))
        thirdGate.complete(Result.success(1.5))
        val afterApplied = vm.uiState.value as TransactionUiState.Ready
        assertFalse(afterApplied.isFetchingRate)
        assertEquals(1.5, afterApplied.form.exchangeRate, 1e-6)
        assertEquals(30000L, afterApplied.form.secondaryAmountMinor)
        assertEquals("Unrelated edit", afterApplied.form.note)
    }

    @Test
    fun externalCategoryCreationStaysBoundToRequestingSplitAcrossSwitchAndDeletion() = runTest {
        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("100.00"))
        vm.onIntent(TransactionIntent.ToggleSplitMode)
        val splitAId = (vm.uiState.value as TransactionUiState.Ready).form.splits.single().localId
        val splitB = EditableSplit(localId = -2L, categoryId = 10L, amountMinor = 4000L, isIncome = false)
        vm.onIntent(TransactionIntent.SaveSplitItem(splitB))

        vm.onIntent(TransactionIntent.OpenEditSplitEditor(splitAId))
        vm.onIntent(TransactionIntent.OpenCategoryBrowser(forSplitLocalId = splitAId))
        vm.onIntent(TransactionIntent.OpenExternalCategoryCreator)
        assertEquals(TransactionEffect.LaunchExternalCategoryCreator, vm.effects.first())

        vm.onIntent(TransactionIntent.DismissSheet)
        vm.onIntent(TransactionIntent.DismissSheet)
        vm.onIntent(TransactionIntent.OpenEditSplitEditor(splitB.localId))
        vm.onIntent(TransactionIntent.UpdateNote("Unrelated edit"))

        repository.catalog = defaultCatalog().copy(
            categories = defaultCatalog().categories + incomeCategory(id = 77L, title = "Bonus"),
        )
        vm.onIntent(TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 77L))

        val afterSwitch = vm.uiState.value as TransactionUiState.Ready
        assertEquals(77L, afterSwitch.form.splits.first { it.localId == splitAId }.categoryId)
        assertEquals(10L, afterSwitch.form.splits.first { it.localId == splitB.localId }.categoryId)
        assertEquals(splitB.localId, afterSwitch.activeSplitEditor?.split?.localId)
        assertEquals(10L, afterSwitch.activeSplitEditor?.split?.categoryId)
        assertEquals("Unrelated edit", afterSwitch.form.note)

        vm.onIntent(TransactionIntent.DismissSheet)
        vm.onIntent(TransactionIntent.OpenEditSplitEditor(splitAId))
        vm.onIntent(TransactionIntent.OpenExternalCategoryCreator)
        assertEquals(TransactionEffect.LaunchExternalCategoryCreator, vm.effects.first())
        vm.onIntent(TransactionIntent.DismissSheet)
        vm.onIntent(TransactionIntent.RemoveSplitItem(splitAId))
        vm.onIntent(TransactionIntent.OpenEditSplitEditor(splitB.localId))

        repository.catalog = repository.catalog.copy(
            categories = repository.catalog.categories + incomeCategory(id = 78L, title = "Gift"),
        )
        vm.onIntent(TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 78L))
        val afterDelete = vm.uiState.value as TransactionUiState.Ready
        assertEquals(10L, afterDelete.activeSplitEditor?.split?.categoryId)
        assertEquals(10L, afterDelete.form.splits.single().categoryId)
        assertTrue(afterDelete.catalog.categories.any { it.id == 78L })
    }

    @Test
    fun externalCategoryCreationSplitTargetSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("100.00"))
        vm.onIntent(TransactionIntent.ToggleSplitMode)
        val splitB = EditableSplit(localId = -2L, categoryId = 10L, amountMinor = 4000L, isIncome = false)
        vm.onIntent(TransactionIntent.SaveSplitItem(splitB))

        vm.onIntent(TransactionIntent.OpenEditSplitEditor(splitB.localId))
        vm.onIntent(TransactionIntent.OpenExternalCategoryCreator)
        assertEquals(TransactionEffect.LaunchExternalCategoryCreator, vm.effects.first())

        val recreated = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        repository.catalog = repository.catalog.copy(
            categories = repository.catalog.categories + incomeCategory(id = 79L, title = "Refund"),
        )
        recreated.onIntent(TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 79L))

        val afterDeath = recreated.uiState.value as TransactionUiState.Ready
        assertEquals(79L, afterDeath.form.splits.first { it.localId == splitB.localId }.categoryId)
        assertTrue(afterDeath.form.isSplitMode)
        assertEquals(TransactionIds.SPLIT_CATEGORY_ID, afterDeath.form.categoryId)
    }

    @Test
    fun aiDraftCommitRunsMarkDraftSavedOnMidSaveCancellationAndBlocksDuplicateInsert() = runTest(testDispatcher) {
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

        val saveJob = launch {
            TransactionActionRunner.executeSave(
                ready = ready,
                command = command,
                saveAndNew = false,
                ports = ports,
                clock = fixedClock,
            )
        }
        assertEquals(1, repository.savedCommands.size)
        saveJob.cancel()
        saveGate.complete(501L)
        saveJob.join()
        assertEquals(501L, draftStore.savedByDraftId["draft-noncancellable"])

        repository.pendingSaveDeferred = null
        val racedVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-raced",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 2500L,
                    fromAccountId = 1L,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                )
            ),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        draftStore.savedByDraftId["draft-raced"] = 88L
        racedVm.onIntent(TransactionIntent.SaveClicked)

        val afterRaced = racedVm.uiState.value as TransactionUiState.Ready
        assertFalse(afterRaced.isSaving)
        assertEquals(1, repository.savedCommands.size)
        assertEquals(ActiveSheet.ConsumedDraftPrompt(88L), afterRaced.activeSheet)
    }

    @Test
    fun receiptStagingNotifiesErrorsForActiveSelectionOnlyAndKeepsLastSelection() = runTest {
        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )

        mediaPort.stageHandler = { null }
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://null"))
        assertTrue(vm.effects.first() is TransactionEffect.ShowMessage)
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isStagingReceipt)

        mediaPort.stageHandler = { throw IllegalStateException("Storage unreadable") }
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://err"))
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
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://first"))

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
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://cancelled-fail"))
        vm.onIntent(TransactionIntent.RequestCameraPhoto)
        assertEquals(TransactionEffect.LaunchCameraCapture, vm.effects.first())
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
