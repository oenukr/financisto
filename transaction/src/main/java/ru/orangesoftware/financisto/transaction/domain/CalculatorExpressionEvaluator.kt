// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

/**
 * Pure Kotlin recursive-descent arithmetic evaluator supporting +, -, *, /, unary signs, and parentheses.
 */
object CalculatorExpressionEvaluator {
    private const val DIV_ZERO_EPSILON = 1e-12

    fun evaluate(expression: String): Double? {
        val sanitized = expression.replace(',', '.').replace(" ", "")
        if (sanitized.isEmpty()) return null
        val parser = Parser(sanitized)
        val result = parser.parseExpression() ?: return null
        return if (parser.isAtEnd() && result.isFinite()) result else null
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= text.length

        fun parseExpression(): Double? {
            var left = parseTerm() ?: return null
            while (pos < text.length) {
                val op = text[pos]
                if (op != '+' && op != '-') break
                pos++
                val right = parseTerm() ?: return null
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        private fun parseTerm(): Double? {
            var left = parseFactor() ?: return null
            while (pos < text.length) {
                val op = text[pos]
                if (op != '*' && op != '/') break
                pos++
                val right = parseFactor() ?: return null
                if (op == '/' && kotlin.math.abs(right) < DIV_ZERO_EPSILON) return null
                left = if (op == '*') left * right else left / right
            }
            return left
        }

        private fun parseFactor(): Double? {
            if (pos >= text.length) return null
            return when (text[pos]) {
                '+' -> {
                    pos++
                    parseFactor()
                }
                '-' -> {
                    pos++
                    parseFactor()?.let { -it }
                }
                '(' -> {
                    pos++
                    val inner = parseExpression() ?: return null
                    if (pos >= text.length || text[pos] != ')') return null
                    pos++
                    inner
                }
                else -> parseNumber()
            }
        }

        private fun parseNumber(): Double? {
            val start = pos
            var dotSeen = false
            while (pos < text.length) {
                val ch = text[pos]
                if (ch.isDigit()) {
                    pos++
                } else if (ch == '.' && !dotSeen) {
                    dotSeen = true
                    pos++
                } else {
                    break
                }
            }
            if (pos == start) return null
            return text.substring(start, pos).toDoubleOrNull()
        }
    }
}
