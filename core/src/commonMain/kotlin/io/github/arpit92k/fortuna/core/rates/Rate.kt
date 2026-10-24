package io.github.arpit92k.fortuna.core.rates

import io.github.arpit92k.fortuna.core.money.Currency

/**
 * An exchange rate exactly as the user typed it: one unit of [unit] is worth
 * the rate's value in [quote].
 *
 * "1 USD = 0.75 GBP" has USD as the unit and GBP as the quote. The same
 * relationship typed the other way round, "1 GBP = 1.33 USD", is a different
 * rate with the currencies swapped. Either one converts in both directions
 * through [convert], so a rate never has to be inverted, and rounded, to be stored.
 */
class Rate private constructor(
    /** The currency of which one unit is priced, such as USD in "1 USD = 0.75 GBP". */
    val unit: Currency,
    /** The currency the price is given in, such as GBP in "1 USD = 0.75 GBP". */
    val quote: Currency,
    /** The digits of the value with the point removed: 75 for "0.75". */
    internal val unscaled: Long,
    /** How many of those digits follow the point: 2 for "0.75". */
    internal val scale: Int,
) {
    /**
     * The value as decimal text with the point as the separator, keeping the
     * decimal places that were typed: "0.75", "0.750", "1.33", "191.5".
     */
    fun toDecimalText(): String {
        val digits = unscaled.toString().padStart(scale + 1, '0')
        if (scale == 0) return digits
        val point = digits.length - scale
        return digits.substring(0, point) + "." + digits.substring(point)
    }

    override fun equals(other: Any?): Boolean =
        other is Rate && other.unit == unit && other.quote == quote &&
            other.unscaled == unscaled && other.scale == scale

    override fun hashCode(): Int = ((unit.hashCode() * 31 + quote.hashCode()) * 31 + unscaled.hashCode()) * 31 + scale

    override fun toString(): String = "1 ${unit.code} = ${toDecimalText()} ${quote.code}"

    companion object {
        /** The most digits a rate may have after the point. */
        const val MAX_DECIMAL_PLACES: Int = 12

        /** The most digits a rate may have in total, not counting leading zeros. */
        const val MAX_DIGITS: Int = 15

        /**
         * Reads a rate from decimal text: one or more digits, optionally a
         * point followed by one or more digits. The value must be above zero.
         * Grouping separators, signs, spaces and exponents are not accepted.
         *
         * @throws IllegalArgumentException if [unit] and [quote] are the same
         *   currency. A currency needs no rate against itself.
         */
        fun parse(text: String, unit: Currency, quote: Currency): RateParseResult {
            require(unit != quote) { "A rate needs two different currencies, not $unit and $quote" }
            if (text.isEmpty()) return RateParseResult.Failure(RateProblem.EMPTY)

            val negative = text.startsWith('-')
            val unsigned = if (negative) text.substring(1) else text
            val pointAt = unsigned.indexOf('.')
            val whole = if (pointAt < 0) unsigned else unsigned.substring(0, pointAt)
            val fraction = if (pointAt < 0) "" else unsigned.substring(pointAt + 1)

            val wellFormed = whole.isNotEmpty() && whole.all(::isAsciiDigit) &&
                (pointAt < 0 || (fraction.isNotEmpty() && fraction.all(::isAsciiDigit)))
            if (!wellFormed) return RateParseResult.Failure(RateProblem.NOT_A_NUMBER)

            val digits = (whole + fraction).trimStart('0')
            if (negative || digits.isEmpty()) return RateParseResult.Failure(RateProblem.NOT_POSITIVE)
            if (fraction.length > MAX_DECIMAL_PLACES) {
                return RateParseResult.Failure(RateProblem.TOO_MANY_DECIMAL_PLACES)
            }
            if (digits.length > MAX_DIGITS) return RateParseResult.Failure(RateProblem.TOO_MANY_DIGITS)

            return RateParseResult.Success(Rate(unit, quote, digits.toLong(), fraction.length))
        }

        private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'
    }
}

/** The outcome of [Rate.parse]. */
sealed interface RateParseResult {
    data class Success(val rate: Rate) : RateParseResult

    data class Failure(val problem: RateProblem) : RateParseResult
}

/** Why a piece of text is not a valid rate. */
enum class RateProblem {
    /** Nothing was entered. */
    EMPTY,

    /** The text is not a plain decimal number. */
    NOT_A_NUMBER,

    /** The rate is zero or negative. */
    NOT_POSITIVE,

    /** The rate has more than [Rate.MAX_DECIMAL_PLACES] digits after the point. */
    TOO_MANY_DECIMAL_PLACES,

    /** The rate has more than [Rate.MAX_DIGITS] digits in total. */
    TOO_MANY_DIGITS,
}
