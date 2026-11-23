// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.ui.components.AccountPickerSheet
import ru.orangesoftware.financisto.transaction.ui.components.CalculatorBottomSheet
import ru.orangesoftware.financisto.transaction.ui.components.CategoryBrowserSheet
import ru.orangesoftware.financisto.transaction.ui.components.ConsumedDraftAlertDialog
import ru.orangesoftware.financisto.transaction.ui.components.CurrencyPickerSheet
import ru.orangesoftware.financisto.transaction.ui.components.DiscardTransactionDialog
import ru.orangesoftware.financisto.transaction.ui.components.ProjectPickerSheet
import ru.orangesoftware.financisto.transaction.ui.components.SplitEditorDraft
import ru.orangesoftware.financisto.transaction.ui.components.SplitItemEditorSheet
import ru.orangesoftware.financisto.transaction.ui.components.StatusPickerSheet
import ru.orangesoftware.financisto.transaction.ui.components.TransactionDatePickerDialog
import ru.orangesoftware.financisto.transaction.ui.components.TransactionTimePickerDialog

private const val DefaultDecimals = 2

@Composable
internal fun TransactionActiveSheetHost(
    state: TransactionUiState.Ready,
    onIntent: (TransactionIntent) -> Unit,
    onDismissAll: () -> Unit,
    enabled: Boolean = !state.isSaving,
    splitEditorDraft: SplitEditorDraft? = null,
    onSplitEditorDraftChanged: (SplitEditorDraft?) -> Unit = {},
) {
    val sheet = state.activeSheet ?: return
    when (sheet) {
        is ActiveSheet.DatePicker,
        is ActiveSheet.TimePicker,
        is ActiveSheet.DiscardConfirmation,
        is ActiveSheet.ConsumedDraftPrompt,
        -> TransactionDialogHost(sheet = sheet, state = state, onIntent = onIntent, onDismiss = onDismissAll)

        is ActiveSheet.StatusPicker,
        is ActiveSheet.AccountPicker,
        is ActiveSheet.CurrencyPicker,
        is ActiveSheet.ProjectPicker,
        -> TransactionPickerSheetHost(sheet = sheet, state = state, onIntent = onIntent, onDismiss = onDismissAll)

        is ActiveSheet.CategoryBrowser -> CategoryBrowserSheetHost(
            sheet = sheet,
            state = state,
            splitEditorDraft = splitEditorDraft,
            onSplitEditorDraftChanged = onSplitEditorDraftChanged,
            onIntent = onIntent,
            onDismiss = onDismissAll,
        )
        is ActiveSheet.SplitItemEditor -> SplitItemEditorSheet(
            split = sheet.split,
            isNew = sheet.isNew,
            categories = state.catalog.categories,
            projects = state.catalog.projects,
            currencyDecimals = state.primaryCurrency?.decimals ?: DefaultDecimals,
            onUpdateDraft = { onIntent(TransactionIntent.UpdateSplitDraft(it)) },
            onPickCategory = { onIntent(TransactionIntent.OpenCategoryBrowser(sheet.split.localId)) },
            onPickProject = { onIntent(TransactionIntent.OpenProjectPicker(sheet.split.localId)) },
            onOpenCalculator = {
                onIntent(
                    TransactionIntent.OpenCalculator(
                        target = CalculatorTarget.SPLIT_AMOUNT,
                        splitLocalId = sheet.split.localId,
                    ),
                )
            },
            onSave = { onIntent(TransactionIntent.SaveSplitItem(it)) },
            onDismiss = onDismissAll,
            enabled = enabled,
            cachedDraft = splitEditorDraft,
            onDraftChanged = onSplitEditorDraftChanged,
        )
        is ActiveSheet.Calculator -> CalculatorBottomSheet(
            target = sheet.target,
            initialText = sheet.initialText,
            onDismiss = onDismissAll,
            onApplyValue = { value ->
                if (enabled && sheet.target == CalculatorTarget.SPLIT_AMOUNT && sheet.splitLocalId != null) {
                    splitEditorDraft?.takeIf { it.splitLocalId == sheet.splitLocalId }?.let { draft ->
                        onSplitEditorDraftChanged(draft.copy(forceSyncAmount = true))
                    }
                }
                onIntent(TransactionIntent.ApplyCalculatorResult(sheet.target, value, sheet.splitLocalId))
            },
        )
    }
}

@Composable
private fun TransactionDialogHost(
    sheet: ActiveSheet,
    state: TransactionUiState.Ready,
    onIntent: (TransactionIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        is ActiveSheet.DatePicker -> TransactionDatePickerDialog(
            dateTimeMillis = state.form.dateTimeMillis,
            onDateSelected = { y, m, d -> onIntent(TransactionIntent.UpdateDate(y, m, d)) },
            onDismiss = onDismiss,
        )
        is ActiveSheet.TimePicker -> TransactionTimePickerDialog(
            dateTimeMillis = state.form.dateTimeMillis,
            is24Hour = state.prefs.is24HourFormat,
            onTimeSelected = { h, min -> onIntent(TransactionIntent.UpdateTime(h, min)) },
            onDismiss = onDismiss,
        )
        is ActiveSheet.DiscardConfirmation -> DiscardTransactionDialog(
            isAiDraft = state.form.isAiDraft,
            onConfirmDiscard = { onIntent(TransactionIntent.ConfirmDiscard) },
            onKeepEditing = onDismiss,
        )
        is ActiveSheet.ConsumedDraftPrompt -> ConsumedDraftAlertDialog(
            savedTransactionId = sheet.savedTransactionId,
            onOpenSaved = { onIntent(TransactionIntent.OpenSavedTransaction(sheet.savedTransactionId)) },
            onCreateDuplicate = { onIntent(TransactionIntent.DuplicateDraftAnyway) },
            onCancel = { onIntent(TransactionIntent.ConfirmDiscard) },
        )
        else -> Unit
    }
}

@Composable
private fun TransactionPickerSheetHost(
    sheet: ActiveSheet,
    state: TransactionUiState.Ready,
    onIntent: (TransactionIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        is ActiveSheet.StatusPicker -> StatusPickerSheet(
            selectedStatus = state.form.status,
            onSelect = { onIntent(TransactionIntent.SelectStatus(it)) },
            onDismiss = onDismiss,
        )
        is ActiveSheet.AccountPicker -> AccountPickerSheet(
            title = stringResource(
                if (sheet.isDestination) R.string.tx_sheet_select_to_account else R.string.tx_sheet_select_account,
            ),
            accounts = state.catalog.accounts,
            selectedAccountId = if (sheet.isDestination) state.form.toAccountId else state.form.fromAccountId,
            onSelect = { onIntent(TransactionIntent.SelectAccount(it, sheet.isDestination)) },
            onDismiss = onDismiss,
        )
        is ActiveSheet.CurrencyPicker -> CurrencyPickerSheet(
            currencies = state.catalog.currencies,
            accountCurrencyCode = state.fromAccount?.currencyCode.orEmpty(),
            selectedOriginalCurrencyId = state.form.originalCurrencyId,
            onSelect = { onIntent(TransactionIntent.SelectCurrency(it)) },
            onDismiss = onDismiss,
        )
        is ActiveSheet.ProjectPicker -> {
            val selectedProjectId = resolveSelectedProjectId(state, sheet.forSplitLocalId)
            ProjectPickerSheet(
                projects = state.catalog.projects,
                selectedProjectId = selectedProjectId,
                pendingProjectName = if (sheet.forSplitLocalId == null) state.form.pendingProjectName else null,
                allowCreateInline = sheet.forSplitLocalId == null,
                onSelectProject = { onIntent(TransactionIntent.SelectProject(it, sheet.forSplitLocalId)) },
                onCreatePendingProject = {
                    onIntent(TransactionIntent.CreatePendingProject(it, sheet.forSplitLocalId))
                },
                onDismiss = onDismiss,
            )
        }
        else -> Unit
    }
}

@Composable
private fun CategoryBrowserSheetHost(
    sheet: ActiveSheet.CategoryBrowser,
    state: TransactionUiState.Ready,
    splitEditorDraft: SplitEditorDraft?,
    onSplitEditorDraftChanged: (SplitEditorDraft?) -> Unit,
    onIntent: (TransactionIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val splitDraft = sheet.forSplitLocalId?.let { splitId ->
        state.activeSplitEditor?.split?.takeIf { it.localId == splitId }
            ?: state.form.splits.firstOrNull { it.localId == splitId }
    }
    val desiredIncome = splitDraft?.isIncome ?: (state.form.mode == TransactionMode.INCOME)
    val includeAll = sheet.forSplitLocalId == null && state.form.mode == TransactionMode.TRANSFER
    val categories = remember(state.catalog.categories, includeAll, desiredIncome) {
        if (includeAll) {
            state.catalog.categories
        } else {
            state.catalog.categories
                .filter { it.isIncome == desiredIncome }
                .ifEmpty { state.catalog.categories }
        }
    }
    val selectedCategoryId = if (sheet.forSplitLocalId != null) {
        splitDraft?.categoryId ?: TransactionIds.NO_CATEGORY_ID
    } else {
        state.form.categoryId
    }

    CategoryBrowserSheet(
        categories = categories,
        selectedCategoryId = selectedCategoryId,
        onSelect = { categoryId ->
            if (!state.isSaving && sheet.forSplitLocalId != null) {
                splitEditorDraft?.takeIf { it.splitLocalId == sheet.forSplitLocalId }?.let { draft ->
                    onSplitEditorDraftChanged(draft.copy(forceSyncIsIncome = true))
                }
            }
            onIntent(TransactionIntent.SelectCategory(categoryId, sheet.forSplitLocalId))
        },
        onCreateCategory = { onIntent(TransactionIntent.OpenExternalCategoryCreator) },
        onDismiss = onDismiss,
    )
}

private fun resolveSelectedProjectId(
    state: TransactionUiState.Ready,
    forSplitLocalId: Long?,
): Long {
    if (forSplitLocalId == null) return state.form.projectId
    val activeSplit = state.activeSplitEditor?.split?.takeIf { it.localId == forSplitLocalId }
    val split = activeSplit ?: state.form.splits.firstOrNull { it.localId == forSplitLocalId }
    return split?.projectId ?: TransactionIds.NO_PROJECT_ID
}
