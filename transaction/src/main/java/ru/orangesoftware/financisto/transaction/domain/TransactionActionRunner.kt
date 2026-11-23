// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.mvi.TransactionEffect
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import timber.log.Timber
import kotlin.time.Clock

internal sealed interface SaveExecutionOutcome {
    data class Finished(
        val savedId: Long,
        val warningMessage: String? = null,
    ) : SaveExecutionOutcome
    data class ResetNext(
        val nextReady: TransactionUiState.Ready,
        val nextLaunchArgs: TransactionLaunchArgs.BlankNew,
    ) : SaveExecutionOutcome
    data class AlreadySavedDraft(val savedTransactionId: Long) : SaveExecutionOutcome
    data class Failed(val message: String) : SaveExecutionOutcome
}

internal object TransactionActionRunner {
    private const val DEFAULT_SAVE_ERROR_MESSAGE = "Failed to save transaction."

    private sealed interface CommitOutcome {
        data class Committed(val savedId: Long) : CommitOutcome
        data class AlreadySaved(val savedTransactionId: Long) : CommitOutcome
        data class Failed(val message: String) : CommitOutcome
    }

    suspend fun <T> runOn(
        dispatcher: CoroutineDispatcher?,
        block: suspend () -> T,
    ): T {
        return if (dispatcher != null) withContext(dispatcher) { block() } else block()
    }

    fun immediateEffectOrNull(
        intent: TransactionIntent,
        currentLaunchArgs: TransactionLaunchArgs,
    ): TransactionEffect? {
        return when (intent) {
            is TransactionIntent.RequestCameraPhoto -> TransactionEffect.LaunchCameraCapture
            is TransactionIntent.RequestGalleryPhoto -> TransactionEffect.LaunchGalleryPick
            is TransactionIntent.OpenExternalLocationCreator -> TransactionEffect.LaunchExternalLocationCreator
            is TransactionIntent.OpenInLegacyClicked -> TransactionEffect.OpenLegacyEditor(currentLaunchArgs)
            else -> null
        }
    }

    fun cancelsPendingRateFetch(intent: TransactionIntent): Boolean {
        return when (intent) {
            is TransactionIntent.UpdateExchangeRate,
            is TransactionIntent.UpdateSecondaryAmount -> true
            is TransactionIntent.ApplyCalculatorResult ->
                intent.target == CalculatorTarget.EXCHANGE_RATE ||
                    intent.target == CalculatorTarget.SECONDARY_AMOUNT
            else -> false
        }
    }

    fun isCurrencyContextChange(intent: TransactionIntent): Boolean {
        return when (intent) {
            is TransactionIntent.SelectAccount,
            is TransactionIntent.SelectCurrency,
            is TransactionIntent.SwapTransferAccounts,
            is TransactionIntent.SwitchMode -> true
            else -> false
        }
    }

    suspend fun executeSave(
        ready: TransactionUiState.Ready,
        command: SaveTransactionCommand,
        saveAndNew: Boolean,
        ports: TransactionPorts,
        clock: Clock = Clock.System,
        ioDispatcher: CoroutineDispatcher? = null,
    ): SaveExecutionOutcome {
        return when (val commit = commitTransaction(ready.form, command, ports, ioDispatcher)) {
            is CommitOutcome.Failed -> SaveExecutionOutcome.Failed(commit.message)
            is CommitOutcome.AlreadySaved -> SaveExecutionOutcome.AlreadySavedDraft(commit.savedTransactionId)
            is CommitOutcome.Committed -> {
                runPostCommitCleanup(ready, command, commit.savedId, ports)
                if (saveAndNew) {
                    val freshOutcome = buildFreshReadyStateOrNull(
                        ready = ready,
                        ports = ports,
                        nowMillis = clock.now().toEpochMilliseconds(),
                        savedId = commit.savedId,
                    )
                    freshOutcome ?: SaveExecutionOutcome.Finished(
                        savedId = commit.savedId,
                        warningMessage = "Transaction saved, but failed to prepare a new form.",
                    )
                } else {
                    SaveExecutionOutcome.Finished(commit.savedId)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun commitTransaction(
        form: TransactionFormState,
        command: SaveTransactionCommand,
        ports: TransactionPorts,
        ioDispatcher: CoroutineDispatcher?,
    ): CommitOutcome {
        currentCoroutineContext().ensureActive()
        val commitContext = if (ioDispatcher != null) ioDispatcher + NonCancellable else NonCancellable
        return withContext(commitContext) {
            try {
                performCommitCriticalSection(form, command, ports)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CommitOutcome.Failed(e.localizedMessage ?: DEFAULT_SAVE_ERROR_MESSAGE)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun performCommitCriticalSection(
        form: TransactionFormState,
        command: SaveTransactionCommand,
        ports: TransactionPorts,
    ): CommitOutcome {
        val draftId = form.aiDraftId
        val existingSavedId = if (draftId != null && !form.isDuplicateDraftConfirmed) {
            ports.draftSessionStore.getSavedTransactionId(draftId)
        } else {
            null
        }
        if (existingSavedId != null) {
            return CommitOutcome.AlreadySaved(existingSavedId)
        }
        val savedId = ports.repository.saveTransaction(command)
        return if (savedId <= 0L) {
            CommitOutcome.Failed(DEFAULT_SAVE_ERROR_MESSAGE)
        } else {
            if (draftId != null) {
                try {
                    ports.draftSessionStore.markDraftSaved(draftId, savedId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to mark AI draft %s as saved for transaction %d", draftId, savedId)
                }
            }
            CommitOutcome.Committed(savedId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runPostCommitCleanup(
        ready: TransactionUiState.Ready,
        command: SaveTransactionCommand,
        savedId: Long,
        ports: TransactionPorts,
    ) {
        try {
            TransactionLoader.deleteUnusedStagedFiles(
                ports = ports,
                stagedFiles = ready.form.newlyStagedPictureFiles,
                keepFileName = command.attachedPicture,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete unused staged receipts after saving transaction %d", savedId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun buildFreshReadyStateOrNull(
        ready: TransactionUiState.Ready,
        ports: TransactionPorts,
        nowMillis: Long,
        savedId: Long,
    ): SaveExecutionOutcome.ResetNext? {
        return try {
            val freshCatalog = ports.repository.loadCatalog(editingTransactionId = null)
            val freshPrefs = ports.preferencesPort.readFormPreferences()
            val freshForm = TransactionInitialStateFactory.fromBlankNew(
                args = TransactionLaunchArgs.BlankNew(
                    accountId = ready.form.fromAccountId.takeIf { it > 0L },
                    initialMode = ready.form.mode,
                    initialDateTimeMillis = ready.form.dateTimeMillis,
                ),
                catalog = freshCatalog,
                prefs = freshPrefs,
                nowMillis = nowMillis,
            )
            val nextArgs = TransactionLaunchArgs.BlankNew(
                accountId = freshForm.fromAccountId.takeIf { it > 0L },
                initialMode = freshForm.mode,
                initialDateTimeMillis = freshForm.dateTimeMillis,
            )
            SaveExecutionOutcome.ResetNext(
                nextReady = TransactionUiState.Ready(
                    form = freshForm,
                    initialFormSnapshot = freshForm,
                    catalog = freshCatalog,
                    prefs = freshPrefs,
                ),
                nextLaunchArgs = nextArgs,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Transaction %d saved, but failed to prepare fresh form", savedId)
            null
        }
    }

    fun buildClearedForm(
        ready: TransactionUiState.Ready,
        nowMillis: Long,
    ): TransactionFormState {
        return TransactionInitialStateFactory.fromBlankNew(
            args = TransactionLaunchArgs.BlankNew(
                accountId = ready.form.fromAccountId.takeIf { it > 0L },
                initialMode = ready.form.mode,
                initialDateTimeMillis = ready.form.dateTimeMillis,
            ),
            catalog = ready.catalog,
            prefs = ready.prefs,
            nowMillis = nowMillis,
        ).copy(
            existingTransactionId = ready.form.existingTransactionId,
            aiDraftId = ready.form.aiDraftId,
            initialPersistedPictureFileName = ready.form.initialPersistedPictureFileName,
            newlyStagedPictureFiles = ready.form.newlyStagedPictureFiles,
            isFromNewBlankLaunch = ready.form.isFromNewBlankLaunch,
            isDuplicateDraftConfirmed = ready.form.isDuplicateDraftConfirmed,
        )
    }
}
