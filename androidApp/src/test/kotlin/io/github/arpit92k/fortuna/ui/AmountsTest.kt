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

    // Decided: the phone's regional format.
    @Test
    fun amountsAreWrittenTheWayTheRegionWritesThem() {
        assertEquals("£2,500.00", formatMoney(Money(250_000, pounds), Locale.UK))
        assertEquals("-£169,500.00", formatMoney(Money(-16_950_000, pounds), Locale.UK))
        val german = formatMoney(Money(250_050, pounds), Locale.GERMANY)
        assertTrue(german.contains("2.500,50"), german)
    }

    @Test
    fun eachCurrencyKeepsItsOwnDecimalPlaces() {
        assertTrue(formatMoney(Money(2_500, yen), Locale.UK).endsWith("2,500"))
        assertTrue(formatMoney(Money(2_500_125, dinars), Locale.UK).endsWith("2,500.125"))
    }

    @Test
    fun amountsAreReadTheWayTheRegionWritesThem() {
        assertEquals(Money(250_050, pounds), assertIs<MoneyParseResult.Success>(parseAmount("2,500.50", pounds, Locale.UK)).money)
        assertEquals(Money(250_050, pounds), assertIs<MoneyParseResult.Success>(parseAmount("2.500,50", pounds, Locale.GERMANY)).money)
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

    // A rate is typed either way round and kept as typed, in the region's format.
    @Test
    fun ratesAreReadAndWrittenTheWayTheRegionWritesThem() {
        val dollars = Currency.forCode("USD")!!
        val uk = assertIs<RateParseResult.Success>(parseRate("0.80", dollars, pounds, Locale.UK)).rate
        assertEquals("1 USD = 0.80 GBP", formatRate(uk, Locale.UK))
        val german = assertIs<RateParseResult.Success>(parseRate("1,25", pounds, dollars, Locale.GERMANY)).rate
        assertEquals("1 GBP = 1,25 USD", formatRate(german, Locale.GERMANY))
        assertEquals("1.25", german.toDecimalText())
        assertEquals(RateProblem.EMPTY, assertIs<RateParseResult.Failure>(parseRate(" ", dollars, pounds, Locale.UK)).problem)
        assertEquals(RateProblem.NOT_POSITIVE, assertIs<RateParseResult.Failure>(parseRate("0", dollars, pounds, Locale.UK)).problem)
    }
}
