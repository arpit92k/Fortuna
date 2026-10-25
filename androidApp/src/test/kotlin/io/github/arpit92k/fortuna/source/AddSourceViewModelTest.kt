package io.github.arpit92k.fortuna.source

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddSourceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pounds = Currency.forCode("GBP")!!
    private val cash = CategoryId(1)
    private val debt = CategoryId(5)
    private lateinit var core: Core
    private lateinit var add: AddSourceViewModel

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        core = phone.unlocked(dispatcher)
        add = AddSourceViewModel(core, locale = { Locale.UK })
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private fun TestScope.addSource(name: String, category: CategoryId, amount: String, date: LocalDate? = null): Boolean {
        var saved = false
        add.name(name)
        add.category(category)
        add.amount(amount)
        add.date(date)
        add.save(pounds) { saved = true }
        advanceUntilIdle()
        return saved
    }

    // From the plan: the user can add an asset and a liability and see the three totals.
    @Test
    fun anAssetAndALiabilityGiveTheThreeTotals() = runTest(dispatcher) {
        val dashboard = DashboardViewModel(core)
        advanceUntilIdle()
        assertTrue(dashboard.ui.value!!.empty, "the empty dashboard invites the first source")

        assertTrue(addSource("Current account", cash, "2,500"))
        assertTrue(addSource("Mortgage", debt, "180,000.00", date = LocalDate(2026, 1, 31)))
        advanceUntilIdle()

        val ui = assertNotNull(dashboard.ui.value)
        assertEquals(Money(250_000, pounds), ui.assets)
        assertEquals(Money(18_000_000, pounds), ui.liabilities)
        assertEquals(Money(-17_750_000, pounds), ui.netWorth)
        assertEquals(listOf("Current account", "Mortgage"), ui.sources.map { it.name })
        assertEquals(listOf(false, true), ui.sources.map { it.liability })
        assertEquals(LocalDate(2026, 1, 31), ui.sources[1].lastUpdated)
        assertEquals(phone.clock.date, ui.sources[0].lastUpdated, "no date chosen means today")
    }

    // From the plan: the data is still there after locking and after restarting the phone.
    @Test
    fun theDataSurvivesLockingAndARestart() = runTest(dispatcher) {
        addSource("Current account", cash, "2,500")
        core.run { lock() }
        assertTrue(core.state.value is SessionState.Locked)

        val afterRestart = phone.start(dispatcher)
        afterRestart.run { unlock("246810") }
        val names = afterRestart.run { read { data, _ -> data.sources.map { it.name } } }
        assertEquals(listOf("Current account"), names)
    }

    @Test
    fun theFormSaysWhatIsMissing() = runTest(dispatcher) {
        add.save(pounds) {}
        assertEquals(AddSourceProblem.NO_NAME, add.form.value.problem)
        add.name("Savings")
        add.save(pounds) {}
        assertEquals(AddSourceProblem.NO_CATEGORY, add.form.value.problem)
        add.category(cash)
        add.save(pounds) {}
        assertEquals(AddSourceProblem.NO_AMOUNT, add.form.value.problem)
        add.amount("lots")
        add.save(pounds) {}
        assertEquals(AddSourceProblem.NOT_AN_AMOUNT, add.form.value.problem)
        add.amount("1.234")
        add.save(pounds) {}
        assertEquals(AddSourceProblem.TOO_MANY_DECIMAL_PLACES, add.form.value.problem)
        add.amount("1.23")
        assertEquals(null, add.form.value.problem, "typing clears the message")
    }

    @Test
    fun namesAreUnique() = runTest(dispatcher) {
        assertTrue(addSource("Savings", cash, "10"))
        assertTrue(!addSource(" savings ", cash, "20"))
        assertEquals(AddSourceProblem.NAME_IN_USE, add.form.value.problem)
        assertEquals("20", add.form.value.amount, "what was typed is kept, to be corrected")
    }

    @Test
    fun aSavedSourceClearsTheForm() = runTest(dispatcher) {
        addSource("Savings", cash, "10")
        assertEquals(AddSourceForm(), add.form.value)
    }

    // What was typed stays while the app is locked, so nothing is lost.
    @Test
    fun theFormIsKeptAcrossALock() = runTest(dispatcher) {
        add.name("Pension")
        add.amount("42,000")
        core.run { lock() }
        assertEquals("Pension", add.form.value.name)
        core.run { unlock("246810") }
        assertEquals("42,000", add.form.value.amount)
    }
}
