// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

object TransactionReducer {

    fun reduce(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.SwitchMode,
            is TransactionIntent.CycleSignOrMode,
            is TransactionIntent.UpdatePrimaryAmount,
            is TransactionIntent.UpdateSecondaryAmount,
            is TransactionIntent.UpdateExchangeRate,
            is TransactionIntent.ApplyCalculatorResult -> AmountModeReducer.reduce(state, intent)

            is TransactionIntent.SelectAccount,
            is TransactionIntent.SwapTransferAccounts,
            is TransactionIntent.SelectCurrency -> CurrencyAccountReducer.reduce(state, intent)

            is TransactionIntent.ToggleInlineField,
            is TransactionIntent.CollapseInlineField,
            is TransactionIntent.UpdatePayeeQuery,
            is TransactionIntent.SelectPayee,
            is TransactionIntent.CreatePendingPayee,
            is TransactionIntent.ClearPayee,
            is TransactionIntent.UpdateLocationQuery,
            is TransactionIntent.SelectLocation,
            is TransactionIntent.ClearLocation,
            is TransactionIntent.UpdateNote,
            is TransactionIntent.SelectRecentNote,
            is TransactionIntent.ClearNote -> EntityNoteReducer.reduce(state, intent)

            is TransactionIntent.SelectCategory,
            is TransactionIntent.ClearCategory,
            is TransactionIntent.ToggleSplitMode,
            is TransactionIntent.OpenNewSplitEditor,
            is TransactionIntent.OpenEditSplitEditor,
            is TransactionIntent.UpdateSplitDraft,
            is TransactionIntent.SaveSplitItem,
            is TransactionIntent.RemoveSplitItem,
            is TransactionIntent.RebalanceAdjustTotal,
            is TransactionIntent.RebalanceDistributeEvenly,
            is TransactionIntent.RebalanceAdjustLast,
            is TransactionIntent.CollapseSingleSplit -> CategorySplitReducer.reduce(state, intent)

            else -> MetaSheetReducer.reduce(state, intent)
        }
    }
}
