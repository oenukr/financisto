// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.ExternalEntityKind
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
    data class ResetNext(val nextReady: TransactionUiState.Ready) : SaveExecutionOutcome
    data class Failed(val message: String) : SaveExecutionOutcome
}

internal object TransactionActionRunner {

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
            is TransactionIntent.OpenExternalCategoryCreator -> TransactionEffect.LaunchExternalCategoryCreator
            is TransactionIntent.OpenExternalLocationCreator -> TransactionEffect.LaunchExternalLocationCreator
            is TransactionIntent.OpenInLegacyClicked -> TransactionEffect.OpenLegacyEditor(currentLaunchArgs)
            else -> null
        }
    }

    suspend fun executeSave(
        ready: TransactionUiState.Ready,
        command: SaveTransactionCommand,
        saveAndNew: Boolean,
        ports: TransactionPorts,
        clock: Clock = Clock.System,
    ): SaveExecutionOutcome {
        val commitResult = commitTransaction(command, ports)
        val savedId = commitResult.getOrNull()
        return when {
            commitResult.isFailure -> SaveExecutionOutcome.Failed(
                commitResult.exceptionOrNull()?.localizedMessage ?: "Failed to save transaction."
            )
            savedId == null || savedId <= 0L -> SaveExecutionOutcome.Failed("Failed to save transaction.")
            else -> {
                runPostCommitCleanup(ready, command, savedId, ports)
                if (saveAndNew) {
                    val freshReady = buildFreshReadyStateOrNull(
                        ready = ready,
                        ports = ports,
                        nowMillis = clock.now().toEpochMilliseconds(),
                        savedId = savedId,
                    )
                    if (freshReady != null) {
                        SaveExecutionOutcome.ResetNext(freshReady)
                    } else {
                        SaveExecutionOutcome.Finished(
                            savedId = savedId,
                            warningMessage = "Transaction saved, but failed to prepare a new form.",
                        )
                    }
                } else {
                    SaveExecutionOutcome.Finished(savedId)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun commitTransaction(
        command: SaveTransactionCommand,
        ports: TransactionPorts,
    ): Result<Long> {
        return try {
            Result.success(ports.repository.saveTransaction(command))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runPostCommitCleanup(
        ready: TransactionUiState.Ready,
        command: SaveTransactionCommand,
        savedId: Long,
        ports: TransactionPorts,
    ) {
        ready.form.aiDraftId?.let { draftId ->
            try {
                ports.draftSessionStore.markDraftSaved(draftId, savedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to mark AI draft %s as saved for transaction %d", draftId, savedId)
            }
        }
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
    ): TransactionUiState.Ready? {
        return try {
            buildFreshReadyState(ready, ports, nowMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Transaction %d saved, but failed to prepare fresh form", savedId)
            null
        }
    }

    private suspend fun buildFreshReadyState(
        ready: TransactionUiState.Ready,
        ports: TransactionPorts,
        nowMillis: Long,
    ): TransactionUiState.Ready {
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
        return TransactionUiState.Ready(
            form = freshForm,
            initialFormSnapshot = freshForm,
            catalog = freshCatalog,
            prefs = freshPrefs,
        )
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

    fun applyExternalEntityCreated(
        ready: TransactionUiState.Ready,
        refreshedCatalog: TransactionCatalog,
        kind: ExternalEntityKind,
        createdId: Long,
    ): TransactionUiState.Ready {
        val withCatalog = ready.copy(catalog = refreshedCatalog)
        return when (kind) {
            ExternalEntityKind.LOCATION ->
                TransactionReducer.reduce(withCatalog, TransactionIntent.SelectLocation(createdId))
            ExternalEntityKind.CATEGORY -> {
                val splitLocalId = (ready.activeSheet as? ActiveSheet.CategoryBrowser)?.forSplitLocalId
                    ?: ready.activeSplitEditor?.split?.localId
                when {
                    splitLocalId != null -> TransactionReducer.reduce(
                        withCatalog,
                        TransactionIntent.SelectCategory(createdId, forSplitLocalId = splitLocalId),
                    )
                    ready.form.isSplitMode -> withCatalog
                    else -> TransactionReducer.reduce(
                        withCatalog,
                        TransactionIntent.SelectCategory(createdId, forSplitLocalId = null),
                    )
                }
            }
        }
    }
}
