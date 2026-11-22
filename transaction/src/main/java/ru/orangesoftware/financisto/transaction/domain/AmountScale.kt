// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object AmountScale {
    private const val DECIMAL_BASE = 10L
    private const val MAX_SUPPORTED_DECIMALS = 6
    private const val MAX_MINOR_MAGNITUDE = 100_000_000_000_000L

    fun pow10(decimals: Int): Long {
        val clamped = decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS)
        var result = 1L
        repeat(clamped) { result *= DECIMAL_BASE }
        return result
    }

    fun parseMinorUnits(rawInput: String, decimals: Int): Long? {
        val normalized = rawInput.trim().replace(',', '.').replace(" ", "")
        if (normalized.isEmpty()) return 0L
        val parsed = normalized.toBigDecimalOrNull() ?: return null
        val scale = decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS)
        val scaled = parsed.abs().movePointRight(scale).setScale(0, RoundingMode.HALF_UP)
        val longVal = runCatching { scaled.longValueExact() }.getOrNull() ?: return null
        return if (longVal > MAX_MINOR_MAGNITUDE) null else longVal
    }

    fun formatEditableAmount(minorUnits: Long, decimals: Int): String {
        val scale = decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS)
        val value = BigDecimal.valueOf(abs(minorUnits)).movePointLeft(scale)
        return value.toPlainString()
    }

    fun formatDisplayAmount(
        minorUnits: Long,
        decimals: Int,
        symbol: String = "",
        includeSign: Boolean = false,
    ): String {
        val scale = decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS)
        val pattern = if (scale == 0) "#,##0" else "#,##0.${"0".repeat(scale)}"
        val symbols = DecimalFormatSymbols(Locale.US)
        val formattedNumber = DecimalFormat(pattern, symbols)
            .format(BigDecimal.valueOf(abs(minorUnits)).movePointLeft(scale))
        val signPrefix = when {
            !includeSign || minorUnits == 0L -> ""
            minorUnits < 0L -> "-"
            else -> "+"
        }
        return if (symbol.isBlank()) {
            "$signPrefix$formattedNumber"
        } else {
            "$signPrefix$symbol $formattedNumber"
        }
    }
}
