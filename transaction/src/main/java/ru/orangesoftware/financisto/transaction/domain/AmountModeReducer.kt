// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import kotlin.math.abs
import kotlin.math.roundToLong

internal object AmountModeReducer {
    private const val DEFAULT_DECIMALS = 2

    fun reduce(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        val (primaryCur, secondaryCur) = TransactionValidator.resolveCurrencyMeta(state.form, state.catalog)
        val pDec = primaryCur?.decimals ?: DEFAULT_DECIMALS
        val sDec = secondaryCur?.decimals ?: DEFAULT_DECIMALS
        return when (intent) {
            is TransactionIntent.SwitchMode -> switchMode(state, intent.mode)
            is TransactionIntent.CycleSignOrMode -> cycleSignMode(state)
            is TransactionIntent.UpdatePrimaryAmount -> applyPrimaryAmountText(state, intent.text, pDec, sDec)
            is TransactionIntent.UpdateSecondaryAmount -> applySecondaryAmountText(state, intent.text, pDec, sDec)
            is TransactionIntent.UpdateExchangeRate -> applyExchangeRateText(state, intent.text, pDec, sDec)
            is TransactionIntent.ApplyCalculatorResult -> applyCalcResult(state, intent, pDec, sDec)
            else -> state
        }
    }

    private fun cycleSignMode(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val nextMode = when (state.form.mode) {
            TransactionMode.EXPENSE -> TransactionMode.INCOME
            TransactionMode.INCOME -> TransactionMode.EXPENSE
            TransactionMode.TRANSFER -> TransactionMode.TRANSFER
        }
        return switchMode(state, nextMode)
    }

    private fun switchMode(state: TransactionUiState.Ready, targetMode: TransactionMode): TransactionUiState.Ready {
        val current = state.form
        if (current.mode == targetMode) return state
        val updatedToAccountId = if (
            targetMode == TransactionMode.TRANSFER &&
            current.toAccountId <= 0L &&
            state.prefs.isRememberAccount
        ) {
            val fromAcc = state.catalog.accounts.firstOrNull { it.id == current.fromAccountId }
            val remembered = fromAcc?.lastAccountId ?: 0L
            val isValidTarget = remembered > 0L && remembered != current.fromAccountId &&
                state.catalog.accounts.any { it.id == remembered && it.isActive }
            if (isValidTarget) remembered else 0L
        } else {
            current.toAccountId
        }
        return state.copy(
            form = current.copy(mode = targetMode, toAccountId = updatedToAccountId),
            validationErrors = emptyList(),
        )
    }

    fun applyPrimaryAmountText(
        state: TransactionUiState.Ready,
        text: String,
        primaryDecimals: Int,
        secondaryDecimals: Int,
    ): TransactionUiState.Ready {
        val parsed = AmountScale.parseMinorUnits(text, primaryDecimals)
            ?: return state.copy(form = state.form.copy(primaryAmountInput = text), validationErrors = emptyList())
        val fromAcc = state.catalog.accounts.firstOrNull { it.id == state.form.fromAccountId }
        val isDual = TransactionValidator.isDualCurrencyActive(state.form, fromAcc, state.catalog)
        var nextSecondary = state.form.secondaryAmountMinor
        var nextSecondaryText = state.form.secondaryAmountInput
        var nextRate = state.form.exchangeRate
        var nextRateText = state.form.exchangeRateInput

        if (isDual) {
            if (state.form.exchangeRate > 0.0) {
                nextSecondary = CurrencyConversionCalculator.computeTargetMinor(
                    fromMinor = parsed,
                    fromDecimals = primaryDecimals,
                    toDecimals = secondaryDecimals,
                    rate = state.form.exchangeRate,
                )
                nextSecondaryText = AmountScale.formatEditableAmount(nextSecondary, secondaryDecimals)
            } else if (parsed > 0L && nextSecondary > 0L) {
                nextRate = CurrencyConversionCalculator.computeRate(
                    fromMinor = parsed,
                    fromDecimals = primaryDecimals,
                    toMinor = nextSecondary,
                    toDecimals = secondaryDecimals,
                )
                nextRateText = CurrencyConversionCalculator.formatRate(nextRate)
            }
        }
        return state.copy(
            form = state.form.copy(
                primaryAmountInput = text,
                primaryAmountMinor = parsed,
                secondaryAmountMinor = nextSecondary,
                secondaryAmountInput = nextSecondaryText,
                exchangeRate = nextRate,
                exchangeRateInput = nextRateText,
            ),
            validationErrors = emptyList(),
        )
    }

    private fun applySecondaryAmountText(
        state: TransactionUiState.Ready,
        text: String,
        primaryDecimals: Int,
        secondaryDecimals: Int,
    ): TransactionUiState.Ready {
        val parsed = AmountScale.parseMinorUnits(text, secondaryDecimals)
            ?: return state.copy(form = state.form.copy(secondaryAmountInput = text), validationErrors = emptyList())
        val rate = CurrencyConversionCalculator.computeRate(
            fromMinor = state.form.primaryAmountMinor,
            fromDecimals = primaryDecimals,
            toMinor = parsed,
            toDecimals = secondaryDecimals,
        )
        return state.copy(
            form = state.form.copy(
                secondaryAmountInput = text,
                secondaryAmountMinor = parsed,
                exchangeRate = rate,
                exchangeRateInput = CurrencyConversionCalculator.formatRate(rate),
            ),
            validationErrors = emptyList(),
        )
    }

    fun applyExchangeRateText(
        state: TransactionUiState.Ready,
        text: String,
        primaryDecimals: Int,
        secondaryDecimals: Int,
    ): TransactionUiState.Ready {
        val rate = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
            ?: return state.copy(form = state.form.copy(exchangeRateInput = text), validationErrors = emptyList())
        val nextSecondary = CurrencyConversionCalculator.computeTargetMinor(
            fromMinor = state.form.primaryAmountMinor,
            fromDecimals = primaryDecimals,
            toDecimals = secondaryDecimals,
            rate = rate,
        )
        return state.copy(
            form = state.form.copy(
                exchangeRateInput = text,
                exchangeRate = rate,
                secondaryAmountMinor = nextSecondary,
                secondaryAmountInput = AmountScale.formatEditableAmount(nextSecondary, secondaryDecimals),
            ),
            validationErrors = emptyList(),
        )
    }

    private fun applyCalcResult(
        state: TransactionUiState.Ready,
        intent: TransactionIntent.ApplyCalculatorResult,
        primaryDecimals: Int,
        secondaryDecimals: Int,
    ): TransactionUiState.Ready {
        val closedState = state.copy(activeSheet = null)
        val safeVal = abs(intent.value)
        return when (intent.target) {
            CalculatorTarget.PRIMARY_AMOUNT -> {
                val minor = (safeVal * AmountScale.pow10(primaryDecimals)).roundToLong()
                val text = AmountScale.formatEditableAmount(minor, primaryDecimals)
                applyPrimaryAmountText(closedState, text, primaryDecimals, secondaryDecimals)
            }
            CalculatorTarget.SECONDARY_AMOUNT -> {
                val minor = (safeVal * AmountScale.pow10(secondaryDecimals)).roundToLong()
                val text = AmountScale.formatEditableAmount(minor, secondaryDecimals)
                applySecondaryAmountText(closedState, text, primaryDecimals, secondaryDecimals)
            }
            CalculatorTarget.EXCHANGE_RATE -> {
                val text = CurrencyConversionCalculator.formatRate(safeVal)
                applyExchangeRateText(closedState, text, primaryDecimals, secondaryDecimals)
            }
            CalculatorTarget.SPLIT_AMOUNT -> {
                val splitId = intent.splitLocalId ?: return closedState
                val minor = (safeVal * AmountScale.pow10(primaryDecimals)).roundToLong()
                val updated = closedState.form.splits.map {
                    if (it.localId == splitId) it.copy(amountMinor = minor) else it
                }
                closedState.copy(form = closedState.form.copy(splits = updated))
            }
        }
    }
}
