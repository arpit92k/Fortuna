package io.github.arpit92k.fortuna.lock

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pin = "246810"
    private lateinit var core: Core
    private lateinit var lock: LockViewModel

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        // Set up once, then start the app again: it opens locked.
        phone.session().apply {
            assertIs<SetupResult.Ready>(beginSetup(pin))
            finishSetup(Currency.forCode("GBP")!!)
        }
        core = phone.start(dispatcher)
        lock = LockViewModel(core)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private fun TestScope.enter(pin: String) {
        pin.forEach(lock::digit)
        lock.ok()
        runCurrent()
    }

    @Test
    fun theRightPinUnlocks() = runTest(dispatcher) {
        runCurrent()
        assertIs<SessionState.Locked>(core.state.value)
        enter(pin)
        assertEquals(SessionState.Unlocked, core.state.value)
        assertEquals("", lock.ui.value.entered, "the PIN is forgotten once used")
    }

    @Test
    fun aWrongPinIsRefused() = runTest(dispatcher) {
        runCurrent()
        enter("111111")
        assertIs<SessionState.Locked>(core.state.value)
        assertEquals(LockMessage.WRONG_PIN, lock.ui.value.message)
        assertEquals("", lock.ui.value.entered)
        assertFalse(lock.ui.value.waiting, "the first three attempts are free")
    }

    @Test
    fun theThirdWrongPinMeansAWaitThatCountsDown() = runTest(dispatcher) {
        runCurrent()
        repeat(3) { enter("111111") }
        runCurrent()
        val ui = lock.ui.value
        assertEquals(1.minutes, ui.wait)
        assertFalse(ui.padEnabled)
        lock.digit('2')
        assertEquals("", lock.ui.value.entered, "the pad is off during the wait")

        phone.clock.advance(20.seconds)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(40.seconds, lock.ui.value.wait)

        phone.clock.advance(40.seconds)
        advanceTimeBy(1_001)
        runCurrent()
        assertFalse(lock.ui.value.waiting)
        assertTrue(lock.ui.value.padEnabled)

        enter(pin)
        assertEquals(SessionState.Unlocked, core.state.value)
    }

    @Test
    fun okNeedsSixDigits() = runTest(dispatcher) {
        runCurrent()
        "12345".forEach(lock::digit)
        assertFalse(lock.ui.value.okEnabled)
        lock.digit('6')
        assertTrue(lock.ui.value.okEnabled)
        lock.deleteDigit()
        assertFalse(lock.ui.value.okEnabled)
    }

    @Test
    fun waitsAreShownInMinutesAndHours() {
        assertEquals("0:45", formatWait(45.seconds))
        assertEquals("1:00", formatWait(1.minutes))
        assertEquals("4:59", formatWait(5.minutes - 1.seconds))
        assertEquals("1:00:00", formatWait(1.hours))
        assertEquals("23:59:59", formatWait(24.hours - 1.seconds))
        assertEquals("0:01", formatWait(1.seconds - Duration.parse("0.5s")), "rounded up, never 0:00 while waiting")
    }

    // A damaged store says so on the lock screen rather than crashing the app.
    @Test
    fun aDamagedStoreIsExplained() = runTest(dispatcher) {
        val name = io.github.arpit92k.fortuna.core.store.DocumentStore.MAIN
        val stored = phone.files.read(name)
        phone.files.write(name, stored.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() })
        runCurrent()
        enter(pin)
        assertIs<SessionState.Locked>(core.state.value)
        assertEquals(LockMessage.DATA_DAMAGED, lock.ui.value.message)
    }
}
