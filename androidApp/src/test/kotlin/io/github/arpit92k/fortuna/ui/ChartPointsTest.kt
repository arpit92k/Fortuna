package io.github.arpit92k.fortuna.ui

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.dashboard.dashboardOf
import io.github.arpit92k.fortuna.source.sourcePageOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Step 21: the figures behind the charts, through the real core. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartPointsTest {
    private val dispatcher = StandardTestDispatcher()
    private val pounds = Currency.forCode("GBP")!!
    private val today = LocalDate(2026, 10, 24)
    private fun gbp(pence: Long) = Money(pence, pounds)

    private suspend fun Core.add(name: String, category: Long, pence: Long, date: LocalDate): SourceId {
        val result = run { record { d, now -> d.addSource(name, CategoryId(category), date, gbp(pence), now) } }
        return assertIs<RecordingResult.Success>(result).dataset.sources.last().id
    }

    private suspend fun Core.value(id: SourceId, date: LocalDate, pence: Long) {
        assertIs<RecordingResult.Success>(run { record { d, now -> d.addSnapshot(id, date, gbp(pence), gbp(0), now) } })
    }

    private suspend fun Core.dashboard() = run { read { data, now -> dashboardOf(data, now) } }

    @Test
    fun withNothingRecordedThereIsNoLine() = runTest(dispatcher) {
        val core = TestPhone().unlocked(dispatcher)
        assertEquals(emptyList(), core.dashboard().timeline)
    }

    @Test
    fun oneValueRecordedTodayIsOnePoint() = runTest(dispatcher) {
        val core = TestPhone().unlocked(dispatcher)
        core.add("Savings", 1, 1_000_00, today)
        assertEquals(listOf(ChartPoint(today, gbp(1_000_00), invested = gbp(1_000_00))), core.dashboard().timeline)
    }

    @Test
    fun oneEarlierValueIsCarriedOnToToday() = runTest(dispatcher) {
        val core = TestPhone().unlocked(dispatcher)
        val id = core.add("Savings", 1, 1_000_00, LocalDate(2026, 1, 31))
        val expected = listOf(
            ChartPoint(LocalDate(2026, 1, 31), gbp(1_000_00), invested = gbp(1_000_00)),
            ChartPoint(today, gbp(1_000_00), invested = gbp(1_000_00)),
        )
        assertEquals(expected, core.dashboard().timeline)
        assertEquals(expected, core.run { read { data, now -> sourcePageOf(data, id, now)!!.chart } })
    }

    @Test
    fun netWorthHasAPointOnEveryDateAValueChanged() = runTest(dispatcher) {
        val core = TestPhone().unlocked(dispatcher)
        val savings = core.add("Savings", 1, 1_000_00, LocalDate(2025, 1, 31))
        core.add("Card", 5, 200_00, LocalDate(2025, 6, 30))
        core.value(savings, LocalDate(2026, 3, 31), 1_500_00)
        assertEquals(
            listOf(
                ChartPoint(LocalDate(2025, 1, 31), gbp(1_000_00), invested = gbp(1_000_00)),
                // The card is owed, so it counts against net worth.
                ChartPoint(LocalDate(2025, 6, 30), gbp(800_00), invested = gbp(800_00)),
                ChartPoint(LocalDate(2026, 3, 31), gbp(1_300_00), invested = gbp(800_00)),
                ChartPoint(today, gbp(1_300_00), invested = gbp(800_00)),
            ),
            core.dashboard().timeline,
        )
    }

    @Test
    fun theAmountIsInWholeUnitsOfTheCurrency() {
        assertEquals(1234.56, ChartPoint(today, gbp(1_234_56)).amount, 1e-9)
        assertEquals(1234.0, ChartPoint(today, Money(1_234, Currency.forCode("JPY")!!)).amount, 1e-9)
    }

    @Test
    fun theRangeLeavesAMarginWithoutCrossingZero() {
        val rising = chartRange(listOf(100_000.0, 110_000.0))
        assertEquals(98_500.0, rising.start, 1e-6, "not stretched down to zero, so the change is visible")
        assertEquals(111_500.0, rising.endInclusive, 1e-6)

        val fromNothing = chartRange(listOf(0.0, 500.0))
        assertEquals(0.0, fromNothing.start, "stops at zero")

        val flat = chartRange(listOf(2_000.0, 2_000.0))
        assertTrue(flat.start < 2_000.0 && flat.endInclusive > 2_000.0, "a flat line sits in the middle")

        val paidOff = chartRange(listOf(-300.0, 0.0))
        assertEquals(0.0, paidOff.endInclusive, "below zero, the margin stops at zero too")
    }

    // The dashboard's gains: the latest change across sources and everything since the start, never money paid in.
    @Test
    fun theDashboardShowsGainsNotMoneyPaidIn() = runTest(dispatcher) {
        val core = TestPhone().unlocked(dispatcher)
        val savings = core.add("Savings", 1, 1_000_00, LocalDate(2026, 1, 31))
        assertEquals(null, core.dashboard().latestGain, "nothing has changed yet")
        assertEquals(null, core.dashboard().sources.single().gain)
        // £600 more, of which £500 was paid in: £100 gained, against the £1,000 there plus the £500 added.
        assertIs<RecordingResult.Success>(
            core.run { record { d, now -> d.addSnapshot(savings, LocalDate(2026, 4, 30), gbp(1_600_00), gbp(500_00), now) } },
        )
        val dashboard = core.dashboard()
        assertEquals(gbp(100_00), dashboard.latestGain!!.amount)
        assertEquals(100.0 * 100 / 1_500, dashboard.latestGain!!.percent!!, 1e-9)
        assertEquals(dashboard.latestGain, dashboard.gainSinceStart)
        assertEquals(dashboard.latestGain, dashboard.sources.single().gain)
        assertEquals(gbp(1_500_00), dashboard.timeline.last().invested)
        assertEquals(gbp(100_00), dashboard.timeline.last().gain)
    }

    @Test
    fun percentagesHaveASignAndOneDecimalPlace() {
        assertEquals("+2.1%", formatPercent(2.14, java.util.Locale.UK))
        assertEquals("-0.4%", formatPercent(-0.44, java.util.Locale.UK))
        assertEquals("0.0%", formatPercent(0.0, java.util.Locale.UK))
    }

    // The full-screen chart's ranges: the value in force on the first day starts the line.
    @Test
    fun aRangeStartsWithTheValueInForceOnItsFirstDay() {
        val points = listOf(
            ChartPoint(LocalDate(2026, 1, 31), gbp(1_000_00), gbp(1_000_00)),
            ChartPoint(LocalDate(2026, 6, 30), gbp(1_200_00), gbp(1_100_00)),
            ChartPoint(LocalDate(2026, 9, 30), gbp(1_300_00), gbp(1_100_00)),
            ChartPoint(today, gbp(1_300_00), gbp(1_100_00)),
        )
        val start = ChartRange.SIX_MONTHS.start(today)
        assertEquals(LocalDate(2026, 4, 24), start)
        assertEquals(
            listOf(ChartPoint(LocalDate(2026, 4, 24), gbp(1_000_00), gbp(1_000_00))) + points.drop(1),
            pointsFrom(points, start),
        )
        assertEquals(points, pointsFrom(points, ChartRange.ALL.start(today)))
        assertEquals(points, pointsFrom(points, LocalDate(2025, 1, 1)), "a range longer than the history shows all of it")
    }

    // Over the range: £1,000 grew to £1,300 with £100 paid in, so £200 was earned, against £1,000 plus the £100.
    @Test
    fun aRangeSummaryLeavesOutMoneyPaidIn() {
        val points = listOf(
            ChartPoint(LocalDate(2026, 4, 24), gbp(1_000_00), gbp(1_000_00)),
            ChartPoint(today, gbp(1_300_00), gbp(1_100_00)),
        )
        val summary = rangeSummary(points)!!
        assertEquals(gbp(1_300_00), summary.value)
        assertEquals(gbp(300_00), summary.change)
        assertEquals(gbp(200_00), summary.gain!!.amount)
        assertEquals(gbp(1_100_00), summary.gain!!.base)
        assertEquals(null, rangeSummary(emptyList()))
    }

    // A debt that grew by interest alone: the gain is a loss.
    @Test
    fun interestOnADebtIsALoss() {
        val points = listOf(
            ChartPoint(LocalDate(2026, 4, 24), gbp(5_000_00), gbp(5_000_00)),
            ChartPoint(today, gbp(5_100_00), gbp(5_000_00)),
        )
        assertEquals(gbp(-100_00), rangeSummary(points, liability = true)!!.gain!!.amount)
    }
}
