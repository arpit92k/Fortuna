package io.github.arpit92k.fortuna.core.domain

import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatasetJsonTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val yen = Currency.forCode("JPY")!!

    private val fixture = DatasetJson.decode(FormatFixtures.DATASET_V1)

    // From the plan: a dataset written and read back is equal to the original.

    @Test
    fun aDatasetSurvivesBeingWrittenAndReadBack() {
        assertEquals(fixture, DatasetJson.decode(DatasetJson.encode(fixture)))
    }

    @Test
    fun aNewDatasetSurvivesBeingWrittenAndReadBack() {
        val dataset = Dataset.new()
        assertEquals(dataset, DatasetJson.decode(DatasetJson.encode(dataset)))
    }

    @Test
    fun writingIsRepeatable() {
        assertEquals(DatasetJson.encode(fixture), DatasetJson.encode(DatasetJson.decode(DatasetJson.encode(fixture))))
    }

    // From the plan: a sample file kept in the repository loads. This is the first format fixture.

    @Test
    fun theVersion1FixtureLoadsWithEveryField() {
        assertEquals(pounds, fixture.baseCurrency)
        assertEquals(11, fixture.nextId)
        assertEquals(5, fixture.categories.size)
        assertEquals(listOf(6L, 7L, 8L, 9L, 10L), fixture.sources.map { it.id.value })

        val brokerage = fixture.source(SourceId(7))!!
        assertEquals("US brokerage", brokerage.name)
        assertEquals(dollars, brokerage.currency)
        assertEquals(CategoryId(2), brokerage.categoryId)
        assertEquals(UpdateFrequency.QUARTERLY, brokerage.updateFrequency)
        assertEquals("Index funds", brokerage.note)
        val april = brokerage.snapshots.on(LocalDate(2026, 4, 30))!!
        assertEquals(Money(1_150_000, dollars), april.value)
        assertEquals(Money(100_000, dollars), april.amountAdded)

        val current = fixture.source(SourceId(6))!!
        assertEquals("Bonus paid in", current.snapshots.latest()!!.value.note)
        assertEquals(null, current.snapshots.earliest()!!.value.note)

        assertEquals(Money(480_000, yen), fixture.source(SourceId(8))!!.snapshots.latest()!!.value.value)
        assertEquals(UpdateFrequency.HALF_YEARLY, fixture.source(SourceId(9))!!.updateFrequency)
        assertEquals(Money(-150_000, pounds), fixture.source(SourceId(9))!!.snapshots.latest()!!.value.amountAdded)
        assertEquals(Status.ARCHIVED, fixture.source(SourceId(10))!!.status)
        assertEquals(CategoryKind.LIABILITY, fixture.category(CategoryId(5))!!.kind)

        val dollarRates = fixture.rates.getValue(dollars)
        assertEquals("1 USD = 0.75 GBP", dollarRates.latestOnOrBefore(LocalDate(2026, 5, 1))!!.value.toString())
        assertEquals("1 GBP = 191.5 JPY", fixture.rates.getValue(yen).latest()!!.value.toString())

        assertEquals(Settings(UpdateFrequency.MONTHLY, 12, 11, LocalDate(2026, 4, 30)), fixture.settings)
    }

    @Test
    fun writtenFilesStartWithTheFormatVersion() {
        assertTrue(DatasetJson.encode(fixture).startsWith("""{"formatVersion":1,"""))
    }

    // Files that must be refused.

    @Test
    fun anUnknownFormatVersionIsRefusedBeforeAnythingElseIsRead() {
        val failure = assertFailsWith<UnsupportedFormatVersionException> {
            DatasetJson.decode("""{"formatVersion": 2, "somethingNew": true}""")
        }
        assertEquals(2, failure.version)
    }

    @Test
    fun aFileWithoutAFormatVersionIsRefused() {
        assertFailsWith<DatasetFormatException> { DatasetJson.decode("""{"baseCurrency": "GBP"}""") }
        assertFailsWith<DatasetFormatException> { DatasetJson.decode("""{"formatVersion": "one"}""") }
    }

    @Test
    fun textThatIsNotADatasetIsRefused() {
        for (text in listOf("", "not json", "[]", "42", """{"formatVersion": 1}""")) {
            assertFailsWith<DatasetFormatException>(text) { DatasetJson.decode(text) }
        }
    }

    private fun assertRefused(change: Pair<String, String>) {
        val (from, to) = change
        val text = FormatFixtures.DATASET_V1
        assertTrue(from in text, "the fixture should contain $from")
        assertFailsWith<DatasetFormatException>("$from changed to $to") { DatasetJson.decode(text.replaceFirst(from, to)) }
    }

    @Test
    fun aFileThatBreaksTheFormatIsRefused() {
        assertRefused(""""currency": "USD",""" to """"currency": "XYZ",""")
        assertRefused(""""value": "10000.00"""" to """"value": "10000.001"""")
        assertRefused(""""value": "10000.00"""" to """"value": "ten thousand"""")
        assertRefused(""""date": "2026-02-15", "value"""" to """"date": "15/02/2026", "value"""")
        assertRefused(""""kind": "liability"""" to """"kind": "overdraft"""")
        assertRefused(""""updateFrequency": "yearly"""" to """"updateFrequency": "fortnightly"""")
        assertRefused(""""rate": "0.80"""" to """"rate": "0"""")
        assertRefused(""""nextId": 11,""" to """"nextId": 11, "unexpected": true,""")
    }

    @Test
    fun aFileThatBreaksTheDomainModelIsRefused() {
        assertRefused(""""id": 7,""" to """"id": 6,""")
        assertRefused(""""category": 5,""" to """"category": 42,""")
        assertRefused(""""nextId": 11,""" to """"nextId": 10,""")
        assertRefused(""""unit": "USD", "quote": "GBP", "rate": "0.80"""" to """"unit": "USD", "quote": "EUR", "rate": "0.80"""")
        assertRefused(""""lastBackupRevision": 11,""" to """"lastBackupRevision": 13,""")
    }

    @Test
    fun twoSnapshotsOnOneDateAreRefusedRatherThanOneReplacingTheOther() {
        assertRefused(
            """{ "date": "2026-04-30", "value": "11500.00"""" to """{ "date": "2026-01-31", "value": "11500.00"""",
        )
    }

    // An account added with a gain it had already made keeps what was put in; others write nothing new.
    @Test
    fun anOpeningPrincipalIsWrittenOnlyWhereThereIsOne() {
        val isa = fixture.source(SourceId(6))!!.copy(openingPrincipal = Money(200_000, pounds))
        val withPrincipal = fixture.copy(sources = fixture.sources.map { if (it.id == isa.id) isa else it })
        val text = DatasetJson.encode(withPrincipal)
        assertTrue(text.contains("\"openingPrincipal\":\"2000.00\""), text)
        assertEquals(withPrincipal, DatasetJson.decode(text))
        assertTrue("openingPrincipal" !in DatasetJson.encode(fixture), "absent where the whole opening value was put in")
    }
}
