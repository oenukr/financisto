// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.LoadedTransactionRecord
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import kotlin.math.abs

object TransactionInitialStateFactory {

    fun fromBlankNew(
        args: TransactionLaunchArgs.BlankNew,
        catalog: TransactionCatalog,
        prefs: TransactionFormPreferences,
        nowMillis: Long,
    ): TransactionFormState {
        val activeAccounts = catalog.accounts.filter { it.isActive }
        val resolvedFromId = resolveInitialAccountId(args.accountId, activeAccounts, prefs)
        val fromAccount = activeAccounts.firstOrNull { it.id == resolvedFromId }
        val resolvedToId = resolveInitialToAccountId(args, fromAccount, activeAccounts, prefs)
        return TransactionFormState(
            mode = args.initialMode,
            dateTimeMillis = args.initialDateTimeMillis ?: nowMillis,
            fromAccountId = resolvedFromId,
            toAccountId = resolvedToId,
            isFromNewBlankLaunch = true,
        )
    }

    fun fromLoadedRecord(
        record: LoadedTransactionRecord,
        catalog: TransactionCatalog,
    ): TransactionFormState {
        val isForeignExpense = record.mode != TransactionMode.TRANSFER && record.originalCurrencyId > 0L
        val primaryMinor = when {
            isForeignExpense -> abs(record.originalFromAmountMinor)
            else -> abs(record.fromAmountMinor)
        }
        val secondaryMinor = when {
            record.mode == TransactionMode.TRANSFER -> abs(record.toAmountMinor)
            isForeignExpense -> abs(record.fromAmountMinor)
            else -> 0L
        }
        val base = TransactionFormState(
            existingTransactionId = record.id,
            mode = record.mode,
            dateTimeMillis = record.dateTimeMillis,
            status = record.status,
            fromAccountId = record.fromAccountId,
            toAccountId = record.toAccountId,
            originalCurrencyId = record.originalCurrencyId.takeIf { it > 0L },
            primaryAmountMinor = primaryMinor,
            secondaryAmountMinor = secondaryMinor,
            payeeId = record.payeeId,
            payeeQuery = catalog.payees.firstOrNull { it.id == record.payeeId }?.title.orEmpty(),
            categoryId = record.categoryId,
            isSplitMode = record.categoryId == TransactionIds.SPLIT_CATEGORY_ID || record.splits.isNotEmpty(),
            splits = record.splits,
            nextSplitLocalId = -(record.splits.size + 1L),
            locationId = record.locationId,
            locationQuery = catalog.locations.firstOrNull { it.id == record.locationId }?.title.orEmpty(),
            note = record.note,
            projectId = record.projectId,
            attachedPictureFileName = record.attachedPicture,
            initialPersistedPictureFileName = record.attachedPicture,
            isCCardPayment = record.isCCardPayment,
            isFromNewBlankLaunch = false,
        )
        return populateFormattedAmountsAndRate(base, catalog)
    }

    fun fromAiDraft(
        draft: TransactionDraft,
        catalog: TransactionCatalog,
        isConsumedDraft: Boolean = false,
    ): TransactionFormState {
        val validCategoryIds = catalog.categories.mapTo(hashSetOf()) { it.id }
        val splits = draft.splits.mapIndexed { index, split ->
            EditableSplit(
                localId = -(index + 1L),
                categoryId = split.categoryId.takeIf { it in validCategoryIds } ?: TransactionIds.NO_CATEGORY_ID,
                amountMinor = abs(split.amountMinor),
                isIncome = split.amountMinor > 0L,
                note = split.note,
                projectId = split.projectId,
            )
        }
        val primaryMinor = resolveDraftPrimaryAmount(draft)
        val secondaryMinor = resolveDraftSecondaryAmount(draft, catalog)
        val matchedPayee = draft.payeeId?.let { id -> catalog.payees.firstOrNull { it.id == id && it.isActive } }
        val payeeTitle = matchedPayee?.title ?: draft.pendingPayeeName.orEmpty()
        val stagedList = if (isConsumedDraft) emptyList() else listOfNotNull(draft.stagedReceiptFileName)
        val persistedPicture = if (isConsumedDraft) draft.stagedReceiptFileName else null
        val base = TransactionFormState(
            aiDraftId = draft.draftId,
            mode = draft.mode,
            dateTimeMillis = draft.dateTimeMillis,
            status = draft.status,
            fromAccountId = draft.fromAccountId ?: 0L,
            toAccountId = draft.toAccountId ?: 0L,
            originalCurrencyId = draft.originalCurrencyId?.takeIf { it > 0L },
            primaryAmountMinor = primaryMinor,
            secondaryAmountMinor = secondaryMinor,
            payeeId = matchedPayee?.id ?: TransactionIds.NO_PAYEE_ID,
            pendingPayeeName = if (matchedPayee == null) draft.pendingPayeeName else null,
            payeeQuery = payeeTitle,
            categoryId = if (splits.isNotEmpty()) {
                TransactionIds.SPLIT_CATEGORY_ID
            } else {
                draft.categoryId?.takeIf { it in validCategoryIds } ?: TransactionIds.NO_CATEGORY_ID
            },
            isSplitMode = splits.isNotEmpty(),
            splits = splits,
            nextSplitLocalId = -(splits.size + 1L),
            locationId = draft.locationId,
            locationQuery = catalog.locations.firstOrNull { it.id == draft.locationId }?.title.orEmpty(),
            note = draft.note,
            projectId = draft.projectId,
            attachedPictureFileName = draft.stagedReceiptFileName,
            initialPersistedPictureFileName = persistedPicture,
            newlyStagedPictureFiles = stagedList,
            isCCardPayment = draft.isCCardPayment,
            isFromNewBlankLaunch = false,
        )
        return populateFormattedAmountsAndRate(base, catalog)
    }

    private fun resolveInitialToAccountId(
        args: TransactionLaunchArgs.BlankNew,
        fromAccount: AccountOption?,
        activeAccounts: List<AccountOption>,
        prefs: TransactionFormPreferences,
    ): Long {
        val shouldLookup = args.initialMode == TransactionMode.TRANSFER && prefs.isRememberAccount
        val candidate = if (shouldLookup) fromAccount?.lastAccountId ?: 0L else 0L
        val isValidTarget = candidate > 0L &&
            candidate != fromAccount?.id &&
            activeAccounts.any { it.id == candidate }
        return if (isValidTarget) candidate else 0L
    }

    private fun resolveDraftPrimaryAmount(draft: TransactionDraft): Long {
        val foreignMinor = draft.originalAmountMinor?.takeIf { draft.originalCurrencyId != null }
        return abs(foreignMinor ?: draft.amountMinor ?: 0L)
    }

    private fun resolveDraftSecondaryAmount(
        draft: TransactionDraft,
        catalog: TransactionCatalog,
    ): Long {
        return when {
            draft.mode == TransactionMode.TRANSFER -> resolveTransferSecondaryAmount(draft, catalog)
            draft.originalCurrencyId != null -> abs(draft.amountMinor ?: 0L)
            else -> 0L
        }
    }

    private fun resolveTransferSecondaryAmount(
        draft: TransactionDraft,
        catalog: TransactionCatalog,
    ): Long {
        val explicit = draft.toAmountMinor
        if (explicit != null) return abs(explicit)
        val fromAccount = draft.fromAccountId?.let { id -> catalog.accounts.firstOrNull { it.id == id } }
        val toAccount = draft.toAccountId?.let { id -> catalog.accounts.firstOrNull { it.id == id } }
        val isSameCurrency = fromAccount != null && fromAccount.currencyId == toAccount?.currencyId
        return if (isSameCurrency) abs(draft.amountMinor ?: 0L) else 0L
    }

    private fun resolveInitialAccountId(
        explicitAccountId: Long?,
        activeAccounts: List<AccountOption>,
        prefs: TransactionFormPreferences,
    ): Long {
        return when {
            explicitAccountId != null && activeAccounts.any { it.id == explicitAccountId } -> explicitAccountId
            prefs.isRememberAccount && activeAccounts.any { it.id == prefs.lastAccountId } -> prefs.lastAccountId
            else -> activeAccounts.singleOrNull()?.id ?: 0L
        }
    }

    private fun populateFormattedAmountsAndRate(
        form: TransactionFormState,
        catalog: TransactionCatalog,
    ): TransactionFormState {
        val (primaryCurrency, secondaryCurrency) = TransactionValidator.resolveCurrencyMeta(form, catalog)
        val primaryDecimals = primaryCurrency?.decimals ?: DEFAULT_DECIMALS
        val secondaryDecimals = secondaryCurrency?.decimals ?: DEFAULT_DECIMALS
        val rate = if (primaryCurrency != null && secondaryCurrency != null &&
            primaryCurrency.id != secondaryCurrency.id
        ) {
            CurrencyConversionCalculator.computeRate(
                fromMinor = form.primaryAmountMinor,
                fromDecimals = primaryDecimals,
                toMinor = form.secondaryAmountMinor,
                toDecimals = secondaryDecimals,
            )
        } else {
            0.0
        }
        return form.copy(
            primaryAmountInput = AmountScale.formatEditableAmount(form.primaryAmountMinor, primaryDecimals),
            secondaryAmountInput = AmountScale.formatEditableAmount(form.secondaryAmountMinor, secondaryDecimals),
            exchangeRate = rate,
            exchangeRateInput = CurrencyConversionCalculator.formatRate(rate),
        )
    }

    private const val DEFAULT_DECIMALS = 2
}
