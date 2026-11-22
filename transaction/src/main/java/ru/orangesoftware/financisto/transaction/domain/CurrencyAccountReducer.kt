// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

internal object CurrencyAccountReducer {

    fun reduce(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.SelectAccount -> selectAccount(state, intent.accountId, intent.isDestination)
            is TransactionIntent.SwapTransferAccounts -> swapTransferAccounts(state)
            is TransactionIntent.SelectCurrency -> selectCurrency(state, intent.currencyId)
            else -> state
        }
    }

    private fun selectAccount(
        state: TransactionUiState.Ready,
        accountId: Long,
        isDestination: Boolean,
    ): TransactionUiState.Ready {
        val account = state.catalog.accounts.firstOrNull { it.id == accountId } ?: return state
        val form = state.form
        val nextForm = if (isDestination) {
            form.copy(toAccountId = accountId)
        } else {
            val shouldRememberCategory = form.isFromNewBlankLaunch &&
                !state.prefs.isShowPayee &&
                state.prefs.isRememberCategory &&
                form.categoryId == TransactionIds.NO_CATEGORY_ID &&
                account.lastCategoryId > 0L
            val shouldRememberTo = form.mode == TransactionMode.TRANSFER &&
                form.isFromNewBlankLaunch &&
                state.prefs.isRememberAccount &&
                form.toAccountId <= 0L &&
                account.lastAccountId > 0L &&
                account.lastAccountId != accountId &&
                state.catalog.accounts.any { it.id == account.lastAccountId && it.isActive }
            form.copy(
                fromAccountId = accountId,
                categoryId = if (shouldRememberCategory) account.lastCategoryId else form.categoryId,
                toAccountId = if (shouldRememberTo) account.lastAccountId else form.toAccountId,
            )
        }
        return syncRateOnCurrencyChange(state.copy(form = nextForm, activeSheet = null, validationErrors = emptyList()))
    }

    private fun swapTransferAccounts(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val form = state.form
        if (form.mode != TransactionMode.TRANSFER) return state
        val fromAcc = state.catalog.accounts.firstOrNull { it.id == form.fromAccountId }
        val isDual = TransactionValidator.isDualCurrencyActive(form, fromAcc, state.catalog)
        val swappedForm = form.copy(
            fromAccountId = form.toAccountId,
            toAccountId = form.fromAccountId,
            primaryAmountMinor = if (isDual) form.secondaryAmountMinor else form.primaryAmountMinor,
            primaryAmountInput = if (isDual) form.secondaryAmountInput else form.primaryAmountInput,
            secondaryAmountMinor = if (isDual) form.primaryAmountMinor else form.secondaryAmountMinor,
            secondaryAmountInput = if (isDual) form.primaryAmountInput else form.secondaryAmountInput,
        )
        return syncRateOnCurrencyChange(state.copy(form = swappedForm, validationErrors = emptyList()))
    }

    private fun selectCurrency(state: TransactionUiState.Ready, currencyId: Long?): TransactionUiState.Ready {
        val fromAcc = state.catalog.accounts.firstOrNull { it.id == state.form.fromAccountId }
        val normalized = if (currencyId == null || currencyId <= 0L || currencyId == fromAcc?.currencyId) {
            null
        } else {
            currencyId
        }
        val nextForm = state.form.copy(
            originalCurrencyId = normalized,
            secondaryAmountMinor = if (normalized == null) 0L else state.form.secondaryAmountMinor,
            secondaryAmountInput = if (normalized == null) "0" else state.form.secondaryAmountInput,
        )
        return syncRateOnCurrencyChange(state.copy(form = nextForm, activeSheet = null, validationErrors = emptyList()))
    }

    private fun syncRateOnCurrencyChange(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val (pCur, sCur) = TransactionValidator.resolveCurrencyMeta(state.form, state.catalog)
        if (pCur == null || sCur == null || pCur.id == sCur.id) {
            return state.copy(form = state.form.copy(exchangeRate = 0.0, exchangeRateInput = ""))
        }
        val nextRate = CurrencyConversionCalculator.computeRate(
            fromMinor = state.form.primaryAmountMinor,
            fromDecimals = pCur.decimals,
            toMinor = state.form.secondaryAmountMinor,
            toDecimals = sCur.decimals,
        )
        return state.copy(
            form = state.form.copy(
                exchangeRate = nextRate,
                exchangeRateInput = CurrencyConversionCalculator.formatRate(nextRate),
            )
        )
    }
}
