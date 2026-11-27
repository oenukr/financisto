// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import android.os.Bundle
import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.transaction.domain.SaveExecutionOutcome
import ru.orangesoftware.financisto.transaction.domain.TransactionActionRunner
import ru.orangesoftware.financisto.transaction.domain.TransactionExternalEntityCoordinator
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
import java.io.IOException
import kotlin.time.Clock

class TransactionViewModel(
    launchArgs: TransactionLaunchArgs,
    private val savedStateHandle: SavedStateHandle,
    private val ports: TransactionPorts,
    private val ioDispatcher: CoroutineDispatcher? = null,
    private val clock: Clock = Clock.System,
    @Suppress("InjectDispatcher")
    transformDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = stateScope ?: viewModelScope
    private val isPinUnlockedState = mutableStateOf(true)
    private val container = SavedStateContainer().apply {
        savedStateHandle.get<Bundle>(KEY_TRANSFORM_CONTAINER)?.let(::restoreState)
    }
    private var currentLaunchArgs: TransactionLaunchArgs = launchArgs
    private var loadJob: Job? = null
    private var formGeneration: Long = 0L

    val currentEditorState: State<TransactionUiState>
        field = mutableStateOf<TransactionUiState>(TransactionUiState.Loading)

    val uiState: State<TransactionUiState> = transform<TransactionUiState>(
        initialValue = TransactionUiState.Loading,
        scope = scope,
        container = container,
        dispatcher = transformDispatcher,
    ) {
        if (!isPinUnlockedState.value) TransactionUiState.PinLockedMask else currentEditorState.value
    }

    private val _effects = Channel<TransactionEffect>(Channel.BUFFERED)
    val effects: Flow<TransactionEffect> = _effects.receiveAsFlow()

    private val rateCoordinator = TransactionRateCoordinator(
        scope = scope,
        ports = ports,
        currentReady = { currentEditorState.value as? TransactionUiState.Ready },
        publishReady = ::publishState,
        sendEffect = { _effects.trySend(it) },
        ioDispatcher = ioDispatcher,
        currentGeneration = { formGeneration },
    )

    private val stagingCoordinator = TransactionReceiptStagingCoordinator(
        scope = scope,
        ports = ports,
        currentReady = { currentEditorState.value as? TransactionUiState.Ready },
        publishReady = ::publishState,
        ioDispatcher = ioDispatcher,
        currentGeneration = { formGeneration },
        onError = { message -> _effects.trySend(TransactionEffect.ShowMessage(message)) },
    )

    private val externalEntityCoordinator = TransactionExternalEntityCoordinator(
        scope = scope,
        ports = ports,
        savedStateHandle = savedStateHandle,
        currentReady = { currentEditorState.value as? TransactionUiState.Ready },
        publishReady = ::publishState,
        sendEffect = { _effects.trySend(it) },
        ioDispatcher = ioDispatcher,
        currentGeneration = { formGeneration },
    )

    init {
        savedStateHandle.setSavedStateProvider(KEY_TRANSFORM_CONTAINER) { container.saveState() }
        val persistedLaunchArgs = savedStateHandle.get<TransactionLaunchArgs>(KEY_EFFECTIVE_LAUNCH_ARGS)
        loadInitialState(persistedLaunchArgs ?: launchArgs, restoreSaved = true)
    }

    fun onPinStateChanged(unlocked: Boolean) {
        Snapshot.withMutableSnapshot {
            isPinUnlockedState.value = unlocked
        }
    }

    fun hasUnsavedUserEdits(): Boolean {
        val ready = currentEditorState.value as? TransactionUiState.Ready ?: return false
        return ready.form != ready.initialFormSnapshot ||
            ready.isStagingReceipt ||
            ready.isSaving ||
            externalEntityCoordinator.hasPendingReloads()
    }

    fun cleanupStagedReceipts() {
        val initialReady = (currentEditorState.value as? TransactionUiState.Ready)?.takeUnless { it.isSaving } ?: return
        stagingCoordinator.cancelAll(clearFlag = true)
        val ready = (currentEditorState.value as? TransactionUiState.Ready)?.takeUnless { it.isSaving } ?: initialReady
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
        scope.launch {
            withContext(NonCancellable) {
                TransactionActionRunner.runOn(ioDispatcher) {
                    TransactionLoader.deleteUnusedStagedFiles(ports, stagedFiles, keepFileName = keepFile)
                }
            }
        }
    }

    fun onIntent(intent: TransactionIntent): Boolean {
        if (intent is TransactionIntent.RetryLoad) {
            val canRetry = currentEditorState.value is TransactionUiState.LoadError
            if (canRetry) loadInitialState(currentLaunchArgs, restoreSaved = true)
            return canRetry
        }
        val ready = (currentEditorState.value as? TransactionUiState.Ready)?.takeUnless { it.isSaving }
        if (ready != null) {
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
                    else -> applyReducerIntent(ready, intent)
                }
            }
        }
        return ready != null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadInitialState(args: TransactionLaunchArgs, restoreSaved: Boolean) {
        formGeneration++
        val capturedGeneration = formGeneration
        loadJob?.cancel()
        rateCoordinator.cancel()
        stagingCoordinator.cancelAll()
        externalEntityCoordinator.cancelAll()
        val restoredForm = if (restoreSaved) savedStateHandle.get<TransactionFormState>(KEY_FORM_STATE) else null
        val restoredSnap = if (restoreSaved) savedStateHandle.get<TransactionFormState>(KEY_INITIAL_SNAPSHOT) else null
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
        loadJob = scope.launch {
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
                    if (restoredForm == null &&
                        (effectiveArgs as? TransactionLaunchArgs.AiDraft)?.draft?.receiptAttachFailed == true
                    ) {
                        _effects.trySend(TransactionEffect.ShowMessage(RECEIPT_ATTACH_FAILED_MESSAGE))
                    }
                    rateCoordinator.maybeLookupStoredRate(loaded, syncInitialSnapshotIfUntouched = true)
                }
            }
        }
    }

    private fun applyReducerIntent(ready: TransactionUiState.Ready, intent: TransactionIntent) {
        when (intent) {
            is TransactionIntent.FetchLiveRate -> rateCoordinator.fetchLiveRate(ready)
            is TransactionIntent.AttachReceiptFromUri -> stagingCoordinator.stageReceiptUri(intent.uriString)
            is TransactionIntent.OpenExternalCategoryCreator -> {
                TransactionExternalEntityHelper.savePendingCategoryTarget(
                    savedStateHandle = savedStateHandle,
                    target = TransactionExternalEntityHelper.captureCategoryTarget(ready),
                )
                _effects.trySend(TransactionEffect.LaunchExternalCategoryCreator)
            }
            is TransactionIntent.ExternalEntityCreated ->
                externalEntityCoordinator.onExternalEntityCreated(ready, intent)
            is TransactionIntent.ClearFormClicked -> {
                formGeneration++
                rateCoordinator.cancel()
                stagingCoordinator.cancelAll()
                externalEntityCoordinator.cancelAll()
                TransactionExternalEntityHelper.clearPendingCategoryTarget(savedStateHandle)
                val cleared = TransactionActionRunner.buildClearedForm(ready, clock.now().toEpochMilliseconds())
                publishState(
                    ready.copy(
                        form = cleared,
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
            else -> {
                val reduced = TransactionReducer.reduce(ready, intent)
                val syncSnapshot = intent is TransactionIntent.DuplicateDraftAnyway &&
                    ready.form == ready.initialFormSnapshot
                var stateToPublish = if (syncSnapshot) reduced.copy(initialFormSnapshot = reduced.form) else reduced
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
        externalEntityCoordinator.cancelAll()
        TransactionExternalEntityHelper.clearPendingCategoryTarget(savedStateHandle)
        val draftId = ready.form.aiDraftId
        val stagedFiles = ready.form.newlyStagedPictureFiles
        val keepFile = ready.form.initialPersistedPictureFileName
        scope.launch {
            withContext(NonCancellable) {
                TransactionActionRunner.runOn(ioDispatcher) {
                    draftId?.let { id ->
                        try {
                            ports.draftSessionStore.clearPendingDraft(id)
                        } catch (e: IOException) {
                            Timber.w(e, "Failed to clear pending AI draft on discard: %s", id)
                        }
                    }
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
            !externalEntityCoordinator.hasPendingReloads() -> validateAndExecuteSave(ready, saveAndNew)
            else -> {
                val capturedGeneration = formGeneration
                rateCoordinator.cancel()
                publishState(ready.copy(isSaving = true, isFetchingRate = false, validationErrors = emptyList()))
                scope.launch {
                    val reloadedOk = externalEntityCoordinator.awaitPendingReloads()
                    val latestReady = (currentEditorState.value as? TransactionUiState.Ready)
                        ?.takeIf { formGeneration == capturedGeneration }
                    if (latestReady != null && reloadedOk) {
                        validateAndExecuteSave(latestReady, saveAndNew)
                    } else if (latestReady != null) {
                        publishState(latestReady.copy(isSaving = false))
                    }
                }
            }
        }
    }

    private fun validateAndExecuteSave(ready: TransactionUiState.Ready, saveAndNew: Boolean) {
        when (val validation = TransactionValidator.validateAndBuildCommand(ready.form, ready.catalog)) {
            is TransactionValidationResult.Invalid -> publishState(
                ready.copy(isSaving = false, validationErrors = validation.errors)
            )
            is TransactionValidationResult.Valid -> {
                rateCoordinator.cancel()
                val savingState = ready.copy(isSaving = true, isFetchingRate = false, validationErrors = emptyList())
                publishState(savingState)
                scope.launch {
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
                            externalEntityCoordinator.cancelAll()
                            TransactionExternalEntityHelper.clearPendingCategoryTarget(savedStateHandle)
                            currentLaunchArgs = outcome.nextLaunchArgs
                            savedStateHandle[KEY_EFFECTIVE_LAUNCH_ARGS] = outcome.nextLaunchArgs
                            publishState(outcome.nextReady)
                            _effects.trySend(TransactionEffect.ShowMessage("Transaction saved"))
                        }
                        is SaveExecutionOutcome.AlreadySavedDraft -> {
                            (currentEditorState.value as? TransactionUiState.Ready)?.let { latest ->
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
                            (currentEditorState.value as? TransactionUiState.Ready)?.let {
                                publishState(it.copy(isSaving = false))
                            }
                            _effects.trySend(TransactionEffect.ShowMessage(outcome.message))
                        }
                    }
                }
            }
        }
    }

    private fun publishState(state: TransactionUiState) {
        if (state is TransactionUiState.Ready) {
            savedStateHandle[KEY_FORM_STATE] = state.form
            savedStateHandle[KEY_INITIAL_SNAPSHOT] = state.initialFormSnapshot
        }
        Snapshot.withMutableSnapshot {
            currentEditorState.value = state
        }
    }

    private companion object {
        const val KEY_FORM_STATE = "transaction_form_state"
        const val KEY_INITIAL_SNAPSHOT = "transaction_initial_snapshot"
        const val KEY_EFFECTIVE_LAUNCH_ARGS = "transaction_effective_launch_args"
        const val KEY_TRANSFORM_CONTAINER = "transaction_transform_container"
        const val DEFAULT_LOAD_ERROR_MESSAGE = "Failed to load transaction."
        const val RECEIPT_ATTACH_FAILED_MESSAGE = "Could not attach receipt image."
    }
}
