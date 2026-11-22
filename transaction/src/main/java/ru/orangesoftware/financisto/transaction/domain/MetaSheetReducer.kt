// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import java.util.Calendar

internal object MetaSheetReducer {
    private const val DEFAULT_DECIMALS = 2

    fun reduce(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.OpenAccountPicker,
            is TransactionIntent.OpenCurrencyPicker,
            is TransactionIntent.OpenCategoryBrowser,
            is TransactionIntent.OpenProjectPicker,
            is TransactionIntent.OpenDatePicker,
            is TransactionIntent.OpenTimePicker,
            is TransactionIntent.OpenStatusPicker,
            is TransactionIntent.OpenCalculator,
            is TransactionIntent.DismissSheet,
            is TransactionIntent.DuplicateDraftAnyway -> reduceSheetNavigation(state, intent)
            else -> reduceFieldMetadata(state, intent)
        }
    }

    private fun reduceSheetNavigation(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.OpenAccountPicker ->
                state.copy(sheetStack = listOf(ActiveSheet.AccountPicker(intent.isDestination)))
            is TransactionIntent.OpenCurrencyPicker ->
                state.copy(sheetStack = listOf(ActiveSheet.CurrencyPicker))
            is TransactionIntent.OpenCategoryBrowser -> openPossiblyNestedSheet(
                state = state,
                sheet = ActiveSheet.CategoryBrowser(intent.forSplitLocalId),
                isForSplit = intent.forSplitLocalId != null,
            )
            is TransactionIntent.OpenProjectPicker -> openPossiblyNestedSheet(
                state = state,
                sheet = ActiveSheet.ProjectPicker(intent.forSplitLocalId),
                isForSplit = intent.forSplitLocalId != null,
            )
            is TransactionIntent.OpenDatePicker -> state.copy(sheetStack = listOf(ActiveSheet.DatePicker))
            is TransactionIntent.OpenTimePicker -> state.copy(sheetStack = listOf(ActiveSheet.TimePicker))
            is TransactionIntent.OpenStatusPicker -> state.copy(sheetStack = listOf(ActiveSheet.StatusPicker))
            is TransactionIntent.OpenCalculator -> openCalculator(state, intent)
            is TransactionIntent.DismissSheet -> state.copy(sheetStack = state.sheetStack.dropLast(1))
            is TransactionIntent.DuplicateDraftAnyway -> state.copy(sheetStack = emptyList())
            else -> state
        }
    }

    private fun reduceFieldMetadata(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.SelectStatus ->
                state.copy(form = state.form.copy(status = intent.status), sheetStack = emptyList())
            is TransactionIntent.ToggleCCardPayment ->
                state.copy(form = state.form.copy(isCCardPayment = intent.checked))
            is TransactionIntent.SelectProject -> selectProject(state, intent.projectId, intent.forSplitLocalId)
            is TransactionIntent.CreatePendingProject ->
                createPendingProject(state, intent.name, intent.forSplitLocalId)
            is TransactionIntent.ClearProject -> state.copy(
                form = state.form.copy(projectId = TransactionIds.NO_PROJECT_ID, pendingProjectName = null)
            )
            is TransactionIntent.UpdateDate -> updateDate(state, intent.year, intent.monthZeroBased, intent.dayOfMonth)
            is TransactionIntent.UpdateTime -> updateTime(state, intent.hourOfDay, intent.minute)
            is TransactionIntent.AttachCapturedPhotoFile -> state.copy(
                form = state.form.copy(
                    attachedPictureFileName = intent.fileName,
                    newlyStagedPictureFiles = state.form.newlyStagedPictureFiles + intent.fileName,
                )
            )
            is TransactionIntent.RemoveReceipt -> state.copy(
                form = state.form.copy(attachedPictureFileName = null)
            )
            else -> state
        }
    }

    private fun openPossiblyNestedSheet(
        state: TransactionUiState.Ready,
        sheet: ActiveSheet,
        isForSplit: Boolean,
    ): TransactionUiState.Ready {
        val nextStack = if (isForSplit) {
            state.sheetStack.filterIsInstance<ActiveSheet.SplitItemEditor>() + sheet
        } else {
            listOf(sheet)
        }
        return state.copy(sheetStack = nextStack)
    }

    private fun selectProject(
        state: TransactionUiState.Ready,
        projectId: Long,
        forSplitLocalId: Long?,
    ): TransactionUiState.Ready {
        return if (forSplitLocalId != null) {
            val editor = state.activeSplitEditor?.takeIf { it.split.localId == forSplitLocalId }
            if (editor != null) {
                state.copy(sheetStack = listOf(editor.copy(split = editor.split.copy(projectId = projectId))))
            } else {
                val updated = state.form.splits.map {
                    if (it.localId == forSplitLocalId) it.copy(projectId = projectId) else it
                }
                state.copy(form = state.form.copy(splits = updated), sheetStack = emptyList())
            }
        } else {
            state.copy(
                form = state.form.copy(projectId = projectId, pendingProjectName = null),
                sheetStack = emptyList(),
            )
        }
    }

    private fun createPendingProject(
        state: TransactionUiState.Ready,
        name: String,
        forSplitLocalId: Long?,
    ): TransactionUiState.Ready {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || forSplitLocalId != null) return state
        return state.copy(
            form = state.form.copy(projectId = TransactionIds.NO_PROJECT_ID, pendingProjectName = trimmed),
            sheetStack = emptyList(),
        )
    }

    private fun updateDate(
        state: TransactionUiState.Ready,
        year: Int,
        monthZeroBased: Int,
        dayOfMonth: Int,
    ): TransactionUiState.Ready {
        val cal = Calendar.getInstance().apply { timeInMillis = state.form.dateTimeMillis }
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthZeroBased)
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        return state.copy(form = state.form.copy(dateTimeMillis = cal.timeInMillis), sheetStack = emptyList())
    }

    private fun updateTime(
        state: TransactionUiState.Ready,
        hourOfDay: Int,
        minute: Int,
    ): TransactionUiState.Ready {
        val cal = Calendar.getInstance().apply { timeInMillis = state.form.dateTimeMillis }
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        return state.copy(form = state.form.copy(dateTimeMillis = cal.timeInMillis), sheetStack = emptyList())
    }

    private fun openCalculator(
        state: TransactionUiState.Ready,
        intent: TransactionIntent.OpenCalculator,
    ): TransactionUiState.Ready {
        val pDec = state.primaryCurrency?.decimals ?: DEFAULT_DECIMALS
        val initialText = when (intent.target) {
            CalculatorTarget.PRIMARY_AMOUNT -> state.form.primaryAmountInput
            CalculatorTarget.SECONDARY_AMOUNT -> state.form.secondaryAmountInput
            CalculatorTarget.EXCHANGE_RATE -> state.form.exchangeRateInput
            CalculatorTarget.SPLIT_AMOUNT -> {
                val split = state.activeSplitEditor?.split?.takeIf { it.localId == intent.splitLocalId }
                    ?: state.form.splits.firstOrNull { it.localId == intent.splitLocalId }
                AmountScale.formatEditableAmount(split?.amountMinor ?: 0L, pDec)
            }
        }
        val sheet = ActiveSheet.Calculator(
            target = intent.target,
            initialText = initialText,
            splitLocalId = intent.splitLocalId,
        )
        return openPossiblyNestedSheet(
            state = state,
            sheet = sheet,
            isForSplit = intent.target == CalculatorTarget.SPLIT_AMOUNT,
        )
    }
}
