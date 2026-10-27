package io.github.arpit92k.fortuna.ui

import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.insights.Gain
import io.github.arpit92k.fortuna.core.insights.investedOn
import io.github.arpit92k.fortuna.core.insights.timeline
import io.github.arpit92k.fortuna.core.money.Money
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.pow

// The figures behind the charts, worked out from the core's insights. Kept
// apart from the drawing, so they can be checked without a screen.

/**
 * One point of a chart: a value on a date, and what had been put in by then,
 * so the gap between the two lines is everything gained.
 */
data class ChartPoint(val date: LocalDate, val value: Money, val invested: Money? = null) {
    /** The value in whole units of its currency, as the chart draws it: 1234.56 for £1,234.56. */
    val amount: Double get() = value.wholeUnits()

    val investedAmount: Double? get() = invested?.wholeUnits()

    /** Gained by this point: the value less what was put in. */
    val gain: Money? get() = invested?.let { value - it }
}

private fun Money.wholeUnits(): Double = minorUnits / 10.0.pow(currency.decimalPlaces)

/**
 * Net worth from the first value recorded up to [today]. The core gives a
 * point on every date a value or a rate was recorded, and on the two ends;
 * nothing changes between them, so these points draw the whole line.
 * Empty when nothing has been recorded.
 */
fun netWorthPoints(data: Dataset, today: LocalDate): List<ChartPoint> {
    val first = data.sources.flatMap { source -> source.snapshots.map { it.date } }.minOrNull() ?: return emptyList()
    if (first > today) return emptyList()
    return data.timeline(first, today).map { ChartPoint(it.date, it.total, data.investedOn(it.date)) }
}

/**
 * A source's values, oldest first, carried on to [today]: the latest value
 * still holds until a newer one is recorded, as on the net worth chart.
 */
fun sourcePoints(values: List<ChartPoint>, today: LocalDate): List<ChartPoint> {
    val latest = values.lastOrNull() ?: return emptyList()
    return if (latest.date < today) values + latest.copy(date = today) else values
}

/**
 * The range the chart's vertical axis shows for [amounts]: the lowest to the
 * highest, with a margin so the line does not touch the edges. Where every
 * amount is on one side of zero, the margin stops at zero rather than
 * crossing it. A flat line gets a margin of a tenth of its value.
 */
fun chartRange(amounts: List<Double>): ClosedFloatingPointRange<Double> {
    require(amounts.isNotEmpty()) { "A chart needs at least one amount" }
    val low = amounts.min()
    val high = amounts.max()
    val margin = if (high > low) (high - low) * 0.15 else maxOf(abs(high) * 0.1, 1.0)
    val bottom = (low - margin).let { if (low >= 0 && it < 0) 0.0 else it }
    val top = (high + margin).let { if (high <= 0 && it > 0) 0.0 else it }
    return bottom..top
}

/** How far back the full-screen chart looks. */
enum class ChartRange {
    THREE_MONTHS,
    SIX_MONTHS,

    /** Since the first of January. */
    YEAR_TO_DATE,
    ONE_YEAR,
    ALL,
    ;

    /** The first day shown on [today], or null for everything. */
    fun start(today: LocalDate): LocalDate? = when (this) {
        THREE_MONTHS -> today.minus(DatePeriod(months = 3))
        SIX_MONTHS -> today.minus(DatePeriod(months = 6))
        YEAR_TO_DATE -> LocalDate(today.year, 1, 1)
        ONE_YEAR -> today.minus(DatePeriod(years = 1))
        ALL -> null
    }
}

/**
 * [points] from [from] on. Values hold until the next is recorded, so the
 * value in force on [from] starts the line there. All of them when [from] is
 * null or before the first point.
 */
fun pointsFrom(points: List<ChartPoint>, from: LocalDate?): List<ChartPoint> {
    if (from == null || points.isEmpty() || points.first().date >= from) return points
    val inForce = points.last { it.date <= from }
    return listOf(inForce.copy(date = from)) + points.filter { it.date > from }
}

/** The figures above the full-screen chart, over the points it shows. */
data class RangeSummary(
    /** The value at the end. */
    val value: Money,
    /** From the first value to the last, including money put in or taken out. */
    val change: Money,
    /** What was earned over the range, or null where what was put in is not known. */
    val gain: Gain?,
)

/**
 * The value, change and gain over [points], oldest first. The gain leaves out
 * money put in or taken out, and is measured, as elsewhere, against the value
 * at the start plus what was added since. For a debt, the gain is turned
 * round: interest owed is a loss. Null with no points.
 */
fun rangeSummary(points: List<ChartPoint>, liability: Boolean = false): RangeSummary? {
    val first = points.firstOrNull() ?: return null
    val last = points.last()
    val gain = if (first.invested != null && last.invested != null) {
        val earned = (last.value - last.invested) - (first.value - first.invested)
        val base = first.value + (last.invested - first.invested)
        Gain(if (liability) -earned else earned, base.takeIf { it.minorUnits > 0 })
    } else {
        null
    }
    return RangeSummary(last.value, last.value - first.value, gain)
}
