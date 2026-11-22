// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appfunctions

import android.app.PendingIntent
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@RequiresApi(value = 36)
@AppFunctionServiceEntryPoint(
    serviceName = "TransactionAppFunctionService",
    appFunctionXmlFileName = "financisto_app_function_service",
)
abstract class TransactionAppFunctions : AppFunctionService(), KoinComponent {

    private val db: AppFunctionDatabase by inject()
    private val clock: Clock by lazy { getKoin().getOrNull<Clock>() ?: Clock.System }

    /**
     * Classifies receipt details or line items against existing categories and resolves candidate accounts.
     *
     * If [lineItems] are provided and classify into multiple distinct categories, groups them into
     * [ReceiptTransactionPreparation.suggestedSplits] and sets [ReceiptTransactionPreparation.isSplitTransaction]
     * to true. If [receiptDateTime] is null and [receiptImageUri] is provided, extracts the date and time
     * from the image EXIF metadata when available.
     *
     * @param merchantName Merchant or payee name extracted from the receipt (e.g. "Whole Foods").
     * @param receiptItems Summary of purchased items or receipt text used for single-category classification.
     * @param lineItems Individual receipt line items or item groups used to detect and classify split categories.
     * @param accountQuery Optional account name hint to match against active accounts (e.g. "Cash", "Main Card").
     * @param categoryQuery Optional category name hint in the user's category language (e.g. "Groceries", "Продукти").
     * Categories may be user-defined in any language; map receipt items semantically to
     * [ReceiptTransactionPreparation.candidateCategories].
     * @param receiptDateTime Date and time extracted from the receipt, if available.
     * @param receiptImageUri URI of the receipt photo used for EXIF date fallback when [receiptDateTime] is null.
     * @param isIncome True for an income receipt, false or null for an expense receipt.
     * @return A [ReceiptTransactionPreparation] with candidate accounts, suggested category or splits, and flags.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun prepareReceiptTransaction(
        merchantName: String? = null,
        receiptItems: String? = null,
        lineItems: List<ReceiptLineItem> = emptyList(),
        accountQuery: String? = null,
        categoryQuery: String? = null,
        receiptDateTime: LocalDateTime? = null,
        receiptImageUri: Uri? = null,
        isIncome: Boolean? = null,
    ): ReceiptTransactionPreparation = executeOnIo("Failed to prepare receipt transaction.") {
        lineItems.forEachIndexed { index, item ->
            item.amount?.let { toValidatedMinorUnits(it, "Line item #${index + 1} amount", allowNegative = false) }
        }
        val accounts = db.getAccounts()
        val catalog = ClassificationCatalog(db.getCategories(), db.getPayees(), db.getCategoryHistory())
        val matchedAccount = resolveCandidateAccount(accounts, accountQuery)
        val matchedPayee =
            merchantName?.let { CategoryClassifier.findBestMatch(catalog.payees, it) { p -> p.title } }
        val resolvedPayeeName = matchedPayee?.title ?: merchantName?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedDateTime = receiptDateTime ?: receiptImageUri?.let(db::extractReceiptImageDateTime)
        val isIncomeTransaction = isIncome == true
        val candidateCategories = catalog.categories
            .filter { it.isIncome == isIncomeTransaction }
            .ifEmpty { catalog.categories }
            .map { CategoryOption(it.id, it.title, it.fullPath, it.isIncome) }
        val resolution = resolveReceiptCategories(
            catalog = catalog,
            lineItems = lineItems,
            categoryQuery = categoryQuery,
            merchantName = merchantName,
            receiptItems = receiptItems,
            matchedAccount = matchedAccount,
            isIncome = isIncomeTransaction,
        )
        ReceiptTransactionPreparation(
            matchedAccount = matchedAccount?.let { AccountOption(it.id, it.title, it.currencyCode, it.type) },
            candidateAccounts = accounts.map { AccountOption(it.id, it.title, it.currencyCode, it.type) },
            suggestedCategory = resolution.suggestedCategory,
            categoryConfidence = resolution.confidence.name,
            isSplitTransaction = resolution.isSplit,
            suggestedSplits = resolution.suggestedSplits,
            candidateCategories = candidateCategories,
            resolvedPayeeName = resolvedPayeeName,
            resolvedDateTime = resolvedDateTime,
            requiresAccountSelection = matchedAccount == null,
            requiresCategoryConfirmation = resolution.isSplit ||
                resolution.confidence.ordinal <= CategoryMatchConfidence.LOW.ordinal,
        )
    }

    /**
     * Prepares an expense, income, split transaction, or transfer draft and returns a PendingIntent to review it.
     *
     * Resolves accounts, categories or splits, payee, date, and optional receipt attachment without writing
     * to the Financisto database yet. If [toAccountName] or [toAccountId] is provided, prepares a transfer
     * from the source account to that destination account. If [splits] is non-empty, prepares a split
     * transaction across multiple existing categories. Otherwise, prepares a single-category expense or income.
     *
     * @param amount Non-zero transaction amount with at most 2 decimal places (e.g. 15.50).
     * @param accountName Optional name of an existing active source account (e.g. "Cash"). Used when [accountId]
     * is null.
     * @param categoryName Optional name or full path of an existing category (e.g. "Food > Groceries",
     * "Їжа > Продукти"). Categories may be user-defined in any language. Ignored for transfers or split
     * transactions; if null, classifies automatically from payee, note, and transaction history.
     * @param payeeName Optional payee or merchant name (e.g. "Starbucks"). Pending new payees are saved only when
     * the user confirms the draft in Financisto.
     * @param note Optional note or item description for the transaction.
     * @param isIncome True for income, false or null for expense. Ignored for transfers.
     * @param toAccountName Optional destination account name for transfers. Used when [toAccountId] is null.
     * Must be null when [splits] is non-empty.
     * @param transactionDateTime Optional transaction date and time. Defaults to EXIF or current date and time.
     * @param receiptImageUri Optional URI of a receipt photo to stage and attach to the transaction draft.
     * @param splits Optional list of category splits for multi-category transactions whose amounts sum to [amount].
     * @param accountId Optional unique ID of an existing active source account from [AccountOption.id]. Takes
     * precedence over [accountName] and disambiguates duplicate or similar account names.
     * @param toAccountId Optional unique ID of an existing active destination account from [AccountOption.id] for
     * transfers. Takes precedence over [toAccountName]; must be null when [splits] is non-empty.
     * @return A [PendingIntent] that opens Financisto's pre-filled transaction review screen before saving.
     */
    @OptIn(ExperimentalUuidApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun openDraft(
        amount: Double,
        accountName: String? = null,
        categoryName: String? = null,
        payeeName: String? = null,
        note: String? = null,
        isIncome: Boolean? = null,
        toAccountName: String? = null,
        transactionDateTime: LocalDateTime? = null,
        receiptImageUri: Uri? = null,
        splits: List<TransactionSplitInput> = emptyList(),
        accountId: Long? = null,
        toAccountId: Long? = null,
    ): PendingIntent = executeOnIo("Failed to prepare transaction draft.") {
        val accounts = db.getAccounts()
        val catalog = ClassificationCatalog(db.getCategories(), db.getPayees(), db.getCategoryHistory())
        val (amountInCents, account) = validateAmountAndResolveAccount(amount, accountName, accountId, accounts)
        val toAccount = resolveDestinationAccount(toAccountName, toAccountId, account, accounts, splits.isNotEmpty())
        val isIncomeTransaction = isIncome == true
        val resolvedCategory = resolveSingleCategory(
            categoryName = categoryName,
            catalog = catalog,
            payeeName = payeeName,
            note = note,
            account = account,
            isIncome = isIncomeTransaction,
            hasSplits = splits.isNotEmpty(),
            isTransfer = toAccount != null,
        )
        val resolvedSplits = resolveSplits(
            splits = splits,
            totalCents = amountInCents,
            catalog = catalog,
            payeeName = payeeName,
            account = account,
            isIncome = isIncomeTransaction,
        )
        val (payeeId, resolvedPayeeName) = resolvePayeeWithoutCreate(payeeName, catalog.payees)
        val attachedPictureFileName = receiptImageUri?.let(db::saveReceiptPicture)
        val resolvedDateTime = transactionDateTime ?: receiptImageUri?.let(db::extractReceiptImageDateTime)
        val epochMillis = resolvedDateTime
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
            ?: clock.now().toEpochMilliseconds()
        val signMultiplier = if (toAccount == null && isIncomeTransaction) 1L else -1L
        val isSplit = resolvedSplits.isNotEmpty()
        val draftId = Uuid.random().toString()
        val draft = AppFunctionTransactionDraft(
            draftId = draftId,
            fromAccountId = account.id,
            toAccountId = toAccount?.id,
            categoryId = if (isSplit) SPLIT_CATEGORY_ID else (resolvedCategory?.id ?: 0L),
            payeeId = payeeId,
            payeeName = resolvedPayeeName,
            amount = signMultiplier * amountInCents,
            toAmount = toAccount?.takeIf { isSameCurrency(account, it) }?.let { amountInCents },
            isIncome = toAccount == null && isIncomeTransaction,
            note = note.orEmpty(),
            dateTime = epochMillis,
            attachedPicture = attachedPictureFileName,
            splits = resolvedSplits.map { it.first },
        )
        db.createTransactionReviewPendingIntent(draft)
    }

    private fun resolveCandidateAccount(
        accounts: List<AccountInfo>,
        accountQuery: String?,
    ): AccountInfo? {
        return when {
            !accountQuery.isNullOrBlank() ->
                CategoryClassifier.findBestMatches(accounts, accountQuery) { it.title }.singleOrNull()
            accounts.size == 1 -> accounts.first()
            else -> null
        }
    }

    private fun resolveReceiptCategories(
        catalog: ClassificationCatalog,
        lineItems: List<ReceiptLineItem>,
        categoryQuery: String?,
        merchantName: String?,
        receiptItems: String?,
        matchedAccount: AccountInfo?,
        isIncome: Boolean,
    ): ReceiptCategoryResolution {
        val splitResult = lineItems.takeIf { it.isNotEmpty() }?.let { items ->
            CategoryClassifier.classifyLineItems(
                categories = catalog.categories,
                payees = catalog.payees,
                lineItems = items,
                merchantName = merchantName,
                matchedAccount = matchedAccount,
                isIncome = isIncome,
                fallbackCategoryQuery = categoryQuery,
                history = catalog.history,
            )
        }
        val singleResult = CategoryClassifier.classify(
            categories = catalog.categories,
            payees = catalog.payees,
            categoryQuery = categoryQuery,
            merchantName = merchantName,
            receiptItems = receiptItems,
            matchedAccount = matchedAccount,
            isIncome = isIncome,
            history = catalog.history,
        )
        val isSplit = splitResult?.isSplitTransaction == true
        val matchedCategory = if (isSplit) null else (splitResult?.overallCategory ?: singleResult.category)
        val confidence = when {
            isSplit -> splitResult.overallConfidence
            splitResult?.overallCategory != null -> splitResult.overallConfidence
            else -> singleResult.confidence
        }
        return ReceiptCategoryResolution(
            isSplit = isSplit,
            confidence = confidence,
            suggestedCategory = matchedCategory?.let { CategoryOption(it.id, it.title, it.fullPath, it.isIncome) },
            suggestedSplits = splitResult?.suggestedSplits.orEmpty(),
        )
    }

    private fun validateAmountAndResolveAccount(
        amount: Double,
        accountName: String?,
        accountId: Long?,
        accounts: List<AccountInfo>,
    ): Pair<Long, AccountInfo> {
        val amountInCents = toValidatedMinorUnits(amount, "Transaction amount", allowNegative = true)
        return amountInCents to resolveUniqueAccount(accounts, accountId, accountName, "Account")
    }

    private fun resolveDestinationAccount(
        toAccountName: String?,
        toAccountId: Long?,
        sourceAccount: AccountInfo,
        accounts: List<AccountInfo>,
        hasSplits: Boolean,
    ): AccountInfo? {
        if (toAccountId == null && toAccountName.isNullOrBlank()) return null
        val toAccount = resolveUniqueAccount(accounts, toAccountId, toAccountName, "Destination account")
        val invalidReason = when {
            hasSplits ->
                "Cannot specify both destination account '${toAccountName ?: toAccount.title}' and category splits."
            toAccount.id == sourceAccount.id ->
                "Source and destination accounts must be different ('${sourceAccount.title}')."
            else -> null
        }
        if (invalidReason != null) {
            throw AppFunctionInvalidArgumentException(invalidReason)
        }
        return toAccount
    }

    private fun resolveSingleCategory(
        categoryName: String?,
        catalog: ClassificationCatalog,
        payeeName: String?,
        note: String?,
        account: AccountInfo,
        isIncome: Boolean,
        hasSplits: Boolean,
        isTransfer: Boolean,
    ): CategoryInfo? {
        if (hasSplits || isTransfer) return null
        return if (!categoryName.isNullOrBlank()) {
            CategoryClassifier.findCategoryMatch(catalog.categories, categoryName, isIncome)
                ?: throw AppFunctionElementNotFoundException(
                    "Category '$categoryName' not found. Available categories: " +
                        "${catalog.categories.joinToString(", ") { it.fullPath }.ifEmpty { "none" }}."
                )
        } else {
            CategoryClassifier.classify(
                categories = catalog.categories,
                payees = catalog.payees,
                categoryQuery = null,
                merchantName = payeeName,
                receiptItems = note,
                matchedAccount = account,
                isIncome = isIncome,
                history = catalog.history,
            ).category
        }
    }

    private fun resolveSplits(
        splits: List<TransactionSplitInput>,
        totalCents: Long,
        catalog: ClassificationCatalog,
        payeeName: String?,
        account: AccountInfo,
        isIncome: Boolean,
    ): List<Pair<TransactionSplit, CategoryInfo>> {
        if (splits.isEmpty()) return emptyList()
        val adjustedCentsList = validateSplitAmounts(splits, totalCents)
        val signMultiplier = if (isIncome) 1L else -1L
        val availableCategories = catalog.categories.joinToString(", ") { it.fullPath }.ifEmpty { "none" }

        return splits.mapIndexed { index, split ->
            val resolvedCategory = if (!split.categoryName.isNullOrBlank()) {
                CategoryClassifier.findCategoryMatch(catalog.categories, split.categoryName, isIncome)
            } else {
                CategoryClassifier.classify(
                    categories = catalog.categories,
                    payees = catalog.payees,
                    categoryQuery = null,
                    merchantName = payeeName,
                    receiptItems = split.note,
                    matchedAccount = account,
                    isIncome = isIncome,
                    history = catalog.history,
                ).category
            } ?: throw AppFunctionElementNotFoundException(
                "Split category '${split.categoryName ?: split.note.orEmpty()}' not found. " +
                    "Available categories: $availableCategories."
            )
            TransactionSplit(
                categoryId = resolvedCategory.id,
                amount = signMultiplier * adjustedCentsList[index],
                note = split.note.orEmpty(),
            ) to resolvedCategory
        }
    }

    private fun validateSplitAmounts(splits: List<TransactionSplitInput>, totalCents: Long): List<Long> {
        val rawCents = splits.mapIndexed { index, split ->
            toValidatedMinorUnits(split.amount, "Split #${index + 1} amount", allowNegative = false)
        }
        val sumSplitCents = rawCents.sum()
        val remainder = totalCents - sumSplitCents
        if (abs(remainder) > 1L) {
            throw AppFunctionInvalidArgumentException(
                "Sum of split amounts (${sumSplitCents / AmountPrecision.CENTS_PER_UNIT}) does not equal " +
                    "total transaction amount (${totalCents / AmountPrecision.CENTS_PER_UNIT})."
            )
        }
        if (remainder == 0L) return rawCents
        val adjustedIndex = rawCents.indices.lastOrNull { rawCents[it] + remainder > 0L }
            ?: throw AppFunctionInvalidArgumentException(
                "Sum of split amounts (${sumSplitCents / AmountPrecision.CENTS_PER_UNIT}) does not equal " +
                    "total transaction amount (${totalCents / AmountPrecision.CENTS_PER_UNIT})."
            )
        return rawCents.mapIndexed { index, cents ->
            if (index == adjustedIndex) cents + remainder else cents
        }
    }

    private fun resolvePayeeWithoutCreate(
        payeeName: String?,
        payees: List<PayeeInfo>,
    ): Pair<Long?, String?> {
        if (payeeName.isNullOrBlank()) return null to null
        val trimmed = payeeName.trim()
        val existing = CategoryClassifier.findBestMatch(payees, trimmed) { it.title }
        return if (existing != null) {
            existing.id to existing.title
        } else {
            null to trimmed
        }
    }

    private companion object {
        private const val SPLIT_CATEGORY_ID = -1L
    }
}

private data class ClassificationCatalog(
    val categories: List<CategoryInfo>,
    val payees: List<PayeeInfo>,
    val history: List<HistoricalCategorySignal>,
)

private data class ReceiptCategoryResolution(
    val isSplit: Boolean,
    val confidence: CategoryMatchConfidence,
    val suggestedCategory: CategoryOption?,
    val suggestedSplits: List<SuggestedSplit>,
)

/** Summary of an active Financisto account. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AccountOption(
    /** Unique database identifier of the account. */
    val id: Long,
    /** Display name of the account (e.g. "Cash", "Main Card"). */
    val name: String,
    /** ISO currency code of the account (e.g. "USD", "EUR"). */
    val currency: String,
    /** Account type identifier (e.g. "CASH", "CARD", "BANK"). */
    val type: String,
)

/** Summary of an existing Financisto transaction category. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategoryOption(
    /** Unique database identifier of the category. */
    val id: Long,
    /** Display name of the category (e.g. "Groceries"). */
    val name: String,
    /** Full hierarchical category path (e.g. "Food > Groceries"). */
    val fullPath: String,
    /** True if this is an income category, false if it is an expense category. */
    val isIncome: Boolean,
)

/** An individual item or item group extracted from a receipt for category or split classification. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ReceiptLineItem(
    /** Description of the purchased item or group of items (e.g. "Milk and bread", "Birthday gift"). */
    val description: String,
    /** Optional positive amount with at most 2 decimal places for this item or group (e.g. 25.00). */
    val amount: Double? = null,
    /**
     * Optional category name or path hint in the user's category language (e.g. "Groceries", "Продукти", "Gifts").
     * Categories may be user-defined in any language; map receipt items semantically to `candidateCategories`
     * returned by `prepareReceiptTransaction`.
     */
    val categoryQuery: String? = null,
    /**
     * Optional semantic category hint in the user's category language (e.g. "Groceries", "Продукти", "Gifts").
     * Categories may be user-defined in any language; map receipt items semantically to `candidateCategories`
     * returned by `prepareReceiptTransaction`.
     */
    val categoryHint: String? = null,
)

/** A suggested category split for a multi-category receipt transaction. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SuggestedSplit(
    /** The matched existing category for this split, or null if unmatched. */
    val category: CategoryOption?,
    /** Confidence level of this split's category match: "HIGH", "MEDIUM", "LOW", or "NONE". */
    val categoryConfidence: String,
    /** Total amount summed for this split category, or null if item amounts were not provided. */
    val amount: Double?,
    /** Combined description of the receipt items in this split. */
    val note: String,
)

/** Input for a single split part when creating a split transaction across multiple categories. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TransactionSplitInput(
    /** Positive amount with at most 2 decimal places allocated to this split category (must be at least 0.01). */
    val amount: Double,
    /** Existing category name or full path for this split (e.g. "Food > Groceries", "Gifts"). */
    val categoryName: String? = null,
    /** Optional note or item description for this split part. */
    val note: String? = null,
)

/** Result of classifying receipt details against existing categories and resolving candidate accounts. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ReceiptTransactionPreparation(
    /** The resolved account when accountQuery uniquely matches or only one active account exists; null otherwise. */
    val matchedAccount: AccountOption?,
    /** Active accounts available to record the transaction. */
    val candidateAccounts: List<AccountOption>,
    /** Best-matching existing category for a single-category transaction, or null when split or unmatched. */
    val suggestedCategory: CategoryOption?,
    /** Overall confidence level of the category classification: "HIGH", "MEDIUM", "LOW", or "NONE". */
    val categoryConfidence: String,
    /** True when lineItems classify into multiple distinct existing categories in the same checkout. */
    val isSplitTransaction: Boolean,
    /** Suggested category splits when lineItems are provided. */
    val suggestedSplits: List<SuggestedSplit>,
    /** Existing categories matching the transaction type (expense or income). */
    val candidateCategories: List<CategoryOption>,
    /** Existing payee name if merchantName matched a known payee, or the provided merchantName. */
    val resolvedPayeeName: String?,
    /** Transaction date and time resolved from receiptDateTime or the receipt image EXIF metadata. */
    val resolvedDateTime: LocalDateTime?,
    /** True when multiple accounts match or no account was specified and the user must choose an account. */
    val requiresAccountSelection: Boolean,
    /** True when categoryConfidence is "LOW" or "NONE" or when isSplitTransaction is true. */
    val requiresCategoryConfirmation: Boolean,
)

// Map exceptions outside withContext so AppFunctionException never crosses a dispatcher boundary
// under JVM stack-trace recovery (kotlinx-coroutines calls getMethod, linking absent extension types).
@Suppress("InjectDispatcher")
private suspend fun <T> executeOnIo(
    fallbackMessage: String,
    block: () -> T,
): T {
    val outcome = withContext(Dispatchers.IO) {
        runCatching(block).onFailure { error ->
            if (error is CancellationException) throw error
        }
    }
    return outcome.getOrElse { error ->
        throw (error as? AppFunctionException)
            ?: AppFunctionAppUnknownException(error.message ?: fallbackMessage)
    }
}

private fun resolveUniqueAccount(
    accounts: List<AccountInfo>,
    accountId: Long?,
    accountName: String?,
    roleLabel: String,
): AccountInfo {
    val available = accounts.joinToString(", ") { it.title }.ifEmpty { "none" }
    val byId = if (accountId != null) {
        if (accountId <= 0L) {
            throw AppFunctionInvalidArgumentException("$roleLabel ID must be a positive identifier.")
        }
        accounts.firstOrNull { it.id == accountId } ?: throw AppFunctionElementNotFoundException(
            "$roleLabel ID $accountId not found. Available accounts: $available."
        )
    } else {
        null
    }
    return byId ?: resolveUniqueAccountByName(accounts, accountName, roleLabel, available)
}

private fun resolveUniqueAccountByName(
    accounts: List<AccountInfo>,
    accountName: String?,
    roleLabel: String,
    available: String,
): AccountInfo {
    val query = accountName?.takeIf { it.isNotBlank() }
    val matches = if (query != null) {
        CategoryClassifier.findBestMatches(accounts, query) { it.title }
    } else {
        emptyList()
    }
    val invalidReason = when {
        query == null -> "$roleLabel name or ID is required. Available accounts: $available."
        matches.size > 1 ->
            "$roleLabel '$query' matches multiple accounts: " +
                "${matches.joinToString(", ") { "${it.title} (id=${it.id})" }}. " +
                "Available accounts: $available."
        else -> null
    }
    if (invalidReason != null) {
        throw AppFunctionInvalidArgumentException(invalidReason)
    }
    return matches.singleOrNull() ?: throw AppFunctionElementNotFoundException(
        "$roleLabel '$query' not found. Available accounts: $available."
    )
}

private object AmountPrecision {
    const val CENTS_PER_UNIT = 100.0
    const val EPSILON = 1e-4
    const val MAX_STORED_CENTS = 1e14
}

private fun isSameCurrency(from: AccountInfo, to: AccountInfo): Boolean {
    return when {
        from.currencyId > 0L && to.currencyId > 0L -> from.currencyId == to.currencyId
        from.currencyCode.isNotBlank() && to.currencyCode.isNotBlank() ->
            from.currencyCode.equals(to.currencyCode, ignoreCase = true)
        else -> false
    }
}

private fun toValidatedMinorUnits(
    amount: Double,
    label: String,
    allowNegative: Boolean,
): Long {
    val scaled = if (amount.isFinite()) abs(amount) * AmountPrecision.CENTS_PER_UNIT else 0.0
    val rounded = round(scaled)
    val cents = rounded.toLong()
    val invalidReason = when {
        !amount.isFinite() -> "$label must be a finite number."
        !allowNegative && amount <= 0.0 -> "$label must be greater than 0.0."
        scaled > AmountPrecision.MAX_STORED_CENTS -> "$label exceeds the maximum supported amount."
        abs(scaled - rounded) > AmountPrecision.EPSILON ->
            "$label ($amount) supports at most 2 decimal places for stored amounts."
        cents <= 0L -> "$label must be at least 0.01."
        else -> null
    }
    if (invalidReason != null) {
        throw AppFunctionInvalidArgumentException(invalidReason)
    }
    return cents
}
