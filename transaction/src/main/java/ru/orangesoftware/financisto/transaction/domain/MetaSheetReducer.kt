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
            is TransactionIntent.OpenAccountPicker ->
                state.copy(activeSheet = ActiveSheet.AccountPicker(intent.isDestination))
            is TransactionIntent.OpenCurrencyPicker -> state.copy(activeSheet = ActiveSheet.CurrencyPicker)
            is TransactionIntent.OpenCategoryBrowser ->
                state.copy(activeSheet = ActiveSheet.CategoryBrowser(intent.forSplitLocalId))
            is TransactionIntent.OpenProjectPicker ->
                state.copy(activeSheet = ActiveSheet.ProjectPicker(intent.forSplitLocalId))
            is TransactionIntent.OpenDatePicker -> state.copy(activeSheet = ActiveSheet.DatePicker)
            is TransactionIntent.OpenTimePicker -> state.copy(activeSheet = ActiveSheet.TimePicker)
            is TransactionIntent.OpenStatusPicker -> state.copy(activeSheet = ActiveSheet.StatusPicker)
            is TransactionIntent.DismissSheet -> state.copy(activeSheet = null)
            is TransactionIntent.SelectStatus ->
                state.copy(form = state.form.copy(status = intent.status), activeSheet = null)
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
            is TransactionIntent.OpenCalculator -> openCalculator(state, intent)
            else -> state
        }
    }

    private fun selectProject(
        state: TransactionUiState.Ready,
        projectId: Long,
        forSplitLocalId: Long?,
    ): TransactionUiState.Ready {
        if (forSplitLocalId != null) {
            val activeSheet = state.activeSheet
            if (activeSheet is ActiveSheet.SplitItemEditor && activeSheet.split.localId == forSplitLocalId) {
                return state.copy(
                    activeSheet = activeSheet.copy(split = activeSheet.split.copy(projectId = projectId))
                )
            }
            val updated = state.form.splits.map {
                if (it.localId == forSplitLocalId) it.copy(projectId = projectId) else it
            }
            return state.copy(form = state.form.copy(splits = updated), activeSheet = null)
        }
        return state.copy(
            form = state.form.copy(projectId = projectId, pendingProjectName = null),
            activeSheet = null,
        )
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
            activeSheet = null,
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
        return state.copy(form = state.form.copy(dateTimeMillis = cal.timeInMillis), activeSheet = null)
    }

    private fun updateTime(
        state: TransactionUiState.Ready,
        hourOfDay: Int,
        minute: Int,
    ): TransactionUiState.Ready {
        val cal = Calendar.getInstance().apply { timeInMillis = state.form.dateTimeMillis }
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        return state.copy(form = state.form.copy(dateTimeMillis = cal.timeInMillis), activeSheet = null)
    }

    private fun openCalculator(
        state: TransactionUiState.Ready,
        intent: TransactionIntent.OpenCalculator,
    ): TransactionUiState.Ready {
        val (pCur, _) = TransactionValidator.resolveCurrencyMeta(state.form, state.catalog)
        val pDec = pCur?.decimals ?: DEFAULT_DECIMALS
        val initialText = when (intent.target) {
            CalculatorTarget.PRIMARY_AMOUNT -> state.form.primaryAmountInput
            CalculatorTarget.SECONDARY_AMOUNT -> state.form.secondaryAmountInput
            CalculatorTarget.EXCHANGE_RATE -> state.form.exchangeRateInput
            CalculatorTarget.SPLIT_AMOUNT -> {
                val split = state.form.splits.firstOrNull { it.localId == intent.splitLocalId }
                AmountScale.formatEditableAmount(split?.amountMinor ?: 0L, pDec)
            }
        }
        return state.copy(
            activeSheet = ActiveSheet.Calculator(
                target = intent.target,
                initialText = initialText,
                splitLocalId = intent.splitLocalId,
            )
        )
    }
}
