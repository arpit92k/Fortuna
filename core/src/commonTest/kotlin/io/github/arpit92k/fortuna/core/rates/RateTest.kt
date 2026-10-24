package io.github.arpit92k.fortuna.core.rates

import io.github.arpit92k.fortuna.core.money.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class RateTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!

    private fun parsed(text: String): Rate =
        assertIs<RateParseResult.Success>(Rate.parse(text, dollars, pounds), "parsing \"$text\"").rate

    private fun problem(text: String): RateProblem =
        assertIs<RateParseResult.Failure>(Rate.parse(text, dollars, pounds), "parsing \"$text\"").problem

    @Test
    fun aRateReadsAsOneUnitOfOneCurrencyInAnother() {
        val rate = parsed("0.75")
        assertEquals(dollars, rate.unit)
        assertEquals(pounds, rate.quote)
        assertEquals("0.75", rate.toDecimalText())
        assertEquals("1 USD = 0.75 GBP", rate.toString())
    }

    @Test
    fun theTypedDecimalPlacesAreKept() {
        assertEquals("0.750", parsed("0.750").toDecimalText())
        assertEquals("0.75", parsed("00.75").toDecimalText())
        assertEquals("191.5", parsed("191.5").toDecimalText())
        assertEquals("2", parsed("2").toDecimalText())
        assertEquals("0.000000000001", parsed("0.000000000001").toDecimalText())
    }

    // From the plan: a zero or negative rate is rejected.
    @Test
    fun zeroOrNegativeRatesAreRejected() {
        assertEquals(RateProblem.NOT_POSITIVE, problem("0"))
        assertEquals(RateProblem.NOT_POSITIVE, problem("0.000"))
        assertEquals(RateProblem.NOT_POSITIVE, problem("-0.75"))
    }

    @Test
    fun textThatIsNotAPlainNumberIsRejected() {
        assertEquals(RateProblem.EMPTY, problem(""))
        for (text in listOf("-", "abc", ".75", "0.", "0,75", "1e3", "+1", " 1", "1 ", "0.7.5", "1,000")) {
            assertEquals(RateProblem.NOT_A_NUMBER, problem(text), "parsing \"$text\"")
        }
    }

    @Test
    fun ratesAreLimitedToTwelveDecimalPlacesAndFifteenDigits() {
        assertEquals(RateProblem.TOO_MANY_DECIMAL_PLACES, problem("0.0000000000001"))
        assertEquals("123456789012345", parsed("123456789012345").toDecimalText())
        assertEquals(RateProblem.TOO_MANY_DIGITS, problem("1234567890123456"))
        assertEquals(RateProblem.TOO_MANY_DIGITS, problem("1234.567890123456"))
    }

    @Test
    fun aRateNeedsTwoDifferentCurrencies() {
        assertFailsWith<IllegalArgumentException> { Rate.parse("1", pounds, pounds) }
    }

    @Test
    fun ratesAreEqualWhenTypedTheSameWay() {
        assertEquals(parsed("0.75"), parsed("0.75"))
        assertNotEquals(parsed("0.75"), parsed("0.750"))
        val other = assertIs<RateParseResult.Success>(Rate.parse("0.75", pounds, dollars)).rate
        assertNotEquals(parsed("0.75"), other)
    }
}
