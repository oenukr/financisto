// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyConversionCalculator {
    private const val RATE_PATTERN = "0.#####"
    private const val SMALL_RATE_THRESHOLD = 1e-3
    private const val PLAIN_DECIMAL_MIN_RATE = 1e-12
    private const val SMALL_RATE_SIGNIFICANT_DIGITS = 4
    private val smallRateMathContext = MathContext(SMALL_RATE_SIGNIFICANT_DIGITS, RoundingMode.HALF_UP)
    private val rateFormatter: ThreadLocal<DecimalFormat> = ThreadLocal.withInitial {
        DecimalFormat(RATE_PATTERN, DecimalFormatSymbols(Locale.US))
    }

    fun parsePositiveRate(text: String): Double? {
        return text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
    }

    fun computeRate(
        fromMinor: Long,
        fromDecimals: Int,
        toMinor: Long,
        toDecimals: Int,
    ): Double {
        if (fromMinor <= 0L || toMinor <= 0L) return 0.0
        val majorFrom = abs(fromMinor).toDouble() / AmountScale.pow10(fromDecimals)
        val majorTo = abs(toMinor).toDouble() / AmountScale.pow10(toDecimals)
        return if (majorFrom > 0.0) majorTo / majorFrom else 0.0
    }

    fun computeTargetMinor(
        fromMinor: Long,
        fromDecimals: Int,
        toDecimals: Int,
        rate: Double,
    ): Long {
        if (fromMinor <= 0L || rate <= 0.0 || !rate.isFinite()) return 0L
        val majorFrom = abs(fromMinor).toDouble() / AmountScale.pow10(fromDecimals)
        val scaled = majorFrom * rate * AmountScale.pow10(toDecimals)
        return if (scaled.isFinite()) scaled.roundToLong().coerceAtLeast(0L) else 0L
    }

    fun formatRate(rate: Double): String {
        return when {
            rate <= 0.0 || !rate.isFinite() -> ""
            rate >= SMALL_RATE_THRESHOLD -> rateFormatter.get()?.format(rate).orEmpty()
            else -> formatSmallPositiveRate(rate)
        }
    }

    fun formatReciprocalPair(
        fromCode: String,
        toCode: String,
        rate: Double,
    ): Pair<String, String>? {
        val hasValidRate = rate > 0.0 && rate.isFinite()
        val hasCodes = fromCode.isNotBlank() && toCode.isNotBlank()
        val inverseRate = if (hasValidRate) 1.0 / rate else Double.NaN
        val hasValidInverse = inverseRate.isFinite() && inverseRate > 0.0
        if (!hasValidRate || !hasCodes || !hasValidInverse) {
            return null
        }
        val forward = formatRate(rate)
        val inverse = formatRate(inverseRate)
        return if (forward.isNotEmpty() && inverse.isNotEmpty()) {
            "1 $fromCode ≈ $forward $toCode" to "1 $toCode ≈ $inverse $fromCode"
        } else {
            null
        }
    }

    private fun formatSmallPositiveRate(rate: Double): String {
        val rounded = BigDecimal.valueOf(rate).round(smallRateMathContext).stripTrailingZeros()
        return if (rate >= PLAIN_DECIMAL_MIN_RATE) rounded.toPlainString() else rounded.toString()
    }
}
