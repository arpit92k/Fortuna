package io.github.arpit92k.fortuna.core.domain

import io.github.arpit92k.fortuna.core.dated.Dated
import io.github.arpit92k.fortuna.core.dated.DatedValues
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatasetTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val euros = Currency.forCode("EUR")!!
    private val day = LocalDate(2026, 1, 31)

    private fun rate(text: String, unit: Currency, quote: Currency): Rate =
        assertIs<RateParseResult.Success>(Rate.parse(text, unit, quote)).rate

    private fun source(id: Long, currency: Currency = pounds, category: Long = 1) = Source(
        id = SourceId(id),
        name = "Source $id",
        categoryId = CategoryId(category),
        currency = currency,
        updateFrequency = UpdateFrequency.MONTHLY,
        snapshots = DatedValues.of(Dated(day, Snapshot(Money(100, currency), Money(0, currency)))),
    )

    @Test
    fun aNewDatasetHasTheStarterCategoriesAndNothingElse() {
        val dataset = Dataset.new()
        assertNull(dataset.baseCurrency)
        assertEquals(listOf("Cash", "Investments", "Property", "Pension", "Debt"), dataset.categories.map { it.name })
        assertEquals(listOf(CategoryKind.LIABILITY), dataset.categories.filter { it.name == "Debt" }.map { it.kind })
        assertTrue(dataset.categories.filter { it.name != "Debt" }.all { it.kind == CategoryKind.ASSET })
        assertTrue(dataset.categories.all { it.status == Status.ACTIVE })
        assertTrue(dataset.sources.isEmpty())
        assertTrue(dataset.rates.isEmpty())
        assertEquals(Settings(), dataset.settings)
        assertEquals(0, dataset.settings.revision)
        assertEquals(6, dataset.nextId)
    }

    @Test
    fun aDatasetWithSourcesAndRatesIsAccepted() {
        val dataset = Dataset.new().copy(
            baseCurrency = pounds,
            sources = listOf(source(6), source(7, dollars)),
            rates = mapOf(dollars to DatedValues.of(Dated(day, rate("0.80", dollars, pounds)))),
            nextId = 8,
        )
        assertEquals(dollars, dataset.source(SourceId(7))?.currency)
        assertEquals("Cash", dataset.category(CategoryId(1))?.name)
        assertNull(dataset.source(SourceId(99)))
    }

    @Test
    fun ratesMayBeTypedFromEitherSide() {
        Dataset.new().copy(
            baseCurrency = pounds,
            rates = mapOf(dollars to DatedValues.of(Dated(day, rate("1.25", pounds, dollars)))),
        )
    }

    @Test
    fun idsMustBeUnique() {
        assertFailsWith<IllegalArgumentException> {
            Dataset.new().copy(sources = listOf(source(6), source(6)), nextId = 7)
        }
        assertFailsWith<IllegalArgumentException> {
            Dataset.new().copy(categories = Dataset.starterCategories + Category(CategoryId(1), "Again", CategoryKind.ASSET))
        }
    }

    @Test
    fun theNextIdMustBeAboveEveryIdInUse() {
        assertFailsWith<IllegalArgumentException> { Dataset.new().copy(sources = listOf(source(6)), nextId = 6) }
        assertFailsWith<IllegalArgumentException> { Dataset.new().copy(nextId = 5) }
    }

    @Test
    fun aSourceMustBelongToACategoryThatExists() {
        assertFailsWith<IllegalArgumentException> {
            Dataset.new().copy(sources = listOf(source(6, category = 42)), nextId = 7)
        }
    }

    @Test
    fun aSnapshotMustBeInItsSourcesCurrency() {
        assertFailsWith<IllegalArgumentException> {
            Source(
                id = SourceId(6),
                name = "Mixed",
                categoryId = CategoryId(1),
                currency = pounds,
                updateFrequency = UpdateFrequency.MONTHLY,
                snapshots = DatedValues.of(Dated(day, Snapshot(Money(100, dollars), Money(0, dollars)))),
            )
        }
        assertFailsWith<IllegalArgumentException> { Snapshot(Money(100, pounds), Money(0, dollars)) }
    }

    @Test
    fun ratesMustRelateTheirCurrencyToTheBase() {
        assertFailsWith<IllegalArgumentException>("rates without a base currency") {
            Dataset.new().copy(rates = mapOf(dollars to DatedValues.of(Dated(day, rate("0.80", dollars, pounds)))))
        }
        assertFailsWith<IllegalArgumentException>("a rate between two foreign currencies") {
            Dataset.new().copy(
                baseCurrency = pounds,
                rates = mapOf(dollars to DatedValues.of(Dated(day, rate("0.90", dollars, euros)))),
            )
        }
        assertFailsWith<IllegalArgumentException>("a rate filed under the wrong currency") {
            Dataset.new().copy(
                baseCurrency = pounds,
                rates = mapOf(euros to DatedValues.of(Dated(day, rate("0.80", dollars, pounds)))),
            )
        }
    }

    @Test
    fun backupRecordsAreConsistent() {
        Settings(revision = 5, lastBackupRevision = 5, lastBackup = day)
        assertFailsWith<IllegalArgumentException>("a backup ahead of the revision") {
            Settings(revision = 5, lastBackupRevision = 6, lastBackup = day)
        }
        assertFailsWith<IllegalArgumentException>("a backup revision without its date") {
            Settings(revision = 5, lastBackupRevision = 5)
        }
        assertFailsWith<IllegalArgumentException>("a negative revision") { Settings(revision = -1) }
    }
}
