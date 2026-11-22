// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

class TransactionDomainTest {

    @Test
    fun amountScaleHandlesZeroTwoAndThreeDecimals() {
        assertEquals(1L, AmountScale.pow10(0))
        assertEquals(100L, AmountScale.pow10(2))
        assertEquals(1000L, AmountScale.pow10(3))

        assertEquals(1250L, AmountScale.parseMinorUnits("1250", 0))
        assertEquals(1550L, AmountScale.parseMinorUnits("15,5", 2))
        assertEquals(12345L, AmountScale.parseMinorUnits(" 12.345 ", 3))
        assertEquals(0L, AmountScale.parseMinorUnits("", 2))
        assertNull(AmountScale.parseMinorUnits("12abc", 2))

        assertEquals("1250", AmountScale.formatEditableAmount(1250L, 0))
        assertEquals("15.50", AmountScale.formatEditableAmount(1550L, 2))
        assertEquals("12.345", AmountScale.formatEditableAmount(12345L, 3))

        assertEquals("-$ 15.50", AmountScale.formatDisplayAmount(-1550L, 2, "$", includeSign = true))
        assertEquals("+¥ 1,250", AmountScale.formatDisplayAmount(1250L, 0, "¥", includeSign = true))
        assertEquals("BD 12.345", AmountScale.formatDisplayAmount(12345L, 3, "BD", includeSign = false))
    }

    @Test
    fun currencyConversionCalculatorComputesRatesTargetsAndPairs() {
        val rateUsdToJpy = CurrencyConversionCalculator.computeRate(
            fromMinor = 10000L,
            fromDecimals = 2,
            toMinor = 15000L,
            toDecimals = 0,
        )
        assertEquals(150.0, rateUsdToJpy, 1e-6)
        assertEquals("150", CurrencyConversionCalculator.formatRate(rateUsdToJpy))

        val bhdMinor = CurrencyConversionCalculator.computeTargetMinor(
            fromMinor = 1000L,
            fromDecimals = 2,
            toDecimals = 3,
            rate = 0.377,
        )
        assertEquals(3770L, bhdMinor)

        val pair = CurrencyConversionCalculator.formatReciprocalPair("USD", "EUR", 0.8)
        assertEquals("1 USD ≈ 0.8 EUR" to "1 EUR ≈ 1.25 USD", pair)
        assertNull(CurrencyConversionCalculator.formatReciprocalPair("USD", "EUR", 0.0))
    }

    @Test
    fun splitAllocationCalculatorComputesBalancesAndRebalancesRemainder() {
        val splits = listOf(
            EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 3300L, isIncome = false),
            EditableSplit(localId = -2L, categoryId = 11L, amountMinor = 3300L, isIncome = false),
            EditableSplit(localId = -3L, categoryId = 12L, amountMinor = 3300L, isIncome = false),
        )
        val parentSigned = SplitAllocationCalculator.parentSignedMinor(10000L, TransactionMode.EXPENSE)
        assertEquals(-10000L, parentSigned)
        assertEquals(-9900L, SplitAllocationCalculator.allocatedSignedMinor(splits))
        val unsplit = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, splits)
        assertEquals(-100L, unsplit)
        assertEquals(9900L, SplitAllocationCalculator.adjustTotalMagnitude(splits))

        val even = SplitAllocationCalculator.distributeEvenly(splits, unsplitSignedDelta = -100L)
        assertEquals(listOf(3333L, 3333L, 3334L), even.map { it.amountMinor })
        assertTrue(even.all { !it.isIncome })
        assertEquals(0L, SplitAllocationCalculator.unsplitSignedMinor(parentSigned, even))

        val adjustedLast = SplitAllocationCalculator.adjustLast(splits, unsplitSignedDelta = -100L)
        assertEquals(listOf(3300L, 3300L, 3400L), adjustedLast.map { it.amountMinor })
    }

    @Test
    fun calculatorExpressionEvaluatorSupportsOperatorsParenthesesAndRejectsInvalidInput() {
        assertEquals(14.0, CalculatorExpressionEvaluator.evaluate("2 + 3 * 4")!!, 1e-6)
        assertEquals(20.0, CalculatorExpressionEvaluator.evaluate("(2 + 3) * 4")!!, 1e-6)
        assertEquals(-3.5, CalculatorExpressionEvaluator.evaluate("-7 / 2")!!, 1e-6)
        assertEquals(12.75, CalculatorExpressionEvaluator.evaluate("10,5 + 2.25")!!, 1e-6)
        assertNull(CalculatorExpressionEvaluator.evaluate("10 / 0"))
        assertNull(CalculatorExpressionEvaluator.evaluate("(2 + 3"))
        assertNull(CalculatorExpressionEvaluator.evaluate(""))
        assertNull(CalculatorExpressionEvaluator.evaluate("12 + * 3"))
    }

    @Test
    fun validatorEnforcesRulesAcrossZeroAmountsForeignCurrencySplitsAndTransfers() {
        val catalog = sampleCatalog()

        val manualZero = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountMinor = 0L,
            isFromNewBlankLaunch = true,
        )
        val manualZeroResult = TransactionValidator.validateAndBuildCommand(manualZero, catalog)
        assertTrue(manualZeroResult is TransactionValidationResult.Valid)

        val aiZero = manualZero.copy(aiDraftId = "draft-1", isFromNewBlankLaunch = false)
        val aiZeroResult = TransactionValidator.validateAndBuildCommand(aiZero, catalog)
        assertTrue(aiZeroResult is TransactionValidationResult.Invalid)

        val foreignExpense = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            originalCurrencyId = 2L,
            primaryAmountMinor = 1000L,
            secondaryAmountMinor = 1200L,
            categoryId = 10L,
            projectId = 5L,
        )
        val foreignValid = TransactionValidator.validateAndBuildCommand(foreignExpense, catalog)
        assertTrue(foreignValid is TransactionValidationResult.Valid)
        val foreignCmd = (foreignValid as TransactionValidationResult.Valid).command
        assertEquals(-1200L, foreignCmd.fromAmountMinor)
        assertEquals(2L, foreignCmd.originalCurrencyId)
        assertEquals(-1000L, foreignCmd.originalFromAmountMinor)

        val mismatchedSplit = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountMinor = 5000L,
            isSplitMode = true,
            projectId = 5L,
            pendingProjectName = "IgnoredParentProject",
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 2000L, isIncome = false, projectId = 5L),
            ),
        )
        assertTrue(
            TransactionValidator.validateAndBuildCommand(mismatchedSplit, catalog) is TransactionValidationResult.Invalid
        )

        val validSplit = mismatchedSplit.copy(
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 2000L, isIncome = false, projectId = 5L),
                EditableSplit(localId = -2L, categoryId = 11L, amountMinor = 3000L, isIncome = false, projectId = 0L),
            )
        )
        val splitValid = TransactionValidator.validateAndBuildCommand(validSplit, catalog)
        val splitCmd = (splitValid as TransactionValidationResult.Valid).command
        assertEquals(TransactionIds.SPLIT_CATEGORY_ID, splitCmd.categoryId)
        assertEquals(TransactionIds.NO_PROJECT_ID, splitCmd.projectId)
        assertNull(splitCmd.pendingProjectName)
        assertEquals(2, splitCmd.splits.size)

        val multiCurrencyTransfer = TransactionFormState(
            mode = TransactionMode.TRANSFER,
            fromAccountId = 1L,
            toAccountId = 2L,
            primaryAmountMinor = 10000L,
            secondaryAmountMinor = 9000L,
        )
        val transferCmd = (TransactionValidator.validateAndBuildCommand(multiCurrencyTransfer, catalog)
            as TransactionValidationResult.Valid).command
        assertEquals(-10000L, transferCmd.fromAmountMinor)
        assertEquals(9000L, transferCmd.toAmountMinor)

        val sameAccountTransfer = multiCurrencyTransfer.copy(toAccountId = 1L)
        assertTrue(TransactionValidator.validateAndBuildCommand(sameAccountTransfer, catalog)
            is TransactionValidationResult.Invalid)
    }

    @Test
    fun reducerHandlesModeSwitchPayeeDefaultsTransferSwapAndSplitRebalancing() {
        val catalog = sampleCatalog()
        val prefs = TransactionFormPreferences(
            isRememberAccount = true,
            isRememberCategory = true,
        )
        val initialForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountMinor = 6000L,
            primaryAmountInput = "60.00",
            note = "Coffee",
            isFromNewBlankLaunch = true,
        )
        var state = TransactionUiState.Ready(
            form = initialForm,
            initialFormSnapshot = initialForm,
            catalog = catalog,
            prefs = prefs,
        )

        state = TransactionReducer.reduce(state, TransactionIntent.SelectPayee(200L))
        assertEquals(11L, state.form.categoryId)
        assertEquals("Starbucks", state.form.payeeQuery)

        state = TransactionReducer.reduce(state, TransactionIntent.SwitchMode(TransactionMode.TRANSFER))
        assertEquals(TransactionMode.TRANSFER, state.form.mode)
        assertEquals(2L, state.form.toAccountId)
        assertEquals("Coffee", state.form.note)

        state = TransactionReducer.reduce(state, TransactionIntent.UpdateSecondaryAmount("54.00"))
        assertEquals(5400L, state.form.secondaryAmountMinor)
        assertEquals(0.9, state.form.exchangeRate, 1e-6)

        state = TransactionReducer.reduce(state, TransactionIntent.SwapTransferAccounts)
        assertEquals(2L, state.form.fromAccountId)
        assertEquals(1L, state.form.toAccountId)
        assertEquals(5400L, state.form.primaryAmountMinor)
        assertEquals(6000L, state.form.secondaryAmountMinor)

        state = TransactionReducer.reduce(state, TransactionIntent.SwitchMode(TransactionMode.EXPENSE))
        state = TransactionReducer.reduce(state, TransactionIntent.UpdatePrimaryAmount("100.00"))
        state = TransactionReducer.reduce(state, TransactionIntent.ToggleSplitMode)
        assertTrue(state.form.isSplitMode)
        assertEquals(1, state.form.splits.size)

        val secondSplit = EditableSplit(localId = -2L, categoryId = 10L, amountMinor = 2000L, isIncome = false)
        state = TransactionReducer.reduce(state, TransactionIntent.SaveSplitItem(secondSplit))
        assertEquals(2, state.form.splits.size)

        state = TransactionReducer.reduce(state, TransactionIntent.RebalanceAdjustTotal)
        assertEquals(12000L, state.form.primaryAmountMinor)
        assertFalse(state.validationErrors.isNotEmpty())

        state = TransactionReducer.reduce(state, TransactionIntent.RemoveSplitItem(-2L))
        state = TransactionReducer.reduce(state, TransactionIntent.CollapseSingleSplit)
        assertFalse(state.form.isSplitMode)
        assertEquals(11L, state.form.categoryId)
        assertTrue(state.form.splits.isEmpty())
    }

    private fun sampleCatalog(): TransactionCatalog {
        val usd = CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true)
        val eur = CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2)
        return TransactionCatalog(
            accounts = listOf(
                AccountOption(
                    id = 1L,
                    title = "Cash",
                    typeLabel = "CASH",
                    currencyId = 1L,
                    currencyCode = "USD",
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    balanceMinor = 50000L,
                    lastAccountId = 2L,
                ),
                AccountOption(
                    id = 2L,
                    title = "Euro Bank",
                    typeLabel = "BANK",
                    currencyId = 2L,
                    currencyCode = "EUR",
                    currencySymbol = "€",
                    currencyDecimals = 2,
                    balanceMinor = 40000L,
                ),
            ),
            currencies = listOf(usd, eur),
            categories = listOf(
                CategoryOption(id = 10L, parentId = 0L, title = "Food", fullPath = "Food", level = 1, isIncome = false),
                CategoryOption(
                    id = 11L,
                    parentId = 10L,
                    title = "Coffee",
                    fullPath = "Food > Coffee",
                    level = 2,
                    isIncome = false,
                ),
                CategoryOption(
                    id = 20L,
                    parentId = 0L,
                    title = "Salary",
                    fullPath = "Salary",
                    level = 1,
                    isIncome = true,
                ),
            ),
            payees = listOf(
                PayeeOption(id = 200L, title = "Starbucks", lastCategoryId = 11L),
            ),
        )
    }
}
