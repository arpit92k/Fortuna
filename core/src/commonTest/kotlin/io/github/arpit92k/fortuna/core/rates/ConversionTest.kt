package io.github.arpit92k.fortuna.core.rates

import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConversionTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val euros = Currency.forCode("EUR")!!
    private val yen = Currency.forCode("JPY")!!
    private val dinars = Currency.forCode("BHD")!!

    private fun rate(text: String, unit: Currency, quote: Currency): Rate =
        assertIs<RateParseResult.Success>(Rate.parse(text, unit, quote), "parsing \"$text\"").rate

    // From the architecture's worked example.
    @Test
    fun theWorkedExampleConvertsExactly() {
        assertEquals(Money(800_000, pounds), convert(Money(1_000_000, dollars), rate("0.80", dollars, pounds)))
        assertEquals(Money(862_500, pounds), convert(Money(1_150_000, dollars), rate("0.75", dollars, pounds)))
    }

    @Test
    fun aRateConvertsInBothDirections() {
        val rate = rate("0.75", dollars, pounds)
        assertEquals(Money(7_500, pounds), convert(Money(10_000, dollars), rate))
        assertEquals(Money(10_000, dollars), convert(Money(7_500, pounds), rate))
    }

    @Test
    fun aRateTypedTheOtherWayRoundGivesTheSameResult() {
        val typedFromDollars = rate("0.8", dollars, pounds)
        val typedFromPounds = rate("1.25", pounds, dollars)
        val hundredDollars = Money(10_000, dollars)
        assertEquals(Money(8_000, pounds), convert(hundredDollars, typedFromDollars))
        assertEquals(Money(8_000, pounds), convert(hundredDollars, typedFromPounds))
    }

    @Test
    fun anInverseThatNeverEndsIsRoundedOnlyAtTheEnd() {
        // 100 / 1.33 = 75.1879..., so £75.19.
        assertEquals(Money(7_519, pounds), convert(Money(10_000, dollars), rate("1.33", pounds, dollars)))
    }

    // From the plan: results that land exactly half-way, and negative amounts.
    @Test
    fun halvesRoundAwayFromZero() {
        val half = rate("0.5", dollars, pounds)
        assertEquals(Money(3, pounds), convert(Money(5, dollars), half), "2.5p rounds up")
        assertEquals(Money(-3, pounds), convert(Money(-5, dollars), half), "-2.5p rounds down")

        val two = rate("2", dollars, pounds)
        assertEquals(Money(1, dollars), convert(Money(1, pounds), two), "half a cent rounds up")
        assertEquals(Money(-1, dollars), convert(Money(-1, pounds), two), "minus half a cent rounds down")

        assertEquals(Money(2, pounds), convert(Money(4, dollars), half), "an exact result is not rounded")
        assertEquals(Money(1, pounds), convert(Money(3, dollars), rate("0.4", dollars, pounds)), "1.2p rounds down")
        assertEquals(Money(2, pounds), convert(Money(4, dollars), rate("0.4", dollars, pounds)), "1.6p rounds up")
    }

    @Test
    fun convertingMinusAnAmountGivesMinusTheConvertedAmount() {
        val rates = listOf(rate("0.75", dollars, pounds), rate("1.33", pounds, dollars), rate("0.0052", yen, pounds))
        for (r in rates) {
            for (units in listOf(1L, 5L, 7L, 99L, 12_345L, 987_654_321L)) {
                val amount = Money(units, r.unit)
                assertEquals(-convert(amount, r), convert(-amount, r), "$amount with $r")
            }
        }
    }

    // From the plan: currencies with different numbers of decimal places.
    @Test
    fun currenciesWithDifferentDecimalPlaces() {
        assertEquals(Money(780, pounds), convert(Money(1_500, yen), rate("0.0052", yen, pounds)), "¥1,500 is £7.80")
        assertEquals(Money(4_770, dinars), convert(Money(1_000, pounds), rate("0.477", pounds, dinars)), "£10 is 4.770 BHD")

        val poundsInYen = rate("191.5", pounds, yen)
        assertEquals(Money(1_915, yen), convert(Money(1_000, pounds), poundsInYen), "£10 is ¥1,915")
        assertEquals(Money(1_000, pounds), convert(Money(1_915, yen), poundsInYen), "¥1,915 is £10")
    }

    @Test
    fun zeroConvertsToZero() {
        assertEquals(Money(0, pounds), convert(Money(0, dollars), rate("0.75", dollars, pounds)))
    }

    @Test
    fun theLargestAmountsConvertExactly() {
        // Long.MAX_VALUE yen at 10^-12 dinars per yen is 9,223,372.036854775807 dinars,
        // which is 9,223,372,036.85 fils, rounded to 9,223,372,037.
        val tiny = rate("0.000000000001", yen, dinars)
        assertEquals(Money(9_223_372_037, dinars), convert(Money(Long.MAX_VALUE, yen), tiny))
    }

    @Test
    fun aResultTooLargeToHoldIsRejected() {
        assertFailsWith<ArithmeticException> { convert(Money(Long.MAX_VALUE, dollars), rate("2", dollars, pounds)) }
        assertFailsWith<ArithmeticException> {
            convert(Money(Long.MAX_VALUE, pounds), rate("0.000000000001", dollars, pounds))
        }
    }

    @Test
    fun anAmountInAThirdCurrencyIsRejected() {
        val rate = rate("0.75", dollars, pounds)
        val failure = assertFailsWith<WrongRateException> { convert(Money(100, euros), rate) }
        assertEquals(euros, failure.amount.currency)
    }
}
