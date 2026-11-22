// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.transaction.domain.SaveExecutionOutcome
import ru.orangesoftware.financisto.transaction.domain.TransactionActionRunner
import ru.orangesoftware.financisto.transaction.domain.TransactionLoader
import ru.orangesoftware.financisto.transaction.domain.TransactionRateCoordinator
import ru.orangesoftware.financisto.transaction.domain.TransactionReceiptStagingCoordinator
import ru.orangesoftware.financisto.transaction.domain.TransactionReducer
import ru.orangesoftware.financisto.transaction.domain.TransactionValidationResult
import ru.orangesoftware.financisto.transaction.domain.TransactionValidator
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import kotlin.time.Clock

class TransactionViewModel(
    launchArgs: TransactionLaunchArgs,
    private val savedStateHandle: SavedStateHandle,
    private val ports: TransactionPorts,
    private val ioDispatcher: CoroutineDispatcher? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private var internalState: TransactionUiState = TransactionUiState.Loading
    private var isPinUnlocked: Boolean = true
    private var currentLaunchArgs: TransactionLaunchArgs = launchArgs
    private var loadJob: Job? = null
    private var formGeneration: Long = 0L

    private val _uiState = MutableStateFlow<TransactionUiState>(TransactionUiState.Loading)
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TransactionEffect>(Channel.BUFFERED)
    val effects: Flow<TransactionEffect> = _effects.receiveAsFlow()

    private val rateCoordinator = TransactionRateCoordinator(
        scope = viewModelScope,
        ports = ports,
        currentReady = { internalState as? TransactionUiState.Ready },
        publishReady = ::publishState,
        sendEffect = { _effects.trySend(it) },
        ioDispatcher = ioDispatcher,
        currentGeneration = { formGeneration },
    )

    private val stagingCoordinator = TransactionReceiptStagingCoordinator(
        scope = viewModelScope,
        ports = ports,
        currentReady = { internalState as? TransactionUiState.Ready },
        publishReady = ::publishState,
        ioDispatcher = ioDispatcher,
        currentGeneration = { formGeneration },
    )

    init {
        loadInitialState(launchArgs, restoreSaved = true)
    }

    fun onPinStateChanged(unlocked: Boolean) {
        isPinUnlocked = unlocked
        _uiState.value = if (!isPinUnlocked) TransactionUiState.PinLockedMask else internalState
    }

    fun hasUnsavedUserEdits(): Boolean {
        val ready = internalState as? TransactionUiState.Ready ?: return false
        return ready.form != ready.initialFormSnapshot
    }

    fun cleanupStagedReceipts() {
        val initialReady = (internalState as? TransactionUiState.Ready)?.takeUnless { it.isSaving } ?: return
        stagingCoordinator.cancelAll(clearFlag = true)
        val ready = (internalState as? TransactionUiState.Ready)?.takeUnless { it.isSaving } ?: initialReady
        val stagedFiles = ready.form.newlyStagedPictureFiles
        if (stagedFiles.isEmpty()) return
        val keepFile = ready.form.initialPersistedPictureFileName
        publishState(
            ready.copy(
                form = ready.form.copy(
                    attachedPictureFileName = keepFile,
                    newlyStagedPictureFiles = emptyList(),
                ),
                isStagingReceipt = false,
            )
        )
        viewModelScope.launch {
            withContext(NonCancellable) {
                TransactionActionRunner.runOn(ioDispatcher) {
                    TransactionLoader.deleteUnusedStagedFiles(ports, stagedFiles, keepFileName = keepFile)
                }
            }
        }
    }

    fun onIntent(intent: TransactionIntent) {
        val ready = (internalState as? TransactionUiState.Ready)?.takeUnless { it.isSaving } ?: return
        val immediateEffect = TransactionActionRunner.immediateEffectOrNull(intent, currentLaunchArgs)
        if (immediateEffect != null) {
            _effects.trySend(immediateEffect)
            return
        }
        when (intent) {
            is TransactionIntent.SaveClicked -> handleSave(ready, saveAndNew = false)
            is TransactionIntent.SaveAndNewClicked -> handleSave(ready, saveAndNew = true)
            is TransactionIntent.CancelOrBackClicked -> if (ready.hasUnsavedChanges) {
                publishState(ready.copy(sheetStack = listOf(ActiveSheet.DiscardConfirmation)))
            } else {
                discardAndClose(ready)
            }
            is TransactionIntent.ConfirmDiscard -> discardAndClose(ready)
            is TransactionIntent.FetchLiveRate -> rateCoordinator.fetchLiveRate(ready)
            is TransactionIntent.AttachReceiptFromUri -> stagingCoordinator.stageReceiptUri(intent.uriString)
            is TransactionIntent.ExternalEntityCreated -> handleExternalEntityCreated(ready, intent)
            is TransactionIntent.ClearFormClicked -> {
                formGeneration++
                rateCoordinator.cancel()
                stagingCoordinator.cancelAll()
                publishState(
                    ready.copy(
                        form = TransactionActionRunner.buildClearedForm(ready, clock.now().toEpochMilliseconds()),
                        sheetStack = emptyList(),
                        expandedField = null,
                        validationErrors = emptyList(),
                        isFetchingRate = false,
                        isStagingReceipt = false,
                    )
                )
            }
            is TransactionIntent.OpenSavedTransaction ->
                loadInitialState(TransactionLaunchArgs.Edit(intent.transactionId), restoreSaved = false)
            else -> applyReducerIntent(ready, intent)
        }
    }

    private fun loadInitialState(args: TransactionLaunchArgs, restoreSaved: Boolean) {
        formGeneration++
        val capturedGeneration = formGeneration
        loadJob?.cancel()
        rateCoordinator.cancel()
        stagingCoordinator.cancelAll()
        currentLaunchArgs = args
        val restoredForm = if (restoreSaved) {
            savedStateHandle.get<TransactionFormState>(KEY_FORM_STATE)
        } else {
            null
        }
        val restoredSnap = if (restoreSaved) {
            savedStateHandle.get<TransactionFormState>(KEY_INITIAL_SNAPSHOT)
        } else {
            null
        }
        if (!restoreSaved) {
            savedStateHandle.remove<TransactionFormState>(KEY_FORM_STATE)
            savedStateHandle.remove<TransactionFormState>(KEY_INITIAL_SNAPSHOT)
        }
        val nowMillis = clock.now().toEpochMilliseconds()
        loadJob = viewModelScope.launch {
            val loaded = TransactionActionRunner.runOn(ioDispatcher) {
                TransactionLoader.loadInitial(
                    launchArgs = args,
                    ports = ports,
                    restoredForm = restoredForm,
                    restoredSnapshot = restoredSnap,
                    nowMillis = nowMillis,
                )
            }
            if (formGeneration != capturedGeneration) return@launch
            if (loaded is TransactionUiState.LegacyRedirecting) {
                _effects.trySend(TransactionEffect.OpenLegacyEditor(args))
            }
            publishState(loaded)
            if (loaded is TransactionUiState.Ready) {
                rateCoordinator.maybeLookupStoredRate(loaded, syncInitialSnapshotIfUntouched = true)
            }
        }
    }

    private fun applyReducerIntent(ready: TransactionUiState.Ready, intent: TransactionIntent) {
        val reduced = TransactionReducer.reduce(ready, intent)
        val next = if (intent is TransactionIntent.DuplicateDraftAnyway && ready.form == ready.initialFormSnapshot) {
            reduced.copy(initialFormSnapshot = reduced.form)
        } else {
            reduced
        }
        val isManualRateEdit = intent is TransactionIntent.UpdateExchangeRate ||
            (intent is TransactionIntent.ApplyCalculatorResult && intent.target == CalculatorTarget.EXCHANGE_RATE)
        if (isManualRateEdit) {
            rateCoordinator.cancel()
            publishState(next.copy(isFetchingRate = false))
        } else {
            publishState(next)
        }
        val isCurrencyContextChange = when (intent) {
            is TransactionIntent.SelectAccount,
            is TransactionIntent.SelectCurrency,
            is TransactionIntent.SwapTransferAccounts,
            is TransactionIntent.SwitchMode -> true
            else -> false
        }
        if (isCurrencyContextChange) {
            rateCoordinator.maybeLookupStoredRate(next)
        }
    }

    private fun discardAndClose(ready: TransactionUiState.Ready) {
        formGeneration++
        rateCoordinator.cancel()
        stagingCoordinator.cancelAll(clearFlag = true)
        val draftId = ready.form.aiDraftId
        val stagedFiles = ready.form.newlyStagedPictureFiles
        val keepFile = ready.form.initialPersistedPictureFileName
        viewModelScope.launch {
            withContext(NonCancellable) {
                TransactionActionRunner.runOn(ioDispatcher) {
                    draftId?.let { ports.draftSessionStore.clearPendingDraft(it) }
                    TransactionLoader.deleteUnusedStagedFiles(ports, stagedFiles, keepFileName = keepFile)
                }
            }
            _effects.trySend(TransactionEffect.CloseCancelled)
        }
    }

    private fun handleSave(ready: TransactionUiState.Ready, saveAndNew: Boolean) {
        val consumedTxId = ready.consumedDraftTransactionId
        when {
            consumedTxId != null && !ready.form.isDuplicateDraftConfirmed ->
                publishState(ready.copy(sheetStack = listOf(ActiveSheet.ConsumedDraftPrompt(consumedTxId))))
            stagingCoordinator.isStagingForCurrentForm() ->
                _effects.trySend(TransactionEffect.ShowMessage("Please wait for the receipt to finish attaching."))
            else -> when (val validation = TransactionValidator.validateAndBuildCommand(ready.form, ready.catalog)) {
                is TransactionValidationResult.Invalid -> publishState(ready.copy(validationErrors = validation.errors))
                is TransactionValidationResult.Valid -> {
                    rateCoordinator.cancel()
                    val savingState = ready.copy(
                        isSaving = true,
                        isFetchingRate = false,
                        validationErrors = emptyList(),
                    )
                    publishState(savingState)
                    viewModelScope.launch {
                        val outcome = TransactionActionRunner.runOn(ioDispatcher) {
                            TransactionActionRunner.executeSave(
                                savingState,
                                validation.command,
                                saveAndNew,
                                ports,
                                clock,
                            )
                        }
                        when (outcome) {
                            is SaveExecutionOutcome.Finished -> {
                                outcome.warningMessage?.let { _effects.trySend(TransactionEffect.ShowMessage(it)) }
                                _effects.trySend(TransactionEffect.SavedAndFinish(outcome.savedId))
                            }
                            is SaveExecutionOutcome.ResetNext -> {
                                formGeneration++
                                rateCoordinator.cancel()
                                stagingCoordinator.cancelAll()
                                currentLaunchArgs = TransactionLaunchArgs.BlankNew(
                                    accountId = outcome.nextReady.form.fromAccountId.takeIf { it > 0L },
                                    initialMode = outcome.nextReady.form.mode,
                                    initialDateTimeMillis = outcome.nextReady.form.dateTimeMillis,
                                )
                                publishState(outcome.nextReady)
                                _effects.trySend(TransactionEffect.ShowMessage("Transaction saved"))
                            }
                            is SaveExecutionOutcome.Failed -> {
                                val latest = internalState as? TransactionUiState.Ready
                                if (latest != null) {
                                    publishState(latest.copy(isSaving = false))
                                }
                                _effects.trySend(TransactionEffect.ShowMessage(outcome.message))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleExternalEntityCreated(
        ready: TransactionUiState.Ready,
        intent: TransactionIntent.ExternalEntityCreated,
    ) {
        val editId = ready.form.existingTransactionId.takeIf { it > 0L }
        val capturedGeneration = formGeneration
        viewModelScope.launch {
            val catalog = TransactionActionRunner.runOn(ioDispatcher) {
                ports.repository.loadCatalog(editingTransactionId = editId)
            }
            if (formGeneration != capturedGeneration) return@launch
            val latestReady = (internalState as? TransactionUiState.Ready)
                ?.takeUnless { it.isSaving }
                ?: return@launch
            val updated = TransactionActionRunner.applyExternalEntityCreated(
                ready = latestReady,
                refreshedCatalog = catalog,
                kind = intent.kind,
                createdId = intent.createdId,
            )
            publishState(updated)
        }
    }

    private fun publishState(state: TransactionUiState) {
        if (state is TransactionUiState.Ready) {
            savedStateHandle[KEY_FORM_STATE] = state.form
            savedStateHandle[KEY_INITIAL_SNAPSHOT] = state.initialFormSnapshot
        }
        internalState = state
        _uiState.value = if (!isPinUnlocked) TransactionUiState.PinLockedMask else state
    }

    private companion object {
        const val KEY_FORM_STATE = "transaction_form_state"
        const val KEY_INITIAL_SNAPSHOT = "transaction_initial_snapshot"
    }
}
