package io.github.arpit92k.fortuna.core.dated

import kotlinx.datetime.LocalDate

/** A value that belongs to one calendar date, such as a snapshot or an exchange rate. */
data class Dated<out T>(val date: LocalDate, val value: T)

/**
 * An immutable collection of values, at most one per calendar date, kept in date order.
 *
 * It answers the question the whole design rests on: what was the latest value
 * on or before a given date? Snapshots of a source and the rates of a currency
 * both use it. Dates are calendar days with no time or time zone.
 *
 * Changes return a new collection and leave this one as it was.
 */
class DatedValues<T> private constructor(
    // Sorted by date, with no two entries on the same date.
    private val entries: List<Dated<T>>,
) : Iterable<Dated<T>> {

    /** How many dates have a value. */
    val size: Int get() = entries.size

    fun isEmpty(): Boolean = entries.isEmpty()

    /** The values from the earliest date to the latest. */
    override fun iterator(): Iterator<Dated<T>> = entries.iterator()

    /** The value with the latest date on or before [date], or null if every value is later. */
    fun latestOnOrBefore(date: LocalDate): Dated<T>? {
        val index = indexOf(date)
        return if (index >= 0) entries[index] else entries.getOrNull(-index - 2)
    }

    /** The value on exactly [date], or null if that date has none. */
    fun on(date: LocalDate): T? {
        val index = indexOf(date)
        return if (index >= 0) entries[index].value else null
    }

    /** The value with the earliest date, or null if the collection is empty. */
    fun earliest(): Dated<T>? = entries.firstOrNull()

    /** The value with the latest date, or null if the collection is empty. */
    fun latest(): Dated<T>? = entries.lastOrNull()

    /** A copy with [value] on [date]. A value already on that date is replaced. */
    fun with(date: LocalDate, value: T): DatedValues<T> {
        val index = indexOf(date)
        val updated = entries.toMutableList()
        if (index >= 0) updated[index] = Dated(date, value) else updated.add(-index - 1, Dated(date, value))
        return DatedValues(updated)
    }

    /** A copy with no value on [date]. If that date has none, the copy equals this collection. */
    fun without(date: LocalDate): DatedValues<T> {
        val index = indexOf(date)
        if (index < 0) return this
        return DatedValues(entries.toMutableList().apply { removeAt(index) })
    }

    override fun equals(other: Any?): Boolean = other is DatedValues<*> && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = entries.joinToString(prefix = "[", postfix = "]") { "${it.date}: ${it.value}" }

    // The index of [date] if present; otherwise -(insertion point) - 1, as binarySearch does.
    private fun indexOf(date: LocalDate): Int = entries.binarySearch { it.date.compareTo(date) }

    companion object {
        /** A collection with no values. */
        fun <T> empty(): DatedValues<T> = DatedValues(emptyList())

        /**
         * A collection of the given values, in any order. If two share a date,
         * the later one in the list wins, just as it would if added one by one.
         */
        fun <T> of(vararg values: Dated<T>): DatedValues<T> =
            values.fold(empty<T>()) { collection, dated -> collection.with(dated.date, dated.value) }
    }
}
