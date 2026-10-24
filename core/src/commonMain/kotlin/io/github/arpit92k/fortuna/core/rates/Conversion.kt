package io.github.arpit92k.fortuna.core.rates

import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money

/**
 * Converts [amount] into the other currency of [rate], whichever way round the
 * rate was typed, and rounds once to that currency's smallest unit.
 *
 * With "1 USD = 0.75 GBP", dollars are multiplied by 0.75 to give pounds and
 * pounds are divided by 0.75 to give dollars. The arithmetic is exact, and the
 * only rounding is the last step: half away from zero, so converting minus an
 * amount always gives minus the converted amount and transfers still cancel.
 *
 * This is the one place in the app where an amount changes currency or is rounded.
 *
 * @throws WrongRateException if [amount] is in neither of the rate's currencies.
 * @throws ArithmeticException if the result is too large to hold exactly.
 */
fun convert(amount: Money, rate: Rate): Money {
    val from = amount.currency
    val to: Currency
    // The result is amount * numeratorFactor / denominator, in the target's smallest unit.
    val numeratorFactor: String
    val denominator: ULong

    when (from) {
        rate.unit -> {
            // Multiply by the rate: units * value * 10^(quote places - unit places).
            to = rate.quote
            val exponent = to.decimalPlaces - from.decimalPlaces - rate.scale
            numeratorFactor = rate.unscaled.toString() + "0".repeat(maxOf(exponent, 0))
            denominator = powerOfTen(maxOf(-exponent, 0))
        }
        rate.quote -> {
            // Divide by the rate: units / value * 10^(unit places - quote places).
            to = rate.unit
            val exponent = rate.scale + to.decimalPlaces - from.decimalPlaces
            numeratorFactor = "1" + "0".repeat(maxOf(exponent, 0))
            denominator = rate.unscaled.toULong() * powerOfTen(maxOf(-exponent, 0))
        }
        else -> throw WrongRateException(amount, rate)
    }

    val magnitude = amount.minorUnits.toString().removePrefix("-")
    val rounded = divideRoundingHalfUp(multiplyDigits(magnitude, numeratorFactor), denominator)
    return Money(if (amount.minorUnits < 0) -rounded else rounded, to)
}

/** Thrown when an amount is converted with a rate between two other currencies. */
class WrongRateException(
    val amount: Money,
    val rate: Rate,
) : IllegalArgumentException("The rate $rate cannot convert an amount in ${amount.currency.code}")

private fun powerOfTen(exponent: Int): ULong {
    var result = 1uL
    repeat(exponent) { result *= 10uL }
    return result
}
