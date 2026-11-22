// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import kotlinx.coroutines.CancellationException
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.ExternalEntityKind
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.port.TransactionPorts

internal sealed interface SaveExecutionOutcome {
    data class Finished(val savedId: Long) : SaveExecutionOutcome
    data class ResetNext(val nextReady: TransactionUiState.Ready) : SaveExecutionOutcome
    data class Failed(val message: String) : SaveExecutionOutcome
}

internal object TransactionActionRunner {

    @Suppress("TooGenericExceptionCaught")
    suspend fun executeSave(
        ready: TransactionUiState.Ready,
        command: ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand,
        saveAndNew: Boolean,
        ports: TransactionPorts,
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
                SaveExecutionOutcome.ResetNext(buildFreshReadyState(ready, ports))
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
            nowMillis = ready.form.dateTimeMillis,
        )
        return TransactionUiState.Ready(
            form = freshForm,
            initialFormSnapshot = freshForm,
            catalog = freshCatalog,
            prefs = freshPrefs,
        )
    }

    fun buildClearedForm(ready: TransactionUiState.Ready): TransactionFormState {
        return TransactionInitialStateFactory.fromBlankNew(
            args = TransactionLaunchArgs.BlankNew(
                accountId = ready.form.fromAccountId.takeIf { it > 0L },
                initialMode = ready.form.mode,
                initialDateTimeMillis = ready.form.dateTimeMillis,
            ),
            catalog = ready.catalog,
            prefs = ready.prefs,
            nowMillis = ready.form.dateTimeMillis,
        ).copy(
            existingTransactionId = ready.form.existingTransactionId,
            aiDraftId = ready.form.aiDraftId,
            initialPersistedPictureFileName = ready.form.initialPersistedPictureFileName,
            newlyStagedPictureFiles = ready.form.newlyStagedPictureFiles,
            isFromNewBlankLaunch = ready.form.isFromNewBlankLaunch,
        )
    }

    suspend fun reloadCatalogAndSelect(
        ready: TransactionUiState.Ready,
        kind: ExternalEntityKind,
        createdId: Long,
        ports: TransactionPorts,
    ): TransactionUiState.Ready {
        val editId = ready.form.existingTransactionId.takeIf { it > 0L }
        val catalog = ports.repository.loadCatalog(editingTransactionId = editId)
        val followUp = when (kind) {
            ExternalEntityKind.CATEGORY -> TransactionIntent.SelectCategory(createdId)
            ExternalEntityKind.LOCATION -> TransactionIntent.SelectLocation(createdId)
        }
        return TransactionReducer.reduce(ready.copy(catalog = catalog), followUp)
    }
}
