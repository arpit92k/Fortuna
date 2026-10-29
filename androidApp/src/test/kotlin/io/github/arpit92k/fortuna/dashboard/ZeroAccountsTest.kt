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
import io.github.arpit92k.fortuna.update.updatePlanOf
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Accounts at zero, hidden from the dashboard and left out of updates with a warning. */
class ZeroAccountsTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val january = LocalDate(2026, 1, 31)
    private val march = LocalDate(2026, 3, 10)
    private val today = LocalDate(2026, 10, 29)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this).dataset
    private fun gbp(pence: Long) = Money(pence, pounds)

    // A current account; an old savings account emptied in March; a dollar account emptied in March too.
    private val data = Dataset.new().chooseBaseCurrency(pounds).dataset()
        .addSource("Current account", CategoryId(1), january, gbp(250_000), today).dataset()
        .addSource("Old savings", CategoryId(1), january, gbp(80_000), today).dataset()
        .addSnapshot(SourceId(7), march, gbp(0), gbp(-80_000), today).dataset()
        .addSource(
            "Old dollars", CategoryId(1), january, Money(10_000, dollars), today,
            rate = assertIs<RateParseResult.Success>(Rate.parse("0.80", dollars, pounds)).rate,
        ).dataset()
        .addSnapshot(SourceId(8), march, Money(0, dollars), Money(-10_000, dollars), today).dataset()

    @Test
    fun hiddenAccountsAtZeroLeaveTheListButNotTheTotals() {
        val shown = dashboardOf(data, today)
        val hidden = dashboardOf(data, today, hideZero = true)
        assertEquals(3, shown.sources.size)
        assertEquals(listOf("Current account"), hidden.sources.map { it.name })
        assertEquals(2, hidden.hiddenAtZero)
        assertEquals(shown.netWorth, hidden.netWorth)
    }

    @Test
    fun withEveryAccountAtZeroHiddenTheDashboardIsNotEmpty() {
        val emptied = data.addSnapshot(SourceId(6), march, gbp(0), gbp(-250_000), today).dataset()
        val ui = dashboardOf(emptied, today, hideZero = true)
        assertEquals(emptyList(), ui.sources)
        assertFalse(ui.empty, "it would otherwise invite a first account")
    }

    @Test
    fun anUpdateSkipsHiddenAccountsAtZeroAndSaysWhich() {
        assertEquals(3, assertNotNull(updatePlanOf(data, today, today)).due.size)
        val plan = assertNotNull(updatePlanOf(data, today, today, hideZero = true))
        assertEquals(listOf("Current account"), plan.due.map { it.name })
        assertEquals(setOf("Old savings", "Old dollars"), plan.hiddenAtZero.map { it.name }.toSet())
        assertEquals(emptyList(), plan.rates, "no dollar account is left to update, so no dollar rate to confirm")
    }
}
