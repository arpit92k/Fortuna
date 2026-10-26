package io.github.arpit92k.fortuna.ui

import io.github.arpit92k.fortuna.core.money.AmountProblem
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.money.MoneyParseResult
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.core.rates.RateProblem
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmountsTest {
    private val pounds = Currency.forCode("GBP")!!
    private val yen = Currency.forCode("JPY")!!
    private val dinars = Currency.forCode("KWD")!!
    private val dollars = Currency.forCode("USD")!!
    private val euros = Currency.forCode("EUR")!!
    private val rupees = Currency.forCode("INR")!!
    private val india = Locale("en", "IN")

    // Decided: each amount in its own currency's style, whatever the phone's region.
    @Test
    fun amountsAreWrittenTheWayTheirCurrencysCountryWritesThem() {
        assertEquals("£2,500.00", formatMoney(Money(250_000, pounds), Locale.UK))
        assertEquals("-£169,500.00", formatMoney(Money(-16_950_000, pounds), Locale.UK))
        // An Indian phone writes pounds as Britain does, not in lakhs.
        assertEquals(Locale.UK, currencyLocale(pounds, india))
        assertEquals("£100,000.00", formatMoney(Money(10_000_000, pounds), india))
        // And rupees as India does.
        assertEquals(india, currencyLocale(rupees, india))
        assertEquals(Locale.US, currencyLocale(dollars, india))
        // Euros follow the phone's language where it is a euro country's, and Ireland's style otherwise.
        assertEquals(Locale.GERMANY, currencyLocale(euros, Locale.GERMANY))
        val german = formatMoney(Money(250_050, euros), Locale.GERMANY)
        assertTrue(german.contains("2.500,50"), german)
        assertEquals(Locale("en", "IE"), currencyLocale(euros, Locale.UK))
        assertEquals("€2,500.50", formatMoney(Money(250_050, euros), Locale.UK))
    }

    @Test
    fun eachCurrencyKeepsItsOwnDecimalPlaces() {
        assertTrue(formatMoney(Money(2_500, yen), Locale.UK).endsWith("2,500"))
        assertTrue(formatMoney(Money(2_500_125, dinars), Locale.UK).endsWith("2,500.125"))
        // Kuwait writes in Arabic, so an English phone keeps its own digits rather than switching script.
        val dinarsStyle = formatMoney(Money(2_500_125, dinars), Locale.UK)
        assertTrue(dinarsStyle.none { it in '\u0660'..'\u0669' }, dinarsStyle)
    }

    @Test
    fun amountsAreReadTheWayTheyAreWritten() {
        assertEquals(Money(250_050, pounds), assertIs<MoneyParseResult.Success>(parseAmount("2,500.50", pounds, Locale.UK)).money)
        assertEquals(Money(250_050, pounds), assertIs<MoneyParseResult.Success>(parseAmount("2,500.50", pounds, india)).money)
        assertEquals(Money(250_050, euros), assertIs<MoneyParseResult.Success>(parseAmount("2.500,50", euros, Locale.GERMANY)).money)
        assertEquals(Money(250_000, pounds), assertIs<MoneyParseResult.Success>(parseAmount(" 2 500 ", pounds, Locale.UK)).money)
        assertEquals(Money(-5_000, pounds), assertIs<MoneyParseResult.Success>(parseAmount("−50", pounds, Locale.UK)).money)
    }

    @Test
    fun badAmountsAreRefused() {
        assertEquals(AmountProblem.EMPTY, assertIs<MoneyParseResult.Failure>(parseAmount("  ", pounds, Locale.UK)).problem)
        assertEquals(AmountProblem.NOT_A_NUMBER, assertIs<MoneyParseResult.Failure>(parseAmount("abc", pounds, Locale.UK)).problem)
        assertEquals(
            AmountProblem.TOO_MANY_DECIMAL_PLACES,
            assertIs<MoneyParseResult.Failure>(parseAmount("1.234", pounds, Locale.UK)).problem,
        )
    }

    @Test
    fun datesAreWrittenTheWayTheRegionWritesThem() {
        assertEquals("25 Oct 2026", formatDate(LocalDate(2026, 10, 25), Locale.UK))
    }

    // A rate is typed either way round and kept as typed, in its quote currency's style.
    @Test
    fun ratesAreReadAndWrittenInTheirQuoteCurrencysStyle() {
        val uk = assertIs<RateParseResult.Success>(parseRate("0.80", dollars, pounds, Locale.UK)).rate
        assertEquals("1 USD = 0.80 GBP", formatRate(uk, Locale.UK))
        val german = assertIs<RateParseResult.Success>(parseRate("0,93", dollars, euros, Locale.GERMANY)).rate
        assertEquals("1 USD = 0,93 EUR", formatRate(german, Locale.GERMANY))
        assertEquals("0.93", german.toDecimalText())
        assertEquals(RateProblem.EMPTY, assertIs<RateParseResult.Failure>(parseRate(" ", dollars, pounds, Locale.UK)).problem)
        assertEquals(RateProblem.NOT_A_NUMBER, assertIs<RateParseResult.Failure>(parseRate(".", dollars, pounds, Locale.UK)).problem)
        assertEquals(RateProblem.NOT_POSITIVE, assertIs<RateParseResult.Failure>(parseRate("0", dollars, pounds, Locale.UK)).problem)
    }

    // ".7" is how many people type 0.7.
    @Test
    fun aLeadingOrTrailingPointIsFilledIn() {
        val rate = assertIs<RateParseResult.Success>(parseRate(".7", dollars, pounds, Locale.UK)).rate
        assertEquals("1 USD = 0.7 GBP", formatRate(rate, Locale.UK))
        val german = assertIs<RateParseResult.Success>(parseRate(",7", dollars, euros, Locale.GERMANY)).rate
        assertEquals("1 USD = 0,7 EUR", formatRate(german, Locale.GERMANY))
        assertEquals("1 USD = 7 GBP", formatRate(assertIs<RateParseResult.Success>(parseRate("7.", dollars, pounds, Locale.UK)).rate, Locale.UK))

        assertEquals(Money(50, pounds), assertIs<MoneyParseResult.Success>(parseAmount(".50", pounds, Locale.UK)).money)
        assertEquals(Money(-50, pounds), assertIs<MoneyParseResult.Success>(parseAmount("-.5", pounds, Locale.UK)).money)
        assertEquals(Money(1_200, pounds), assertIs<MoneyParseResult.Success>(parseAmount("12.", pounds, Locale.UK)).money)
    }
}
