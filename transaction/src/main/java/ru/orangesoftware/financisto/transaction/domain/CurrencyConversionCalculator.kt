// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyConversionCalculator {
    private const val RATE_PATTERN = "0.#####"
    private const val MIN_POSITIVE_RATE = 1e-9

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
        if (fromMinor <= 0L || rate <= MIN_POSITIVE_RATE || !rate.isFinite()) return 0L
        val majorFrom = abs(fromMinor).toDouble() / AmountScale.pow10(fromDecimals)
        val targetMajor = majorFrom * rate
        return (targetMajor * AmountScale.pow10(toDecimals)).roundToLong().coerceAtLeast(0L)
    }

    fun formatRate(rate: Double): String {
        if (rate <= MIN_POSITIVE_RATE || !rate.isFinite()) return ""
        return DecimalFormat(RATE_PATTERN, DecimalFormatSymbols(Locale.US)).format(rate)
    }

    fun formatReciprocalPair(
        fromCode: String,
        toCode: String,
        rate: Double,
    ): Pair<String, String>? {
        if (rate <= MIN_POSITIVE_RATE || !rate.isFinite() || fromCode.isBlank() || toCode.isBlank()) {
            return null
        }
        val forward = formatRate(rate)
        val inverse = formatRate(1.0 / rate)
        return "1 $fromCode ≈ $forward $toCode" to "1 $toCode ≈ $inverse $fromCode"
    }
}
