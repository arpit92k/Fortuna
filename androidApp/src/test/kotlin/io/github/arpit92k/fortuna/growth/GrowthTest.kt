package io.github.arpit92k.fortuna.growth

import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.recording.chooseBaseCurrency
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Growth: net worth against earlier dates that the history covers, money paid in counting as much as money earned. */
class GrowthTest {
    private val pounds = Currency.forCode("GBP")!!
    private val today = LocalDate(2026, 10, 30)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this).dataset
    private fun gbp(pounds: Long) = Money(pounds * 100, this.pounds)
    private fun months(comparisons: List<Comparison>) = comparisons.map { it.lookback.months }

    // Five years: £10,000 in October 2021; £20,000 in October 2023, £8,000 of it paid in; £30,000 since June 2025.
    private val long = Dataset.new().chooseBaseCurrency(pounds).dataset()
        .addSource("Savings", CategoryId(1), LocalDate(2021, 10, 30), gbp(10_000), today).dataset()
        .addSnapshot(SourceId(6), LocalDate(2023, 10, 30), gbp(20_000), gbp(8_000), today).dataset()
        .addSnapshot(SourceId(6), LocalDate(2025, 6, 1), gbp(30_000), gbp(0), today).dataset()

    // Eight months: £5,000 in February, £6,000 in August.
    private val short = Dataset.new().chooseBaseCurrency(pounds).dataset()
        .addSource("Savings", CategoryId(1), LocalDate(2026, 2, 28), gbp(5_000), today).dataset()
        .addSnapshot(SourceId(6), LocalDate(2026, 8, 31), gbp(6_000), gbp(500), today).dataset()

    @Test
    fun fiveYearsOfHistoryCompareWithTheLongestPeriodsThatFit() {
        val growth = assertNotNull(growthOf(long, today))
        // 2, 3 and 5 years fit; 10 do not. The start is five years ago, so it is not repeated.
        assertEquals(listOf(24, 36, 60), months(growth.comparisons))
        val five = growth.comparisons.last()
        assertEquals(gbp(20_000), five.change, "money paid in counts as growth")
        assertEquals(200.0, five.percent!!, 1e-9)
    }

    @Test
    fun eightMonthsOfHistoryCompareWithMonthsAndTheStart() {
        val growth = assertNotNull(growthOf(short, today))
        assertEquals(listOf(1, 3, 6, null), months(growth.comparisons))
        val start = growth.comparisons.last()
        assertEquals(LocalDate(2026, 2, 28), start.date)
        assertEquals(gbp(1_000), start.change)
    }

    @Test
    fun aLongHistoryStepsByYearAndAShortOneByMonth() {
        val long = assertNotNull(growthOf(long, today))
        assertEquals(Step.YEAR, long.step)
        assertEquals((2021..2026).map { LocalDate(it, 10, 30) }, long.steps.map { it.date })
        assertNull(long.steps.first().change, "nothing tracked a year before")
        assertEquals(gbp(10_000), long.steps[2].change)

        val short = assertNotNull(growthOf(short, today))
        assertEquals(Step.MONTH, short.step)
        // The 30th of each month back to the start: eight months back is 28 February, the first value's own date.
        assertEquals(LocalDate(2026, 2, 28), short.steps.first().date)
        assertEquals(LocalDate(2026, 3, 30), short.steps[1].date)
        assertEquals(today, short.steps.last().date)
        assertEquals(9, short.steps.size)
    }

    @Test
    fun theChartRunsFromTheFirstValueWithTheValueAlone() {
        val chart = assertNotNull(growthOf(short, today)).chart
        assertEquals(LocalDate(2026, 2, 28), chart.first().date)
        assertEquals(today, chart.last().date)
        assertTrue(chart.all { it.invested == null })
    }

    @Test
    fun withNothingRecordedThereIsNoGrowth() {
        assertNull(growthOf(Dataset.new().chooseBaseCurrency(pounds).dataset(), today))
    }
}
