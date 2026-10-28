package io.github.arpit92k.fortuna.dashboard

import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.recording.chooseBaseCurrency
import io.github.arpit92k.fortuna.core.recording.recordRate
import io.github.arpit92k.fortuna.core.insights.netWorthChange
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The dashboard's figures shown in another currency the accounts use. */
class DashboardCurrencyTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val january = LocalDate(2026, 1, 31)
    private val april = LocalDate(2026, 4, 30)
    private val today = LocalDate(2026, 10, 28)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this).dataset
    private fun rate(text: String) = assertIs<RateParseResult.Success>(Rate.parse(text, dollars, pounds)).rate
    private fun gbp(pence: Long) = Money(pence, pounds)
    private fun usd(cents: Long) = Money(cents, dollars)

    // £2,500 in a current account and $10,000 at 0.80 in January; in April the dollar is at 0.75.
    private val data = Dataset.new().chooseBaseCurrency(pounds).dataset()
        .addSource("Current account", CategoryId(1), january, gbp(250_000), today).dataset()
        .addSource("US brokerage", CategoryId(2), january, usd(1_000_000), today, rate = rate("0.80")).dataset()
        .recordRate(dollars, april, rate("0.75"), today).dataset()
        .addSnapshot(SourceId(6), april, gbp(300_000), gbp(50_000), today).dataset()

    @Test
    fun theFiguresAreInTheBaseCurrencyUnlessAnotherIsChosen() {
        val ui = dashboardOf(data, today)
        assertEquals(pounds, ui.shownIn)
        assertEquals(listOf(pounds, dollars), ui.currencies)
        assertEquals(gbp(300_000 + 750_000), ui.netWorth)
    }

    @Test
    fun inDollarsEveryFigureIsConvertedAtItsDatesRate() {
        val ui = dashboardOf(data, today, dollars)
        assertEquals(dollars, ui.shownIn)
        assertEquals(usd(400_000 + 1_000_000), ui.netWorth, "£3,000 at 0.75, and the $10,000 itself")
        // January's point at January's 0.80: £2,500 and £8,000 make $13,125.
        assertEquals(usd(1_312_500), ui.timeline.first().value)
        // The dollar account needs no second figure; the pound one shows its dollars.
        assertNull(ui.sources.single { it.value.currency == dollars }.valueInBase)
        assertEquals(usd(400_000), ui.sources.single { it.value.currency == pounds }.valueInBase)
        // Since the start, £500 lost (£10,500 now against £11,000 put in), at today's 0.75.
        assertEquals(gbp(-50_000), dashboardOf(data, today).gainSinceStart!!.amount)
        assertEquals(usd(-66_667), ui.gainSinceStart!!.amount)
    }

    @Test
    fun aCurrencyNoAccountUsesFallsBackToTheBase() {
        assertEquals(pounds, dashboardOf(data, today, Currency.forCode("JPY")!!).shownIn)
    }

    // Over the chart's range in dollars, the parts add up to the change the chart draws.
    @Test
    fun theBreakdownInDollarsAddsUpToTheChartsChange() {
        val change = netWorthChangeIn(data, january, today, dollars)
        val points = dashboardOf(data, today, dollars).timeline
        assertEquals(points.last().value - points.first().value, change.total)
        assertEquals(change.total, change.added + change.growth + change.currencyEffect + change.newlyTracked)
        assertEquals(usd(66_667), change.added, "the £500 paid in, at today's 0.75")
        assertEquals(netWorthChangeIn(data, january, today, pounds), data.netWorthChange(january, today))
    }
}
