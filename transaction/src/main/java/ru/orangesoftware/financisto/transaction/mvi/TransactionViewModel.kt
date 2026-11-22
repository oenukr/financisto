// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.transaction.domain.AmountModeReducer
import ru.orangesoftware.financisto.transaction.domain.CurrencyConversionCalculator
import ru.orangesoftware.financisto.transaction.domain.SaveExecutionOutcome
import ru.orangesoftware.financisto.transaction.domain.TransactionActionRunner
import ru.orangesoftware.financisto.transaction.domain.TransactionLoader
import ru.orangesoftware.financisto.transaction.domain.TransactionReducer
import ru.orangesoftware.financisto.transaction.domain.TransactionValidationResult
import ru.orangesoftware.financisto.transaction.domain.TransactionValidator
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.port.TransactionPorts

class TransactionViewModel(
    private val launchArgs: TransactionLaunchArgs,
    private val savedStateHandle: SavedStateHandle,
    private val ports: TransactionPorts,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private var internalState: TransactionUiState = TransactionUiState.Loading
    private var isPinUnlocked: Boolean = true

    private val _uiState = MutableStateFlow<TransactionUiState>(TransactionUiState.Loading)
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TransactionEffect>(Channel.BUFFERED)
    val effects: Flow<TransactionEffect> = _effects.receiveAsFlow()

    init {
        loadInitialState(launchArgs, restoreSaved = true)
    }

    fun onPinStateChanged(unlocked: Boolean) {
        isPinUnlocked = unlocked
        publishState(internalState)
    }

    fun onIntent(intent: TransactionIntent) {
        val ready = internalState as? TransactionUiState.Ready ?: return
        when (intent) {
            is TransactionIntent.SaveClicked -> handleSave(ready, saveAndNew = false)
            is TransactionIntent.SaveAndNewClicked -> handleSave(ready, saveAndNew = true)
            is TransactionIntent.CancelOrBackClicked -> handleCancelOrBack(ready)
            is TransactionIntent.ConfirmDiscard -> discardAndClose(ready)
            is TransactionIntent.FetchLiveRate -> fetchLiveRate(ready)
            is TransactionIntent.RequestCameraPhoto -> _effects.trySend(TransactionEffect.LaunchCameraCapture)
            is TransactionIntent.RequestGalleryPhoto -> _effects.trySend(TransactionEffect.LaunchGalleryPick)
            is TransactionIntent.OpenExternalCategoryCreator ->
                _effects.trySend(TransactionEffect.LaunchExternalCategoryCreator)
            is TransactionIntent.OpenExternalLocationCreator ->
                _effects.trySend(TransactionEffect.LaunchExternalLocationCreator)
            is TransactionIntent.AttachReceiptFromUri -> stageReceiptUri(intent.uriString)
            is TransactionIntent.AttachCapturedPhotoFile -> publishReady(
                ready.copy(
                    form = ready.form.copy(
                        attachedPictureFileName = intent.fileName,
                        newlyStagedPictureFiles = ready.form.newlyStagedPictureFiles + intent.fileName,
                    )
                )
            )
            is TransactionIntent.RemoveReceipt -> publishReady(
                ready.copy(form = ready.form.copy(attachedPictureFileName = null))
            )
            is TransactionIntent.ExternalEntityCreated -> viewModelScope.launch(ioDispatcher) {
                val updated = TransactionActionRunner.reloadCatalogAndSelect(
                    ready, intent.kind, intent.createdId, ports
                )
                publishReady(updated)
            }
            is TransactionIntent.ClearFormClicked -> publishReady(
                ready.copy(
                    form = TransactionActionRunner.buildClearedForm(ready),
                    expandedField = null,
                    validationErrors = emptyList(),
                )
            )
            is TransactionIntent.OpenInLegacyClicked ->
                _effects.trySend(TransactionEffect.OpenLegacyEditor(launchArgs))
            is TransactionIntent.OpenSavedTransaction ->
                loadInitialState(TransactionLaunchArgs.Edit(intent.transactionId), restoreSaved = false)
            is TransactionIntent.DuplicateDraftAnyway -> publishReady(ready.copy(activeSheet = null))
            else -> applyReducerIntent(ready, intent)
        }
    }

    private fun loadInitialState(args: TransactionLaunchArgs, restoreSaved: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val restoredForm = if (restoreSaved) savedStateHandle.get<TransactionFormState>(KEY_FORM_STATE) else null
            val restoredSnap = if (restoreSaved) {
                savedStateHandle.get<TransactionFormState>(KEY_INITIAL_SNAPSHOT)
            } else {
                null
            }
            val loaded = TransactionLoader.loadInitial(
                launchArgs = args,
                ports = ports,
                restoredForm = restoredForm,
                restoredSnapshot = restoredSnap,
                nowMillis = System.currentTimeMillis(),
            )
            if (loaded is TransactionUiState.LegacyRedirecting) {
                _effects.trySend(TransactionEffect.OpenLegacyEditor(args))
            }
            publishState(loaded)
            if (loaded is TransactionUiState.Ready) {
                maybeLookupStoredRate(loaded)
            }
        }
    }

    private fun applyReducerIntent(ready: TransactionUiState.Ready, intent: TransactionIntent) {
        val next = TransactionReducer.reduce(ready, intent)
        publishReady(next)
        if (
            intent is TransactionIntent.SelectAccount ||
            intent is TransactionIntent.SelectCurrency ||
            intent is TransactionIntent.SwapTransferAccounts ||
            intent is TransactionIntent.SwitchMode
        ) {
            maybeLookupStoredRate(next)
        }
    }

    private fun handleCancelOrBack(ready: TransactionUiState.Ready) {
        if (ready.hasUnsavedChanges) {
            publishReady(ready.copy(activeSheet = ActiveSheet.DiscardConfirmation))
        } else {
            discardAndClose(ready)
        }
    }

    private fun discardAndClose(ready: TransactionUiState.Ready) {
        viewModelScope.launch(ioDispatcher) {
            ready.form.aiDraftId?.let { ports.draftSessionStore.clearPendingDraft(it) }
            TransactionLoader.deleteUnusedStagedFiles(ports, ready.form.newlyStagedPictureFiles, keepFileName = null)
            _effects.trySend(TransactionEffect.CloseCancelled)
        }
    }

    private fun handleSave(ready: TransactionUiState.Ready, saveAndNew: Boolean) {
        if (ready.isSaving) return
        when (val validation = TransactionValidator.validateAndBuildCommand(ready.form, ready.catalog)) {
            is TransactionValidationResult.Invalid -> publishReady(ready.copy(validationErrors = validation.errors))
            is TransactionValidationResult.Valid -> {
                publishReady(ready.copy(isSaving = true, validationErrors = emptyList()))
                viewModelScope.launch(ioDispatcher) {
                    val outcome = TransactionActionRunner.executeSave(ready, validation.command, saveAndNew, ports)
                    when (outcome) {
                        is SaveExecutionOutcome.Finished ->
                            _effects.trySend(TransactionEffect.SavedAndFinish(outcome.savedId))
                        is SaveExecutionOutcome.ResetNext -> {
                            publishReady(outcome.nextReady)
                            _effects.trySend(TransactionEffect.ShowMessage("Transaction saved"))
                        }
                        is SaveExecutionOutcome.Failed -> {
                            publishReady(ready.copy(isSaving = false))
                            _effects.trySend(TransactionEffect.ShowMessage(outcome.message))
                        }
                    }
                }
            }
        }
    }

    private fun fetchLiveRate(ready: TransactionUiState.Ready) {
        val (pCur, sCur) = TransactionValidator.resolveCurrencyMeta(ready.form, ready.catalog)
        if (pCur == null || sCur == null || pCur.id == sCur.id) return
        publishReady(ready.copy(isFetchingRate = true))
        viewModelScope.launch(ioDispatcher) {
            val outcome = ports.exchangeRatePort.fetchLiveRate(pCur.id, sCur.id)
            val currentReady = (internalState as? TransactionUiState.Ready) ?: return@launch
            outcome.onSuccess { rate ->
                val formatted = CurrencyConversionCalculator.formatRate(rate)
                val updated = AmountModeReducer.applyExchangeRateText(
                    currentReady.copy(isFetchingRate = false), formatted, pCur.decimals, sCur.decimals
                )
                publishReady(updated)
            }.onFailure { error ->
                publishReady(currentReady.copy(isFetchingRate = false))
                _effects.trySend(TransactionEffect.ShowMessage(error.localizedMessage ?: "Rate download failed"))
            }
        }
    }

    private fun maybeLookupStoredRate(ready: TransactionUiState.Ready) {
        val (pCur, sCur) = TransactionValidator.resolveCurrencyMeta(ready.form, ready.catalog)
        if (pCur == null || sCur == null || pCur.id == sCur.id || ready.form.exchangeRate > 0.0) return
        viewModelScope.launch(ioDispatcher) {
            val stored = ports.repository.getLatestExchangeRate(pCur.id, sCur.id) ?: return@launch
            val currentReady = (internalState as? TransactionUiState.Ready) ?: return@launch
            if (currentReady.form.exchangeRate <= 0.0) {
                val formatted = CurrencyConversionCalculator.formatRate(stored)
                val updated = AmountModeReducer.applyExchangeRateText(
                    currentReady, formatted, pCur.decimals, sCur.decimals
                )
                publishReady(updated)
            }
        }
    }

    private fun stageReceiptUri(uriString: String) {
        viewModelScope.launch(ioDispatcher) {
            val staged = ports.receiptMediaPort.stageFromUri(uriString) ?: return@launch
            val currentReady = (internalState as? TransactionUiState.Ready) ?: return@launch
            publishReady(
                currentReady.copy(
                    form = currentReady.form.copy(
                        attachedPictureFileName = staged,
                        newlyStagedPictureFiles = currentReady.form.newlyStagedPictureFiles + staged,
                    )
                )
            )
        }
    }

    private fun publishReady(next: TransactionUiState.Ready) {
        savedStateHandle[KEY_FORM_STATE] = next.form
        savedStateHandle[KEY_INITIAL_SNAPSHOT] = next.initialFormSnapshot
        publishState(next)
    }

    private fun publishState(state: TransactionUiState) {
        internalState = state
        _uiState.value = if (!isPinUnlocked) TransactionUiState.PinLockedMask else state
    }

    private companion object {
        const val KEY_FORM_STATE = "transaction_form_state"
        const val KEY_INITIAL_SNAPSHOT = "transaction_initial_snapshot"
    }
}
