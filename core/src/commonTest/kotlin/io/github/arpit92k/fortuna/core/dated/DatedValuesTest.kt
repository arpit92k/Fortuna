package io.github.arpit92k.fortuna.core.dated

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatedValuesTest {
    private val january = LocalDate(2026, 1, 31)
    private val february = LocalDate(2026, 2, 28)
    private val april = LocalDate(2026, 4, 30)

    // January and April have values; February has none.
    private val values = DatedValues.of(Dated(january, "Jan"), Dated(april, "Apr"))

    // The plan's cases for "latest on or before".

    @Test
    fun anEmptyCollectionHasNoValueOnAnyDate() {
        val empty = DatedValues.empty<String>()
        assertTrue(empty.isEmpty())
        assertNull(empty.latestOnOrBefore(april))
        assertNull(empty.earliest())
        assertNull(empty.latest())
    }

    @Test
    fun aDateBeforeTheFirstValueHasNone() {
        assertNull(values.latestOnOrBefore(LocalDate(2025, 12, 31)))
    }

    @Test
    fun anExactDateGivesItsOwnValue() {
        assertEquals(Dated(january, "Jan"), values.latestOnOrBefore(january))
        assertEquals(Dated(april, "Apr"), values.latestOnOrBefore(april))
    }

    @Test
    fun aDateBetweenTwoValuesGivesTheEarlierOne() {
        assertEquals(Dated(january, "Jan"), values.latestOnOrBefore(february))
        assertEquals(Dated(january, "Jan"), values.latestOnOrBefore(LocalDate(2026, 4, 29)))
    }

    @Test
    fun aDateAfterTheLastValueGivesTheLastOne() {
        assertEquals(Dated(april, "Apr"), values.latestOnOrBefore(LocalDate(2030, 1, 1)))
    }

    // From the plan: one record per date, where a second entry replaces the first.
    @Test
    fun aSecondValueOnTheSameDateReplacesTheFirst() {
        val replaced = values.with(january, "Jan again")
        assertEquals(2, replaced.size)
        assertEquals("Jan again", replaced.on(january))
        assertEquals("Jan", values.on(january), "the original collection is unchanged")
    }

    @Test
    fun valuesAreKeptInDateOrderWhateverTheOrderAdded() {
        val added = DatedValues.empty<String>().with(april, "Apr").with(january, "Jan").with(february, "Feb")
        assertEquals(listOf(january, february, april), added.map { it.date })
        assertEquals(Dated(january, "Jan"), added.earliest())
        assertEquals(Dated(april, "Apr"), added.latest())
    }

    @Test
    fun onGivesOnlyAValueOnExactlyThatDate() {
        assertEquals("Jan", values.on(january))
        assertNull(values.on(february))
    }

    @Test
    fun removingADateLeavesTheOthers() {
        val removed = values.without(january)
        assertEquals(listOf(april), removed.map { it.date })
        assertNull(removed.latestOnOrBefore(february))
        assertEquals(2, values.size, "the original collection is unchanged")
    }

    @Test
    fun removingADateWithNoValueChangesNothing() {
        assertSame(values, values.without(february))
    }

    @Test
    fun collectionsWithTheSameValuesAreEqual() {
        val sameValuesAddedInAnotherOrder = DatedValues.empty<String>().with(april, "Apr").with(january, "Jan")
        assertEquals(values, sameValuesAddedInAnotherOrder)
        assertEquals(values.hashCode(), sameValuesAddedInAnotherOrder.hashCode())
    }

    @Test
    fun whenBuiltFromAListTheLaterValueOnADateWins() {
        val built = DatedValues.of(Dated(january, "first"), Dated(january, "second"))
        assertEquals(1, built.size)
        assertEquals("second", built.on(january))
    }

    @Test
    fun manyValuesAreFoundCorrectly() {
        // Every other day of 2026, so that each lookup has a gap to fall into.
        var collection = DatedValues.empty<Int>()
        var day = LocalDate(2026, 1, 1)
        val days = mutableListOf<LocalDate>()
        while (day.year == 2026) {
            days += day
            day = LocalDate.fromEpochDays(day.toEpochDays() + 2)
        }
        days.shuffled(kotlin.random.Random(7)).forEach { collection = collection.with(it, it.toEpochDays().toInt()) }

        for (date in days) {
            val nextDay = LocalDate.fromEpochDays(date.toEpochDays() + 1)
            assertEquals(date, collection.latestOnOrBefore(date)?.date)
            assertEquals(date, collection.latestOnOrBefore(nextDay)?.date)
        }
    }
}
