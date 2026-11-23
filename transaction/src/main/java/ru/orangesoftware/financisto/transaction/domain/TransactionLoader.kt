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

    fun resolveEffectiveLaunchArgs(
        args: TransactionLaunchArgs,
        restoredForm: TransactionFormState?,
    ): TransactionLaunchArgs {
        val restoredEditId = restoredForm?.takeIf { it.isEditing && !it.isAiDraft }?.existingTransactionId
        return if (args is TransactionLaunchArgs.AiDraft && restoredEditId != null) {
            TransactionLaunchArgs.Edit(restoredEditId)
        } else {
            args
        }
    }

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
        val consumedDraftId = consumedTxId?.takeIf { restoredForm == null || restoredForm.isAiDraft }
        val isConsumedForDraft = consumedDraftId != null
        ports.draftSessionStore.acknowledgeDraftOpened(args.draft.draftId)
        val resolvedReceipt = consumedDraftId?.let {
            resolveConsumedDraftReceipt(it, args.draft.stagedReceiptFileName, ports)
        }
        val rawInitial = restoredSnapshot
            ?: TransactionInitialStateFactory.fromAiDraft(args.draft, catalog, isConsumedDraft = isConsumedForDraft)
        val initialForm = sanitizeConsumedDraftReceipt(
            form = rawInitial,
            draftReceipt = args.draft.stagedReceiptFileName,
            resolvedReceipt = resolvedReceipt,
            isConsumed = isConsumedForDraft,
            isBaseForm = restoredSnapshot == null,
            ports = ports,
        )
        val currentForm = if (restoredForm != null) {
            sanitizeConsumedDraftReceipt(
                form = restoredForm,
                draftReceipt = args.draft.stagedReceiptFileName,
                resolvedReceipt = resolvedReceipt,
                isConsumed = isConsumedForDraft,
                isBaseForm = false,
                ports = ports,
            )
        } else {
            initialForm
        }
        val effectiveConsumedTxId = consumedTxId?.takeIf { currentForm.isAiDraft }
        val sheet = if (effectiveConsumedTxId != null && !currentForm.isDuplicateDraftConfirmed) {
            ActiveSheet.ConsumedDraftPrompt(effectiveConsumedTxId)
        } else {
            null
        }
        return TransactionUiState.Ready(
            form = currentForm,
            initialFormSnapshot = initialForm,
            catalog = catalog,
            prefs = prefs,
            sheetStack = listOfNotNull(sheet),
            consumedDraftTransactionId = effectiveConsumedTxId,
        )
    }

    private suspend fun resolveConsumedDraftReceipt(
        consumedTxId: Long,
        draftReceipt: String?,
        ports: TransactionPorts,
    ): String? {
        val candidate = when (val editResult = ports.repository.loadForEdit(consumedTxId)) {
            is TransactionEditLoadResult.Loaded -> editResult.record.attachedPicture
            is TransactionEditLoadResult.NotFound,
            is TransactionEditLoadResult.RequiresLegacyFallback -> draftReceipt
        }
        return candidate?.takeIf { ports.receiptMediaPort.resolveFilePath(it) != null }
    }

    private fun sanitizeConsumedDraftReceipt(
        form: TransactionFormState,
        draftReceipt: String?,
        resolvedReceipt: String?,
        isConsumed: Boolean,
        isBaseForm: Boolean,
        ports: TransactionPorts,
    ): TransactionFormState {
        if (!isConsumed) return form
        val currentAttached = form.attachedPictureFileName
        val nextAttached = when {
            isBaseForm -> resolvedReceipt
            currentAttached == null -> null
            ports.receiptMediaPort.resolveFilePath(currentAttached) != null -> {
                if (currentAttached == draftReceipt && resolvedReceipt != draftReceipt) {
                    resolvedReceipt
                } else {
                    currentAttached
                }
            }
            currentAttached == draftReceipt -> resolvedReceipt
            else -> null
        }
        val filteredStaged = form.newlyStagedPictureFiles.filterNot { file ->
            file == resolvedReceipt || file == draftReceipt
        }
        return form.copy(
            attachedPictureFileName = nextAttached,
            initialPersistedPictureFileName = resolvedReceipt,
            newlyStagedPictureFiles = filteredStaged,
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
