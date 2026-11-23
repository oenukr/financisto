// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import ru.orangesoftware.financisto.transaction.domain.TransactionExternalEntityHelper
import ru.orangesoftware.financisto.transaction.domain.TransactionLoader
import ru.orangesoftware.financisto.transaction.domain.TransactionRateCoordinator
import ru.orangesoftware.financisto.transaction.domain.TransactionReceiptStagingCoordinator
import ru.orangesoftware.financisto.transaction.domain.TransactionReducer
import ru.orangesoftware.financisto.transaction.domain.TransactionValidationResult
import ru.orangesoftware.financisto.transaction.domain.TransactionValidator
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import timber.log.Timber
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
        onError = { message -> _effects.trySend(TransactionEffect.ShowMessage(message)) },
    )

    init {
        val persistedLaunchArgs = savedStateHandle.get<TransactionLaunchArgs>(KEY_EFFECTIVE_LAUNCH_ARGS)
        loadInitialState(persistedLaunchArgs ?: launchArgs, restoreSaved = true)
    }

    fun onPinStateChanged(unlocked: Boolean) {
        isPinUnlocked = unlocked
        _uiState.value = if (!isPinUnlocked) TransactionUiState.PinLockedMask else internalState
    }

    fun hasUnsavedUserEdits(): Boolean {
        val ready = internalState as? TransactionUiState.Ready ?: return false
        return ready.form != ready.initialFormSnapshot || ready.isStagingReceipt || ready.isSaving
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
        if (intent is TransactionIntent.RetryLoad) {
            if (internalState is TransactionUiState.LoadError) {
                loadInitialState(currentLaunchArgs, restoreSaved = true)
            }
            return
        }
        val ready = (internalState as? TransactionUiState.Ready)?.takeUnless { it.isSaving } ?: return
        val immediateEffect = TransactionActionRunner.immediateEffectOrNull(intent, currentLaunchArgs)
        if (immediateEffect != null) {
            _effects.trySend(immediateEffect)
        } else {
            when (intent) {
                is TransactionIntent.SaveClicked,
                is TransactionIntent.SaveAndNewClicked ->
                    handleSave(ready, saveAndNew = intent is TransactionIntent.SaveAndNewClicked)
                is TransactionIntent.CancelOrBackClicked,
                is TransactionIntent.ConfirmDiscard ->
                    discardAndClose(ready, promptIfDirty = intent is TransactionIntent.CancelOrBackClicked)
                is TransactionIntent.FetchLiveRate -> rateCoordinator.fetchLiveRate(ready)
                is TransactionIntent.AttachReceiptFromUri -> stagingCoordinator.stageReceiptUri(intent.uriString)
                is TransactionIntent.OpenExternalCategoryCreator -> {
                    TransactionExternalEntityHelper.savePendingCategoryTarget(
                        savedStateHandle = savedStateHandle,
                        target = TransactionExternalEntityHelper.captureCategoryTarget(ready),
                    )
                    _effects.trySend(TransactionEffect.LaunchExternalCategoryCreator)
                }
                is TransactionIntent.ExternalEntityCreated -> handleExternalEntityCreated(ready, intent)
                is TransactionIntent.ClearFormClicked -> {
                    formGeneration++
                    rateCoordinator.cancel()
                    stagingCoordinator.cancelAll()
                    TransactionExternalEntityHelper.clearPendingCategoryTarget(savedStateHandle)
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
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadInitialState(args: TransactionLaunchArgs, restoreSaved: Boolean) {
        formGeneration++
        val capturedGeneration = formGeneration
        loadJob?.cancel()
        rateCoordinator.cancel()
        stagingCoordinator.cancelAll()
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
        val effectiveArgs = TransactionLoader.resolveEffectiveLaunchArgs(args, restoredForm)
        currentLaunchArgs = effectiveArgs
        savedStateHandle[KEY_EFFECTIVE_LAUNCH_ARGS] = effectiveArgs
        if (!restoreSaved) {
            savedStateHandle.remove<TransactionFormState>(KEY_FORM_STATE)
            savedStateHandle.remove<TransactionFormState>(KEY_INITIAL_SNAPSHOT)
            TransactionExternalEntityHelper.clearPendingCategoryTarget(savedStateHandle)
        }
        publishState(TransactionUiState.Loading)
        val nowMillis = clock.now().toEpochMilliseconds()
        loadJob = viewModelScope.launch {
            val loaded = try {
                TransactionActionRunner.runOn(ioDispatcher) {
                    TransactionLoader.loadInitial(
                        launchArgs = effectiveArgs,
                        ports = ports,
                        restoredForm = restoredForm,
                        restoredSnapshot = restoredSnap,
                        nowMillis = nowMillis,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (formGeneration == capturedGeneration) {
                    Timber.e(e, "Failed to load transaction editor state for %s", effectiveArgs)
                }
                TransactionUiState.LoadError(
                    e.localizedMessage?.takeIf { it.isNotBlank() } ?: DEFAULT_LOAD_ERROR_MESSAGE
                )
            }
            if (formGeneration == capturedGeneration) {
                if (loaded is TransactionUiState.LegacyRedirecting) {
                    _effects.trySend(TransactionEffect.OpenLegacyEditor(effectiveArgs))
                }
                publishState(loaded)
                if (loaded is TransactionUiState.Ready) {
                    rateCoordinator.maybeLookupStoredRate(loaded, syncInitialSnapshotIfUntouched = true)
                }
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
        var stateToPublish = next
        if (TransactionActionRunner.cancelsPendingRateFetch(intent)) {
            rateCoordinator.cancel()
            stateToPublish = stateToPublish.copy(isFetchingRate = false)
        }
        if (intent is TransactionIntent.AttachCapturedPhotoFile || intent is TransactionIntent.RemoveReceipt) {
            stagingCoordinator.cancelAll()
            stateToPublish = stateToPublish.copy(isStagingReceipt = false)
        }
        publishState(stateToPublish)
        if (TransactionActionRunner.isCurrencyContextChange(intent)) {
            rateCoordinator.maybeLookupStoredRate(stateToPublish)
        }
    }

    private fun discardAndClose(ready: TransactionUiState.Ready, promptIfDirty: Boolean) {
        if (promptIfDirty && ready.hasUnsavedChanges) {
            publishState(ready.copy(sheetStack = listOf(ActiveSheet.DiscardConfirmation)))
            return
        }
        formGeneration++
        rateCoordinator.cancel()
        stagingCoordinator.cancelAll(clearFlag = true)
        TransactionExternalEntityHelper.clearPendingCategoryTarget(savedStateHandle)
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
        if (consumedTxId != null && !ready.form.isDuplicateDraftConfirmed) {
            publishState(ready.copy(sheetStack = listOf(ActiveSheet.ConsumedDraftPrompt(consumedTxId))))
            return
        }
        if (stagingCoordinator.isStagingForCurrentForm()) {
            _effects.trySend(TransactionEffect.ShowMessage("Please wait for the receipt to finish attaching."))
            return
        }
        when (val validation = TransactionValidator.validateAndBuildCommand(ready.form, ready.catalog)) {
            is TransactionValidationResult.Invalid -> publishState(ready.copy(validationErrors = validation.errors))
            is TransactionValidationResult.Valid -> {
                rateCoordinator.cancel()
                val savingState = ready.copy(isSaving = true, isFetchingRate = false, validationErrors = emptyList())
                publishState(savingState)
                viewModelScope.launch {
                    val outcome = TransactionActionRunner.runOn(ioDispatcher) {
                        TransactionActionRunner.executeSave(savingState, validation.command, saveAndNew, ports, clock)
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
                            TransactionExternalEntityHelper.clearPendingCategoryTarget(savedStateHandle)
                            currentLaunchArgs = outcome.nextLaunchArgs
                            savedStateHandle[KEY_EFFECTIVE_LAUNCH_ARGS] = outcome.nextLaunchArgs
                            publishState(outcome.nextReady)
                            _effects.trySend(TransactionEffect.ShowMessage("Transaction saved"))
                        }
                        is SaveExecutionOutcome.AlreadySavedDraft -> {
                            (internalState as? TransactionUiState.Ready)?.let { latest ->
                                val prompt = ActiveSheet.ConsumedDraftPrompt(outcome.savedTransactionId)
                                publishState(
                                    latest.copy(
                                        isSaving = false,
                                        consumedDraftTransactionId = outcome.savedTransactionId,
                                        sheetStack = listOf(prompt),
                                    )
                                )
                            }
                        }
                        is SaveExecutionOutcome.Failed -> {
                            (internalState as? TransactionUiState.Ready)?.let {
                                publishState(it.copy(isSaving = false))
                            }
                            _effects.trySend(TransactionEffect.ShowMessage(outcome.message))
                        }
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleExternalEntityCreated(
        ready: TransactionUiState.Ready,
        intent: TransactionIntent.ExternalEntityCreated,
    ) {
        val editId = ready.form.existingTransactionId.takeIf { it > 0L }
        val capturedGeneration = formGeneration
        val pendingCategoryTarget = if (intent.kind == ExternalEntityKind.CATEGORY) {
            TransactionExternalEntityHelper.consumePendingCategoryTarget(savedStateHandle)
                ?: TransactionExternalEntityHelper.captureCategoryTarget(ready)
        } else {
            null
        }
        viewModelScope.launch {
            val catalog = try {
                TransactionActionRunner.runOn(ioDispatcher) {
                    ports.repository.loadCatalog(editingTransactionId = editId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload catalog after external %s creation", intent.kind)
                if (formGeneration == capturedGeneration) {
                    _effects.trySend(
                        TransactionEffect.ShowMessage(
                            e.localizedMessage?.takeIf { it.isNotBlank() } ?: DEFAULT_LOAD_ERROR_MESSAGE
                        )
                    )
                }
                null
            }
            if (catalog != null && formGeneration == capturedGeneration) {
                val latestReady = (internalState as? TransactionUiState.Ready)?.takeUnless { it.isSaving }
                if (latestReady != null) {
                    val updated = TransactionExternalEntityHelper.applyExternalEntityCreated(
                        ready = latestReady,
                        refreshedCatalog = catalog,
                        kind = intent.kind,
                        createdId = intent.createdId,
                        pendingCategoryTarget = pendingCategoryTarget,
                    )
                    publishState(updated)
                }
            }
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
        const val KEY_EFFECTIVE_LAUNCH_ARGS = "transaction_effective_launch_args"
        const val DEFAULT_LOAD_ERROR_MESSAGE = "Failed to load transaction."
    }
}
