package io.github.arpit92k.fortuna.source

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
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A foreign account's page in the base currency, with the currency's effect on it. */
class BaseViewTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val january = LocalDate(2026, 1, 31)
    private val february = LocalDate(2026, 2, 28)
    private val april = LocalDate(2026, 4, 30)
    private val today = LocalDate(2026, 10, 28)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this).dataset
    private fun rate(text: String) = assertIs<RateParseResult.Success>(Rate.parse(text, dollars, pounds)).rate
    private fun gbp(pence: Long) = Money(pence, pounds)

    // $10,000 at 0.80 in January; the dollar at 0.70 in February; $11,500 with $1,000 added at 0.75 in April.
    private val data = Dataset.new().chooseBaseCurrency(pounds).dataset()
        .addSource("Current account", CategoryId(1), january, gbp(250_000), today).dataset()
        .addSource("US brokerage", CategoryId(2), january, Money(1_000_000, dollars), today, rate = rate("0.80")).dataset()
        .recordRate(dollars, february, rate("0.70"), today).dataset()
        .recordRate(dollars, april, rate("0.75"), today).dataset()
        .addSnapshot(SourceId(7), april, Money(1_150_000, dollars), Money(100_000, dollars), today).dataset()

    @Test
    fun aForeignAccountCanBeSeenInTheBaseCurrency() {
        val page = assertNotNull(sourcePageOf(data, SourceId(7), today))
        val inBase = assertNotNull(page.inBase)
        assertEquals(gbp(862_500), inBase.value, "$11,500 at 0.75")
        assertEquals(gbp(62_500), inBase.sinceOpening.total)
        assertEquals(gbp(75_000), inBase.sinceOpening.added)
        assertEquals(gbp(37_500), inBase.sinceOpening.growth)
        assertEquals(gbp(-50_000), inBase.sinceOpening.currencyEffect)
        assertNull(inBase.rows.getValue(january).change, "the opening value has no change")
        assertEquals(gbp(-50_000), inBase.rows.getValue(april).change!!.currencyEffect)
        // The chart turns at February's rate too, though no value was recorded then.
        assertEquals(listOf(january, february, april, today), inBase.chart.map { it.date })
    }

    @Test
    fun theChartFollowsTheChosenCurrency() {
        val page = assertNotNull(sourcePageOf(data, SourceId(7), today))
        assertEquals(page.chart, page.shownChart)
        assertEquals(page.inBase!!.chart, page.copy(showingBase = true).shownChart)
    }

    @Test
    fun anAccountInTheBaseCurrencyHasNoSwitch() {
        assertNull(assertNotNull(sourcePageOf(data, SourceId(6), today)).inBase)
    }
}
