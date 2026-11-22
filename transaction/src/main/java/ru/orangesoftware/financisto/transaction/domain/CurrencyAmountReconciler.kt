// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

internal object CurrencyAmountReconciler {
    private const val DEFAULT_DECIMALS = 2

    fun reconcileAfterCurrencyRoleChange(
        previous: TransactionUiState.Ready,
        next: TransactionUiState.Ready,
    ): TransactionUiState.Ready {
        val oldPrimary = previous.primaryCurrency
        val newPrimary = next.primaryCurrency
        val oldSecondary = previous.secondaryCurrency
        val newSecondary = next.secondaryCurrency
        val pairUnchanged = oldPrimary?.id == newPrimary?.id &&
            oldSecondary?.id == newSecondary?.id &&
            previous.isDualCurrency == next.isDualCurrency
        if (pairUnchanged) return next

        val oldPrimaryDec = oldPrimary?.decimals ?: DEFAULT_DECIMALS
        val newPrimaryDec = newPrimary?.decimals ?: oldPrimaryDec
        val oldSecondaryDec = oldSecondary?.decimals ?: DEFAULT_DECIMALS
        val newSecondaryDec = newSecondary?.decimals ?: oldSecondaryDec

        val (primaryInput, primaryMinor) = reconcileAmount(
            input = next.form.primaryAmountInput,
            minor = next.form.primaryAmountMinor,
            oldDecimals = oldPrimaryDec,
            newDecimals = newPrimaryDec,
        )
        val (secondaryInput, secondaryMinor) = if (previous.isDualCurrency && !next.isDualCurrency) {
            "0" to 0L
        } else {
            reconcileAmount(
                input = next.form.secondaryAmountInput,
                minor = next.form.secondaryAmountMinor,
                oldDecimals = oldSecondaryDec,
                newDecimals = newSecondaryDec,
            )
        }
        val reconciledSplits = reconcileSplits(next.form.splits, oldPrimaryDec, newPrimaryDec)
        val updatedForm = next.form.copy(
            primaryAmountInput = primaryInput,
            primaryAmountMinor = primaryMinor,
            secondaryAmountInput = secondaryInput,
            secondaryAmountMinor = secondaryMinor,
            splits = reconciledSplits,
        )
        return syncRate(next.copy(form = updatedForm))
    }

    fun syncRate(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val pCur = state.primaryCurrency
        val sCur = state.secondaryCurrency?.takeIf { it.id != pCur?.id }
        if (!state.isDualCurrency || pCur == null || sCur == null) {
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

    private fun reconcileAmount(
        input: String,
        minor: Long,
        oldDecimals: Int,
        newDecimals: Int,
    ): Pair<String, Long> {
        return when {
            oldDecimals == newDecimals -> input to minor
            input.isBlank() -> input to 0L
            else -> {
                val reparsed = AmountScale.parseMinorUnits(input, newDecimals)
                val targetMinor = reparsed ?: run {
                    val fallbackText = AmountScale.formatEditableAmount(minor, oldDecimals)
                    AmountScale.parseMinorUnits(fallbackText, newDecimals) ?: 0L
                }
                AmountScale.formatEditableAmount(targetMinor, newDecimals) to targetMinor
            }
        }
    }

    private fun reconcileSplits(
        splits: List<EditableSplit>,
        oldDecimals: Int,
        newDecimals: Int,
    ): List<EditableSplit> {
        if (oldDecimals == newDecimals || splits.isEmpty()) return splits
        return splits.map { split ->
            val text = AmountScale.formatEditableAmount(split.amountMinor, oldDecimals)
            val rescaled = AmountScale.parseMinorUnits(text, newDecimals) ?: 0L
            split.copy(amountMinor = rescaled)
        }
    }
}
