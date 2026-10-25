package io.github.arpit92k.fortuna.settings

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
import io.github.arpit92k.fortuna.source.AddSourceProblem
import io.github.arpit92k.fortuna.source.AddSourceViewModel
import io.github.arpit92k.fortuna.source.rateInUse
import io.github.arpit92k.fortuna.update.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Step 19: foreign currencies, through the real core. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForeignCurrencyTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val january = LocalDate(2026, 1, 31)
    private val april = LocalDate(2026, 4, 30)
    private val june = LocalDate(2026, 6, 30)

    private lateinit var core: Core
    private lateinit var add: AddSourceViewModel
    private lateinit var dashboard: DashboardViewModel
    private lateinit var update: UpdateViewModel
    private lateinit var currencies: CurrenciesViewModel

    private fun gbp(pence: Long) = Money(pence, pounds)
    private fun usd(cents: Long) = Money(cents, dollars)
    private fun rate(text: String, unit: Currency = dollars, quote: Currency = pounds) =
        assertIs<RateParseResult.Success>(Rate.parse(text, unit, quote)).rate

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        core = phone.unlocked(dispatcher)
        add = AddSourceViewModel(core, locale = { Locale.UK })
        dashboard = DashboardViewModel(core)
        update = UpdateViewModel(core, locale = { Locale.UK })
        currencies = CurrenciesViewModel(core)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    /** Fills in S-11 for an investment in dollars and saves; returns whether it was saved. */
    private fun TestScope.addDollars(name: String, amount: String, date: LocalDate, rate: Rate?): Boolean {
        var saved = false
        add.name(name)
        add.category(CategoryId(2))
        add.currency(dollars)
        add.amount(amount)
        add.date(date)
        add.rate(rate)
        add.save(pounds) { saved = true }
        advanceUntilIdle()
        return saved
    }

    private fun TestScope.brokerage() {
        assertTrue(addDollars("US brokerage", "10,000", january, rate("0.80")))
        advanceUntilIdle()
    }

    // From the plan: a source in another currency shows both values on the dashboard and counts in the base currency.
    @Test
    fun aForeignSourceShowsBothValuesAndCountsInTheBase() = runTest(dispatcher) {
        brokerage()
        val row = dashboard.ui.value!!.sources.single()
        assertEquals(usd(1_000_000), row.value)
        assertEquals(gbp(800_000), row.valueInBase)
        assertEquals(gbp(800_000), dashboard.ui.value!!.netWorth)
    }

    // A rate can be entered the other way round and is kept as typed.
    @Test
    fun aRateTypedTheOtherWayRoundGivesTheSameValue() = runTest(dispatcher) {
        assertTrue(addDollars("US brokerage", "10,000", january, rate("1.25", unit = pounds, quote = dollars)))
        advanceUntilIdle()
        assertEquals(gbp(800_000), dashboard.ui.value!!.sources.single().valueInBase)
        val kept = currencies.ui.value!!.currencies.single().rates.single().value
        assertEquals(pounds, kept.unit, "kept as typed: 1 GBP = 1.25 USD")
    }

    // From the screen map: a new foreign currency asks for a rate before saving.
    @Test
    fun aNewCurrencyNeedsARate() = runTest(dispatcher) {
        assertFalse(addDollars("US brokerage", "10,000", january, rate = null))
        assertEquals(AddSourceProblem.RATE_NEEDED, add.form.value.problem)
        assertTrue(dashboard.ui.value!!.empty, "nothing was saved")
    }

    @Test
    fun aCurrencyWithARateAlreadyNeedsNoNewOne() = runTest(dispatcher) {
        brokerage()
        val inUse = core.run { read { data, _ -> rateInUse(data, dollars, april) } }
        assertEquals(rate("0.80"), assertNotNull(inUse).value)
        assertNull(core.run { read { data, _ -> rateInUse(data, pounds, april) } }, "the base currency has no rate")
        assertTrue(addDollars("US savings", "500", april, rate = null))
    }

    @Test
    fun choosingAnotherCurrencyDropsTheRateEntered() = runTest(dispatcher) {
        add.currency(dollars)
        add.rate(rate("0.80"))
        add.currency(Currency.forCode("EUR")!!)
        assertNull(add.form.value.rate)
    }

    // From the plan: a newer rate during an update moves net worth.
    @Test
    fun aNewRateDuringAnUpdateMovesNetWorth() = runTest(dispatcher) {
        brokerage()
        update.prepare()
        update.date(april)
        advanceUntilIdle()
        val due = assertNotNull(update.ui.value.plan).rates.single()
        assertEquals(dollars, due.currency)
        assertEquals(rate("0.80"), due.last)
        assertEquals(89, due.ageInDays, "the January rate is 89 days old on 30 April")

        update.recordRate(dollars, rate("0.75"))
        advanceUntilIdle()
        val renewed = update.ui.value.plan!!.rates.single()
        assertEquals(rate("0.75"), renewed.last)
        assertEquals(0, renewed.ageInDays)
        assertEquals(gbp(750_000), dashboard.ui.value!!.netWorth)
    }

    // From the plan: a newer rate from settings moves net worth.
    @Test
    fun aNewRateFromSettingsMovesNetWorth() = runTest(dispatcher) {
        brokerage()
        currencies.saveRate(dollars, june, rate("0.70"))
        advanceUntilIdle()
        val usdRates = currencies.ui.value!!.currencies.single()
        assertEquals(listOf(june, january), usdRates.rates.map { it.date }, "newest first")
        assertEquals(listOf("US brokerage"), usdRates.usedBy)
        assertEquals(gbp(700_000), dashboard.ui.value!!.netWorth)

        currencies.saveRate(dollars, june, rate("0.72"))
        advanceUntilIdle()
        assertEquals(rate("0.72"), currencies.ui.value!!.currencies.single().rates.first().value, "corrected, not added")
        assertEquals(2, currencies.ui.value!!.currencies.single().rates.size)
    }

    // From the plan: the rate the earliest snapshot depends on cannot be deleted.
    @Test
    fun theRateTheOpeningValueNeedsCannotBeDeleted() = runTest(dispatcher) {
        brokerage()
        currencies.saveRate(dollars, june, rate("0.70"))
        advanceUntilIdle()

        currencies.deleteRate(dollars, january)
        advanceUntilIdle()
        assertEquals(RateActionProblem.NeededBy("US brokerage", january), currencies.ui.value!!.problem)
        assertEquals(2, currencies.ui.value!!.currencies.single().rates.size, "nothing deleted")

        currencies.clearProblem()
        currencies.deleteRate(dollars, june)
        advanceUntilIdle()
        assertNull(currencies.ui.value!!.problem)
        assertEquals(listOf(january), currencies.ui.value!!.currencies.single().rates.map { it.date })
        assertEquals(gbp(800_000), dashboard.ui.value!!.netWorth, "back to the January rate")
    }

    @Test
    fun aRateCannotBeDatedInTheFuture() = runTest(dispatcher) {
        brokerage()
        currencies.saveRate(dollars, LocalDate(2027, 1, 1), rate("0.70"))
        advanceUntilIdle()
        assertEquals(RateActionProblem.InFuture, currencies.ui.value!!.problem)
    }

    @Test
    fun withOnlyTheBaseCurrencyThereAreNoRatesToShow() = runTest(dispatcher) {
        advanceUntilIdle()
        assertTrue(currencies.ui.value!!.currencies.isEmpty())
        assertEquals(pounds, currencies.ui.value!!.base)
    }
}
