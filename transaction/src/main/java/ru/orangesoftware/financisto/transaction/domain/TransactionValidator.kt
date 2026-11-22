// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.SaveSplitCommand
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState

sealed interface TransactionValidationResult {
    data class Valid(val command: SaveTransactionCommand) : TransactionValidationResult
    data class Invalid(val errors: List<String>) : TransactionValidationResult
}

object TransactionValidator {
    private const val DEFAULT_DECIMALS = 2

    fun validateAndBuildCommand(
        form: TransactionFormState,
        catalog: TransactionCatalog,
    ): TransactionValidationResult {
        val errors = mutableListOf<String>()
        val fromAccount = catalog.accounts.firstOrNull { it.id == form.fromAccountId }
        if (fromAccount == null) {
            errors.add("Please select a source account.")
        }
        val isDualCurrency = isDualCurrencyActive(form, fromAccount, catalog)
        validateNumericInputs(form, catalog, isDualCurrency, errors)
        if (form.mode == TransactionMode.TRANSFER) {
            validateTransfer(form, catalog, isDualCurrency, errors)
        } else {
            validateNonTransfer(form, isDualCurrency, errors)
        }
        if (errors.isNotEmpty() || fromAccount == null) {
            return TransactionValidationResult.Invalid(errors)
        }
        val command = when (form.mode) {
            TransactionMode.TRANSFER -> buildTransferCommand(form, isDualCurrency)
            TransactionMode.EXPENSE, TransactionMode.INCOME ->
                buildSingleOrSplitCommand(form, fromAccount, isDualCurrency)
        }
        return TransactionValidationResult.Valid(command)
    }

    private fun validateNumericInputs(
        form: TransactionFormState,
        catalog: TransactionCatalog,
        isDualCurrency: Boolean,
        errors: MutableList<String>,
    ) {
        val (primaryCurrency, secondaryCurrency) = resolveCurrencyMeta(form, catalog)
        val primaryDecimals = primaryCurrency?.decimals ?: DEFAULT_DECIMALS
        val secondaryDecimals = secondaryCurrency?.decimals ?: DEFAULT_DECIMALS
        if (form.primaryAmountInput.isNotBlank() &&
            AmountScale.parseMinorUnits(form.primaryAmountInput, primaryDecimals) == null
        ) {
            errors.add("Enter a valid transaction amount.")
        }
        if (isDualCurrency &&
            form.secondaryAmountInput.isNotBlank() &&
            AmountScale.parseMinorUnits(form.secondaryAmountInput, secondaryDecimals) == null
        ) {
            errors.add("Enter a valid converted amount.")
        }
        if (isDualCurrency &&
            form.exchangeRateInput.isNotBlank() &&
            CurrencyConversionCalculator.parsePositiveRate(form.exchangeRateInput) == null
        ) {
            errors.add("Enter a valid exchange rate.")
        }
    }

    private fun validateTransfer(
        form: TransactionFormState,
        catalog: TransactionCatalog,
        isDualCurrency: Boolean,
        errors: MutableList<String>,
    ) {
        val toAccount = catalog.accounts.firstOrNull { it.id == form.toAccountId }
        if (toAccount == null) {
            errors.add("Please select a destination account.")
        } else if (toAccount.id == form.fromAccountId) {
            errors.add("Source and destination accounts must be different.")
        }
        if (form.isAiDraft && form.primaryAmountMinor <= 0L) {
            errors.add("Transfer amount must be greater than zero.")
        }
        if (form.isAiDraft && isDualCurrency && form.secondaryAmountMinor <= 0L) {
            errors.add("Destination transfer amount must be greater than zero.")
        }
    }

    private fun validateNonTransfer(
        form: TransactionFormState,
        isDualCurrency: Boolean,
        errors: MutableList<String>,
    ) {
        if (form.isSplitMode) {
            if (form.primaryAmountMinor <= 0L) {
                errors.add("Split transaction total must be greater than zero.")
            }
            if (form.splits.isEmpty()) {
                errors.add("Add at least one split item or turn off split mode.")
            }
            if (form.splits.any { it.amountMinor <= 0L }) {
                errors.add("Every split item must have a non-zero amount.")
            }
            val parentSigned = SplitAllocationCalculator.parentSignedMinor(form.primaryAmountMinor, form.mode)
            val unsplit = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, form.splits)
            if (form.splits.isNotEmpty() && unsplit != 0L) {
                errors.add("Split amounts must sum up to the total transaction amount.")
            }
        } else if (form.isAiDraft && form.primaryAmountMinor <= 0L) {
            errors.add("Transaction amount must be greater than zero.")
        }
        if (isDualCurrency && form.primaryAmountMinor > 0L && form.secondaryAmountMinor <= 0L) {
            errors.add("Converted account amount must be greater than zero.")
        }
    }

    private fun buildTransferCommand(
        form: TransactionFormState,
        isDualCurrency: Boolean,
    ): SaveTransactionCommand {
        val outMinor = -form.primaryAmountMinor
        val inMinor = if (isDualCurrency) form.secondaryAmountMinor else form.primaryAmountMinor
        return SaveTransactionCommand(
            existingTransactionId = form.existingTransactionId,
            mode = TransactionMode.TRANSFER,
            fromAccountId = form.fromAccountId,
            toAccountId = form.toAccountId,
            categoryId = TransactionIds.NO_CATEGORY_ID,
            payeeId = form.payeeId,
            pendingPayeeName = form.pendingPayeeName?.trim()?.takeIf { it.isNotEmpty() },
            projectId = form.projectId,
            pendingProjectName = form.pendingProjectName?.trim()?.takeIf { it.isNotEmpty() },
            locationId = form.locationId,
            fromAmountMinor = outMinor,
            toAmountMinor = inMinor,
            originalCurrencyId = 0L,
            originalFromAmountMinor = 0L,
            note = form.note.trim(),
            dateTimeMillis = form.dateTimeMillis,
            status = form.status,
            attachedPicture = form.attachedPictureFileName,
            isCCardPayment = form.isCCardPayment,
            splits = emptyList(),
        )
    }

    private fun buildSingleOrSplitCommand(
        form: TransactionFormState,
        fromAccount: AccountOption,
        isDualCurrency: Boolean,
    ): SaveTransactionCommand {
        val sign = if (form.mode == TransactionMode.INCOME) 1L else -1L
        val foreignCurrencyId = if (isDualCurrency) form.originalCurrencyId ?: 0L else 0L
        val accountAmountMinor = if (foreignCurrencyId > 0L) {
            sign * form.secondaryAmountMinor
        } else {
            sign * form.primaryAmountMinor
        }
        val originalFromMinor = if (foreignCurrencyId > 0L) sign * form.primaryAmountMinor else 0L
        val saveSplits = buildSaveSplits(
            form = form,
            foreignCurrencyId = foreignCurrencyId,
            originalFromMinor = originalFromMinor,
            accountAmountMinor = accountAmountMinor,
        )
        val effectiveCategoryId = if (form.isSplitMode) {
            TransactionIds.SPLIT_CATEGORY_ID
        } else {
            form.categoryId.coerceAtLeast(TransactionIds.NO_CATEGORY_ID)
        }
        val effectiveProjectId = if (form.isSplitMode) TransactionIds.NO_PROJECT_ID else form.projectId
        return SaveTransactionCommand(
            existingTransactionId = form.existingTransactionId,
            mode = form.mode,
            fromAccountId = fromAccount.id,
            toAccountId = 0L,
            categoryId = effectiveCategoryId,
            payeeId = form.payeeId,
            pendingPayeeName = form.pendingPayeeName?.trim()?.takeIf { it.isNotEmpty() },
            projectId = effectiveProjectId,
            pendingProjectName = if (form.isSplitMode) {
                null
            } else {
                form.pendingProjectName?.trim()?.takeIf { it.isNotEmpty() }
            },
            locationId = form.locationId,
            fromAmountMinor = accountAmountMinor,
            toAmountMinor = 0L,
            originalCurrencyId = foreignCurrencyId,
            originalFromAmountMinor = originalFromMinor,
            note = form.note.trim(),
            dateTimeMillis = form.dateTimeMillis,
            status = form.status,
            attachedPicture = form.attachedPictureFileName,
            isCCardPayment = form.isCCardPayment,
            splits = saveSplits,
        )
    }

    private fun buildSaveSplits(
        form: TransactionFormState,
        foreignCurrencyId: Long,
        originalFromMinor: Long,
        accountAmountMinor: Long,
    ): List<SaveSplitCommand> {
        return when {
            !form.isSplitMode -> emptyList()
            foreignCurrencyId > 0L -> {
                val accountAmounts = SplitAllocationCalculator.allocateAccountAmounts(
                    splits = form.splits,
                    originalTotalSignedMinor = originalFromMinor,
                    accountTotalSignedMinor = accountAmountMinor,
                )
                form.splits.mapIndexed { index, split ->
                    SaveSplitCommand(
                        categoryId = split.categoryId,
                        amountMinor = accountAmounts[index],
                        note = split.note.trim(),
                        projectId = split.projectId,
                        originalFromAmountMinor = SplitAllocationCalculator.splitSignedMinor(split),
                    )
                }
            }
            else -> form.splits.map { split ->
                SaveSplitCommand(
                    categoryId = split.categoryId,
                    amountMinor = SplitAllocationCalculator.splitSignedMinor(split),
                    note = split.note.trim(),
                    projectId = split.projectId,
                    originalFromAmountMinor = 0L,
                )
            }
        }
    }

    fun isDualCurrencyActive(
        form: TransactionFormState,
        fromAccount: AccountOption?,
        catalog: TransactionCatalog,
    ): Boolean {
        if (fromAccount == null) return false
        return if (form.mode == TransactionMode.TRANSFER) {
            val toAccount = catalog.accounts.firstOrNull { it.id == form.toAccountId }
            toAccount != null && toAccount.currencyId != fromAccount.currencyId
        } else {
            val origId = form.originalCurrencyId
            origId != null && origId > 0L && origId != fromAccount.currencyId
        }
    }

    fun resolveCurrencyMeta(
        form: TransactionFormState,
        catalog: TransactionCatalog,
    ): Pair<CurrencyOption?, CurrencyOption?> {
        val fromAccount = catalog.accounts.firstOrNull { it.id == form.fromAccountId }
        val toAccount = if (form.mode == TransactionMode.TRANSFER) {
            catalog.accounts.firstOrNull { it.id == form.toAccountId }
        } else {
            null
        }
        return resolveCurrencyMeta(form, fromAccount, toAccount, catalog)
    }

    fun resolveCurrencyMeta(
        form: TransactionFormState,
        fromAccount: AccountOption?,
        toAccount: AccountOption?,
        catalog: TransactionCatalog,
    ): Pair<CurrencyOption?, CurrencyOption?> {
        val fromAccountCurrency = fromAccount?.let {
            catalog.currencies.firstOrNull { c -> c.id == it.currencyId }
                ?: CurrencyOption(it.currencyId, it.currencyCode, it.currencySymbol, it.currencyDecimals)
        }
        return if (form.mode == TransactionMode.TRANSFER) {
            val toCurrency = toAccount?.let {
                catalog.currencies.firstOrNull { c -> c.id == it.currencyId }
                    ?: CurrencyOption(it.currencyId, it.currencyCode, it.currencySymbol, it.currencyDecimals)
            }
            fromAccountCurrency to toCurrency
        } else {
            val origCurrency = form.originalCurrencyId?.let { id ->
                catalog.currencies.firstOrNull { c -> c.id == id }
            }
            if (origCurrency != null && fromAccountCurrency != null && origCurrency.id != fromAccountCurrency.id) {
                origCurrency to fromAccountCurrency
            } else {
                fromAccountCurrency to fromAccountCurrency
            }
        }
    }
}
