// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.port.TransactionPorts

internal object TransactionLoader {

    suspend fun loadInitial(
        launchArgs: TransactionLaunchArgs,
        ports: TransactionPorts,
        restoredForm: TransactionFormState?,
        restoredSnapshot: TransactionFormState?,
        nowMillis: Long,
    ): TransactionUiState {
        val prefs = ports.preferencesPort.readFormPreferences()
        return when (launchArgs) {
            is TransactionLaunchArgs.Edit -> loadEditState(launchArgs, ports, prefs, restoredForm, restoredSnapshot)
            is TransactionLaunchArgs.AiDraft ->
                loadAiDraftState(launchArgs, ports, prefs, restoredForm, restoredSnapshot)
            is TransactionLaunchArgs.BlankNew ->
                loadBlankState(launchArgs, ports, prefs, restoredForm, restoredSnapshot, nowMillis)
        }
    }

    private suspend fun loadBlankState(
        args: TransactionLaunchArgs.BlankNew,
        ports: TransactionPorts,
        prefs: TransactionFormPreferences,
        restoredForm: TransactionFormState?,
        restoredSnapshot: TransactionFormState?,
        nowMillis: Long,
    ): TransactionUiState {
        val catalog = ports.repository.loadCatalog(editingTransactionId = null)
        if (catalog.accounts.none { it.isActive }) {
            return TransactionUiState.NoAccounts
        }
        val initialForm = restoredSnapshot
            ?: TransactionInitialStateFactory.fromBlankNew(args, catalog, prefs, nowMillis)
        val currentForm = restoredForm ?: initialForm
        return TransactionUiState.Ready(
            form = currentForm,
            initialFormSnapshot = initialForm,
            catalog = catalog,
            prefs = prefs,
        )
    }

    private suspend fun loadEditState(
        args: TransactionLaunchArgs.Edit,
        ports: TransactionPorts,
        prefs: TransactionFormPreferences,
        restoredForm: TransactionFormState?,
        restoredSnapshot: TransactionFormState?,
    ): TransactionUiState {
        return when (val result = ports.repository.loadForEdit(args.transactionId)) {
            is TransactionEditLoadResult.NotFound -> TransactionUiState.NotFound(args.transactionId)
            is TransactionEditLoadResult.RequiresLegacyFallback -> TransactionUiState.LegacyRedirecting
            is TransactionEditLoadResult.Loaded -> {
                val catalog = ports.repository.loadCatalog(editingTransactionId = result.record.id)
                val initialForm = restoredSnapshot
                    ?: TransactionInitialStateFactory.fromLoadedRecord(result.record, catalog)
                val currentForm = restoredForm ?: initialForm
                TransactionUiState.Ready(
                    form = currentForm,
                    initialFormSnapshot = initialForm,
                    catalog = catalog,
                    prefs = prefs,
                )
            }
        }
    }

    private suspend fun loadAiDraftState(
        args: TransactionLaunchArgs.AiDraft,
        ports: TransactionPorts,
        prefs: TransactionFormPreferences,
        restoredForm: TransactionFormState?,
        restoredSnapshot: TransactionFormState?,
    ): TransactionUiState {
        val catalog = ports.repository.loadCatalog(editingTransactionId = null)
        if (catalog.accounts.none { it.isActive }) {
            return TransactionUiState.NoAccounts
        }
        val consumedTxId = ports.draftSessionStore.getSavedTransactionId(args.draft.draftId)
        val isConsumed = consumedTxId != null
        ports.draftSessionStore.acknowledgeDraftOpened(args.draft.draftId)
        val committedReceipt = args.draft.stagedReceiptFileName
        val rawInitial = restoredSnapshot
            ?: TransactionInitialStateFactory.fromAiDraft(args.draft, catalog, isConsumedDraft = isConsumed)
        val initialForm = sanitizeConsumedDraftReceipt(rawInitial, committedReceipt, isConsumed)
        val currentForm = sanitizeConsumedDraftReceipt(restoredForm ?: initialForm, committedReceipt, isConsumed)
        val sheet = if (consumedTxId != null && !currentForm.isDuplicateDraftConfirmed) {
            ActiveSheet.ConsumedDraftPrompt(consumedTxId)
        } else {
            null
        }
        return TransactionUiState.Ready(
            form = currentForm,
            initialFormSnapshot = initialForm,
            catalog = catalog,
            prefs = prefs,
            sheetStack = listOfNotNull(sheet),
            consumedDraftTransactionId = consumedTxId,
        )
    }

    private fun sanitizeConsumedDraftReceipt(
        form: TransactionFormState,
        committedReceipt: String?,
        isConsumed: Boolean,
    ): TransactionFormState {
        if (!isConsumed || committedReceipt == null) return form
        return form.copy(
            initialPersistedPictureFileName = form.initialPersistedPictureFileName ?: committedReceipt,
            newlyStagedPictureFiles = form.newlyStagedPictureFiles.filterNot { it == committedReceipt },
        )
    }

    suspend fun deleteUnusedStagedFiles(
        ports: TransactionPorts,
        stagedFiles: List<String>,
        keepFileName: String?,
    ) {
        stagedFiles.distinct().forEach { fileName ->
            if (fileName != keepFileName) {
                ports.receiptMediaPort.deleteStagedFile(fileName)
            }
        }
    }
}
