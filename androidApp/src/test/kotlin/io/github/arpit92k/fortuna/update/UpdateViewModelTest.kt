package io.github.arpit92k.fortuna.update

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.navigation.Destination
import io.github.arpit92k.fortuna.navigation.entersValue
import io.github.arpit92k.fortuna.source.ValueEntryProblem
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Step 18: the periodic update, through the real core. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pounds = Currency.forCode("GBP")!!
    private val january = LocalDate(2026, 1, 31)
    private val february = LocalDate(2026, 2, 28)
    private val march = LocalDate(2026, 3, 31)
    private val april = LocalDate(2026, 4, 30)

    // A new dataset's first sources get these ids; setUp checks it.
    private val account = SourceId(6)
    private val mortgage = SourceId(7)
    private val pension = SourceId(8)

    private lateinit var core: Core
    private lateinit var update: UpdateViewModel

    private fun gbp(pence: Long) = Money(pence, pounds)

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        core = phone.unlocked(dispatcher)
        update = UpdateViewModel(core, locale = { Locale.UK })
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private suspend fun add(name: String, category: Long, pence: Long, date: LocalDate): SourceId {
        val result = core.run { record { d, today -> d.addSource(name, CategoryId(category), date, gbp(pence), today) } }
        return assertIs<RecordingResult.Success>(result).dataset.sources.last().id
    }

    /** The current account was last updated in February, the others in January. */
    private suspend fun TestScope.setUp() {
        assertEquals(account, add("Current account", 1, 250_000, january))
        assertEquals(mortgage, add("Mortgage", 5, 18_000_000, january))
        assertEquals(pension, add("Pension", 4, 4_000_000, january))
        core.run { record { d, today -> d.addSnapshot(account, february, gbp(260_000), gbp(0), today) } }
        advanceUntilIdle()
    }

    private fun TestScope.prepare(date: LocalDate?) {
        update.prepare()
        update.date(date)
        advanceUntilIdle()
    }

    private fun TestScope.enter(value: String, added: String = "") {
        update.value(value)
        update.added(added)
        update.save()
        advanceUntilIdle()
    }

    private fun currentName() = update.ui.value.current?.name

    private suspend fun valueOn(id: SourceId, date: LocalDate): Money? =
        core.run { read { d, _ -> d.source(id)!!.snapshots.on(date)?.value } }

    // From the plan: the whole update in one sitting, the one updated longest ago first.
    @Test
    fun everySourceIsUpdatedInOneSitting() = runTest(dispatcher) {
        setUp()
        prepare(april)
        val plan = assertNotNull(update.ui.value.plan)
        assertEquals(listOf("Mortgage", "Pension", "Current account"), plan.due.map { it.name }, "longest ago first")
        assertEquals(0, plan.leftOut)

        update.start()
        assertEquals(1, update.ui.value.position)
        assertEquals(3, update.ui.value.total)
        assertEquals(gbp(18_000_000), update.ui.value.current!!.lastValue)

        enter("178,950.25", added = "-1,500")
        assertEquals("Pension", currentName())
        assertEquals(2, update.ui.value.position)
        enter("41,000")
        enter("2,700", added = "100")
        assertNull(update.ui.value.current, "the sitting is over")

        assertEquals(gbp(17_895_025), valueOn(mortgage, april))
        assertEquals(gbp(4_100_000), valueOn(pension, april))
        assertEquals(gbp(270_000), valueOn(account, april))
    }

    // From the plan: leave half-way, then continue with the sources not yet updated.
    @Test
    fun startingAgainWithTheSameDateContinues() = runTest(dispatcher) {
        setUp()
        prepare(april)
        update.start()
        enter("178,950.25")
        update.stop()

        prepare(april)
        assertEquals(listOf("Pension", "Current account"), update.ui.value.plan!!.due.map { it.name })
        assertEquals(1, update.ui.value.plan!!.leftOut, "the mortgage is done for this date")
    }

    // A skipped source records nothing and is offered again next time.
    @Test
    fun aSkippedSourceIsOfferedAgain() = runTest(dispatcher) {
        setUp()
        prepare(april)
        update.start()
        update.skip()
        assertEquals("Pension", currentName())
        assertEquals(2, update.ui.value.position)
        assertNull(valueOn(mortgage, april))
        update.skip()
        update.skip()
        assertNull(update.ui.value.current)

        update.finish()
        prepare(april)
        assertEquals(3, update.ui.value.plan!!.due.size)
    }

    // From the plan: an update dated before a source's latest value leaves it out and says so.
    @Test
    fun anUpdateBeforeASourcesLatestValueLeavesItOut() = runTest(dispatcher) {
        setUp()
        prepare(march)
        assertEquals(listOf("Mortgage", "Pension", "Current account"), update.ui.value.plan!!.due.map { it.name })
        prepare(february)
        val plan = update.ui.value.plan!!
        assertEquals(listOf("Mortgage", "Pension"), plan.due.map { it.name })
        assertEquals(1, plan.leftOut, "the current account already has a February value")
    }

    @Test
    fun noDateMeansToday() = runTest(dispatcher) {
        setUp()
        prepare(null)
        assertEquals(phone.clock.date, update.ui.value.plan!!.date)
    }

    // Decided: blank counts as nothing added.
    @Test
    fun aBlankAmountAddedCountsAsNothingAdded() = runTest(dispatcher) {
        setUp()
        prepare(april)
        update.start()
        enter("180,000")
        val snapshot = core.run { read { d, _ -> d.source(mortgage)!!.snapshots.on(april)!! } }
        assertEquals(gbp(0), snapshot.amountAdded)
    }

    @Test
    fun aMissingValueIsReported() = runTest(dispatcher) {
        setUp()
        prepare(april)
        update.start()
        update.save()
        assertEquals(ValueEntryProblem.NO_VALUE, update.ui.value.form.problem)
        update.value("abc")
        update.save()
        assertEquals(ValueEntryProblem.NOT_AN_AMOUNT, update.ui.value.form.problem)
        assertEquals("Mortgage", currentName(), "still on the same source")
    }

    // What was typed stays while the app is locked, and the update carries on after unlocking.
    @Test
    fun theUpdateIsKeptAcrossALock() = runTest(dispatcher) {
        setUp()
        prepare(april)
        update.start()
        enter("178,950.25")
        update.value("41,000")
        core.run { lock() }
        advanceUntilIdle()
        core.run { unlock("246810") }
        advanceUntilIdle()
        assertEquals("Pension", currentName())
        assertEquals("41,000", update.ui.value.form.value)
        assertEquals(2, update.ui.value.position)
    }

    @Test
    fun theUpdateStepEarnsTheLongerGrace() {
        assertTrue(entersValue(Destination.UpdateStep))
        assertTrue(!entersValue(Destination.Update))
    }
}
