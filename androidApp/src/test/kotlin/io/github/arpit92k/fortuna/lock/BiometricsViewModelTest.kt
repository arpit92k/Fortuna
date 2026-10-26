package io.github.arpit92k.fortuna.lock

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.platform.BiometricResult
import io.github.arpit92k.fortuna.platform.Biometrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Biometric unlock through the real core, with a stand-in for the sensor and the Keystore. */
@OptIn(ExperimentalCoroutinesApi::class)
class BiometricsViewModelTest {
    /** Keeps the copy in memory. [next] is how the next check ends; null means the finger is recognised. */
    private class FakeBiometrics(var available: Boolean = true) : Biometrics {
        var copy: ByteArray? = null
        var next: BiometricResult<Nothing>? = null
        var prompts = 0

        override fun available() = available

        override fun enabled() = copy != null

        override suspend fun enable(dataKey: ByteArray): BiometricResult<Unit> {
            prompts++
            next?.let { return it }
            copy = dataKey.copyOf()
            return BiometricResult.Done(Unit)
        }

        override suspend fun unlock(): BiometricResult<ByteArray> {
            prompts++
            next?.let {
                if (it == BiometricResult.Invalidated) copy = null
                return it
            }
            return copy?.let { BiometricResult.Done(it.copyOf()) } ?: BiometricResult.Failed
        }

        override fun disable() {
            copy = null
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pin = "246810"
    private val sensor = FakeBiometrics()
    private lateinit var core: Core
    private lateinit var biometrics: BiometricsViewModel

    @Before
    fun start() = Dispatchers.setMain(dispatcher)

    @After
    fun stop() = Dispatchers.resetMain()

    /** Sets up from scratch, as a new user does, with the view model watching. */
    private fun TestScope.setUp() {
        core = phone.start(dispatcher)
        biometrics = BiometricsViewModel(core, sensor)
        advanceUntilIdle()
        core.post { assertIs<SetupResult.Ready>(beginSetup(pin)) }
        advanceUntilIdle()
        core.post { finishSetup(Currency.forCode("GBP")!!) }
        advanceUntilIdle()
    }

    private fun TestScope.lock() {
        core.post { lock() }
        advanceUntilIdle()
    }

    @Test
    fun itIsOfferedOnceAfterSetup() = runTest(dispatcher) {
        setUp()
        assertTrue(biometrics.ui.value.offer)
        biometrics.notNow()
        assertFalse(biometrics.ui.value.offer)
        // Unlocking with the PIN later is not a reason to ask again.
        lock()
        core.run { unlock(pin) }
        advanceUntilIdle()
        assertFalse(biometrics.ui.value.offer)
    }

    @Test
    fun notOfferedOnAPhoneWithoutIt() = runTest(dispatcher) {
        sensor.available = false
        setUp()
        assertFalse(biometrics.ui.value.offer)
        assertFalse(biometrics.ui.value.available)
    }

    @Test
    fun turnedOnTheLockScreenAsksOnceAndUnlocks() = runTest(dispatcher) {
        setUp()
        biometrics.enable()
        advanceUntilIdle()
        assertTrue(biometrics.ui.value.enabled)
        lock()

        biometrics.lockScreenShown()
        advanceUntilIdle()
        assertEquals(SessionState.Unlocked, core.state.value)
        assertEquals("GBP", core.dataset.value?.baseCurrency?.code)
    }

    @Test
    fun choosingThePinLeavesItLockedAndAsksOnlyOncePerLock() = runTest(dispatcher) {
        setUp()
        biometrics.enable()
        advanceUntilIdle()
        lock()
        sensor.next = BiometricResult.Cancelled
        val before = sensor.prompts
        biometrics.lockScreenShown()
        advanceUntilIdle()
        biometrics.lockScreenShown()
        advanceUntilIdle()
        assertEquals(before + 1, sensor.prompts, "the screen returning to the front does not ask again")
        assertIs<SessionState.Locked>(core.state.value)
        assertNull(biometrics.ui.value.message)
        assertIs<UnlockResult.Success>(core.run { unlock(pin) }, "the PIN still works")
    }

    @Test
    fun changedFingerprintsTurnItOff() = runTest(dispatcher) {
        setUp()
        biometrics.enable()
        advanceUntilIdle()
        lock()
        sensor.next = BiometricResult.Invalidated
        biometrics.unlock()
        advanceUntilIdle()
        assertEquals(BiometricMessage.CHANGED, biometrics.ui.value.message)
        assertFalse(biometrics.ui.value.enabled)
        assertIs<SessionState.Locked>(core.state.value)
    }

    @Test
    fun aCopyThatNoLongerOpensTheDataIsTurnedOff() = runTest(dispatcher) {
        setUp()
        biometrics.enable()
        advanceUntilIdle()
        lock()
        sensor.copy = ByteArray(32) { 9 }
        biometrics.unlock()
        advanceUntilIdle()
        assertEquals(BiometricMessage.STOPPED_WORKING, biometrics.ui.value.message)
        assertFalse(biometrics.ui.value.enabled)
        assertIs<SessionState.Locked>(core.state.value)
    }

    @Test
    fun itCanBeTurnedOff() = runTest(dispatcher) {
        setUp()
        biometrics.enable()
        advanceUntilIdle()
        biometrics.disable()
        assertFalse(biometrics.ui.value.enabled)
        lock()
        val before = sensor.prompts
        biometrics.lockScreenShown()
        advanceUntilIdle()
        assertEquals(before, sensor.prompts, "nothing to ask for")
    }
}
