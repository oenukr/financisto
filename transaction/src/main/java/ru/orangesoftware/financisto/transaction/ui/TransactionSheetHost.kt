// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui

import androidx.compose.runtime.Composable
import ru.orangesoftware.financisto.transaction.domain.TransactionValidator
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.ui.components.AccountPickerSheet
import ru.orangesoftware.financisto.transaction.ui.components.CalculatorBottomSheet
import ru.orangesoftware.financisto.transaction.ui.components.CategoryBrowserSheet
import ru.orangesoftware.financisto.transaction.ui.components.ConsumedDraftAlertDialog
import ru.orangesoftware.financisto.transaction.ui.components.CurrencyPickerSheet
import ru.orangesoftware.financisto.transaction.ui.components.DiscardTransactionDialog
import ru.orangesoftware.financisto.transaction.ui.components.PlatformDatePickerEffect
import ru.orangesoftware.financisto.transaction.ui.components.PlatformTimePickerEffect
import ru.orangesoftware.financisto.transaction.ui.components.ProjectPickerSheet
import ru.orangesoftware.financisto.transaction.ui.components.SplitItemEditorSheet
import ru.orangesoftware.financisto.transaction.ui.components.StatusPickerSheet

private const val DEFAULT_DECIMALS = 2

@Composable
internal fun TransactionActiveSheetHost(
    state: TransactionUiState.Ready,
    onIntent: (TransactionIntent) -> Unit,
    onDismissAll: () -> Unit,
) {
    val sheet = state.activeSheet ?: return
    val (pCur, _) = TransactionValidator.resolveCurrencyMeta(state.form, state.catalog)
    val pDecimals = pCur?.decimals ?: DEFAULT_DECIMALS
    val fromAcc = state.catalog.accounts.firstOrNull { it.id == state.form.fromAccountId }

    when (sheet) {
        is ActiveSheet.DatePicker -> PlatformDatePickerEffect(
            dateTimeMillis = state.form.dateTimeMillis,
            onDateSelected = { y, m, d -> onIntent(TransactionIntent.UpdateDate(y, m, d)) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.TimePicker -> PlatformTimePickerEffect(
            dateTimeMillis = state.form.dateTimeMillis,
            is24Hour = state.prefs.is24HourFormat,
            onTimeSelected = { h, min -> onIntent(TransactionIntent.UpdateTime(h, min)) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.StatusPicker -> StatusPickerSheet(
            selectedStatus = state.form.status,
            onSelect = { onIntent(TransactionIntent.SelectStatus(it)) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.AccountPicker -> AccountPickerSheet(
            title = if (sheet.isDestination) "Select To Account" else "Select Account",
            accounts = state.catalog.accounts,
            selectedAccountId = if (sheet.isDestination) state.form.toAccountId else state.form.fromAccountId,
            onSelect = { onIntent(TransactionIntent.SelectAccount(it, sheet.isDestination)) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.CurrencyPicker -> CurrencyPickerSheet(
            currencies = state.catalog.currencies,
            accountCurrencyCode = fromAcc?.currencyCode.orEmpty(),
            selectedOriginalCurrencyId = state.form.originalCurrencyId,
            onSelect = { onIntent(TransactionIntent.SelectCurrency(it)) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.CategoryBrowser -> CategoryBrowserSheet(
            categories = state.catalog.categories.filter {
                state.form.mode == TransactionMode.TRANSFER ||
                    it.isIncome == (state.form.mode == TransactionMode.INCOME)
            }.ifEmpty { state.catalog.categories },
            selectedCategoryId = if (sheet.forSplitLocalId != null) {
                state.form.splits.firstOrNull { it.localId == sheet.forSplitLocalId }?.categoryId ?: 0L
            } else {
                state.form.categoryId
            },
            onSelect = { onIntent(TransactionIntent.SelectCategory(it, sheet.forSplitLocalId)) },
            onCreateCategory = { onIntent(TransactionIntent.OpenExternalCategoryCreator) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.SplitItemEditor -> SplitItemEditorSheet(
            split = sheet.split,
            isNew = sheet.isNew,
            categories = state.catalog.categories,
            projects = state.catalog.projects,
            currencyDecimals = pDecimals,
            onPickCategory = { onIntent(TransactionIntent.OpenCategoryBrowser(sheet.split.localId)) },
            onPickProject = { onIntent(TransactionIntent.OpenProjectPicker(sheet.split.localId)) },
            onOpenCalculator = {
                onIntent(
                    TransactionIntent.OpenCalculator(
                        target = ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget.SPLIT_AMOUNT,
                        splitLocalId = sheet.split.localId,
                    )
                )
            },
            onSave = { onIntent(TransactionIntent.SaveSplitItem(it)) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.ProjectPicker -> ProjectPickerSheet(
            projects = state.catalog.projects,
            selectedProjectId = if (sheet.forSplitLocalId != null) {
                state.form.splits.firstOrNull { it.localId == sheet.forSplitLocalId }?.projectId ?: 0L
            } else {
                state.form.projectId
            },
            pendingProjectName = if (sheet.forSplitLocalId == null) state.form.pendingProjectName else null,
            allowCreateInline = sheet.forSplitLocalId == null,
            onSelectProject = { onIntent(TransactionIntent.SelectProject(it, sheet.forSplitLocalId)) },
            onCreatePendingProject = { onIntent(TransactionIntent.CreatePendingProject(it, sheet.forSplitLocalId)) },
            onDismiss = onDismissAll,
        )
        is ActiveSheet.Calculator -> CalculatorBottomSheet(
            target = sheet.target,
            initialText = sheet.initialText,
            onDismiss = onDismissAll,
            onApplyValue = {
                onIntent(TransactionIntent.ApplyCalculatorResult(sheet.target, it, sheet.splitLocalId))
            },
        )
        is ActiveSheet.DiscardConfirmation -> DiscardTransactionDialog(
            isAiDraft = state.form.isAiDraft,
            onConfirmDiscard = { onIntent(TransactionIntent.ConfirmDiscard) },
            onKeepEditing = onDismissAll,
        )
        is ActiveSheet.ConsumedDraftPrompt -> ConsumedDraftAlertDialog(
            savedTransactionId = sheet.savedTransactionId,
            onOpenSaved = { onIntent(TransactionIntent.OpenSavedTransaction(sheet.savedTransactionId)) },
            onCreateDuplicate = { onIntent(TransactionIntent.DuplicateDraftAnyway) },
            onCancel = { onIntent(TransactionIntent.ConfirmDiscard) },
        )
    }
}
