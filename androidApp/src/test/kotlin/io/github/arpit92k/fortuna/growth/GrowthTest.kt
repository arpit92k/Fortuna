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

/** Growth: net worth against earlier years, money paid in counting as much as money earned. */
class GrowthTest {
    private val pounds = Currency.forCode("GBP")!!
    private val today = LocalDate(2026, 10, 29)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this).dataset
    private fun gbp(pounds: Long) = Money(pounds * 100, this.pounds)

    // £10,000 five years ago; £20,000 three years ago, £8,000 of it paid in; £30,000 since June 2025.
    private val data = Dataset.new().chooseBaseCurrency(pounds).dataset()
        .addSource("Savings", CategoryId(1), LocalDate(2021, 10, 29), gbp(10_000), today).dataset()
        .addSnapshot(SourceId(6), LocalDate(2023, 10, 29), gbp(20_000), gbp(8_000), today).dataset()
        .addSnapshot(SourceId(6), LocalDate(2025, 6, 1), gbp(30_000), gbp(0), today).dataset()

    @Test
    fun netWorthIsComparedWithOneThreeAndFiveYearsAgo() {
        val growth = assertNotNull(growthOf(data, today))
        assertEquals(gbp(30_000), growth.now)
        val (one, three, five) = growth.comparisons
        assertEquals(gbp(0), one.change)
        assertEquals(gbp(10_000), three.change, "money paid in counts as growth")
        assertEquals(50.0, three.percent!!, 1e-9)
        assertEquals(gbp(20_000), five.change)
        assertEquals(200.0, five.percent!!, 1e-9)
    }

    @Test
    fun eachYearHasItsValueAndItsChangeOnTheYearBefore() {
        val years = assertNotNull(growthOf(data, today)).years
        assertEquals((2021..2026).map { LocalDate(it, 10, 29) }, years.map { it.date })
        assertEquals(listOf(10_000L, 10_000, 20_000, 20_000, 30_000, 30_000).map { gbp(it) }, years.map { it.value })
        assertNull(years.first().change, "nothing tracked a year before")
        assertEquals(gbp(10_000), years[2].change)
    }

    @Test
    fun yearsBeforeTheFirstValueAreNotTracked() {
        val recent = Dataset.new().chooseBaseCurrency(pounds).dataset()
            .addSource("Savings", CategoryId(1), LocalDate(2024, 1, 31), gbp(5_000), today).dataset()
        val growth = assertNotNull(growthOf(recent, today))
        assertEquals(gbp(5_000), growth.comparisons[0].then)
        assertNull(growth.comparisons[1].change)
        assertNull(growth.comparisons[2].change)
        assertEquals(listOf(LocalDate(2024, 10, 29), LocalDate(2025, 10, 29), today), growth.years.map { it.date })
    }

    @Test
    fun withNothingRecordedThereIsNoGrowth() {
        assertNull(growthOf(Dataset.new().chooseBaseCurrency(pounds).dataset(), today))
    }
}
