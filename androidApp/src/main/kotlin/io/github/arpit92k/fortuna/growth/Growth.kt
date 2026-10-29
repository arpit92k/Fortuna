package io.github.arpit92k.fortuna.growth

import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.insights.fromBase
import io.github.arpit92k.fortuna.core.insights.netWorthOn
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

// Growth: how net worth itself has moved, whatever moved it. Money paid in
// counts as much as money earned, unlike the gains elsewhere in the app.

/** Net worth now against [years] ago: what it was then, and the change since. Null figures before the first value. */
data class Comparison(val years: Int, val date: LocalDate, val then: Money?, val change: Money?) {
    /** The change as a percentage of what it was then, or null where that was not above zero. */
    val percent: Double? get() = if (then != null && change != null && then.minorUnits > 0) change.minorUnits * 100.0 / then.minorUnits else null
}

/** Net worth on one date a whole number of years back, and its change on the year before, if that was tracked. */
data class YearPoint(val date: LocalDate, val value: Money, val change: Money?)

data class GrowthUi(
    val now: Money,
    /** Against 1, 3 and 5 years ago. */
    val comparisons: List<Comparison>,
    /** Net worth on this date in each of the last five years and today, oldest first, from the first value on. */
    val years: List<YearPoint>,
)

/**
 * Net worth's growth to [today], in [shownIn] (the base currency when null),
 * each figure at the rate on its own date. Dates before the first value
 * recorded have no figure: nothing was tracked then.
 */
fun growthOf(data: Dataset, today: LocalDate, shownIn: Currency? = null): GrowthUi? {
    val base = data.baseCurrency ?: return null
    val first = data.sources.mapNotNull { it.snapshots.earliest()?.date }.minOrNull() ?: return null
    val currency = shownIn?.takeIf { data.fromBase(Money(0, base), it, today) != null } ?: base
    fun worthOn(date: LocalDate): Money? =
        if (date < first) null else data.fromBase(data.netWorthOn(date).total, currency, date)
    val now = worthOn(today) ?: return null
    val comparisons = listOf(1, 3, 5).map { years ->
        val date = today.minus(DatePeriod(years = years))
        val then = worthOn(date)
        Comparison(years, date, then, then?.let { now - it })
    }
    val years = (5 downTo 0).mapNotNull { back ->
        val date = today.minus(DatePeriod(years = back))
        worthOn(date)?.let { value ->
            YearPoint(date, value, worthOn(date.minus(DatePeriod(years = 1)))?.let { value - it })
        }
    }
    return GrowthUi(now, comparisons, years)
}
