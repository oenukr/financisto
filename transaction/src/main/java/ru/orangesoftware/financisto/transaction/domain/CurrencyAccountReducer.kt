// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
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
        val nextForm = if (isDestination) {
            state.form.copy(toAccountId = accountId)
        } else {
            selectSourceAccount(state, account)
        }
        return CurrencyAmountReconciler.reconcileAfterCurrencyRoleChange(
            previous = state,
            next = state.copy(form = nextForm, sheetStack = emptyList(), validationErrors = emptyList()),
        )
    }

    private fun selectSourceAccount(
        state: TransactionUiState.Ready,
        account: AccountOption,
    ): TransactionFormState {
        val form = state.form
        val canRememberCategory = !form.isEditing &&
            !state.prefs.isShowPayee &&
            state.prefs.isRememberCategory &&
            !form.isSplitMode
        val shouldRememberCategory = canRememberCategory &&
            form.categoryId == TransactionIds.NO_CATEGORY_ID &&
            account.lastCategoryId > 0L &&
            state.catalog.categories.any { it.id == account.lastCategoryId }
        val nextCategoryId = if (shouldRememberCategory) account.lastCategoryId else form.categoryId
        val nextMode = resolveRememberedCategoryMode(state, shouldRememberCategory, nextCategoryId)
        val nextToId = resolveRememberedDestination(state, account)
        return form.copy(
            fromAccountId = account.id,
            categoryId = nextCategoryId,
            mode = nextMode,
            toAccountId = nextToId,
        )
    }

    private fun resolveRememberedCategoryMode(
        state: TransactionUiState.Ready,
        shouldRememberCategory: Boolean,
        categoryId: Long,
    ): TransactionMode {
        val form = state.form
        if (!shouldRememberCategory || form.mode == TransactionMode.TRANSFER) return form.mode
        val category = state.catalog.categories.firstOrNull { it.id == categoryId }
        return when {
            category == null -> form.mode
            category.isIncome -> TransactionMode.INCOME
            else -> TransactionMode.EXPENSE
        }
    }

    private fun resolveRememberedDestination(
        state: TransactionUiState.Ready,
        account: AccountOption,
    ): Long {
        val form = state.form
        val canRememberTo = form.mode == TransactionMode.TRANSFER &&
            !form.isEditing &&
            state.prefs.isRememberAccount
        val validLastAccount = account.lastAccountId > 0L &&
            account.lastAccountId != account.id &&
            state.catalog.accounts.any { it.id == account.lastAccountId && it.isActive }
        return if (canRememberTo && form.toAccountId <= 0L && validLastAccount) {
            account.lastAccountId
        } else {
            form.toAccountId
        }
    }

    private fun swapTransferAccounts(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val form = state.form
        if (form.mode != TransactionMode.TRANSFER) return state
        val isDual = state.isDualCurrency
        val swappedForm = form.copy(
            fromAccountId = form.toAccountId,
            toAccountId = form.fromAccountId,
            primaryAmountMinor = if (isDual) form.secondaryAmountMinor else form.primaryAmountMinor,
            primaryAmountInput = if (isDual) form.secondaryAmountInput else form.primaryAmountInput,
            secondaryAmountMinor = if (isDual) form.primaryAmountMinor else form.secondaryAmountMinor,
            secondaryAmountInput = if (isDual) form.primaryAmountInput else form.secondaryAmountInput,
        )
        return CurrencyAmountReconciler.syncRate(state.copy(form = swappedForm, validationErrors = emptyList()))
    }

    private fun selectCurrency(state: TransactionUiState.Ready, currencyId: Long?): TransactionUiState.Ready {
        val fromAcc = state.fromAccount
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
        return CurrencyAmountReconciler.reconcileAfterCurrencyRoleChange(
            previous = state,
            next = state.copy(form = nextForm, sheetStack = emptyList(), validationErrors = emptyList()),
        )
    }
}
