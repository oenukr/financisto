// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.LoadedTransactionRecord
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import ru.orangesoftware.financisto.transaction.port.TransactionPreferencesPort
import ru.orangesoftware.financisto.transaction.port.TransactionRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeTransactionRepository
    private lateinit var draftStore: FakeDraftSessionStore
    private lateinit var mediaPort: FakeReceiptMediaPort
    private lateinit var ports: TransactionPorts

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeTransactionRepository(defaultCatalog())
        draftStore = FakeDraftSessionStore()
        mediaPort = FakeReceiptMediaPort()
        ports = TransactionPorts(
            repository = repository,
            preferencesPort = object : TransactionPreferencesPort {
                override fun readFormPreferences(): TransactionFormPreferences = TransactionFormPreferences()
            },
            draftSessionStore = draftStore,
            exchangeRatePort = object : ExchangeRatePort {
                override suspend fun fetchLiveRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double> =
                    Result.success(1.25)
            },
            receiptMediaPort = mediaPort,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun blankNewSaveAndSaveAndNewWorkAndResetForm() = runTest {
        val vm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L, initialMode = TransactionMode.EXPENSE),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
        )
        val initial = vm.uiState.value as TransactionUiState.Ready
        assertEquals(1L, initial.form.fromAccountId)

        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("25.50"))
        vm.onIntent(TransactionIntent.UpdateNote("Lunch"))
        vm.onIntent(TransactionIntent.SaveAndNewClicked)

        assertEquals(1, repository.savedCommands.size)
        assertEquals(-2550L, repository.savedCommands[0].amountCommandMinor())
        val afterReset = vm.uiState.value as TransactionUiState.Ready
        assertEquals(1L, afterReset.form.fromAccountId)
        assertEquals(0L, afterReset.form.primaryAmountMinor)
        assertEquals("", afterReset.form.note)
        assertTrue(vm.effects.first() is TransactionEffect.ShowMessage)

        vm.onIntent(TransactionIntent.UpdatePrimaryAmount("10.00"))
        vm.onIntent(TransactionIntent.SaveClicked)
        val finishEffect = vm.effects.first()
        assertTrue(finishEffect is TransactionEffect.SavedAndFinish)
        assertEquals(102L, (finishEffect as TransactionEffect.SavedAndFinish).transactionId)
    }

    @Test
    fun editHandlesNotFoundLegacyFallbackAndInactiveEntities() = runTest {
        repository.editResult = TransactionEditLoadResult.NotFound(999L)
        val notFoundVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.Edit(999L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
        )
        assertEquals(TransactionUiState.NotFound(999L), notFoundVm.uiState.value)

        repository.editResult = TransactionEditLoadResult.RequiresLegacyFallback(50L, isTransfer = false)
        val legacyVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.Edit(50L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
        )
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
            LoadedTransactionRecord(
                id = 77L,
                mode = TransactionMode.EXPENSE,
                fromAccountId = 99L,
                toAccountId = 0L,
                categoryId = 10L,
                payeeId = 300L,
                projectId = 400L,
                locationId = 0L,
                fromAmountMinor = -4200L,
                toAmountMinor = 0L,
                originalCurrencyId = 0L,
                originalFromAmountMinor = 0L,
                note = "Legacy note",
                dateTimeMillis = 1_700_000_000_000L,
                status = TransactionStatusOption.CLEARED,
                attachedPicture = null,
                isCCardPayment = false,
                splits = emptyList(),
            )
        )
        val loadedVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.Edit(77L),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
        )
        val ready = loadedVm.uiState.value as TransactionUiState.Ready
        assertEquals(99L, ready.form.fromAccountId)
        assertEquals(300L, ready.form.payeeId)
        assertEquals(400L, ready.form.projectId)
        assertFalse(ready.hasUnsavedChanges)
    }

    @Test
    fun aiDraftHandlesConsumedPromptAndStagedReceiptCleanupOnDiscardVsSave() = runTest {
        draftStore.savedByDraftId["draft-consumed"] = 88L
        repository.editResult = TransactionEditLoadResult.Loaded(
            LoadedTransactionRecord(
                id = 88L,
                mode = TransactionMode.EXPENSE,
                fromAccountId = 1L,
                toAccountId = 0L,
                categoryId = 10L,
                payeeId = 0L,
                projectId = 0L,
                locationId = 0L,
                fromAmountMinor = -1500L,
                toAmountMinor = 0L,
                originalCurrencyId = 0L,
                originalFromAmountMinor = 0L,
                note = "Saved earlier",
                dateTimeMillis = 1_700_000_000_000L,
                status = TransactionStatusOption.UR,
                attachedPicture = null,
                isCCardPayment = false,
                splits = emptyList(),
            )
        )
        val consumedVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-consumed",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 1500L,
                    fromAccountId = 1L,
                    dateTimeMillis = 1_700_000_000_000L,
                    stagedReceiptFileName = "staged_a.jpg",
                )
            ),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
        )
        val consumedState = consumedVm.uiState.value as TransactionUiState.Ready
        assertEquals(ActiveSheet.ConsumedDraftPrompt(88L), consumedState.activeSheet)

        consumedVm.onIntent(TransactionIntent.CancelOrBackClicked)
        assertEquals(
            ActiveSheet.DiscardConfirmation,
            (consumedVm.uiState.value as TransactionUiState.Ready).activeSheet,
        )
        consumedVm.onIntent(TransactionIntent.ConfirmDiscard)
        assertEquals(listOf("staged_a.jpg"), mediaPort.deletedFiles)
        assertTrue(consumedVm.effects.first() is TransactionEffect.CloseCancelled)

        val saveVm = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "draft-fresh",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 2500L,
                    fromAccountId = 1L,
                    dateTimeMillis = 1_700_000_000_000L,
                    stagedReceiptFileName = "staged_initial.jpg",
                )
            ),
            savedStateHandle = SavedStateHandle(),
            ports = ports,
            ioDispatcher = testDispatcher,
        )
        saveVm.onIntent(TransactionIntent.AttachCapturedPhotoFile("staged_replacement.jpg"))
        saveVm.onIntent(TransactionIntent.SaveClicked)

        assertEquals(listOf("staged_initial.jpg"), mediaPort.deletedFiles.drop(1))
        assertEquals(101L, draftStore.savedByDraftId["draft-fresh"])
        assertEquals("staged_replacement.jpg", repository.savedCommands.last().attachedPicture)
    }

    @Test
    fun pinMaskAndSavedStateHandleRestoreWorkAcrossProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val vm1 = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
            ports = ports,
            ioDispatcher = testDispatcher,
        )
        vm1.onIntent(TransactionIntent.UpdatePrimaryAmount("75.25"))
        vm1.onIntent(TransactionIntent.UpdateNote("Before process death"))

        vm1.onPinStateChanged(unlocked = false)
        assertEquals(TransactionUiState.PinLockedMask, vm1.uiState.value)
        vm1.onPinStateChanged(unlocked = true)
        assertEquals("Before process death", (vm1.uiState.value as TransactionUiState.Ready).form.note)

        val vm2 = TransactionViewModel(
            launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
            savedStateHandle = handle,
            ports = ports,
            ioDispatcher = testDispatcher,
        )
        val restored = vm2.uiState.value as TransactionUiState.Ready
        assertEquals(7525L, restored.form.primaryAmountMinor)
        assertEquals("Before process death", restored.form.note)
        assertTrue(restored.hasUnsavedChanges)
        assertNull(restored.activeSheet)
    }

    private fun SaveTransactionCommand.amountCommandMinor(): Long = fromAmountMinor

    private fun defaultCatalog(): TransactionCatalog {
        return TransactionCatalog(
            accounts = listOf(
                AccountOption(
                    id = 1L,
                    title = "Cash",
                    typeLabel = "CASH",
                    currencyId = 1L,
                    currencyCode = "USD",
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    balanceMinor = 50000L,
                ),
            ),
            currencies = listOf(
                CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true),
            ),
            categories = listOf(
                CategoryOption(id = 10L, parentId = 0L, title = "Food", fullPath = "Food", level = 1, isIncome = false),
            ),
        )
    }

    private class FakeTransactionRepository(
        var catalog: TransactionCatalog,
    ) : TransactionRepository {
        var editResult: TransactionEditLoadResult = TransactionEditLoadResult.NotFound(-1L)
        val savedCommands = mutableListOf<SaveTransactionCommand>()
        private var nextId = 101L

        override suspend fun loadCatalog(editingTransactionId: Long?): TransactionCatalog = catalog

        override suspend fun loadForEdit(transactionId: Long): TransactionEditLoadResult = editResult

        override suspend fun getLatestExchangeRate(fromCurrencyId: Long, toCurrencyId: Long): Double? = null

        override suspend fun saveTransaction(command: SaveTransactionCommand): Long {
            savedCommands.add(command)
            return nextId++
        }
    }

    private class FakeDraftSessionStore : DraftSessionStore {
        val savedByDraftId = mutableMapOf<String, Long>()

        override suspend fun getSavedTransactionId(draftId: String): Long? = savedByDraftId[draftId]

        override suspend fun markDraftSaved(draftId: String, transactionId: Long) {
            savedByDraftId[draftId] = transactionId
        }
    }

    private class FakeReceiptMediaPort : ReceiptMediaPort {
        val deletedFiles = mutableListOf<String>()

        override suspend fun stageFromUri(uriString: String): String = "uri_staged.jpg"

        override fun resolveFilePath(fileName: String): String = "/tmp/$fileName"

        override suspend fun deleteStagedFile(fileName: String) {
            deletedFiles.add(fileName)
        }
    }
}
