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
        val pDec = state.primaryCurrency?.decimals ?: DEFAULT_DECIMALS
        val sDec = state.secondaryCurrency?.decimals ?: DEFAULT_DECIMALS
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
            val remembered = state.fromAccount?.lastAccountId ?: 0L
            val isValidTarget = remembered > 0L && remembered != current.fromAccountId &&
                state.catalog.accounts.any { it.id == remembered && it.isActive }
            if (isValidTarget) remembered else 0L
        } else {
            current.toAccountId
        }
        val next = state.copy(
            form = current.copy(mode = targetMode, toAccountId = updatedToAccountId),
            validationErrors = emptyList(),
        )
        return CurrencyAmountReconciler.reconcileAfterCurrencyRoleChange(previous = state, next = next)
    }

    fun applyPrimaryAmountText(
        state: TransactionUiState.Ready,
        text: String,
        primaryDecimals: Int,
        secondaryDecimals: Int,
    ): TransactionUiState.Ready {
        val parsed = AmountScale.parseMinorUnits(text, primaryDecimals)
            ?: return state.copy(form = state.form.copy(primaryAmountInput = text), validationErrors = emptyList())
        var nextSecondary = state.form.secondaryAmountMinor
        var nextSecondaryText = state.form.secondaryAmountInput
        var nextRate = state.form.exchangeRate
        var nextRateText = state.form.exchangeRateInput

        if (state.isDualCurrency) {
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
        val rate = CurrencyConversionCalculator.parsePositiveRate(text)
        return if (rate == null) {
            state.copy(
                form = state.form.copy(
                    exchangeRateInput = text,
                    exchangeRate = if (text.isBlank()) 0.0 else state.form.exchangeRate,
                ),
                validationErrors = emptyList(),
            )
        } else {
            val nextSecondary = CurrencyConversionCalculator.computeTargetMinor(
                fromMinor = state.form.primaryAmountMinor,
                fromDecimals = primaryDecimals,
                toDecimals = secondaryDecimals,
                rate = rate,
            )
            state.copy(
                form = state.form.copy(
                    exchangeRateInput = text,
                    exchangeRate = rate,
                    secondaryAmountMinor = nextSecondary,
                    secondaryAmountInput = AmountScale.formatEditableAmount(nextSecondary, secondaryDecimals),
                ),
                validationErrors = emptyList(),
            )
        }
    }

    private fun applyCalcResult(
        state: TransactionUiState.Ready,
        intent: TransactionIntent.ApplyCalculatorResult,
        primaryDecimals: Int,
        secondaryDecimals: Int,
    ): TransactionUiState.Ready {
        val safeVal = abs(intent.value)
        return when (intent.target) {
            CalculatorTarget.PRIMARY_AMOUNT -> {
                val minor = (safeVal * AmountScale.pow10(primaryDecimals)).roundToLong()
                val text = AmountScale.formatEditableAmount(minor, primaryDecimals)
                applyPrimaryAmountText(state.copy(sheetStack = emptyList()), text, primaryDecimals, secondaryDecimals)
            }
            CalculatorTarget.SECONDARY_AMOUNT -> {
                val minor = (safeVal * AmountScale.pow10(secondaryDecimals)).roundToLong()
                val text = AmountScale.formatEditableAmount(minor, secondaryDecimals)
                applySecondaryAmountText(state.copy(sheetStack = emptyList()), text, primaryDecimals, secondaryDecimals)
            }
            CalculatorTarget.EXCHANGE_RATE -> {
                val text = CurrencyConversionCalculator.formatRate(safeVal)
                applyExchangeRateText(state.copy(sheetStack = emptyList()), text, primaryDecimals, secondaryDecimals)
            }
            CalculatorTarget.SPLIT_AMOUNT ->
                applySplitCalcResult(state, intent.splitLocalId, safeVal, primaryDecimals)
        }
    }

    private fun applySplitCalcResult(
        state: TransactionUiState.Ready,
        splitLocalId: Long?,
        safeVal: Double,
        primaryDecimals: Int,
    ): TransactionUiState.Ready {
        val splitId = splitLocalId ?: return state.copy(sheetStack = state.sheetStack.dropLast(1))
        val minor = (safeVal * AmountScale.pow10(primaryDecimals)).roundToLong()
        val editor = state.activeSplitEditor?.takeIf { it.split.localId == splitId }
        return if (editor != null) {
            state.copy(sheetStack = listOf(editor.copy(split = editor.split.copy(amountMinor = minor))))
        } else {
            val updated = state.form.splits.map {
                if (it.localId == splitId) it.copy(amountMinor = minor) else it
            }
            state.copy(form = state.form.copy(splits = updated), sheetStack = emptyList())
        }
    }
}
