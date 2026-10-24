package io.github.arpit92k.fortuna.core.rates

// Exact arithmetic on non-negative whole numbers written as decimal digits,
// for the one conversion function. Numbers here can be larger than a Long
// (an amount of up to 19 digits times a rate of up to 15), so they are kept
// as digit arrays, most significant digit first.
//
// The divisor always fits in a ULong below 10^18: a rate has at most 15
// digits and the decimal places of two currencies differ by at most 3. That
// keeps every step of the long division below 10^19, within a ULong.

/** The product of two numbers given as decimal digit text. */
internal fun multiplyDigits(a: String, b: String): IntArray {
    val result = IntArray(a.length + b.length)
    for (i in a.indices.reversed()) {
        val x = a[i] - '0'
        for (j in b.indices.reversed()) {
            val sum = x * (b[j] - '0') + result[i + j + 1]
            result[i + j] += sum / 10
            result[i + j + 1] = sum % 10
        }
    }
    return result
}

/**
 * [dividend] divided by [divisor], rounded to the nearest whole number, with
 * exact halves rounded up. The caller applies the sign, which makes the
 * overall rounding half away from zero.
 *
 * @throws ArithmeticException if the result does not fit in a Long.
 */
internal fun divideRoundingHalfUp(dividend: IntArray, divisor: ULong): Long {
    require(divisor > 0uL && divisor < MAX_DIVISOR) { "Divisor out of range: $divisor" }

    var quotient = 0L
    var remainder = 0uL
    for (digit in dividend) {
        remainder = remainder * 10uL + digit.toULong()
        val next = (remainder / divisor).toLong()
        remainder %= divisor
        if (quotient > (Long.MAX_VALUE - next) / 10) throw ArithmeticException("Converted amount out of range")
        quotient = quotient * 10 + next
    }
    if (remainder * 2uL >= divisor) {
        if (quotient == Long.MAX_VALUE) throw ArithmeticException("Converted amount out of range")
        quotient += 1
    }
    return quotient
}

private const val MAX_DIVISOR: ULong = 1_000_000_000_000_000_000uL
