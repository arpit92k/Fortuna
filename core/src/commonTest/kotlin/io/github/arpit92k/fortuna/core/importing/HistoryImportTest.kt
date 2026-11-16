package io.github.arpit92k.fortuna.core.importing

import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.core.recording.RecordingError
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.recording.chooseBaseCurrency
import io.github.arpit92k.fortuna.core.recording.closeSource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Step 31 (UF-15): importing history from the template. */
class HistoryImportTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val today = LocalDate(2026, 10, 24)
    private val january = LocalDate(2026, 1, 31)
    private val february = LocalDate(2026, 2, 28)
    private val april = LocalDate(2026, 4, 30)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this).dataset
    private fun gbp(pence: Long) = Money(pence, pounds)
    private fun usd(cents: Long) = Money(cents, dollars)
    private fun rate(text: String) = assertIs<RateParseResult.Success>(Rate.parse(text, dollars, pounds)).rate

    private val empty = Dataset.new().chooseBaseCurrency(pounds).dataset()
    private val withSavings = empty.addSource("Savings", CategoryId(1), january, gbp(100_000), today).dataset()

    private fun file(vararg rows: String) = (listOf("Account,Category,Date,Value,Added,Currency") + rows).joinToString("\n")

    private fun Dataset.ready(text: String): ImportPlan = assertIs<ImportCheck.Ready>(checkImport(text, today)).plan

    private fun Dataset.problems(text: String): List<ImportProblem> = assertIs<ImportCheck.Problems>(checkImport(text, today)).problems

    @Test
    fun theTemplateIsTheHeaderAlone() {
        assertEquals("Account,Category,Date,Value,Added,Currency\n", IMPORT_TEMPLATE)
        assertEquals(listOf(ImportProblem.NoRows()), empty.problems(IMPORT_TEMPLATE))
    }

    @Test
    fun aNewAccountsHistoryIsImportedInOneChange() {
        val text = file(
            "ISA,Investments,2026-01-31,1000.00,,GBP",
            "ISA,Investments,2026-04-30,1300,200,",
            "ISA,Investments,2026-02-28,1100,50,GBP",
        )
        val plan = empty.ready(text)
        assertEquals(listOf("ISA"), plan.newAccounts.map { it.name })
        assertEquals("Investments", plan.newAccounts.single().category.name)
        assertTrue(plan.conflicts.isEmpty())
        assertTrue(plan.ratesNeeded.isEmpty())

        val imported = empty.applyImport(plan, emptyMap(), replace = false, today = today).dataset()
        val isa = imported.sources.single()
        assertEquals(listOf(january, february, april), isa.snapshots.map { it.date }, "in date order whatever the file's")
        assertEquals(gbp(100_000), isa.snapshots.on(january)!!.value)
        assertEquals(gbp(5_000), isa.snapshots.on(february)!!.amountAdded)
        assertEquals(gbp(20_000), isa.snapshots.on(april)!!.amountAdded)
    }

    @Test
    fun aForeignAccountNeedsARateWhichIsRecordedOnItsFirstDate() {
        val plan = empty.ready(file("Brokerage,Investments,2026-01-31,500,,usd"))
        assertEquals(mapOf(dollars to january), plan.ratesNeeded)
        assertEquals(
            RecordingError.RateNeeded(dollars, january),
            assertIs<RecordingResult.Failure>(empty.applyImport(plan, emptyMap(), false, today)).error,
        )
        val imported = empty.applyImport(plan, mapOf(dollars to rate("0.8")), false, today).dataset()
        assertEquals(usd(50_000), imported.sources.single().snapshots.on(january)!!.value)
        assertEquals(january, imported.rates[dollars]!!.earliest()!!.date)
    }

    @Test
    fun aDateWithAValueIsSkippedOrReplacedAsChosen() {
        val text = file("savings,,2026-01-31,1200,,", "Savings,,2026-04-30,1500,100,")
        val plan = withSavings.ready(text)
        assertTrue(plan.newAccounts.isEmpty(), "an existing account, whatever its case")
        assertEquals(listOf(2), plan.conflicts.map { it.line })

        val skipped = withSavings.applyImport(plan, emptyMap(), replace = false, today = today).dataset().sources.single()
        assertEquals(gbp(100_000), skipped.snapshots.on(january)!!.value)
        assertEquals(gbp(150_000), skipped.snapshots.on(april)!!.value)

        val replaced = withSavings.applyImport(plan, emptyMap(), replace = true, today = today).dataset().sources.single()
        assertEquals(gbp(120_000), replaced.snapshots.on(january)!!.value)
    }

    @Test
    fun everyProblemIsNamedByItsLine() {
        val problems = empty.problems(
            file(
                "A,Cash,31/01/2026,1,,",
                // A comma for decimals, unquoted, adds a column.
                "B,Cash,2026-01-31,1,234.50,,GBP",
                "C,Cash,2027-01-01,1,,",
                "D,Cars,2026-01-31,1,,",
                "E,Cash,2026-01-31,1,,XYZ",
                "F,Cash,2026-01-31,1,5,",
                ",Cash,2026-01-31,1,,",
                "G,Cash,2026-01-31,1,,",
                "G,Cash,2026-01-31,2,,",
            ),
        )
        assertEquals(
            listOf(
                ImportProblem.BadDate(2, "31/01/2026"),
                ImportProblem.WrongColumnCount(3, 7),
                ImportProblem.DateInFuture(4, LocalDate(2027, 1, 1)),
                ImportProblem.UnknownCategory(5, "Cars"),
                ImportProblem.UnknownCurrency(6, "XYZ"),
                ImportProblem.OpeningHasAdded(7, "F"),
                ImportProblem.NoAccount(8),
                ImportProblem.DuplicateDate(10, "G", january),
            ),
            problems,
        )
    }

    @Test
    fun amountsUseADotAndNoSeparators() {
        val problems = empty.problems(file("A,Cash,2026-01-31,\"1,234.50\",,", "B,Cash,2026-01-31,1234,50,,"))
        assertEquals(ImportProblem.BadAmount(2, "Value", "1,234.50"), problems.first())
    }

    @Test
    fun aFileThatIsNotTheTemplateIsRefused() {
        assertEquals(listOf(ImportProblem.NotTheTemplate()), empty.problems("Name,Amount\nA,1"))
        assertEquals(listOf(ImportProblem.NotTheTemplate()), empty.problems(""))
    }

    @Test
    fun aClosedAccountAndAWrongCurrencyAreRefused() {
        val closed = withSavings.closeSource(withSavings.sources.single().id, april, today).dataset()
        assertEquals(listOf(ImportProblem.AccountClosed(2, "Savings")), closed.problems(file("Savings,,2026-05-01,1,,")))
        assertEquals(listOf(ImportProblem.WrongCurrency(2, "Savings", pounds)), withSavings.problems(file("Savings,,2026-05-01,1,,USD")))
    }

    @Test
    fun aRefusedImportChangesNothing() {
        val plan = empty.ready(file("Brokerage,Investments,2026-01-31,500,,USD"))
        val before = empty
        empty.applyImport(plan, emptyMap(), false, today)
        assertSame(before, empty)
    }
}
