// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.transaction.domain.TransactionRateCoordinator
import ru.orangesoftware.financisto.transaction.domain.TransactionReducer
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.FIXED_NOW_MILLIS
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.defaultCatalog
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.fixedClock
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.multiCurrencyCatalog
import ru.orangesoftware.financisto.transaction.mvi.TransactionTestFakes.sampleRecord
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelConcurrencyTest {

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
    fun crossCurrencyAiTransferDraftTriggersStoredRateLookupInsteadOfFabricatingOne() = runTest {
        repository.catalog = multiCurrencyCatalog()
        repository.storedRates[1L to 2L] = 0.85

        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-fx-transfer",
                    mode = TransactionMode.TRANSFER,
                    amountMinor = 10000L,
                    fromAccountId = 1L,
                    toAccountId = 2L,
                    toAmountMinor = null,
                    dateTimeMillis = FIXED_NOW_MILLIS,
                )
            ),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )

        val ready = vm.uiState.value as TransactionUiState.Ready
        assertEquals(listOf(1L to 2L), repository.requestedRatePairs)
        assertEquals(0.85, ready.form.exchangeRate, 1e-6)
        assertEquals(8500L, ready.form.secondaryAmountMinor)
        assertEquals("85.00", ready.form.secondaryAmountInput)
        assertFalse(vm.hasUnsavedUserEdits())
    }

    @Test
    fun isSavingGuardsAgainstMutationsResavesAndDiscard() = runTest {
        val saveGate = CompletableDeferred<Long>()
        repository.pendingSaveDeferred = saveGate

        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("30.00"))
        vm.onIntent(TransactionIntent.AttachCapturedPhotoFile("pending_photo.jpg"))
        vm.onIntent(TransactionIntent.SaveClicked)

        val whileSaving = vm.uiState.value as TransactionUiState.Ready
        assertTrue(whileSaving.isSaving)

        vm.onIntent(TransactionIntent.UpdateNote("Mutated mid-save"))
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("99.00"))
        vm.onIntent(TransactionIntent.SaveClicked)
        vm.onIntent(TransactionIntent.CancelOrBackClicked)
        vm.onIntent(TransactionIntent.ConfirmDiscard)
        vm.cleanupStagedReceipts()

        val stillSaving = vm.uiState.value as TransactionUiState.Ready
        assertTrue(stillSaving.isSaving)
        assertEquals("", stillSaving.form.note)
        assertEquals(3000L, stillSaving.form.primaryAmountMinor)
        assertNull(stillSaving.activeSheet)
        assertTrue(mediaPort.deletedFiles.isEmpty())
        assertEquals(1, repository.savedCommands.size)

        saveGate.complete(501L)
        assertEquals(TransactionEffect.SavedAndFinish(501L), vm.effects.first())
    }

    @Test
    fun supersededLiveRateFetchIsCancelledAndStaleCurrencyPairIsRejected() = runTest {
        repository.catalog = multiCurrencyCatalog()
        val firstFetch = CompletableDeferred<Result<Double>>()
        val secondFetch = CompletableDeferred<Result<Double>>()
        val fetchQueue = ArrayDeque(listOf(firstFetch, secondFetch))
        exchangeRatePort.handler = { _, _ -> fetchQueue.removeFirst().await() }

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
        assertTrue((vm.uiState.value as TransactionUiState.Ready).isFetchingRate)
        vm.onIntent(TransactionIntent.FetchLiveRate)

        secondFetch.complete(Result.success(1.2))
        firstFetch.complete(Result.success(9.99))

        val afterSecond = vm.uiState.value as TransactionUiState.Ready
        assertFalse(afterSecond.isFetchingRate)
        assertEquals(1.2, afterSecond.form.exchangeRate, 1e-6)
        assertEquals(12000L, afterSecond.form.secondaryAmountMinor)

        val staleFetch = CompletableDeferred<Result<Double>>()
        exchangeRatePort.handler = { _, _ -> staleFetch.await() }
        vm.onIntent(TransactionIntent.FetchLiveRate)
        assertTrue((vm.uiState.value as TransactionUiState.Ready).isFetchingRate)

        vm.onIntent(TransactionIntent.SelectCurrency(3L))
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isFetchingRate)

        staleFetch.complete(Result.success(7.77))
        val afterSwitch = vm.uiState.value as TransactionUiState.Ready
        assertEquals(3L, afterSwitch.primaryCurrency?.id)
        assertFalse(afterSwitch.form.exchangeRate == 7.77)
    }

    @Test
    fun nonCancellableStalePairRateResultIsRejectedByPairGuard() = runTest {
        repository.catalog = multiCurrencyCatalog()
        lateinit var vm: TransactionViewModel
        exchangeRatePort.handler = { _, _ ->
            vm.onIntent(TransactionIntent.SelectCurrency(3L))
            Result.success(4.2)
        }

        vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("100.00"))
        vm.onIntent(TransactionIntent.SelectCurrency(2L))
        vm.onIntent(TransactionIntent.FetchLiveRate)

        val ready = vm.uiState.value as TransactionUiState.Ready
        assertEquals(3L, ready.primaryCurrency?.id)
        assertFalse(ready.isFetchingRate)
        assertEquals(0.0, ready.form.exchangeRate, 1e-6)
    }

    @Test
    fun externalEntityCreatedAndFailedSaveReduceAgainstLatestState() = runTest {
        val catalogGate = CompletableDeferred<TransactionCatalog>()
        repository.pendingCatalogDeferred = catalogGate

        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 55L))
        vm.onIntent(TransactionIntent.UpdateNote("Keep this note"))

        val updatedCatalog = defaultCatalog().copy(
            categories = defaultCatalog().categories + CategoryOption(
                id = 55L,
                parentId = 0L,
                title = "Books",
                fullPath = "Books",
                level = 1,
                isIncome = false,
            )
        )
        catalogGate.complete(updatedCatalog)

        val afterReload = vm.uiState.value as TransactionUiState.Ready
        assertEquals(55L, afterReload.form.categoryId)
        assertEquals("Keep this note", afterReload.form.note)

        repository.failSaveMessage = "Disk full"
        vm.onIntent(TransactionIntent.SaveClicked)
        val afterFail = vm.uiState.value as TransactionUiState.Ready
        assertFalse(afterFail.isSaving)
        assertEquals(TransactionEffect.ShowMessage("Disk full"), vm.effects.first())
    }

    @Test
    fun externalCategoryCreatedInsideSplitBrowserUpdatesSplitDraftAndReturnsToSplitEditor() = runTest {
        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("50.00"))
        vm.onIntent(TransactionIntent.ToggleSplitMode)
        vm.onIntent(TransactionIntent.OpenNewSplitEditor)
        val newSplitId = ((vm.uiState.value as TransactionUiState.Ready).activeSheet as ActiveSheet.SplitItemEditor)
            .split
            .localId
        vm.onIntent(TransactionIntent.OpenCategoryBrowser(forSplitLocalId = newSplitId))

        repository.catalog = defaultCatalog().copy(
            categories = defaultCatalog().categories + CategoryOption(
                id = 77L,
                parentId = 0L,
                title = "Bonus",
                fullPath = "Bonus",
                level = 1,
                isIncome = true,
            )
        )
        vm.onIntent(TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, 77L))

        val ready = vm.uiState.value as TransactionUiState.Ready
        assertTrue(ready.form.isSplitMode)
        val returnedEditor = ready.activeSheet as ActiveSheet.SplitItemEditor
        assertEquals(newSplitId, returnedEditor.split.localId)
        assertEquals(77L, returnedEditor.split.categoryId)
        assertTrue(returnedEditor.split.isIncome)
    }

    @Test
    fun stateAndSavedStateHandleMutationsStayConfinedToMainDispatcher() = runTest {
        val ioExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-io-thread") }
        val mainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-main-thread") }
        val dedicatedIo = ioExecutor.asCoroutineDispatcher()
        val dedicatedMain = mainExecutor.asCoroutineDispatcher()
        val catalogThread = AtomicReference<String>()
        val publishThread = AtomicReference<String>()
        val startCatalogGate = CompletableDeferred<Unit>()
        val readySignal = CompletableDeferred<Unit>()
        try {
            Dispatchers.setMain(dedicatedMain)
            repository.onLoadCatalog = {
                startCatalogGate.await()
                catalogThread.set(Thread.currentThread().name)
            }
            val handle = SavedStateHandle()
            val vm = withContext(dedicatedMain) {
                TransactionViewModel(
                    launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
                    savedStateHandle = handle,
                    ports = ports,
                    ioDispatcher = dedicatedIo,
                    clock = fixedClock,
                )
            }
            val collectorJob = launch(testDispatcher) {
                vm.uiState.collect { state ->
                    if (state is TransactionUiState.Ready) {
                        publishThread.set(Thread.currentThread().name)
                        readySignal.complete(Unit)
                    }
                }
            }
            startCatalogGate.complete(Unit)
            readySignal.await()
            collectorJob.cancel()
            assertTrue(catalogThread.get().orEmpty().startsWith("test-io-thread"))
            assertTrue(publishThread.get().orEmpty().startsWith("test-main-thread"))
            assertTrue(handle.get<TransactionFormState>("transaction_form_state") != null)
            assertTrue(vm.uiState.value is TransactionUiState.Ready)
        } finally {
            Dispatchers.setMain(testDispatcher)
            dedicatedIo.close()
            dedicatedMain.close()
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }

    @Test
    fun staleRateResultsForSameCurrencyPairAreRejectedAcrossFormGenerations() = runTest {
        repository.catalog = multiCurrencyCatalog()
        lateinit var vm: TransactionViewModel
        exchangeRatePort.handler = { _, _ ->
            vm.onIntent(TransactionIntent.ClearFormClicked)
            vm.onIntent(TransactionIntent.SelectAccount(2L, isDestination = true))
            Result.success(9.99)
        }

        vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L, initialMode = TransactionMode.TRANSFER),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.SelectAccount(2L, isDestination = true))
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("100.00"))
        vm.onIntent(TransactionIntent.FetchLiveRate)

        val afterClear = vm.uiState.value as TransactionUiState.Ready
        assertEquals(1L, afterClear.primaryCurrency?.id)
        assertEquals(2L, afterClear.secondaryCurrency?.id)
        assertFalse(afterClear.isFetchingRate)
        assertEquals(0.0, afterClear.form.exchangeRate, 1e-6)

        var triggeredReset = false
        repository.rateHandler = { _, _ ->
            if (!triggeredReset) {
                triggeredReset = true
                vm.onIntent(TransactionIntent.ClearFormClicked)
                vm.onIntent(TransactionIntent.SelectCurrency(2L))
                7.5
            } else {
                null
            }
        }
        vm.onIntent(TransactionIntent.SwitchMode(TransactionMode.EXPENSE))
        vm.onIntent(TransactionIntent.SelectCurrency(2L))

        val afterStoredClear = vm.uiState.value as TransactionUiState.Ready
        assertEquals(2L, afterStoredClear.primaryCurrency?.id)
        assertEquals(1L, afterStoredClear.secondaryCurrency?.id)
        assertEquals(0.0, afterStoredClear.form.exchangeRate, 1e-6)
    }

    @Test
    fun receiptStagingBlocksSaveAndDeletesOrphanedFileOnFormResetAndDiscard() = runTest {
        val stageGate = CompletableDeferred<String?>()
        mediaPort.stageHandler = { stageGate.await() }

        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("20.00"))
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://gallery/1"))
        assertTrue((vm.uiState.value as TransactionUiState.Ready).isStagingReceipt)

        vm.onIntent(TransactionIntent.SaveClicked)
        assertTrue(repository.savedCommands.isEmpty())
        assertTrue(vm.effects.first() is TransactionEffect.ShowMessage)

        vm.onIntent(TransactionIntent.ClearFormClicked)
        assertFalse((vm.uiState.value as TransactionUiState.Ready).isStagingReceipt)
        stageGate.complete("late_staged_1.jpg")
        assertNull((vm.uiState.value as TransactionUiState.Ready).form.attachedPictureFileName)

        mediaPort.stageHandler = {
            vm.onIntent(TransactionIntent.ClearFormClicked)
            "orphan_on_clear.jpg"
        }
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://gallery/2"))
        assertNull((vm.uiState.value as TransactionUiState.Ready).form.attachedPictureFileName)
        assertEquals(listOf("orphan_on_clear.jpg"), mediaPort.deletedFiles)

        repository.editResult = TransactionEditLoadResult.Loaded(sampleRecord(id = 77L))
        mediaPort.stageHandler = {
            vm.onIntent(TransactionIntent.OpenSavedTransaction(77L))
            "orphan_on_open_saved.jpg"
        }
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://gallery/3"))
        assertEquals(77L, (vm.uiState.value as TransactionUiState.Ready).form.existingTransactionId)
        assertNull((vm.uiState.value as TransactionUiState.Ready).form.attachedPictureFileName)
        assertEquals(listOf("orphan_on_clear.jpg", "orphan_on_open_saved.jpg"), mediaPort.deletedFiles)

        mediaPort.stageHandler = {
            vm.onIntent(TransactionIntent.ConfirmDiscard)
            "orphan_on_discard.jpg"
        }
        vm.onIntent(TransactionIntent.AttachReceiptFromUri("content://gallery/4"))
        assertEquals(
            listOf("orphan_on_clear.jpg", "orphan_on_open_saved.jpg", "orphan_on_discard.jpg"),
            mediaPort.deletedFiles,
        )
        assertEquals(TransactionEffect.CloseCancelled, vm.effects.first())
    }

    @Test
    fun hasUnsavedUserEditsIsTrueWhileStagingReceiptOrSavingUntouchedEdit() = runTest {
        val stageGate = CompletableDeferred<String?>()
        mediaPort.stageHandler = { stageGate.await() }
        val blankVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        assertFalse(blankVm.hasUnsavedUserEdits())
        blankVm.onIntent(TransactionIntent.AttachReceiptFromUri("content://gallery/in-flight"))
        assertTrue((blankVm.uiState.value as TransactionUiState.Ready).isStagingReceipt)
        assertTrue(blankVm.hasUnsavedUserEdits())
        stageGate.complete(null)
        assertFalse((blankVm.uiState.value as TransactionUiState.Ready).isStagingReceipt)
        assertFalse(blankVm.hasUnsavedUserEdits())

        val saveGate = CompletableDeferred<Long>()
        repository.editResult = TransactionEditLoadResult.Loaded(sampleRecord(id = 77L))
        repository.pendingSaveDeferred = saveGate
        val editVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.Edit(77L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )
        assertFalse(editVm.hasUnsavedUserEdits())
        editVm.onIntent(TransactionIntent.SaveClicked)
        assertTrue((editVm.uiState.value as TransactionUiState.Ready).isSaving)
        assertTrue(editVm.hasUnsavedUserEdits())
        saveGate.complete(77L)
        assertEquals(TransactionEffect.SavedAndFinish(77L), editVm.effects.first())
    }

    @Test
    fun rateCoordinatorPairGuardRejectsMismatchedPairWithoutInvalidatingRequestId() = runTest(testDispatcher) {
        val catalog = multiCurrencyCatalog()
        val initialForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            originalCurrencyId = 2L,
            primaryAmountInput = "100.00",
            primaryAmountMinor = 10000L,
        )
        var currentState = TransactionUiState.Ready(
            form = initialForm,
            initialFormSnapshot = initialForm,
            catalog = catalog,
            prefs = TransactionFormPreferences(),
        )
        val effects = mutableListOf<TransactionEffect>()
        val coordinator = TransactionRateCoordinator(
            scope = this,
            ports = ports,
            currentReady = { currentState },
            publishReady = { currentState = it },
            sendEffect = { effects.add(it) },
            ioDispatcher = testDispatcher,
            currentGeneration = { 1L },
        )

        val mismatchGate = CompletableDeferred<Result<Double>>()
        exchangeRatePort.handler = { _, _ -> mismatchGate.await() }
        coordinator.fetchLiveRate(currentState)
        assertTrue(currentState.isFetchingRate)

        currentState = TransactionReducer.reduce(
            currentState,
            TransactionIntent.SelectCurrency(3L),
        ).copy(isFetchingRate = true)
        mismatchGate.complete(Result.success(4.2))

        assertEquals(3L, currentState.primaryCurrency?.id)
        assertFalse(currentState.isFetchingRate)
        assertEquals(0.0, currentState.form.exchangeRate, 1e-6)
        assertEquals(0L, currentState.form.secondaryAmountMinor)

        val matchGate = CompletableDeferred<Result<Double>>()
        exchangeRatePort.handler = { _, _ -> matchGate.await() }
        coordinator.fetchLiveRate(currentState)
        matchGate.complete(Result.success(1.25))

        assertFalse(currentState.isFetchingRate)
        assertEquals(1.25, currentState.form.exchangeRate, 1e-6)
        assertEquals(12500L, currentState.form.secondaryAmountMinor)
        assertTrue(effects.isEmpty())
    }
}
