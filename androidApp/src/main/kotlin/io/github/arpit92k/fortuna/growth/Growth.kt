package io.github.arpit92k.fortuna.growth

import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.insights.fromBase
import io.github.arpit92k.fortuna.core.insights.netWorthOn
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.ui.ChartPoint
import io.github.arpit92k.fortuna.ui.netWorthPoints
import io.github.arpit92k.fortuna.ui.pointsFrom
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

// Growth: how net worth itself has moved, whatever moved it. Money paid in
// counts as much as money earned, unlike the gains elsewhere in the app.
//
// The periods follow the data: with eight months of history, growth over
// 5 years means nothing, so the page compares with 1, 3 and 6 months ago and
// with the start instead.

/** How far back a comparison looks: a number of months, or, with null, to the first value. */
data class Lookback(val months: Int?) {
    companion object {
        val SINCE_START = Lookback(null)

        /** The periods offered, shortest first. */
        val CHOICES: List<Lookback> = listOf(1, 3, 6, 12, 24, 36, 60, 120).map { Lookback(it) }
    }
}

/** Net worth now against [lookback]: what it was on [date], and the change since. */
data class Comparison(val lookback: Lookback, val date: LocalDate, val then: Money, val change: Money) {
    /** The change as a percentage of what it was then, or null where that was not above zero. */
    val percent: Double? get() = if (then.minorUnits > 0) change.minorUnits * 100.0 / then.minorUnits else null
}

/** How the page steps through the history: a month at a time for a short one, a year at a time for a long one. */
enum class Step { MONTH, YEAR }

/** Net worth on one step's date, and its change on the step before, if that was tracked. */
data class StepPoint(val date: LocalDate, val value: Money, val change: Money?)

data class GrowthUi(
    val now: Money,
    /** The first value's date: everything before it was not tracked. */
    val since: LocalDate,
    /** Against up to three periods that fit in the history, longest last, then against the start. */
    val comparisons: List<Comparison>,
    val step: Step,
    /** Net worth on this date in each of the last steps, oldest first, from the first value on. */
    val steps: List<StepPoint>,
    /** Net worth since the first value, up to ten years, for the chart: the value alone, as growth counts money paid in too. */
    val chart: List<ChartPoint> = emptyList(),
) {
    /** Values on one date only: nothing has grown yet, so the page says so instead of showing +£0 everywhere. */
    val tooLittleHistory: Boolean get() = comparisons.isEmpty()
}

/** At most this many periods are compared, besides the start. */
private const val COMPARISONS = 3

/** A history at least this long steps a year at a time; a shorter one, a month at a time. */
private const val YEARS_FOR_YEARLY = 2

/**
 * Net worth's growth to [today], in [shownIn] (the base currency when null),
 * each figure at the rate on its own date. The periods compared and the steps
 * shown are chosen to fit the history, from its first value. Null with
 * nothing recorded.
 */
fun growthOf(data: Dataset, today: LocalDate, shownIn: Currency? = null): GrowthUi? {
    val base = data.baseCurrency ?: return null
    val first = data.sources.mapNotNull { it.snapshots.earliest()?.date }.minOrNull() ?: return null
    if (first > today) return null
    val currency = shownIn?.takeIf { data.fromBase(Money(0, base), it, today) != null } ?: base
    fun worthOn(date: LocalDate): Money? =
        if (date < first) null else data.fromBase(data.netWorthOn(date).total, currency, date)
    val now = worthOn(today)!!
    // Values on a single date: no growth to show yet.
    val dates = data.sources.flatMap { source -> source.snapshots.map { it.date } }.distinct()
    if (dates.size < 2) return GrowthUi(now, first, emptyList(), Step.MONTH, emptyList())

    // The longest periods that fit, then the start, unless the start is one of them.
    val fitting = Lookback.CHOICES
        .map { it to today.minus(DatePeriod(months = it.months!!)) }
        .filter { (_, date) -> date >= first }
        .takeLast(COMPARISONS)
    val comparisons = (fitting + listOfNotNull((Lookback.SINCE_START to first).takeIf { fitting.none { (_, date) -> date == first } && first < today }))
        .map { (lookback, date) -> worthOn(date)!!.let { then -> Comparison(lookback, date, then, now - then) } }

    val step = if (first <= today.minus(DatePeriod(years = YEARS_FOR_YEARLY))) Step.YEAR else Step.MONTH
    // Up to ten years, or twelve months, back to the first value.
    val monthsPerStep = if (step == Step.YEAR) 12 else 1
    val count = if (step == Step.YEAR) 10 else 12
    val steps = (count downTo 0).mapNotNull { back ->
        val date = today.minus(DatePeriod(months = back * monthsPerStep))
        worthOn(date)?.let { value -> StepPoint(date, value, worthOn(date.minus(DatePeriod(months = monthsPerStep)))?.let { value - it }) }
    }

    val chartFrom = maxOf(first, today.minus(DatePeriod(years = 10)))
    val chart = netWorthPoints(data, today)
        .let { points -> pointsFrom(points, chartFrom) }
        .map { point -> ChartPoint(point.date, data.fromBase(point.value, currency, point.date)!!) }
    return GrowthUi(now, first, comparisons, step, steps, chart)
}
