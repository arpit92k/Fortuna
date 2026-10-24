package io.github.arpit92k.fortuna.core.money

/**
 * An exact amount in one currency, held as a whole number of the currency's
 * smallest unit: pence for pounds, cents for dollars, yen for yen.
 *
 * Amounts in different currencies are never combined: adding or subtracting
 * them throws [CurrencyMismatchException]. Converting between currencies is
 * the job of exchange rates, which come later.
 */
data class Money(
    /** The amount in the currency's smallest unit, so 1,000,000 for £10,000.00. */
    val minorUnits: Long,
    val currency: Currency,
) {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(addExact(minorUnits, other.minorUnits), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(subtractExact(minorUnits, other.minorUnits), currency)
    }

    operator fun unaryMinus(): Money = Money(negateExact(minorUnits), currency)

    /**
     * The amount as decimal text with exactly the currency's number of decimal
     * places and a point as the separator: "10000.00", "-0.50", or "1500" for yen.
     * This is the form [parse] reads, not a display format.
     */
    fun toDecimalText(): String {
        val places = currency.decimalPlaces
        val digits = minorUnits.toString().removePrefix("-").padStart(places + 1, '0')
        val sign = if (minorUnits < 0) "-" else ""
        if (places == 0) return sign + digits
        val point = digits.length - places
        return sign + digits.substring(0, point) + "." + digits.substring(point)
    }

    override fun toString(): String = "${toDecimalText()} ${currency.code}"

    private fun requireSameCurrency(other: Money) {
        if (other.currency != currency) throw CurrencyMismatchException(currency, other.currency)
    }

    companion object {
        /**
         * Reads an amount in [currency] from decimal text: an optional minus sign,
         * one or more digits, and optionally a point followed by one or more digits.
         *
         * Fewer decimal places than the currency has are filled with zeros, so
         * "10.5" in pounds is 1,050 pence. More decimal places than it has are
         * rejected rather than rounded. Grouping separators, a plus sign, spaces
         * and exponents are not accepted.
         */
        fun parse(text: String, currency: Currency): MoneyParseResult {
            if (text.isEmpty()) return MoneyParseResult.Failure(AmountProblem.EMPTY)

            val negative = text.startsWith('-')
            val unsigned = if (negative) text.substring(1) else text
            val pointAt = unsigned.indexOf('.')
            val whole = if (pointAt < 0) unsigned else unsigned.substring(0, pointAt)
            val fraction = if (pointAt < 0) "" else unsigned.substring(pointAt + 1)

            val wellFormed = whole.isNotEmpty() && whole.all(::isAsciiDigit) &&
                (pointAt < 0 || (fraction.isNotEmpty() && fraction.all(::isAsciiDigit)))
            if (!wellFormed) return MoneyParseResult.Failure(AmountProblem.NOT_A_NUMBER)

            if (fraction.length > currency.decimalPlaces) {
                return MoneyParseResult.Failure(AmountProblem.TOO_MANY_DECIMAL_PLACES)
            }

            var value = 0L
            for (digit in whole + fraction.padEnd(currency.decimalPlaces, '0')) {
                val d = digit - '0'
                if (value > (Long.MAX_VALUE - d) / 10) {
                    return MoneyParseResult.Failure(AmountProblem.OUT_OF_RANGE)
                }
                value = value * 10 + d
            }
            return MoneyParseResult.Success(Money(if (negative) -value else value, currency))
        }

        private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'
    }
}

/** The outcome of [Money.parse]. */
sealed interface MoneyParseResult {
    data class Success(val money: Money) : MoneyParseResult

    data class Failure(val problem: AmountProblem) : MoneyParseResult
}

/** Why a piece of text is not a valid amount. */
enum class AmountProblem {
    /** Nothing was entered. */
    EMPTY,

    /** The text is not a plain decimal number. */
    NOT_A_NUMBER,

    /** The text has more digits after the point than the currency allows. */
    TOO_MANY_DECIMAL_PLACES,

    /** The amount is too large to hold exactly. */
    OUT_OF_RANGE,
}

/** Thrown when two amounts in different currencies are added or subtracted. */
class CurrencyMismatchException(
    val first: Currency,
    val second: Currency,
) : IllegalArgumentException("Cannot combine an amount in $first with one in $second")

private fun addExact(a: Long, b: Long): Long {
    val result = a + b
    if ((a xor result) and (b xor result) < 0) throw ArithmeticException("Amount out of range")
    return result
}

private fun subtractExact(a: Long, b: Long): Long {
    val result = a - b
    if ((a xor b) and (a xor result) < 0) throw ArithmeticException("Amount out of range")
    return result
}

private fun negateExact(a: Long): Long {
    if (a == Long.MIN_VALUE) throw ArithmeticException("Amount out of range")
    return -a
}
