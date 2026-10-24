package io.github.arpit92k.fortuna.core.insights

import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.Source
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.rates.convert
import kotlinx.datetime.LocalDate

// Calculations over a dataset. Nothing here is stored: every figure is worked
// out from snapshots and rates when asked for, using one rule throughout:
// the value and the rate on a date are the latest ones on or before it.
//
// All of these need the base currency, so they are for use after setup.

/** Net worth on [date] in the base currency. A liability counts against it. */
data class NetWorth(
    val date: LocalDate,
    /** The sum of every asset's value. An overdrawn account counts as a negative asset. */
    val assets: Money,
    /** The sum of every liability's value. A credit card in credit counts as a negative liability. */
    val liabilities: Money,
) {
    val total: Money get() = assets - liabilities
}

/** One row of the dashboard's source list. */
data class SourceSummary(
    val source: Source,
    val kind: CategoryKind,
    /** The latest value, in the source's own currency, as its statement shows. */
    val value: Money,
    /** The same value in the base currency, for a foreign source only. */
    val valueInBase: Money?,
    /** The date of the latest snapshot. */
    val lastUpdated: LocalDate,
)

/** A source's snapshots with what changed between each, in its own currency. */
data class SourceHistory(
    val source: Source,
    val entries: List<HistoryEntry>,
    /** Everything added since the opening balance. */
    val totalAdded: Money,
    /** Everything the source earned (or for a liability, the interest and charges) since the opening balance. */
    val totalGrowth: Money,
)

/**
 * One snapshot in a [SourceHistory]. For the opening balance, which counts as
 * neither added nor growth, [change], [amountAdded] and [growth] are null.
 */
data class HistoryEntry(
    val date: LocalDate,
    val value: Money,
    val note: String?,
    /** The value minus the previous snapshot's value. */
    val change: Money?,
    val amountAdded: Money?,
    /** The change not explained by the amount added. */
    val growth: Money?,
)

/**
 * A change in one source between two of its snapshots, in the base currency,
 * split into its parts. For a liability, positive figures mean the debt grew.
 * The parts always add up to [total]: the currency effect is the remainder.
 */
data class ChangeSplit(
    val total: Money,
    val added: Money,
    val growth: Money,
    val currencyEffect: Money,
)

/** Thrown when a foreign amount has no rate on or before its date, which the recording rules prevent. */
class MissingRateException(val currency: Currency, val date: LocalDate) :
    IllegalStateException("No rate for $currency on or before $date")

// ---- Net worth ----

/**
 * Net worth on [date]: each source's latest snapshot on or before [date], at
 * the latest rate on or before [date]. A source counts from its opening date
 * and not before. Archived sources still count, at their last value.
 */
fun Dataset.netWorthOn(date: LocalDate): NetWorth {
    val base = requireBase()
    var assets = Money(0, base)
    var liabilities = Money(0, base)
    for (source in sources) {
        val value = source.snapshots.latestOnOrBefore(date)?.value?.value ?: continue
        val inBase = toBase(value, date)
        when (kindOf(source)) {
            CategoryKind.ASSET -> assets += inBase
            CategoryKind.LIABILITY -> liabilities += inBase
        }
    }
    return NetWorth(date, assets, liabilities)
}

/**
 * Net worth over the period from [from] to [to], both included: on [from],
 * on every date in between on which a snapshot or a rate was recorded, and on [to].
 * Between those dates nothing changes, so these points draw the whole line.
 */
fun Dataset.timeline(from: LocalDate, to: LocalDate): List<NetWorth> {
    require(from <= to) { "The period starts on $from, after it ends on $to" }
    val changes = sources.flatMap { source -> source.snapshots.map { it.date } } +
        rates.values.flatMap { history -> history.map { it.date } }
    val dates = (changes.filter { it > from && it < to } + from + to).distinct().sorted()
    return dates.map { netWorthOn(it) }
}

// ---- Sources ----

/**
 * The dashboard's list of active sources, in the order they were added, each
 * with its latest value and, for a foreign source, that value in the base
 * currency at the latest rate on or before [today].
 */
fun Dataset.sourceList(today: LocalDate): List<SourceSummary> {
    val base = requireBase()
    return sources.filter { it.status == Status.ACTIVE }.map { source ->
        val latest = source.snapshots.latest()!!
        val value = latest.value.value
        SourceSummary(
            source = source,
            kind = kindOf(source),
            value = value,
            valueInBase = if (source.currency == base) null else toBase(value, today),
            lastUpdated = latest.date,
        )
    }
}

/** A source's history in its own currency, from the opening balance to the latest snapshot. */
fun Dataset.history(id: SourceId): SourceHistory {
    val source = requireNotNull(source(id)) { "No source ${id.value}" }
    val zero = Money(0, source.currency)
    var previous: Money? = null
    var totalAdded = zero
    var totalGrowth = zero
    val entries = source.snapshots.map { (date, snapshot) ->
        val before = previous
        previous = snapshot.value
        if (before == null) {
            HistoryEntry(date, snapshot.value, snapshot.note, change = null, amountAdded = null, growth = null)
        } else {
            val change = snapshot.value - before
            val growth = change - snapshot.amountAdded
            totalAdded += snapshot.amountAdded
            totalGrowth += growth
            HistoryEntry(date, snapshot.value, snapshot.note, change, snapshot.amountAdded, growth)
        }
    }
    return SourceHistory(source, entries, totalAdded, totalGrowth)
}

/**
 * The change in a source from its snapshot on [from] to its snapshot on [to],
 * in the base currency, split into added, growth and currency effect:
 *
 * - added: the amounts added of the snapshots after [from] up to [to], at the closing rate
 * - growth: closing value minus opening value minus added, at the closing rate
 * - currency effect: the remainder, so the parts add up to the total after rounding
 *
 * For a source in the base currency the currency effect is zero.
 */
fun Dataset.splitChange(id: SourceId, from: LocalDate, to: LocalDate): ChangeSplit {
    val source = requireNotNull(source(id)) { "No source ${id.value}" }
    require(from < to) { "The split runs from an earlier snapshot to a later one, not $from to $to" }
    val opening = requireNotNull(source.snapshots.on(from)) { "Source ${id.value} has no snapshot on $from" }.value
    val closing = requireNotNull(source.snapshots.on(to)) { "Source ${id.value} has no snapshot on $to" }.value
    val added = source.snapshots
        .filter { it.date > from && it.date <= to }
        .fold(Money(0, source.currency)) { sum, dated -> sum + dated.value.amountAdded }

    val total = toBase(closing, to) - toBase(opening, from)
    val addedInBase = toBase(added, to)
    val growthInBase = toBase(closing - opening - added, to)
    return ChangeSplit(total, addedInBase, growthInBase, total - addedInBase - growthInBase)
}

// ---- Helpers ----

private fun Dataset.requireBase(): Currency =
    checkNotNull(baseCurrency) { "Insights need the base currency, which setup chooses" }

private fun Dataset.kindOf(source: Source): CategoryKind = category(source.categoryId)!!.kind

/** [amount] in the base currency at the latest rate on or before [date]. */
private fun Dataset.toBase(amount: Money, date: LocalDate): Money {
    if (amount.currency == requireBase()) return amount
    val rate = rates[amount.currency]?.latestOnOrBefore(date)?.value ?: throw MissingRateException(amount.currency, date)
    return convert(amount, rate)
}
