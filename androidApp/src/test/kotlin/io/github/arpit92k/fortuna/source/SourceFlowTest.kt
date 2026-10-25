package io.github.arpit92k.fortuna.source

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSource
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Step 17: the source page and value entry, through the real core. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pounds = Currency.forCode("GBP")!!
    private val january = LocalDate(2026, 1, 31)
    private val april = LocalDate(2026, 4, 30)
    private lateinit var core: Core
    private lateinit var page: SourcePageViewModel
    private lateinit var entry: ValueEntryViewModel
    private lateinit var dashboard: DashboardViewModel
    private lateinit var savings: SourceId
    private lateinit var mortgage: SourceId

    private fun gbp(pence: Long) = Money(pence, pounds)

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        core = phone.unlocked(dispatcher)
        page = SourcePageViewModel(core)
        entry = ValueEntryViewModel(core, locale = { Locale.UK })
        dashboard = DashboardViewModel(core)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private suspend fun add(name: String, category: Long, pence: Long, date: LocalDate): SourceId {
        val result = core.run { record { d, today -> d.addSource(name, CategoryId(category), date, gbp(pence), today) } }
        return assertIs<RecordingResult.Success>(result).dataset.sources.last().id
    }

    private suspend fun source(id: SourceId) =
        valueEntrySourceOf(core.run { read { d, _ -> d } }, id, phone.clock.date)!!

    /** Fills in S-12 and saves; returns whether it was saved. */
    private suspend fun TestScope.enter(
        id: SourceId,
        value: String,
        added: String = "",
        date: LocalDate? = null,
        editing: LocalDate? = null,
    ): Boolean {
        val src = source(id)
        entry.open(src, editing, null, null)
        entry.value(value)
        entry.added(added)
        if (editing == null) entry.date(date)
        var saved = false
        entry.save(src, editing) { saved = true }
        advanceUntilIdle()
        return saved
    }

    private suspend fun TestScope.setUp() {
        savings = add("Savings", 1, 300_000, january)
        mortgage = add("Mortgage", 5, 18_000_000, january)
        advanceUntilIdle()
    }

    private fun TestScope.show(id: SourceId): SourcePageUi {
        page.show(id)
        advanceUntilIdle()
        return assertNotNull(page.ui.value)
    }

    // From the plan: record a new value, and the dashboard totals follow.
    @Test
    fun aNewValueSplitsIntoAddedAndGrowth() = runTest(dispatcher) {
        setUp()
        assertTrue(enter(savings, "3,600", added = "500", date = april))

        val ui = show(savings)
        assertEquals(gbp(360_000), ui.value)
        val latest = ui.entries.first()
        assertEquals(april, latest.date)
        assertEquals(gbp(60_000), latest.change)
        assertEquals(gbp(50_000), latest.added)
        assertEquals(gbp(10_000), latest.growth, "£100 of growth")
        assertEquals(gbp(50_000), ui.totalAdded)
        assertEquals(gbp(10_000), ui.totalGrowth)
        assertTrue(ui.entries.last().opening)
        assertEquals(gbp(360_000 - 18_000_000), dashboard.ui.value!!.netWorth)
    }

    // Decided: blank counts as nothing added.
    @Test
    fun aBlankAmountAddedCountsAsNothingAdded() = runTest(dispatcher) {
        setUp()
        assertTrue(enter(savings, "3,100", date = april))
        val latest = show(savings).entries.first()
        assertEquals(gbp(0), latest.added)
        assertEquals(gbp(10_000), latest.growth)
    }

    @Test
    fun aRepaymentLowersADebt() = runTest(dispatcher) {
        setUp()
        assertTrue(enter(mortgage, "178,950.25", added = "-1,500", date = april))
        val latest = show(mortgage).entries.first()
        assertEquals(gbp(-150_000), latest.added)
        assertEquals(gbp(45_025), latest.growth, "£450.25 of interest")
    }

    // From the plan: correct a value.
    @Test
    fun aValueCanBeCorrected() = runTest(dispatcher) {
        setUp()
        enter(savings, "3,600", added = "500", date = april)
        val src = source(savings)
        entry.open(src, april, gbp(360_000) to gbp(50_000), null)
        assertEquals("3600.00", entry.form.value.value, "the form opens with the figures recorded")
        assertEquals("500.00", entry.form.value.added)
        entry.value("3,650")
        var saved = false
        entry.save(src, april) { saved = true }
        advanceUntilIdle()
        assertTrue(saved, "an edit needs no confirmation")
        assertEquals(gbp(15_000), show(savings).entries.first().growth)
    }

    // From the plan: replace a value by entering the same date again, after confirming.
    @Test
    fun theSameDateAgainAsksBeforeReplacing() = runTest(dispatcher) {
        setUp()
        enter(savings, "3,600", date = april)
        val saved = enter(savings, "3,700", date = april)
        assertFalse(saved)
        assertTrue(entry.form.value.confirmReplace, "asks first")
        assertEquals(gbp(360_000), show(savings).value, "nothing replaced yet")

        entry.keepExisting()
        assertFalse(entry.form.value.confirmReplace)

        var replaced = false
        entry.save(source(savings), null, replace = true) { replaced = true }
        advanceUntilIdle()
        assertTrue(replaced)
        assertEquals(gbp(370_000), show(savings).value)
        assertEquals(2, show(savings).entries.size, "replaced, not added")
    }

    @Test
    fun aNewValueCannotBeDatedBeforeTheLatest() = runTest(dispatcher) {
        setUp()
        enter(savings, "3,600", date = april)
        assertFalse(enter(savings, "3,500", date = LocalDate(2026, 3, 1)))
        assertEquals(ValueEntryProblem.BEFORE_LATEST, entry.form.value.problem)
    }

    // From the plan: delete a value; the totals follow.
    @Test
    fun theLatestValueCanBeDeleted() = runTest(dispatcher) {
        setUp()
        enter(savings, "3,600", date = april)
        assertTrue(show(savings).canDeleteLatest)
        page.deleteLatest(savings)
        advanceUntilIdle()
        val ui = show(savings)
        assertEquals(gbp(300_000), ui.value)
        assertFalse(ui.canDeleteLatest, "the opening value is the only one left")
        assertEquals(gbp(300_000 - 18_000_000), dashboard.ui.value!!.netWorth)
    }

    // From the plan: remove a source created by mistake.
    @Test
    fun aSourceWithOnlyItsOpeningValueCanBeDeleted() = runTest(dispatcher) {
        setUp()
        assertTrue(show(mortgage).canDeleteSource)
        var deleted = false
        page.deleteSource(mortgage) { deleted = true }
        advanceUntilIdle()
        assertTrue(deleted)
        assertNull(page.ui.value)
        assertEquals(listOf("Savings"), dashboard.ui.value!!.sources.map { it.name })
        assertEquals(gbp(300_000), dashboard.ui.value!!.netWorth)
    }

    @Test
    fun aSourceWithHistoryIsKept() = runTest(dispatcher) {
        setUp()
        enter(savings, "3,600", date = april)
        assertFalse(show(savings).canDeleteSource)
        var deleted = false
        page.deleteSource(savings) { deleted = true }
        advanceUntilIdle()
        assertFalse(deleted)
    }

    // The opening value has no amount added, whatever is typed.
    @Test
    fun correctingTheOpeningValueAddsNothing() = runTest(dispatcher) {
        setUp()
        assertTrue(enter(savings, "3,050", added = "999", editing = january))
        val opening = show(savings).entries.single()
        assertEquals(gbp(305_000), opening.value)
        assertTrue(opening.opening)
    }

    // What was typed stays across a lock, so nothing is lost.
    @Test
    fun theFormIsKeptAcrossALockAndReopening() = runTest(dispatcher) {
        setUp()
        val src = source(savings)
        entry.open(src, null, null, null)
        entry.value("3,999")
        core.run { lock() }
        core.run { unlock("246810") }
        entry.open(src, null, null, null)
        assertEquals("3,999", entry.form.value.value, "opening the same entry again keeps what was typed")
        entry.discard()
        assertEquals("", entry.form.value.value)
    }
}
