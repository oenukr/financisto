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
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

class TransactionDomainReviewFixesTest {

    @Test
    fun currencyConversionCalculatorKeepsSmallPositiveRatesVisibleAndSafe() {
        assertEquals("0.000001", CurrencyConversionCalculator.formatRate(0.000001))
        assertEquals("0.000001235", CurrencyConversionCalculator.formatRate(0.0000012345))
        assertEquals("0.0000000001", CurrencyConversionCalculator.formatRate(1e-10))

        val tiny = CurrencyConversionCalculator.formatRate(1e-15)
        assertTrue(tiny.isNotEmpty() && tiny != "0")
        assertTrue((CurrencyConversionCalculator.parsePositiveRate(tiny) ?: 0.0) > 0.0)

        val smallPair = CurrencyConversionCalculator.formatReciprocalPair("VND", "USD", 0.000001)
        assertEquals("1 VND ≈ 0.000001 USD" to "1 USD ≈ 1000000 VND", smallPair)
        assertNull(CurrencyConversionCalculator.formatReciprocalPair("A", "B", Double.MIN_VALUE))
        assertEquals("", CurrencyConversionCalculator.formatRate(0.0))
        assertEquals("", CurrencyConversionCalculator.formatRate(-1.0))
        assertEquals("", CurrencyConversionCalculator.formatRate(Double.NaN))
    }

    @Test
    fun switchModeReconcilesAmountsWhenCurrencyDecimalsChange() {
        val catalog = multiScaleCatalog()
        val initialForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            originalCurrencyId = 3L,
            primaryAmountInput = "100",
            primaryAmountMinor = 100L,
            secondaryAmountInput = "1.00",
            secondaryAmountMinor = 100L,
            exchangeRate = 0.01,
            exchangeRateInput = "0.01",
            isFromNewBlankLaunch = true,
        )
        val initialState = TransactionUiState.Ready(
            form = initialForm,
            initialFormSnapshot = initialForm,
            catalog = catalog,
            prefs = TransactionFormPreferences(isRememberAccount = true),
        )

        val transferToUsd = TransactionReducer.reduce(
            initialState,
            TransactionIntent.SwitchMode(TransactionMode.TRANSFER),
        )
        assertEquals(5L, transferToUsd.form.toAccountId)
        assertFalse(transferToUsd.isDualCurrency)
        assertEquals("USD", transferToUsd.primaryCurrency?.code)
        assertEquals("100.00", transferToUsd.form.primaryAmountInput)
        assertEquals(10000L, transferToUsd.form.primaryAmountMinor)
        assertEquals("0", transferToUsd.form.secondaryAmountInput)
        assertEquals(0L, transferToUsd.form.secondaryAmountMinor)
        assertEquals(0.0, transferToUsd.form.exchangeRate, 1e-6)

        val withJpyDestination = initialState.copy(form = initialForm.copy(toAccountId = 3L))
        val transferToJpy = TransactionReducer.reduce(
            withJpyDestination,
            TransactionIntent.SwitchMode(TransactionMode.TRANSFER),
        )
        assertTrue(transferToJpy.isDualCurrency)
        assertEquals("100.00", transferToJpy.form.primaryAmountInput)
        assertEquals(10000L, transferToJpy.form.primaryAmountMinor)
        assertEquals("1", transferToJpy.form.secondaryAmountInput)
        assertEquals(1L, transferToJpy.form.secondaryAmountMinor)
        assertEquals(0.01, transferToJpy.form.exchangeRate, 1e-6)
    }

    @Test
    fun validatorBlocksInvalidPrimarySecondaryAndExchangeRateInputs() {
        val catalog = multiScaleCatalog()
        val baseForm = TransactionFormState(mode = TransactionMode.EXPENSE, fromAccountId = 1L)
        var state = TransactionUiState.Ready(
            form = baseForm,
            initialFormSnapshot = baseForm,
            catalog = catalog,
            prefs = TransactionFormPreferences(),
        )
        state = TransactionReducer.reduce(state, TransactionIntent.UpdatePrimaryAmount("12.50"))
        assertEquals(1250L, state.form.primaryAmountMinor)

        state = TransactionReducer.reduce(state, TransactionIntent.UpdatePrimaryAmount("1..2"))
        assertEquals("1..2", state.form.primaryAmountInput)
        assertTrue(
            TransactionValidator.validateAndBuildCommand(state.form, catalog) is TransactionValidationResult.Invalid
        )

        val dotOnly = TransactionReducer.reduce(state, TransactionIntent.UpdatePrimaryAmount("."))
        assertTrue(
            TransactionValidator.validateAndBuildCommand(dotOnly.form, catalog) is TransactionValidationResult.Invalid
        )

        var dual = TransactionReducer.reduce(state, TransactionIntent.UpdatePrimaryAmount("10.00"))
        dual = TransactionReducer.reduce(dual, TransactionIntent.SelectCurrency(2L))
        dual = TransactionReducer.reduce(dual, TransactionIntent.UpdateSecondaryAmount("9.00"))
        val badSecondary = TransactionReducer.reduce(dual, TransactionIntent.UpdateSecondaryAmount("9..0"))
        val badSecondaryResult = TransactionValidator.validateAndBuildCommand(badSecondary.form, catalog)
        assertTrue(badSecondaryResult is TransactionValidationResult.Invalid)

        val badRate = TransactionReducer.reduce(dual, TransactionIntent.UpdateExchangeRate("1..5"))
        assertTrue(
            TransactionValidator.validateAndBuildCommand(badRate.form, catalog) is TransactionValidationResult.Invalid
        )

        val zeroRate = TransactionReducer.reduce(dual, TransactionIntent.UpdateExchangeRate("0"))
        assertTrue(
            TransactionValidator.validateAndBuildCommand(zeroRate.form, catalog) is TransactionValidationResult.Invalid
        )

        val clearedRate = TransactionReducer.reduce(dual, TransactionIntent.UpdateExchangeRate(""))
        assertEquals(0.0, clearedRate.form.exchangeRate, 1e-6)
        assertTrue(
            TransactionValidator.validateAndBuildCommand(clearedRate.form, catalog) is TransactionValidationResult.Valid
        )

        val singleWithStaleDualFields = baseForm.copy(
            primaryAmountInput = "10.00",
            primaryAmountMinor = 1000L,
            secondaryAmountInput = "bad..sec",
            exchangeRateInput = "bad..rate",
        )
        assertTrue(
            TransactionValidator.validateAndBuildCommand(
                singleWithStaleDualFields,
                catalog,
            ) is TransactionValidationResult.Valid
        )
    }

    @Test
    fun selectSourceAccountAppliesRememberedCategoryModeAndPreservesTransfer() {
        val catalog = multiScaleCatalog()
        val baseForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            categoryId = TransactionIds.NO_CATEGORY_ID,
            isFromNewBlankLaunch = true,
        )
        val prefs = TransactionFormPreferences(isShowPayee = false, isRememberCategory = true)
        val state = TransactionUiState.Ready(
            form = baseForm,
            initialFormSnapshot = baseForm,
            catalog = catalog,
            prefs = prefs,
        )

        val afterIncomeAcc = TransactionReducer.reduce(
            state,
            TransactionIntent.SelectAccount(accountId = 2L, isDestination = false),
        )
        assertEquals(20L, afterIncomeAcc.form.categoryId)
        assertEquals(TransactionMode.INCOME, afterIncomeAcc.form.mode)

        val incomeState = state.copy(form = baseForm.copy(mode = TransactionMode.INCOME))
        val afterExpenseAcc = TransactionReducer.reduce(
            incomeState,
            TransactionIntent.SelectAccount(accountId = 3L, isDestination = false),
        )
        assertEquals(10L, afterExpenseAcc.form.categoryId)
        assertEquals(TransactionMode.EXPENSE, afterExpenseAcc.form.mode)

        val transferState = state.copy(form = baseForm.copy(mode = TransactionMode.TRANSFER))
        val afterTransferAcc = TransactionReducer.reduce(
            transferState,
            TransactionIntent.SelectAccount(accountId = 2L, isDestination = false),
        )
        assertEquals(TransactionMode.TRANSFER, afterTransferAcc.form.mode)
    }

    @Test
    fun accountAndCurrencyChangesNormalizeAmountScalesAndKeepSameScaleUnchanged() {
        val catalog = multiScaleCatalog()
        val usdForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountInput = "10.00",
            primaryAmountMinor = 1000L,
            isSplitMode = true,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 600L, isIncome = false),
                EditableSplit(localId = -2L, categoryId = 11L, amountMinor = 400L, isIncome = false),
            ),
        )
        val usdState = TransactionUiState.Ready(
            form = usdForm,
            initialFormSnapshot = usdForm,
            catalog = catalog,
            prefs = TransactionFormPreferences(),
        )

        val switchedToJpy = TransactionReducer.reduce(
            usdState,
            TransactionIntent.SelectAccount(accountId = 3L, isDestination = false),
        )
        assertEquals("10", switchedToJpy.form.primaryAmountInput)
        assertEquals(10L, switchedToJpy.form.primaryAmountMinor)
        assertEquals(listOf(6L, 4L), switchedToJpy.form.splits.map { it.amountMinor })

        val switchedToBhd = TransactionReducer.reduce(
            usdState,
            TransactionIntent.SelectAccount(accountId = 4L, isDestination = false),
        )
        assertEquals("10.000", switchedToBhd.form.primaryAmountInput)
        assertEquals(10000L, switchedToBhd.form.primaryAmountMinor)

        val foreignJpy = TransactionReducer.reduce(
            usdState.copy(form = usdForm.copy(primaryAmountInput = "10.75", primaryAmountMinor = 1075L)),
            TransactionIntent.SelectCurrency(3L),
        )
        assertEquals("11", foreignJpy.form.primaryAmountInput)
        assertEquals(11L, foreignJpy.form.primaryAmountMinor)

        val invalidTextOnUsd = usdState.copy(
            form = usdForm.copy(primaryAmountInput = "1..2", primaryAmountMinor = 1000L),
        )
        val invalidSwitchedToJpy = TransactionReducer.reduce(
            invalidTextOnUsd,
            TransactionIntent.SelectAccount(accountId = 3L, isDestination = false),
        )
        assertEquals("10", invalidSwitchedToJpy.form.primaryAmountInput)
        assertEquals(10L, invalidSwitchedToJpy.form.primaryAmountMinor)

        val sameScaleStart = usdState.copy(form = usdForm.copy(primaryAmountInput = "10.5", primaryAmountMinor = 1050L))
        val sameScaleEur = TransactionReducer.reduce(
            sameScaleStart,
            TransactionIntent.SelectAccount(accountId = 2L, isDestination = false),
        )
        assertEquals("10.5", sameScaleEur.form.primaryAmountInput)
        assertEquals(1050L, sameScaleEur.form.primaryAmountMinor)
    }

    @Test
    fun rebalanceAdjustTotalPreservesAllocationSignAndUpdatesMode() {
        val catalog = multiScaleCatalog()
        val expenseWithNetIncomeSplits = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountInput = "50.00",
            primaryAmountMinor = 5000L,
            isSplitMode = true,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 2000L, isIncome = false),
                EditableSplit(localId = -2L, categoryId = 20L, amountMinor = 12000L, isIncome = true),
            ),
        )
        val initial = TransactionUiState.Ready(
            form = expenseWithNetIncomeSplits,
            initialFormSnapshot = expenseWithNetIncomeSplits,
            catalog = catalog,
            prefs = TransactionFormPreferences(),
        )

        val adjustedToIncome = TransactionReducer.reduce(initial, TransactionIntent.RebalanceAdjustTotal)
        assertEquals(TransactionMode.INCOME, adjustedToIncome.form.mode)
        assertEquals(10000L, adjustedToIncome.form.primaryAmountMinor)
        assertEquals("100.00", adjustedToIncome.form.primaryAmountInput)
        val parentSignedIncome = SplitAllocationCalculator.parentSignedMinor(
            adjustedToIncome.form.primaryAmountMinor,
            adjustedToIncome.form.mode,
        )
        assertEquals(0L, SplitAllocationCalculator.unsplitSignedMinor(parentSignedIncome, adjustedToIncome.form.splits))

        val incomeWithNetExpenseSplits = initial.copy(
            form = expenseWithNetIncomeSplits.copy(
                mode = TransactionMode.INCOME,
                splits = listOf(
                    EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 7000L, isIncome = false),
                    EditableSplit(localId = -2L, categoryId = 20L, amountMinor = 2000L, isIncome = true),
                ),
            )
        )
        val adjustedToExpense = TransactionReducer.reduce(
            incomeWithNetExpenseSplits,
            TransactionIntent.RebalanceAdjustTotal,
        )
        assertEquals(TransactionMode.EXPENSE, adjustedToExpense.form.mode)
        assertEquals(5000L, adjustedToExpense.form.primaryAmountMinor)

        val zeroSumSplits = initial.copy(
            form = expenseWithNetIncomeSplits.copy(
                mode = TransactionMode.EXPENSE,
                splits = listOf(
                    EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 5000L, isIncome = false),
                    EditableSplit(localId = -2L, categoryId = 20L, amountMinor = 5000L, isIncome = true),
                ),
            )
        )
        val adjustedZero = TransactionReducer.reduce(zeroSumSplits, TransactionIntent.RebalanceAdjustTotal)
        assertEquals(TransactionMode.EXPENSE, adjustedZero.form.mode)
        assertEquals(0L, adjustedZero.form.primaryAmountMinor)
    }

    @Test
    fun collapseSingleSplitReconcilesFormModeToMatchSplitSign() {
        val catalog = multiScaleCatalog()
        val expenseFormWithIncomeSplit = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountInput = "50.00",
            primaryAmountMinor = 5000L,
            isSplitMode = true,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 20L, amountMinor = 5000L, isIncome = true),
            ),
        )
        val initialExpense = TransactionUiState.Ready(
            form = expenseFormWithIncomeSplit,
            initialFormSnapshot = expenseFormWithIncomeSplit,
            catalog = catalog,
            prefs = TransactionFormPreferences(),
        )
        val collapsedToIncome = TransactionReducer.reduce(initialExpense, TransactionIntent.CollapseSingleSplit)
        assertFalse(collapsedToIncome.form.isSplitMode)
        assertEquals(TransactionMode.INCOME, collapsedToIncome.form.mode)
        assertEquals(20L, collapsedToIncome.form.categoryId)
        assertTrue(collapsedToIncome.form.splits.isEmpty())
        val incomeCmd = (
            TransactionValidator.validateAndBuildCommand(collapsedToIncome.form, catalog)
                as TransactionValidationResult.Valid
            ).command
        assertEquals(5000L, incomeCmd.fromAmountMinor)

        val incomeFormWithExpenseSplit = initialExpense.copy(
            form = expenseFormWithIncomeSplit.copy(
                mode = TransactionMode.INCOME,
                splits = listOf(
                    EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 5000L, isIncome = false),
                ),
            ),
        )
        val collapsedToExpense = TransactionReducer.reduce(
            incomeFormWithExpenseSplit,
            TransactionIntent.CollapseSingleSplit,
        )
        assertEquals(TransactionMode.EXPENSE, collapsedToExpense.form.mode)
        assertEquals(10L, collapsedToExpense.form.categoryId)
    }

    @Test
    fun calculatorExpressionEvaluatorAllowsSmallNonZeroDivisorsAndRejectsZero() {
        val smallDiv = requireNotNull(CalculatorExpressionEvaluator.evaluate("0.000000000001 / 0.0000000000001"))
        assertEquals(10.0, smallDiv, 1e-6)
        val tinyDivisor = requireNotNull(CalculatorExpressionEvaluator.evaluate("1 / 0.0000000000001"))
        assertEquals(1e13, tinyDivisor, 1e7)
        assertNull(CalculatorExpressionEvaluator.evaluate("10 / 0"))
        assertNull(CalculatorExpressionEvaluator.evaluate("10 / -0"))
        assertNull(CalculatorExpressionEvaluator.evaluate("10 / (5 - 5)"))
    }

    @Test
    fun allocateAccountAmountsNeverReversesSplitDirectionOnRounding() {
        val fourUnitIncomeSplits = (1..4).map { idx ->
            EditableSplit(localId = -idx.toLong(), categoryId = 20L, amountMinor = 1L, isIncome = true)
        }
        val incomeAllocated = checkNotNull(
            SplitAllocationCalculator.allocateAccountAmounts(
                splits = fourUnitIncomeSplits,
                originalTotalSignedMinor = 4L,
                accountTotalSignedMinor = 2L,
            )
        )
        assertEquals(2L, incomeAllocated.sum())
        assertTrue(incomeAllocated.all { it >= 0L })

        val fourUnitExpenseSplits = (1..4).map { idx ->
            EditableSplit(localId = -idx.toLong(), categoryId = 10L, amountMinor = 1L, isIncome = false)
        }
        val expenseAllocated = checkNotNull(
            SplitAllocationCalculator.allocateAccountAmounts(
                splits = fourUnitExpenseSplits,
                originalTotalSignedMinor = -4L,
                accountTotalSignedMinor = -2L,
            )
        )
        assertEquals(-2L, expenseAllocated.sum())
        assertTrue(expenseAllocated.all { it <= 0L })

        val mixedRoundingSplits = listOf(
            EditableSplit(localId = -1L, categoryId = 20L, amountMinor = 4L, isIncome = true),
            EditableSplit(localId = -2L, categoryId = 10L, amountMinor = 1L, isIncome = false),
            EditableSplit(localId = -3L, categoryId = 11L, amountMinor = 1L, isIncome = false),
        )
        val mixedAllocated = checkNotNull(
            SplitAllocationCalculator.allocateAccountAmounts(
                splits = mixedRoundingSplits,
                originalTotalSignedMinor = 2L,
                accountTotalSignedMinor = 1L,
            )
        )
        assertEquals(1L, mixedAllocated.sum())
        assertTrue(mixedAllocated[0] >= 0L)
        assertTrue(mixedAllocated[1] <= 0L)
        assertTrue(mixedAllocated[2] <= 0L)
    }

    @Test
    fun allocateAccountAmountsReturnsNullWhenPerSplitQuotientOverflowsLong() {
        val overflowSplits = listOf(
            EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 1_000_000_000_000L, isIncome = false),
            EditableSplit(localId = -2L, categoryId = 20L, amountMinor = 999_999_999_999L, isIncome = true),
        )
        assertNull(
            SplitAllocationCalculator.allocateAccountAmounts(
                splits = overflowSplits,
                originalTotalSignedMinor = -1L,
                accountTotalSignedMinor = -1_000_000_000_000L,
            )
        )

        val inRangeMixedSplits = listOf(
            EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 1000L, isIncome = false),
            EditableSplit(localId = -2L, categoryId = 20L, amountMinor = 999L, isIncome = true),
        )
        assertEquals(
            listOf(-100_000L, 99_900L),
            SplitAllocationCalculator.allocateAccountAmounts(
                splits = inRangeMixedSplits,
                originalTotalSignedMinor = -1L,
                accountTotalSignedMinor = -100L,
            ),
        )
    }

    @Test
    fun validatorRejectsForeignCurrencySplitWhenConvertedAllocationOverflowsLong() {
        val catalog = multiScaleCatalog()
        val overflowForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            originalCurrencyId = 2L,
            primaryAmountInput = "0.01",
            primaryAmountMinor = 1L,
            secondaryAmountInput = "10000000000.00",
            secondaryAmountMinor = 1_000_000_000_000L,
            isSplitMode = true,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 1_000_000_000_000L, isIncome = false),
                EditableSplit(localId = -2L, categoryId = 20L, amountMinor = 999_999_999_999L, isIncome = true),
            ),
        )
        val overflowResult = TransactionValidator.validateAndBuildCommand(overflowForm, catalog)
        assertTrue(overflowResult is TransactionValidationResult.Invalid)
        assertTrue((overflowResult as TransactionValidationResult.Invalid).errors.isNotEmpty())

        val validSameSignForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            originalCurrencyId = 2L,
            primaryAmountInput = "10.00",
            primaryAmountMinor = 1000L,
            secondaryAmountInput = "12.00",
            secondaryAmountMinor = 1200L,
            isSplitMode = true,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 700L, isIncome = false),
                EditableSplit(localId = -2L, categoryId = 11L, amountMinor = 300L, isIncome = false),
            ),
        )
        val validResult = TransactionValidator.validateAndBuildCommand(validSameSignForm, catalog)
        val command = (validResult as TransactionValidationResult.Valid).command
        assertEquals(command.fromAmountMinor, command.splits.sumOf { it.amountMinor })
    }

    private fun multiScaleCatalog(): TransactionCatalog {
        val usd = CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true)
        val eur = CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2)
        val jpy = CurrencyOption(id = 3L, code = "JPY", symbol = "¥", decimals = 0)
        val bhd = CurrencyOption(id = 4L, code = "BHD", symbol = "BD", decimals = 3)
        return TransactionCatalog(
            accounts = multiScaleAccounts(),
            currencies = listOf(usd, eur, jpy, bhd),
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
        )
    }

    private fun multiScaleAccounts(): List<AccountOption> = listOf(
        AccountOption(
            id = 1L,
            title = "USD Cash",
            typeLabel = "CASH",
            currencyId = 1L,
            currencyCode = "USD",
            currencySymbol = "$",
            currencyDecimals = 2,
            balanceMinor = 50000L,
            lastAccountId = 5L,
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
            lastCategoryId = 20L,
        ),
        AccountOption(
            id = 3L,
            title = "JPY Wallet",
            typeLabel = "CASH",
            currencyId = 3L,
            currencyCode = "JPY",
            currencySymbol = "¥",
            currencyDecimals = 0,
            balanceMinor = 50000L,
            lastCategoryId = 10L,
        ),
        AccountOption(
            id = 4L,
            title = "BHD Bank",
            typeLabel = "BANK",
            currencyId = 4L,
            currencyCode = "BHD",
            currencySymbol = "BD",
            currencyDecimals = 3,
            balanceMinor = 10000L,
        ),
        AccountOption(
            id = 5L,
            title = "USD Savings",
            typeLabel = "BANK",
            currencyId = 1L,
            currencyCode = "USD",
            currencySymbol = "$",
            currencyDecimals = 2,
            balanceMinor = 90000L,
        ),
    )
}
