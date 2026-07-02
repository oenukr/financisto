package ru.orangesoftware.financisto.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.round

class TransactionAppFunctions : KoinComponent {

    private val db: AppFunctionDatabase by inject()

    /**
     * Creates a transaction or transfer in Financisto.
     *
     * @param context The AppFunctionContext.
     * @param amount The transaction amount (e.g. 15.50).
     * @param accountName The name of the source account (e.g. "Cash").
     * @param categoryName The optional category of the transaction (e.g. "Food").
     * @param payeeName The optional payee name (e.g. "Starbucks"). If not found, a new Payee is created.
     * @param note An optional note/description for the transaction.
     * @param isIncome True for income, false/null for expense. Ignored for transfers.
     * @param toAccountName The optional destination account name. If provided, the transaction is treated as a transfer.
     * @return The database ID of the created transaction, or -1 if execution failed.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addTransaction(
        context: AppFunctionContext,
        amount: Double,
        accountName: String,
        categoryName: String?,
        payeeName: String?,
        note: String?,
        isIncome: Boolean?,
        toAccountName: String?
    ): Long = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve source account with intelligent matching
            val account = findBestMatch(db.getAccounts(), accountName) { it.title } ?: return@withContext -1L

            // 2. Resolve category (optional) with intelligent matching
            val categoryId = if (!categoryName.isNullOrBlank()) {
                findBestMatch(db.getCategories(), categoryName) { it.title }?.id ?: 0L
            } else {
                0L
            }

            // 3. Resolve or create payee (optional) with intelligent matching
            val payeeId = if (!payeeName.isNullOrBlank()) {
                val existing = findBestMatch(db.getPayees(), payeeName) { it.title }
                existing?.id ?: db.createPayee(payeeName)
            } else {
                0L
            }

            val absAmount = abs(amount)
            val amountInCents = round(absAmount * 100).toLong()
            val finalNote = note ?: ""

            if (!toAccountName.isNullOrBlank()) {
                // 4a. Handle Transfer with intelligent matching
                val toAccount = findBestMatch(db.getAccounts(), toAccountName) { it.title } ?: return@withContext -1L

                if (account.id == toAccount.id) return@withContext -1L

                db.insertOrUpdateTransaction(
                    fromAccountId = account.id,
                    toAccountId = toAccount.id,
                    categoryId = categoryId,
                    payeeId = payeeId,
                    amount = -amountInCents, // Deducted from source
                    toAmount = amountInCents,  // Added to destination
                    note = finalNote,
                    dateTime = System.currentTimeMillis()
                )
            } else {
                // 4b. Handle Regular Transaction
                val signMultiplier = if (isIncome == true) 1 else -1
                db.insertOrUpdateTransaction(
                    fromAccountId = account.id,
                    toAccountId = null,
                    categoryId = categoryId,
                    payeeId = payeeId,
                    amount = signMultiplier * amountInCents,
                    toAmount = null,
                    note = finalNote,
                    dateTime = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            -1L
        }
    }
}

/**
 * Normalizes a string by converting it to lowercase and removing all non-alphanumeric characters.
 */
private fun normalizeString(s: String): String {
    return s.lowercase().filter { it.isLetterOrDigit() }
}

/**
 * Calculates the Levenshtein distance between two strings.
 */
private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
    if (s1 == s2) return 0
    if (s1.isEmpty()) return s2.length
    if (s2.isEmpty()) return s1.length

    val dp = IntArray(s2.length + 1) { it }
    for (i in 1..s1.length) {
        var prev = i - 1
        dp[0] = i
        for (j in 1..s2.length) {
            val temp = dp[j]
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
            prev = temp
        }
    }
    return dp[s2.length]
}

/**
 * Finds the best match in [items] for [query] using title string returned by [getTitle].
 * Resolves exact matches first, then normalized matches, and falls back to Levenshtein distance.
 */
private fun <T> findBestMatch(items: List<T>, query: String, getTitle: (T) -> String): T? {
    if (query.isBlank()) return null
    val trimmedQuery = query.trim()

    // 1. Case-insensitive exact match
    val exactMatch = items.firstOrNull { getTitle(it).equals(trimmedQuery, ignoreCase = true) }
    if (exactMatch != null) return exactMatch

    // 2. Normalized match (ignores casing, spaces, and punctuation)
    val normalizedQuery = normalizeString(trimmedQuery)
    if (normalizedQuery.isEmpty()) return null

    val normalizedMatch = items.firstOrNull { normalizeString(getTitle(it)) == normalizedQuery }
    if (normalizedMatch != null) return normalizedMatch

    // 3. Levenshtein edit distance match (for typos and misspellings)
    var bestItem: T? = null
    var minDistance = Int.MAX_VALUE

    for (item in items) {
        val title = getTitle(item)
        val normalizedTitle = normalizeString(title)
        if (normalizedTitle.isEmpty()) continue

        val distance = calculateLevenshteinDistance(normalizedQuery, normalizedTitle)

        // Threshold:
        // - Allow 0 typos for queries of length <= 2
        // - Allow 1 typo for queries of length <= 4
        // - Allow 2 typos for queries of length <= 8
        // - Allow 3 typos for longer queries
        val threshold = when {
            normalizedQuery.length <= 2 -> 0
            normalizedQuery.length <= 4 -> 1
            normalizedQuery.length <= 8 -> 2
            else -> 3
        }

        if (distance <= threshold && distance < minDistance) {
            minDistance = distance
            bestItem = item
        }
    }

    return bestItem
}
