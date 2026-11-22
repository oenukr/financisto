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
import kotlin.time.Clock

internal sealed interface SaveExecutionOutcome {
    data class Finished(val savedId: Long) : SaveExecutionOutcome
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

    @Suppress("TooGenericExceptionCaught")
    suspend fun executeSave(
        ready: TransactionUiState.Ready,
        command: SaveTransactionCommand,
        saveAndNew: Boolean,
        ports: TransactionPorts,
        clock: Clock = Clock.System,
    ): SaveExecutionOutcome {
        return try {
            val savedId = ports.repository.saveTransaction(command)
            ready.form.aiDraftId?.let { draftId ->
                ports.draftSessionStore.markDraftSaved(draftId, savedId)
            }
            TransactionLoader.deleteUnusedStagedFiles(
                ports = ports,
                stagedFiles = ready.form.newlyStagedPictureFiles,
                keepFileName = command.attachedPicture,
            )
            if (saveAndNew) {
                SaveExecutionOutcome.ResetNext(
                    buildFreshReadyState(ready, ports, clock.now().toEpochMilliseconds())
                )
            } else {
                SaveExecutionOutcome.Finished(savedId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveExecutionOutcome.Failed(e.localizedMessage ?: "Failed to save transaction.")
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
        )
    }

    fun applyExternalEntityCreated(
        ready: TransactionUiState.Ready,
        refreshedCatalog: TransactionCatalog,
        kind: ExternalEntityKind,
        createdId: Long,
    ): TransactionUiState.Ready {
        val splitLocalId = (ready.activeSheet as? ActiveSheet.CategoryBrowser)?.forSplitLocalId
        val followUp = when (kind) {
            ExternalEntityKind.CATEGORY -> TransactionIntent.SelectCategory(createdId, forSplitLocalId = splitLocalId)
            ExternalEntityKind.LOCATION -> TransactionIntent.SelectLocation(createdId)
        }
        return TransactionReducer.reduce(ready.copy(catalog = refreshedCatalog), followUp)
    }
}
