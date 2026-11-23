// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

/**
 * Pure Kotlin recursive-descent arithmetic evaluator supporting +, -, *, /, unary signs, and parentheses.
 */
object CalculatorExpressionEvaluator {

    fun evaluate(expression: String): Double? {
        val sanitized = sanitize(expression)
        if (sanitized.isEmpty()) return null
        val parser = Parser(sanitized)
        val result = parser.parseExpression()
        return result?.takeIf { parser.isAtEnd() && it.isFinite() }
    }

    private fun sanitize(expression: String): String {
        return buildString(expression.length) {
            for (ch in expression) {
                when (ch) {
                    ' ', '\t', '\n', '\r' -> Unit
                    ',' -> append('.')
                    '×' -> append('*')
                    '÷' -> append('/')
                    '−' -> append('-')
                    else -> append(ch)
                }
            }
        }
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= text.length

        fun parseExpression(): Double? {
            var left = parseTerm()
            while (left != null && pos < text.length) {
                val op = text[pos]
                if (op != '+' && op != '-') break
                pos++
                val right = parseTerm()
                left = when {
                    right == null -> null
                    op == '+' -> left + right
                    else -> left - right
                }
            }
            return left
        }

        private fun parseTerm(): Double? {
            var left = parseFactor()
            while (left != null && pos < text.length) {
                val op = text[pos]
                if (op != '*' && op != '/') break
                pos++
                val right = parseFactor()
                left = when {
                    right == null -> null
                    op == '/' && right == 0.0 -> null
                    op == '*' -> left * right
                    else -> left / right
                }
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
                '(' -> parseParenthesized()
                else -> parseNumber()
            }
        }

        private fun parseParenthesized(): Double? {
            pos++
            val inner = parseExpression()
            val valid = inner != null && pos < text.length && text[pos] == ')'
            if (valid) pos++
            return if (valid) inner else null
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
            return if (pos == start) null else text.substring(start, pos).toDoubleOrNull()
        }
    }
}
