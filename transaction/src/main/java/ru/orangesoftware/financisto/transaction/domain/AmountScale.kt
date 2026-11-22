// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import kotlin.math.abs

object AmountScale {
    private const val DECIMAL_BASE = 10L
    private const val GROUP_SIZE = 3
    private const val MAX_SUPPORTED_DECIMALS = 6
    private const val MAX_MINOR_MAGNITUDE = 100_000_000_000_000L
    private const val ROUND_HALF_DIGIT = '5'
    private val POW10_TABLE = longArrayOf(
        1L,
        10L,
        100L,
        1_000L,
        10_000L,
        100_000L,
        1_000_000L,
    )

    fun pow10(decimals: Int): Long = POW10_TABLE[decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS)]

    fun parseMinorUnits(rawInput: String, decimals: Int): Long? {
        val unsigned = normalizeUnsigned(rawInput)
        return when {
            unsigned == null -> null
            unsigned.isEmpty() -> 0L
            else -> parseUnsignedDecimal(unsigned, decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS))
        }
    }

    fun formatEditableAmount(minorUnits: Long, decimals: Int): String {
        val scale = decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS)
        val magnitude = safeAbs(minorUnits)
        if (scale == 0) return magnitude.toString()
        val factor = POW10_TABLE[scale]
        val whole = magnitude / factor
        val fraction = magnitude % factor
        return buildString {
            append(whole)
            append('.')
            appendPaddedFraction(fraction, scale)
        }
    }

    fun formatDisplayAmount(
        minorUnits: Long,
        decimals: Int,
        symbol: String = "",
        includeSign: Boolean = false,
    ): String {
        val scale = decimals.coerceIn(0, MAX_SUPPORTED_DECIMALS)
        val magnitude = safeAbs(minorUnits)
        val factor = POW10_TABLE[scale]
        val whole = magnitude / factor
        val fraction = magnitude % factor
        return buildString {
            if (includeSign && minorUnits != 0L) {
                append(if (minorUnits < 0L) '-' else '+')
            }
            if (symbol.isNotBlank()) {
                append(symbol)
                append(' ')
            }
            appendGroupedWhole(whole)
            if (scale > 0) {
                append('.')
                appendPaddedFraction(fraction, scale)
            }
        }
    }

    private fun normalizeUnsigned(rawInput: String): String? {
        val stripped = buildString(rawInput.length) {
            for (ch in rawInput) {
                when (ch) {
                    ' ', '\t', '\n', '\r' -> Unit
                    ',' -> append('.')
                    else -> append(ch)
                }
            }
        }
        return when {
            stripped.isEmpty() -> ""
            stripped[0] == '+' || stripped[0] == '-' -> stripped.substring(1).ifEmpty { null }
            else -> stripped
        }
    }

    private fun parseUnsignedDecimal(unsigned: String, scale: Int): Long? {
        val dotIndex = unsigned.indexOf('.')
        val secondDot = if (dotIndex >= 0) unsigned.indexOf('.', dotIndex + 1) else -1
        if (secondDot >= 0) return null
        val intPart = if (dotIndex >= 0) unsigned.substring(0, dotIndex) else unsigned
        val fracPart = if (dotIndex >= 0) unsigned.substring(dotIndex + 1) else ""
        val hasDigits = intPart.isNotEmpty() || fracPart.isNotEmpty()
        val allDigits = hasDigits && intPart.all { it in '0'..'9' } && fracPart.all { it in '0'..'9' }
        return if (allDigits) accumulateMinorUnits(intPart, fracPart, scale) else null
    }

    private fun accumulateMinorUnits(intPart: String, fracPart: String, scale: Int): Long? {
        var minor = 0L
        var overflow = false
        for (ch in intPart) {
            val digit = ch - '0'
            if (minor > (MAX_MINOR_MAGNITUDE - digit) / DECIMAL_BASE) {
                overflow = true
                break
            }
            minor = minor * DECIMAL_BASE + digit
        }
        if (!overflow) {
            for (i in 0 until scale) {
                val digit = if (i < fracPart.length) fracPart[i] - '0' else 0
                if (minor > (MAX_MINOR_MAGNITUDE - digit) / DECIMAL_BASE) {
                    overflow = true
                    break
                }
                minor = minor * DECIMAL_BASE + digit
            }
        }
        val roundUp = !overflow && fracPart.length > scale && fracPart[scale] >= ROUND_HALF_DIGIT
        if (roundUp) {
            if (minor >= MAX_MINOR_MAGNITUDE) {
                overflow = true
            } else {
                minor += 1L
            }
        }
        return if (overflow) null else minor
    }

    private fun safeAbs(minorUnits: Long): Long = if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else abs(minorUnits)

    private fun StringBuilder.appendPaddedFraction(fraction: Long, scale: Int) {
        val fracText = fraction.toString()
        repeat(scale - fracText.length) { append('0') }
        append(fracText)
    }

    private fun StringBuilder.appendGroupedWhole(whole: Long) {
        val digits = whole.toString()
        val len = digits.length
        for (i in 0 until len) {
            if (i > 0 && (len - i) % GROUP_SIZE == 0) {
                append(',')
            }
            append(digits[i])
        }
    }
}
