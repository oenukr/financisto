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
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

class TransactionDomainTest {

    @Test
    fun amountScaleHandlesZeroTwoAndThreeDecimalsAndEdgeCases() {
        assertEquals(1L, AmountScale.pow10(0))
        assertEquals(100L, AmountScale.pow10(2))
        assertEquals(1000L, AmountScale.pow10(3))

        assertEquals(1250L, AmountScale.parseMinorUnits("1250", 0))
        assertEquals(1550L, AmountScale.parseMinorUnits("15,5", 2))
        assertEquals(12345L, AmountScale.parseMinorUnits(" 12.345 ", 3))
        assertEquals(1235L, AmountScale.parseMinorUnits("12.345", 2))
        assertEquals(1300L, AmountScale.parseMinorUnits("12.999", 2))
        assertEquals(50L, AmountScale.parseMinorUnits(".5", 2))
        assertEquals(1550L, AmountScale.parseMinorUnits("-15.50", 2))
        assertEquals(150L, AmountScale.parseMinorUnits("00000000000000000001.50", 2))
        assertEquals(0L, AmountScale.parseMinorUnits("", 2))
        assertNull(AmountScale.parseMinorUnits("12abc", 2))
        assertNull(AmountScale.parseMinorUnits(".", 2))
        assertNull(AmountScale.parseMinorUnits("-", 2))
        assertNull(AmountScale.parseMinorUnits("1.2.3", 2))
        assertNull(AmountScale.parseMinorUnits("99999999999999999999", 2))

        assertEquals("1250", AmountScale.formatEditableAmount(1250L, 0))
        assertEquals("15.50", AmountScale.formatEditableAmount(1550L, 2))
        assertEquals("0.05", AmountScale.formatEditableAmount(5L, 2))
        assertEquals("12.345", AmountScale.formatEditableAmount(12345L, 3))

        assertEquals("-$ 15.50", AmountScale.formatDisplayAmount(-1550L, 2, "$", includeSign = true))
        assertEquals("+¥ 1,250", AmountScale.formatDisplayAmount(1250L, 0, "¥", includeSign = true))
        assertEquals("BD 12.345", AmountScale.formatDisplayAmount(12345L, 3, "BD", includeSign = false))
        assertEquals("$ 1,234,567.89", AmountScale.formatDisplayAmount(123456789L, 2, "$", includeSign = false))
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
    fun calculatorExpressionEvaluatorSupportsAsciiAndUnicodeOperatorsAndRejectsInvalidInput() {
        assertEvaluatesTo(14.0, "2 + 3 * 4")
        assertEvaluatesTo(20.0, "(2 + 3) * 4")
        assertEvaluatesTo(-3.5, "-7 / 2")
        assertEvaluatesTo(12.75, "10,5 + 2.25")
        assertEvaluatesTo(24.0, "6 × 4")
        assertEvaluatesTo(2.5, "10 ÷ 4")
        assertEvaluatesTo(6.0, "10 − 4")
        assertEvaluatesTo(-3.5, "−7 ÷ 2")
        assertEvaluatesTo(17.0, "(2 + 3) × 4 − 6 ÷ 2")
        assertNull(CalculatorExpressionEvaluator.evaluate("10 / 0"))
        assertNull(CalculatorExpressionEvaluator.evaluate("10 ÷ 0"))
        assertNull(CalculatorExpressionEvaluator.evaluate("(2 + 3"))
        assertNull(CalculatorExpressionEvaluator.evaluate(""))
        assertNull(CalculatorExpressionEvaluator.evaluate("12 + * 3"))
    }

    @Test
    fun validatorEnforcesRulesForManualAiAndForeignTransactions() {
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
    }

    @Test
    fun validatorEnforcesRulesForDomesticSplitsAndTransfers() {
        val catalog = sampleCatalog()
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
        val mismatchRes = TransactionValidator.validateAndBuildCommand(mismatchedSplit, catalog)
        assertTrue(mismatchRes is TransactionValidationResult.Invalid)

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
        assertEquals(listOf(-2000L, -3000L), splitCmd.splits.map { it.amountMinor })
        assertEquals(listOf(0L, 0L), splitCmd.splits.map { it.originalFromAmountMinor })

        val multiCurrencyTransfer = TransactionFormState(
            mode = TransactionMode.TRANSFER,
            fromAccountId = 1L,
            toAccountId = 2L,
            primaryAmountMinor = 10000L,
            secondaryAmountMinor = 9000L,
        )
        val transferResult = TransactionValidator.validateAndBuildCommand(multiCurrencyTransfer, catalog)
        val transferCmd = (transferResult as TransactionValidationResult.Valid).command
        assertEquals(-10000L, transferCmd.fromAmountMinor)
        assertEquals(9000L, transferCmd.toAmountMinor)

        val sameAccountTransfer = multiCurrencyTransfer.copy(toAccountId = 1L)
        val sameAccResult = TransactionValidator.validateAndBuildCommand(sameAccountTransfer, catalog)
        assertTrue(sameAccResult is TransactionValidationResult.Invalid)
    }

    @Test
    fun validatorPersistsForeignCurrencySplitsWithExactAccountSumAndOriginalAmounts() {
        val catalog = sampleCatalog()
        val foreignSplitForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            originalCurrencyId = 2L,
            primaryAmountMinor = 10000L,
            secondaryAmountMinor = 11000L,
            isSplitMode = true,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 3333L, isIncome = false, note = "A"),
                EditableSplit(localId = -2L, categoryId = 11L, amountMinor = 3333L, isIncome = false, note = "B"),
                EditableSplit(localId = -3L, categoryId = 10L, amountMinor = 3334L, isIncome = false, note = "C"),
            ),
        )
        val result = TransactionValidator.validateAndBuildCommand(foreignSplitForm, catalog)
        assertTrue(result is TransactionValidationResult.Valid)
        val cmd = (result as TransactionValidationResult.Valid).command

        assertEquals(2L, cmd.originalCurrencyId)
        assertEquals(-11000L, cmd.fromAmountMinor)
        assertEquals(-10000L, cmd.originalFromAmountMinor)
        assertEquals(listOf(-3333L, -3333L, -3334L), cmd.splits.map { it.originalFromAmountMinor })
        assertEquals(cmd.originalFromAmountMinor, cmd.splits.sumOf { it.originalFromAmountMinor })
        assertEquals(listOf(-3666L, -3666L, -3668L), cmd.splits.map { it.amountMinor })
        assertEquals(cmd.fromAmountMinor, cmd.splits.sumOf { it.amountMinor })

        val mixedSignForm = foreignSplitForm.copy(
            primaryAmountMinor = 10000L,
            secondaryAmountMinor = 12000L,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 12000L, isIncome = false),
                EditableSplit(localId = -2L, categoryId = 20L, amountMinor = 2000L, isIncome = true),
            ),
        )
        val mixedCmd = (TransactionValidator.validateAndBuildCommand(mixedSignForm, catalog)
            as TransactionValidationResult.Valid).command
        assertEquals(listOf(-12000L, 2000L), mixedCmd.splits.map { it.originalFromAmountMinor })
        assertEquals(listOf(-14400L, 2400L), mixedCmd.splits.map { it.amountMinor })
        assertEquals(mixedCmd.fromAmountMinor, mixedCmd.splits.sumOf { it.amountMinor })
    }

    @Test
    fun togglingSplitModeOffResetsSplitCategoryId() {
        val catalog = sampleCatalog()
        val initialForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            categoryId = 10L,
            primaryAmountMinor = 5000L,
            primaryAmountInput = "50.00",
        )
        var state = TransactionUiState.Ready(
            form = initialForm,
            initialFormSnapshot = initialForm,
            catalog = catalog,
            prefs = TransactionFormPreferences(),
        )

        state = TransactionReducer.reduce(state, TransactionIntent.ToggleSplitMode)
        assertTrue(state.form.isSplitMode)
        assertEquals(TransactionIds.SPLIT_CATEGORY_ID, state.form.categoryId)

        state = TransactionReducer.reduce(state, TransactionIntent.ToggleSplitMode)
        assertFalse(state.form.isSplitMode)
        assertEquals(TransactionIds.NO_CATEGORY_ID, state.form.categoryId)

        val saveResult = TransactionValidator.validateAndBuildCommand(state.form, catalog)
        assertTrue(saveResult is TransactionValidationResult.Valid)
        val cmd = (saveResult as TransactionValidationResult.Valid).command
        assertEquals(TransactionIds.NO_CATEGORY_ID, cmd.categoryId)
        assertTrue(cmd.splits.isEmpty())
    }

    @Test
    fun nestedSplitItemEditorFlowForNewSplitPreservesDraftAcrossPickersAndDismiss() {
        var state = splitReadyState()

        state = TransactionReducer.reduce(state, TransactionIntent.OpenNewSplitEditor)
        val openedNew = state.activeSheet as ActiveSheet.SplitItemEditor
        assertTrue(openedNew.isNew)
        assertEquals(-2L, openedNew.split.localId)
        assertEquals(4000L, openedNew.split.amountMinor)
        assertFalse(openedNew.split.isIncome)

        state = TransactionReducer.reduce(
            state,
            TransactionIntent.UpdateSplitDraft(openedNew.split.copy(note = "Refund item")),
        )
        state = TransactionReducer.reduce(state, TransactionIntent.OpenCategoryBrowser(forSplitLocalId = -2L))
        assertEquals(ActiveSheet.CategoryBrowser(forSplitLocalId = -2L), state.activeSheet)
        assertEquals(2, state.sheetStack.size)

        state = TransactionReducer.reduce(state, TransactionIntent.SelectCategory(20L, forSplitLocalId = -2L))
        val afterCategory = state.activeSheet as ActiveSheet.SplitItemEditor
        assertEquals(20L, afterCategory.split.categoryId)
        assertTrue(afterCategory.split.isIncome)
        assertEquals("Refund item", afterCategory.split.note)

        state = TransactionReducer.reduce(state, TransactionIntent.OpenProjectPicker(forSplitLocalId = -2L))
        assertEquals(ActiveSheet.ProjectPicker(forSplitLocalId = -2L), state.activeSheet)
        state = TransactionReducer.reduce(state, TransactionIntent.DismissSheet)
        assertEquals(afterCategory, state.activeSheet)

        state = TransactionReducer.reduce(state, TransactionIntent.OpenProjectPicker(forSplitLocalId = -2L))
        state = TransactionReducer.reduce(state, TransactionIntent.SelectProject(5L, forSplitLocalId = -2L))
        val afterProject = state.activeSheet as ActiveSheet.SplitItemEditor
        assertEquals(5L, afterProject.split.projectId)

        state = TransactionReducer.reduce(
            state,
            TransactionIntent.OpenCalculator(CalculatorTarget.SPLIT_AMOUNT, splitLocalId = -2L),
        )
        val calcSheet = state.activeSheet as ActiveSheet.Calculator
        assertEquals("40.00", calcSheet.initialText)
        assertEquals(-2L, calcSheet.splitLocalId)

        state = TransactionReducer.reduce(
            state,
            TransactionIntent.ApplyCalculatorResult(CalculatorTarget.SPLIT_AMOUNT, 25.5, splitLocalId = -2L),
        )
        val afterCalc = state.activeSheet as ActiveSheet.SplitItemEditor
        assertEquals(2550L, afterCalc.split.amountMinor)
        assertEquals(20L, afterCalc.split.categoryId)
        assertEquals(5L, afterCalc.split.projectId)
        assertEquals("Refund item", afterCalc.split.note)

        state = TransactionReducer.reduce(state, TransactionIntent.DismissSheet)
        assertNull(state.activeSheet)
    }

    @Test
    fun nestedSplitItemEditorFlowForExistingSplitUpdatesDraftUntilSaved() {
        var state = splitReadyState()

        state = TransactionReducer.reduce(state, TransactionIntent.OpenEditSplitEditor(localId = -1L))
        val openedExisting = state.activeSheet as ActiveSheet.SplitItemEditor
        assertFalse(openedExisting.isNew)
        assertEquals(6000L, openedExisting.split.amountMinor)

        state = TransactionReducer.reduce(state, TransactionIntent.OpenCategoryBrowser(forSplitLocalId = -1L))
        state = TransactionReducer.reduce(state, TransactionIntent.SelectCategory(20L, forSplitLocalId = -1L))
        val existingAfterCat = state.activeSheet as ActiveSheet.SplitItemEditor
        assertEquals(20L, existingAfterCat.split.categoryId)
        assertTrue(existingAfterCat.split.isIncome)
        assertEquals(10L, state.form.splits.first().categoryId)

        state = TransactionReducer.reduce(
            state,
            TransactionIntent.OpenCalculator(CalculatorTarget.SPLIT_AMOUNT, splitLocalId = -1L),
        )
        assertEquals("60.00", (state.activeSheet as ActiveSheet.Calculator).initialText)
        state = TransactionReducer.reduce(
            state,
            TransactionIntent.ApplyCalculatorResult(CalculatorTarget.SPLIT_AMOUNT, 75.0, splitLocalId = -1L),
        )
        val existingAfterCalc = state.activeSheet as ActiveSheet.SplitItemEditor
        assertEquals(7500L, existingAfterCalc.split.amountMinor)

        state = TransactionReducer.reduce(state, TransactionIntent.SaveSplitItem(existingAfterCalc.split))
        assertNull(state.activeSheet)
        assertEquals(7500L, state.form.splits.first().amountMinor)
        assertEquals(20L, state.form.splits.first().categoryId)
        assertTrue(state.form.splits.first().isIncome)
    }

    @Test
    fun crossCurrencyAiTransferDraftLeavesSecondaryAmountAndRateUnsetWhenToAmountNull() {
        val catalog = sampleCatalog()
        val crossCurrencyDraft = TransactionDraft(
            draftId = "draft-xfer-fx",
            mode = TransactionMode.TRANSFER,
            amountMinor = 10000L,
            fromAccountId = 1L,
            toAccountId = 2L,
            toAmountMinor = null,
            dateTimeMillis = 1_700_000_000_000L,
        )
        val form = TransactionInitialStateFactory.fromAiDraft(crossCurrencyDraft, catalog)
        assertEquals(10000L, form.primaryAmountMinor)
        assertEquals(0L, form.secondaryAmountMinor)
        assertEquals(0.0, form.exchangeRate, 1e-6)
        assertEquals("", form.exchangeRateInput)

        val explicitToAmountDraft = crossCurrencyDraft.copy(toAmountMinor = 9000L)
        val explicitForm = TransactionInitialStateFactory.fromAiDraft(explicitToAmountDraft, catalog)
        assertEquals(9000L, explicitForm.secondaryAmountMinor)
        assertEquals(0.9, explicitForm.exchangeRate, 1e-6)
    }

    @Test
    fun reducerHandlesPayeeDefaultsTransferSwitchAndSwap() {
        var state = blankReadyState()

        assertEquals(1L, state.fromAccount?.id)
        assertNull(state.toAccount)
        assertFalse(state.isDualCurrency)
        assertEquals("USD", state.primaryCurrency?.code)

        state = TransactionReducer.reduce(state, TransactionIntent.SelectPayee(200L))
        assertEquals(11L, state.form.categoryId)
        assertEquals("Starbucks", state.form.payeeQuery)

        state = TransactionReducer.reduce(state, TransactionIntent.SwitchMode(TransactionMode.TRANSFER))
        assertEquals(TransactionMode.TRANSFER, state.form.mode)
        assertEquals(2L, state.form.toAccountId)
        assertEquals("Coffee", state.form.note)
        assertTrue(state.isDualCurrency)
        assertEquals("USD", state.primaryCurrency?.code)
        assertEquals("EUR", state.secondaryCurrency?.code)

        state = TransactionReducer.reduce(state, TransactionIntent.UpdateSecondaryAmount("54.00"))
        assertEquals(5400L, state.form.secondaryAmountMinor)
        assertEquals(0.9, state.form.exchangeRate, 1e-6)

        state = TransactionReducer.reduce(state, TransactionIntent.SwapTransferAccounts)
        assertEquals(2L, state.form.fromAccountId)
        assertEquals(1L, state.form.toAccountId)
        assertEquals(5400L, state.form.primaryAmountMinor)
        assertEquals(6000L, state.form.secondaryAmountMinor)
    }

    @Test
    fun reducerHandlesSplitSeedingRebalancingAndCollapse() {
        var state = blankReadyState()
        state = TransactionReducer.reduce(state, TransactionIntent.SelectPayee(200L))
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

    private fun blankReadyState(): TransactionUiState.Ready {
        val initialForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountMinor = 6000L,
            primaryAmountInput = "60.00",
            note = "Coffee",
            isFromNewBlankLaunch = true,
        )
        return TransactionUiState.Ready(
            form = initialForm,
            initialFormSnapshot = initialForm,
            catalog = sampleCatalog(),
            prefs = TransactionFormPreferences(
                isRememberAccount = true,
                isRememberCategory = true,
            ),
        )
    }

    private fun splitReadyState(): TransactionUiState.Ready {
        val initialForm = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            fromAccountId = 1L,
            primaryAmountMinor = 10000L,
            primaryAmountInput = "100.00",
            isSplitMode = true,
            categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            splits = listOf(
                EditableSplit(localId = -1L, categoryId = 10L, amountMinor = 6000L, isIncome = false, projectId = 5L),
            ),
            nextSplitLocalId = -2L,
        )
        return TransactionUiState.Ready(
            form = initialForm,
            initialFormSnapshot = initialForm,
            catalog = sampleCatalog(),
            prefs = TransactionFormPreferences(),
        )
    }

    private fun assertEvaluatesTo(expected: Double, expression: String) {
        val actual = requireNotNull(CalculatorExpressionEvaluator.evaluate(expression)) {
            "Expected non-null evaluation for '$expression'"
        }
        assertEquals(expected, actual, 1e-6)
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
            projects = listOf(
                ProjectOption(id = 5L, title = "Trip"),
            ),
        )
    }
}
