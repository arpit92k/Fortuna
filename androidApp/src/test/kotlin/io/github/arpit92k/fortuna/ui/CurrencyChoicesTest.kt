package io.github.arpit92k.fortuna.ui

import io.github.arpit92k.fortuna.core.money.Currency
import org.junit.Test
import kotlin.test.assertEquals

/** The currency picker: the base currency, then those already in use, then the rest. */
class CurrencyChoicesTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val rupees = Currency.forCode("INR")!!
    private val euros = Currency.forCode("EUR")!!

    @Test
    fun theBaseThenTheCurrenciesInUseComeFirst() {
        val choices = currencyChoices("", pounds, listOf(dollars, pounds, rupees))
        assertEquals(listOf(pounds, dollars, rupees), choices.take(3))
        assertEquals(Currency.all.size, choices.size, "each currency once")
    }

    @Test
    fun aSearchKeepsTheCurrenciesInUseFirstAmongWhatMatches() {
        // "dollar" matches the Australian, Canadian, US and other dollars; the US dollar is in use, so it comes first.
        val choices = currencyChoices("dollar", null, listOf(rupees, dollars))
        assertEquals(dollars, choices.first())
        assertEquals(false, rupees in choices, "rupees do not match, so they are left out")
        assertEquals(true, choices.size > 1)
    }

    @Test
    fun theMostUsedCurrencyComesFirst() {
        assertEquals(
            listOf(pounds, dollars, euros, rupees),
            currenciesInUse(listOf(rupees, dollars, pounds, euros, pounds, dollars, pounds)),
        )
    }
}
