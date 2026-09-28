package com.bizzeh.bruce.skills.automatic

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed interface Calculation {
    data class Value(val value: BigDecimal) : Calculation

    /** [reason] is for the model; it never repeats the expression. */
    data class Error(val reason: String) : Calculation
}

/**
 * Exact arithmetic for the calculator skill: a recursive-descent parser over [BigDecimal], never
 * code evaluation. Grammar, loosest first:
 * expression = term (("+" | "-") term)*; term = power (("*" | "/") power)*;
 * power = unary ("^" power)?; unary = ("-" | "+") unary | postfix; postfix = primary "%"*;
 * primary = number | "(" expression ")". `×` and `÷` are accepted, and `,` as a thousands separator.
 * Input size, nesting and exponents are bounded, so no expression can exhaust the phone.
 */
object Calculator {
    const val MAX_LENGTH = 200
    private const val MAX_DEPTH = 32
    private const val MAX_EXPONENT = 1000
    private const val MAX_DIGITS = 1000
    private val CONTEXT = MathContext(34, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)

    fun evaluate(expression: String): Calculation {
        if (expression.length > MAX_LENGTH) return Calculation.Error("expression longer than $MAX_LENGTH characters")
        // Spaces separate tokens but never join them: "1 2" is refused, not read as 12.
        val text = expression.replace('×', '*').replace('÷', '/').replace(",", "")
        if (text.isBlank()) return Calculation.Error("empty expression")
        return try {
            val parser = Parser(text)
            val value = parser.expression(0)
            if (!parser.atEnd()) throw CalculationException("unexpected '${text[parser.position]}'")
            Calculation.Value(value.stripTrailingZeros())
        } catch (e: CalculationException) {
            Calculation.Error(e.message ?: "invalid expression")
        } catch (e: ArithmeticException) {
            Calculation.Error("the result cannot be represented")
        }
    }

    /** Plain notation without exponent, trailing zeros removed. */
    fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private class CalculationException(message: String) : Exception(message)

    private class Parser(private val text: String) {
        var position = 0

        fun atEnd(): Boolean {
            skipSpaces()
            return position >= text.length
        }

        private fun skipSpaces() {
            while (text.getOrNull(position)?.isWhitespace() == true) position++
        }

        private fun peek(): Char? {
            skipSpaces()
            return text.getOrNull(position)
        }

        fun expression(depth: Int): BigDecimal {
            if (depth > MAX_DEPTH) throw CalculationException("too deeply nested")
            var value = term(depth)
            while (true) {
                value = when (peek()) {
                    '+' -> { position++; value.add(term(depth), CONTEXT) }
                    '-' -> { position++; value.subtract(term(depth), CONTEXT) }
                    else -> return bounded(value)
                }
            }
        }

        private fun term(depth: Int): BigDecimal {
            var value = power(depth)
            while (true) {
                value = when (peek()) {
                    '*' -> { position++; value.multiply(power(depth), CONTEXT) }
                    '/' -> {
                        position++
                        val divisor = power(depth)
                        if (divisor.signum() == 0) throw CalculationException("division by zero")
                        value.divide(divisor, CONTEXT)
                    }
                    else -> return bounded(value)
                }
            }
        }

        private fun power(depth: Int): BigDecimal {
            val base = unary(depth)
            if (peek() != '^') return base
            position++
            val exponent = power(depth + 1)
            val whole = try {
                exponent.intValueExact()
            } catch (e: ArithmeticException) {
                throw CalculationException("exponents must be whole numbers")
            }
            if (kotlin.math.abs(whole) > MAX_EXPONENT) throw CalculationException("exponent larger than $MAX_EXPONENT")
            if (whole < 0 && base.signum() == 0) throw CalculationException("division by zero")
            return bounded(base.pow(whole, CONTEXT))
        }

        private fun unary(depth: Int): BigDecimal {
            if (depth > MAX_DEPTH) throw CalculationException("too deeply nested")
            return when (peek()) {
                '-' -> { position++; unary(depth + 1).negate() }
                '+' -> { position++; unary(depth + 1) }
                else -> postfix(depth)
            }
        }

        private fun postfix(depth: Int): BigDecimal {
            var value = primary(depth)
            while (peek() == '%') {
                position++
                value = value.divide(HUNDRED, CONTEXT)
            }
            return value
        }

        private fun primary(depth: Int): BigDecimal {
            val c = peek() ?: throw CalculationException("expression ends too soon")
            if (c == '(') {
                position++
                val value = expression(depth + 1)
                if (peek() != ')') throw CalculationException("missing ')'")
                position++
                return value
            }
            if (c.isDigit() || c == '.') return number()
            throw CalculationException("unexpected '$c'")
        }

        private fun number(): BigDecimal {
            val start = position
            while (text.getOrNull(position)?.let { it.isDigit() || it == '.' } == true) position++
            val digits = text.substring(start, position)
            if (digits.count { it == '.' } > 1 || digits == ".") throw CalculationException("malformed number")
            return BigDecimal(digits)
        }

        /** Keeps intermediate values to a size the phone can print and the model can read. */
        private fun bounded(value: BigDecimal): BigDecimal {
            if (value.precision() - value.scale() > MAX_DIGITS) throw CalculationException("result too large")
            return value
        }
    }
}
