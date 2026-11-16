// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appfunctions

import java.text.Normalizer
import java.util.Locale
import kotlin.math.round

object CategoryClassifier {

    private const val SHORT_QUERY_LENGTH = 2
    private const val MEDIUM_QUERY_LENGTH = 4
    private const val LONG_QUERY_LENGTH = 8
    private const val MAX_LEVENSHTEIN_DISTANCE = 3
    private const val MIN_STEM_LENGTH = 3
    private const val MIN_PREFIX_MATCH_LENGTH = 4
    private const val MIN_PREFIX_RATIO_NUMERATOR = 7
    private const val PREFIX_RATIO_DENOMINATOR = 10
    private const val DIRECT_TITLE_MATCH_SCORE = 12
    private const val DIRECT_PATH_MATCH_SCORE = 8
    private const val CENTS_SCALE = 100.0

    private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
    private val DIACRITIC_MARKS_REGEX = Regex("\\p{M}+")

    fun classify(
        categories: List<CategoryInfo>,
        payees: List<PayeeInfo>,
        categoryQuery: String? = null,
        merchantName: String? = null,
        receiptItems: String? = null,
        matchedAccount: AccountInfo? = null,
        isIncome: Boolean? = null,
        history: List<HistoricalCategorySignal> = emptyList(),
    ): ClassificationResult {
        val validCategories = categories.filter { it.id > 0L }
        val strictById = HistoryScoreAccumulator.filterStrictCategoriesById(validCategories, isIncome)
        val candidates = strictById.values.toList().ifEmpty { validCategories }
        val explicitMatch = categoryQuery?.let { findCategoryMatch(validCategories, it, isIncome) }
        val matchedPayee = merchantName?.let { findBestMatch(payees, it) { payee -> payee.title } }
        val itemTokens = tokenize(receiptItems.orEmpty())
        val merchantTokens = tokenize(merchantName.orEmpty())
        val payeeCategory = HistoryScoreAccumulator.resolvePayeeCategory(strictById, matchedPayee, history)

        val directOrHistoryMatch = matchByCategoryTokens(candidates, itemTokens, merchantTokens)
            ?.let { ClassificationResult(it, CategoryMatchConfidence.HIGH) }
            ?: matchByHistory(candidates, itemTokens, history, matchedPayee?.id)
            ?: matchByCategoryTokens(candidates, merchantTokens, emptySet())
                ?.let { ClassificationResult(it, CategoryMatchConfidence.HIGH) }
            ?: payeeCategory?.let { ClassificationResult(it, CategoryMatchConfidence.HIGH) }
            ?: matchByHistory(candidates, merchantTokens, history, matchedPayee?.id)

        val accountCategory = strictById[matchedAccount?.lastCategoryId]

        return when {
            explicitMatch != null -> ClassificationResult(explicitMatch, CategoryMatchConfidence.HIGH)
            directOrHistoryMatch != null -> directOrHistoryMatch
            accountCategory != null -> ClassificationResult(accountCategory, CategoryMatchConfidence.LOW)
            else -> ClassificationResult(null, CategoryMatchConfidence.NONE)
        }
    }

    fun classifyLineItems(
        categories: List<CategoryInfo>,
        payees: List<PayeeInfo>,
        lineItems: List<ReceiptLineItem>,
        merchantName: String? = null,
        matchedAccount: AccountInfo? = null,
        isIncome: Boolean? = null,
        fallbackCategoryQuery: String? = null,
        history: List<HistoricalCategorySignal> = emptyList(),
    ): SplitClassificationResult {
        val grouped = LinkedHashMap<Long?, MutableList<Pair<ReceiptLineItem, ClassificationResult>>>()
        for (item in lineItems) {
            val itemQuery = item.categoryHint ?: item.categoryQuery ?: fallbackCategoryQuery
            val resolved = classify(
                categories = categories,
                payees = payees,
                categoryQuery = itemQuery,
                merchantName = merchantName,
                receiptItems = item.description,
                matchedAccount = matchedAccount,
                isIncome = isIncome,
                history = history,
            )
            grouped.getOrPut(resolved.category?.id) { mutableListOf() }.add(item to resolved)
        }

        val suggestedSplits = grouped.values.map { entries ->
            HistoryScoreAccumulator.buildSuggestedSplit(entries, CENTS_SCALE)
        }
        val isSplit = suggestedSplits.size >= SHORT_QUERY_LENGTH
        val overallConfidence = if (suggestedSplits.isEmpty()) {
            CategoryMatchConfidence.NONE
        } else {
            suggestedSplits.minOf { CategoryMatchConfidence.valueOf(it.categoryConfidence) }
        }
        val singleCategory = if (!isSplit && grouped.size == 1) {
            grouped.values.first().first().second.category
        } else {
            null
        }

        return SplitClassificationResult(
            isSplitTransaction = isSplit,
            suggestedSplits = suggestedSplits,
            overallCategory = singleCategory,
            overallConfidence = overallConfidence,
        )
    }

    fun findCategoryMatch(
        categories: List<CategoryInfo>,
        query: String,
        isIncome: Boolean? = null,
    ): CategoryInfo? {
        val validCategories = categories.filter { it.id > 0L }
        val preferred = validCategories
            .filter { isIncome == null || it.isIncome == isIncome }
            .ifEmpty { validCategories }
        val selectors = listOf(CategoryInfo::fullPath, CategoryInfo::title)
        val queryTokens = tokenize(query)
        return listOf(preferred, validCategories).firstNotNullOfOrNull { pool ->
            selectors.firstNotNullOfOrNull { selector -> findBestMatch(pool, query, exactOnly = true, selector) }
                ?: selectors.firstNotNullOfOrNull { selector ->
                    findBestMatch(pool, query, exactOnly = false, selector)
                }
                ?: matchByCategoryTokens(pool, queryTokens, emptySet())
        }
    }

    fun <T> findBestMatch(
        items: List<T>,
        query: String,
        exactOnly: Boolean = false,
        getTitle: (T) -> String,
    ): T? = findBestMatches(items, query, exactOnly, getTitle).firstOrNull()

    fun <T> findBestMatches(
        items: List<T>,
        query: String,
        exactOnly: Boolean = false,
        getTitle: (T) -> String,
    ): List<T> {
        val trimmedQuery = query.trim()
        val normalizedQuery = HistoryScoreAccumulator.normalizeAlphanumeric(trimmedQuery)
        if (normalizedQuery.isEmpty()) return emptyList()

        val exactMatches = items.filter { getTitle(it).trim().equals(trimmedQuery, ignoreCase = true) }
        val normalizedMatches = if (exactMatches.isEmpty()) {
            items.filter { HistoryScoreAccumulator.normalizeAlphanumeric(getTitle(it)) == normalizedQuery }
        } else {
            emptyList()
        }
        val threshold = when {
            normalizedQuery.length <= SHORT_QUERY_LENGTH -> 0
            normalizedQuery.length <= MEDIUM_QUERY_LENGTH -> 1
            normalizedQuery.length <= LONG_QUERY_LENGTH -> 2
            else -> MAX_LEVENSHTEIN_DISTANCE
        }
        return when {
            exactMatches.isNotEmpty() -> exactMatches
            normalizedMatches.isNotEmpty() || exactOnly -> normalizedMatches
            else -> {
                val withinThreshold = items
                    .map { item -> item to HistoryScoreAccumulator.normalizeAlphanumeric(getTitle(item)) }
                    .filter { (_, normalizedTitle) -> normalizedTitle.isNotEmpty() }
                    .map { (item, normalizedTitle) ->
                        item to HistoryScoreAccumulator.calculateLevenshteinDistance(normalizedQuery, normalizedTitle)
                    }
                    .filter { (_, distance) -> distance <= threshold }
                val minDistance = withinThreshold.minOfOrNull { (_, distance) -> distance }
                if (minDistance == null) {
                    emptyList()
                } else {
                    withinThreshold.filter { (_, distance) -> distance == minDistance }.map { (item, _) -> item }
                }
            }
        }
    }

    private fun matchByCategoryTokens(
        categories: List<CategoryInfo>,
        primaryTokens: Set<String>,
        contextTokens: Set<String>,
    ): CategoryInfo? {
        if (primaryTokens.isEmpty()) return null
        var bestCategory: CategoryInfo? = null
        var bestScore = 0
        for (category in categories) {
            val titleTokens = tokenize(category.title)
            val pathTokens = tokenize(category.fullPath)
            val parentTokens = pathTokens.filterNot { p -> titleTokens.any { t -> tokensMatch(p, t) } }.toSet()
            val titleHits = HistoryScoreAccumulator.matchingStems(primaryTokens, titleTokens).size
            val primaryParentHits = HistoryScoreAccumulator.matchingStems(primaryTokens, parentTokens).size
            val contextParentHits = if (titleHits > 0) {
                HistoryScoreAccumulator.matchingStems(contextTokens, parentTokens).size
            } else {
                0
            }
            val parentHits = primaryParentHits + contextParentHits
            val childBonus = if (titleHits > 0 && category.parentId > 0L) 1 else 0
            val score = titleHits * DIRECT_TITLE_MATCH_SCORE + parentHits * DIRECT_PATH_MATCH_SCORE + childBonus
            if (score > bestScore) {
                bestScore = score
                bestCategory = category
            }
        }
        return bestCategory
    }

    private fun matchByHistory(
        categories: List<CategoryInfo>,
        inputTokens: Set<String>,
        history: List<HistoricalCategorySignal>,
        matchedPayeeId: Long?,
    ): ClassificationResult? {
        if (inputTokens.isEmpty() || history.isEmpty()) return null
        val categoryById = categories.associateBy { it.id }
        val accumulators = LinkedHashMap<Long, HistoryScoreAccumulator>()
        for (signal in history) {
            val category = categoryById[signal.categoryId]
            if (category != null) {
                val matchedStems = HistoryScoreAccumulator.matchingStems(inputTokens, tokenize(signal.note))
                if (matchedStems.isNotEmpty()) {
                    accumulators
                        .getOrPut(category.id) { HistoryScoreAccumulator(category) }
                        .record(matchedStems, signal.payeeId, matchedPayeeId)
                }
            }
        }
        return accumulators.values
            .maxByOrNull { it.rankScore() }
            ?.toResult()
    }

    fun tokenize(text: String): Set<String> {
        val rawTokens = text
            .lowercase(Locale.ROOT)
            .split(NON_ALPHANUMERIC_REGEX)
            .filter { it.length >= SHORT_QUERY_LENGTH }
        val result = LinkedHashSet<String>()
        for (token in rawTokens) {
            result.add(token)
            val normalized = Normalizer.normalize(token, Normalizer.Form.NFD)
                .replace(DIACRITIC_MARKS_REGEX, "")
            if (normalized.length >= SHORT_QUERY_LENGTH) {
                result.add(normalized)
            }
        }
        return result
    }

    fun tokensMatch(tokenA: String, tokenB: String): Boolean {
        val stemA = HistoryScoreAccumulator.canonicalStem(tokenA, DIACRITIC_MARKS_REGEX, MIN_STEM_LENGTH)
        val stemB = HistoryScoreAccumulator.canonicalStem(tokenB, DIACRITIC_MARKS_REGEX, MIN_STEM_LENGTH)
        val exactMatch = tokenA.equals(tokenB, ignoreCase = true) || stemA == stemB
        val rawPrefixLen = tokenA.commonPrefixWith(tokenB, ignoreCase = true).length
        val rawMaxLen = maxOf(tokenA.length, tokenB.length)
        val rawPrefixMatch = rawPrefixLen >= MIN_PREFIX_MATCH_LENGTH &&
            rawPrefixLen * PREFIX_RATIO_DENOMINATOR >= rawMaxLen * MIN_PREFIX_RATIO_NUMERATOR
        val stemPrefixLen = stemA.commonPrefixWith(stemB).length
        val stemMaxLen = maxOf(stemA.length, stemB.length)
        val stemPrefixMatch = stemPrefixLen >= MIN_PREFIX_MATCH_LENGTH &&
            stemPrefixLen * PREFIX_RATIO_DENOMINATOR >= stemMaxLen * MIN_PREFIX_RATIO_NUMERATOR
        return exactMatch || rawPrefixMatch || stemPrefixMatch
    }
}

private class HistoryScoreAccumulator(
    val category: CategoryInfo,
    val matchedStems: MutableSet<String> = LinkedHashSet(),
    var hasPayeeHit: Boolean = false,
    var score: Int = 0,
) {
    fun record(stems: Set<String>, signalPayeeId: Long, matchedPayeeId: Long?) {
        val isPayeeHit = matchedPayeeId != null && matchedPayeeId > 0L && signalPayeeId == matchedPayeeId
        matchedStems.addAll(stems)
        if (isPayeeHit) hasPayeeHit = true
        score += stems.size * TOKEN_SCORE + if (isPayeeHit) PAYEE_BOOST else 0
    }

    fun rankScore(): Int = score * TIE_MULTIPLIER + if (category.parentId > 0L) 1 else 0

    fun toResult(): ClassificationResult {
        val confidence = if (matchedStems.size >= MIN_HIGH_HITS || hasPayeeHit) {
            CategoryMatchConfidence.HIGH
        } else {
            CategoryMatchConfidence.MEDIUM
        }
        return ClassificationResult(category, confidence)
    }

    companion object {
        private const val MIN_HIGH_HITS = 2
        private const val TOKEN_SCORE = 6
        private const val PAYEE_BOOST = 8
        private const val TIE_MULTIPLIER = 10
        private const val STEM_MIN_LEN = 3
        private val MARKS_REGEX = Regex("\\p{M}+")

        fun filterStrictCategoriesById(categories: List<CategoryInfo>, isIncome: Boolean?): Map<Long, CategoryInfo> =
            categories
                .filter { it.id > 0L && (isIncome == null || it.isIncome == isIncome) }
                .associateBy { it.id }

        fun resolvePayeeCategory(
            strictById: Map<Long, CategoryInfo>,
            matchedPayee: PayeeInfo?,
            history: List<HistoricalCategorySignal>,
        ): CategoryInfo? {
            val payeeId = matchedPayee?.id ?: 0L
            val mostFrequentId = if (payeeId > 0L) {
                history
                    .filter { it.payeeId == payeeId && it.categoryId in strictById }
                    .groupingBy { it.categoryId }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
            } else {
                null
            }
            return strictById[matchedPayee?.lastCategoryId] ?: strictById[mostFrequentId]
        }

        fun buildSuggestedSplit(
            entries: List<Pair<ReceiptLineItem, ClassificationResult>>,
            centsScale: Double,
        ): SuggestedSplit {
            val representative = entries.first().second.category
            val confidence = entries.minOf { it.second.confidence }
            val amounts = entries.mapNotNull { it.first.amount }
            val validPositiveAmounts = amounts.isNotEmpty() && amounts.all { it.isFinite() && it > 0.0 }
            val summedCents = if (validPositiveAmounts) {
                amounts.sumOf { round(it * centsScale).toLong() }
            } else {
                0L
            }
            val summedAmount = if (summedCents > 0L) summedCents / centsScale else null
            val combinedNote = entries
                .map { it.first.description.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
            return SuggestedSplit(
                category = representative?.let { CategoryOption(it.id, it.title, it.fullPath, it.isIncome) },
                categoryConfidence = confidence.name,
                amount = summedAmount,
                note = combinedNote,
            )
        }

        fun calculateLevenshteinDistance(s1: String, s2: String): Int {
            if (s1 == s2 || s1.isEmpty() || s2.isEmpty()) {
                return if (s1 == s2) 0 else s1.length + s2.length
            }

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

        fun matchingStems(queryTokens: Set<String>, targetTokens: Set<String>): Set<String> {
            if (queryTokens.isEmpty() || targetTokens.isEmpty()) return emptySet()
            return queryTokens
                .filter { q -> targetTokens.any { t -> CategoryClassifier.tokensMatch(q, t) } }
                .mapTo(LinkedHashSet()) { canonicalStem(it, MARKS_REGEX, STEM_MIN_LEN) }
        }

        fun normalizeAlphanumeric(text: String): String =
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(MARKS_REGEX, "")
                .filter(Char::isLetterOrDigit)

        fun canonicalStem(token: String, marksRegex: Regex, minStemLen: Int): String {
            val normalized = Normalizer.normalize(token.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(marksRegex, "")
            return when {
                normalized.length > minStemLen + 2 && normalized.endsWith("ies") ->
                    normalized.dropLast(minStemLen) + "i"
                normalized.length > minStemLen && normalized.endsWith("y") ->
                    normalized.dropLast(1) + "i"
                else -> normalized
            }
        }
    }
}

enum class CategoryMatchConfidence {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}

data class ClassificationResult(
    val category: CategoryInfo?,
    val confidence: CategoryMatchConfidence,
)

data class SplitClassificationResult(
    val isSplitTransaction: Boolean,
    val suggestedSplits: List<SuggestedSplit>,
    val overallCategory: CategoryInfo?,
    val overallConfidence: CategoryMatchConfidence,
)
