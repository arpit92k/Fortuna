package io.github.arpit92k.fortuna.core.insights

import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.domain.Status
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class InsightsTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!

    private val today = LocalDate(2026, 10, 24)
    private val newYear = LocalDate(2026, 1, 1)
    private val january = LocalDate(2026, 1, 31)
    private val february = LocalDate(2026, 2, 28)
    private val april = LocalDate(2026, 4, 30)

    private val cash = CategoryId(1)
    private val investments = CategoryId(2)
    private val debt = CategoryId(5)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this).dataset

    private fun rate(text: String): Rate = assertIs<RateParseResult.Success>(Rate.parse(text, dollars, pounds)).rate

    private fun gbp(pence: Long) = Money(pence, pounds)

    private fun usd(cents: Long) = Money(cents, dollars)

    /** The last source added to the dataset. */
    private val Dataset.newest: SourceId get() = sources.last().id

    // Pounds as base. In January: a current account, the architecture's US
    // brokerage at 0.80 and a mortgage. In April each is updated and the dollar
    // is at 0.75.
    private val opened = Dataset.new().chooseBaseCurrency(pounds).dataset()
        .addSource("Current account", cash, january, gbp(250_000), today).dataset()
        .addSource("US brokerage", investments, january, usd(1_000_000), today, rate = rate("0.80")).dataset()
        .addSource("Mortgage", debt, january, gbp(18_000_000), today).dataset()
    private val account = SourceId(6)
    private val brokerage = SourceId(7)
    private val mortgage = SourceId(8)

    private val updated = opened
        .recordRate(dollars, april, rate("0.75"), today).dataset()
        .addSnapshot(account, april, gbp(310_050), gbp(50_000), today).dataset()
        .addSnapshot(brokerage, april, usd(1_150_000), usd(100_000), today).dataset()
        .addSnapshot(mortgage, april, gbp(17_895_025), gbp(-150_000), today).dataset()

    // ---- Net worth ----

    // From the plan: a liability is subtracted.
    @Test
    fun netWorthIsAssetsMinusLiabilitiesInTheBaseCurrency() {
        val inJanuary = updated.netWorthOn(january)
        assertEquals(gbp(1_050_000), inJanuary.assets, "£2,500 and $10,000 at 0.80")
        assertEquals(gbp(18_000_000), inJanuary.liabilities)
        assertEquals(gbp(-16_950_000), inJanuary.total)

        val inApril = updated.netWorthOn(april)
        assertEquals(gbp(1_172_550), inApril.assets, "£3,100.50 and $11,500 at 0.75")
        assertEquals(gbp(17_895_025), inApril.liabilities)
        assertEquals(gbp(-16_722_475), inApril.total)
    }

    // From the plan: a source with no new snapshot keeps its last value.
    @Test
    fun aSourceKeepsItsLastValueUntilItsNextSnapshot() {
        assertEquals(updated.netWorthOn(january).total, updated.netWorthOn(february).total)
        assertEquals(updated.netWorthOn(april).total, updated.netWorthOn(today).total)
    }

    // From the plan: a source does not count before its opening date.
    @Test
    fun aSourceCountsFromItsOpeningDateAndNotBefore() {
        val nothingYet = updated.netWorthOn(LocalDate(2025, 12, 31))
        assertEquals(gbp(0), nothingYet.assets)
        assertEquals(gbp(0), nothingYet.liabilities)

        val withSavings = updated.addSource("Savings", cash, LocalDate(2026, 2, 15), gbp(100_000), today).dataset()
        assertEquals(gbp(1_050_000), withSavings.netWorthOn(LocalDate(2026, 2, 14)).assets)
        assertEquals(gbp(1_150_000), withSavings.netWorthOn(LocalDate(2026, 2, 15)).assets)
    }

    // From the plan: a transfer recorded as a negative and a positive amount added cancels in the total.
    @Test
    fun aTransferBetweenSourcesCancelsInTheTotal() {
        val withSavings = updated.addSource("Savings", cash, april, gbp(100_000), today).dataset()
        val savings = withSavings.newest
        val may = LocalDate(2026, 5, 31)
        val moved = withSavings
            .addSnapshot(account, may, gbp(260_050), gbp(-50_000), today).dataset()
            .addSnapshot(savings, may, gbp(150_000), gbp(50_000), today).dataset()

        assertEquals(withSavings.netWorthOn(april).total, moved.netWorthOn(may).total)
        assertEquals(gbp(0), moved.history(account).entries.last().growth)
        assertEquals(gbp(0), moved.history(savings).entries.last().growth)
    }

    // From the plan: a new rate moves net worth on its own date.
    @Test
    fun aNewRateMovesNetWorthOnItsOwnDate() {
        val withRate = opened.recordRate(dollars, LocalDate(2026, 3, 15), rate("0.78"), today).dataset()
        assertEquals(gbp(1_050_000), withRate.netWorthOn(LocalDate(2026, 3, 14)).assets)
        assertEquals(gbp(1_030_000), withRate.netWorthOn(LocalDate(2026, 3, 15)).assets, "$10,000 at 0.78 is £7,800")
    }

    @Test
    fun negativeValuesCountTheOtherWay() {
        val overdrawn = updated.addSnapshot(account, today, gbp(-20_000), gbp(-330_050), today).dataset()
        assertEquals(gbp(-20_000 + 862_500), overdrawn.netWorthOn(today).assets)

        val card = overdrawn.addSource("Credit card", debt, today, gbp(-2_000), today).dataset()
        assertEquals(gbp(17_895_025 - 2_000), card.netWorthOn(today).liabilities, "a card in credit reduces the liabilities")
    }

    @Test
    fun anArchivedSourceStillCountsAtItsLastValue() {
        val archived = updated.copy(sources = updated.sources.map { if (it.id == account) it.copy(status = Status.ARCHIVED) else it })
        assertEquals(updated.netWorthOn(today), archived.netWorthOn(today))
    }

    @Test
    fun insightsNeedTheBaseCurrencyAndEveryRate() {
        assertFailsWith<IllegalStateException> { Dataset.new().netWorthOn(today) }
        val withoutRates = updated.copy(rates = emptyMap())
        val failure = assertFailsWith<MissingRateException> { withoutRates.netWorthOn(today) }
        assertEquals(dollars, failure.currency)
    }

    // ---- The timeline ----

    @Test
    fun theTimelineHasAPointOnEachDateSomethingChanged() {
        val timeline = updated.timeline(newYear, today)
        assertEquals(listOf(newYear, january, april, today), timeline.map { it.date })
        assertEquals(listOf(gbp(0), gbp(-16_950_000), gbp(-16_722_475), gbp(-16_722_475)), timeline.map { it.total })
    }

    @Test
    fun theTimelineIncludesRateChangesAndOnlyDatesInThePeriod() {
        val withRate = updated.recordRate(dollars, LocalDate(2026, 3, 15), rate("0.78"), today).dataset()
        assertEquals(
            listOf(february, LocalDate(2026, 3, 15), april),
            withRate.timeline(february, april).map { it.date },
        )
        assertEquals(listOf(today), withRate.timeline(today, today).map { it.date })
        assertFailsWith<IllegalArgumentException> { withRate.timeline(april, february) }
    }

    // ---- The source list ----

    @Test
    fun theSourceListShowsEachValueInItsOwnCurrencyAndForeignOnesInTheBase() {
        val list = updated.sourceList(today)
        assertEquals(listOf(account, brokerage, mortgage), list.map { it.source.id })

        val (current, us, home) = list
        assertEquals(gbp(310_050), current.value)
        assertNull(current.valueInBase, "a source in the base currency shows one value")
        assertEquals(usd(1_150_000), us.value)
        assertEquals(gbp(862_500), us.valueInBase, "$11,500 · £8,625")
        assertEquals(CategoryKind.LIABILITY, home.kind)
        assertEquals(gbp(17_895_025), home.value, "a liability shows its balance as entered")
        assertEquals(listOf(april, april, april), list.map { it.lastUpdated })
    }

    @Test
    fun theSourceListShowsActiveSourcesOnly() {
        val archived = updated.copy(sources = updated.sources.map { if (it.id == account) it.copy(status = Status.ARCHIVED) else it })
        assertEquals(listOf(brokerage, mortgage), archived.sourceList(today).map { it.source.id })
    }

    // ---- A source's history ----

    @Test
    fun aSourcesHistorySplitsEachChangeIntoAddedAndGrowth() {
        val history = updated.history(brokerage)
        val (opening, latest) = history.entries
        assertEquals(usd(1_000_000), opening.value)
        assertNull(opening.change, "the opening balance is neither added nor growth")
        assertNull(opening.amountAdded)
        assertNull(opening.growth)
        assertEquals(usd(150_000), latest.change)
        assertEquals(usd(100_000), latest.amountAdded)
        assertEquals(usd(50_000), latest.growth)
        assertEquals(usd(100_000), history.totalAdded)
        assertEquals(usd(50_000), history.totalGrowth)
    }

    @Test
    fun aLiabilitysGrowthIsItsInterestAndCharges() {
        // £1,500 repaid, yet the balance fell by only £1,049.75: £450.25 of interest.
        val latest = updated.history(mortgage).entries.last()
        assertEquals(gbp(-104_975), latest.change)
        assertEquals(gbp(-150_000), latest.amountAdded)
        assertEquals(gbp(45_025), latest.growth)
    }

    // ---- Splitting a change ----

    // From the plan: the architecture's worked example.
    @Test
    fun theWorkedExampleSplitsIntoAddedGrowthAndCurrencyEffect() {
        val split = updated.splitChange(brokerage, january, april)
        assertEquals(gbp(62_500), split.total, "£8,000 to £8,625")
        assertEquals(gbp(75_000), split.added, "$1,000 at 0.75")
        assertEquals(gbp(37_500), split.growth, "the other $500 at 0.75")
        assertEquals(gbp(-50_000), split.currencyEffect, "$10,000 losing 0.05 per dollar")
    }

    // From the plan: the parts add up to the total after rounding.
    @Test
    fun thePartsAddUpToTheTotalAfterRounding() {
        val d1 = LocalDate(2026, 6, 1)
        val d2 = LocalDate(2026, 7, 1)
        val small = Dataset.new().chooseBaseCurrency(pounds).dataset()
            .addSource("Small", cash, d1, usd(1_001), today, rate = rate("0.333")).dataset()
        val id = small.newest
        val later = small.recordRate(dollars, d2, rate("0.337"), today).dataset()
            .addSnapshot(id, d2, usd(1_002), usd(0), today).dataset()

        val split = later.splitChange(id, d1, d2)
        assertEquals(gbp(5), split.total)
        assertEquals(gbp(0), split.added)
        assertEquals(gbp(0), split.growth)
        assertEquals(gbp(5), split.currencyEffect)
    }

    @Test
    fun aSourceInTheBaseCurrencyHasNoCurrencyEffect() {
        val split = updated.splitChange(account, january, april)
        assertEquals(gbp(60_050), split.total)
        assertEquals(gbp(50_000), split.added)
        assertEquals(gbp(10_050), split.growth)
        assertEquals(gbp(0), split.currencyEffect)
    }

    @Test
    fun aSplitRunsBetweenTwoOfTheSourcesSnapshots() {
        assertFailsWith<IllegalArgumentException> { updated.splitChange(brokerage, april, january) }
        assertFailsWith<IllegalArgumentException> { updated.splitChange(brokerage, january, february) }
    }

    // ---- Gains and losses ----

    // Decided: money put in is not a gain. Interest, market moves and the currency effect are.
    @Test
    fun whatWasPutInLeavesOutEveryGain() {
        // £2,500 + £500; $10,000 at 0.80 + $1,000 at 0.75; less £180,000 owed and a £1,500 repayment.
        assertEquals(gbp(300_000 + 800_000 + 75_000 - 17_850_000), updated.investedOn(april))
        assertEquals(gbp(250_000 + 800_000 - 18_000_000), updated.investedOn(january))
        assertEquals(gbp(0), updated.investedOn(newYear), "nothing before the first value")
    }

    @Test
    fun theGainSinceTheStartIsNetWorthLessWhatWasPutIn() {
        // £100.50 interest on the account, $500 growth less the dollar's fall on the
        // brokerage (-£125.00), and £450.25 interest on the mortgage.
        val gain = updated.gainSinceStart(april)
        assertEquals(gbp(10_050 - 12_500 - 45_025), gain.amount)
        assertNull(gain.base, "more owed than put in: nothing to measure against")
        assertEquals(gbp(0), opened.gainSinceStart(january).amount, "nothing gained on the first day")
    }

    @Test
    fun theGainHasAPercentageWhenThereIsSomethingToMeasureAgainst() {
        val savings = Dataset.new().chooseBaseCurrency(pounds).dataset()
            .addSource("Savings", cash, january, gbp(1_000_000), today).dataset()
            .addSnapshot(SourceId(6), april, gbp(1_050_000), gbp(20_000), today).dataset()
        val gain = savings.gainSinceStart(april)
        assertEquals(gbp(30_000), gain.amount, "£500 more, of which £200 was paid in")
        assertEquals(gbp(1_020_000), gain.base, "£10,000 there plus £200 paid in")
        assertEquals(gain, savings.latestGain(), "one change so far, so the latest is the whole")
    }

    @Test
    fun theLatestGainAddsUpEachSourcesLatestChange() {
        val latest = updated.latestGain()
        assertEquals(gbp(10_050 - 12_500 - 45_025), latest.amount)
        assertNull(latest.base)
        assertEquals(gbp(0), opened.latestGain().amount, "opening values only: no change yet")
    }

    @Test
    fun eachSourcesLatestGainIsInItsOwnCurrency() {
        val accountGain = updated.sourceLatestGain(account)!!
        assertEquals(gbp(10_050), accountGain.amount)
        assertEquals(gbp(300_000), accountGain.base)

        val brokerageGain = updated.sourceLatestGain(brokerage)!!
        assertEquals(usd(50_000), brokerageGain.amount, "$500 grown, before the currency")
        assertEquals(usd(1_100_000), brokerageGain.base)

        // A debt's interest is a loss, against what was owed after the repayment.
        val mortgageGain = updated.sourceLatestGain(mortgage)!!
        assertEquals(gbp(-45_025), mortgageGain.amount)
        assertEquals(gbp(17_850_000), mortgageGain.base, "what was owed after the repayment")

        assertNull(opened.sourceLatestGain(SourceId(6)), "only an opening value")
    }
}
