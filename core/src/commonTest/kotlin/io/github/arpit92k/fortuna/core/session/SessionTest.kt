package io.github.arpit92k.fortuna.core.session

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.keys.FakeClock
import io.github.arpit92k.fortuna.core.keys.FakeDeviceKey
import io.github.arpit92k.fortuna.core.keys.KeyGuard
import io.github.arpit92k.fortuna.core.keys.RecoveryResult
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.ports.AppLifecycle
import io.github.arpit92k.fortuna.core.ports.LifecycleListener
import io.github.arpit92k.fortuna.core.ports.OkioPrivateFiles
import io.github.arpit92k.fortuna.core.recording.chooseBaseCurrency
import io.github.arpit92k.fortuna.core.store.DocumentStore
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SessionTest {
    private val crypto = PlatformCrypto
    private val clock = FakeClock()
    private val disk = OkioPrivateFiles(FakeFileSystem(), "/data".toPath())
    private val deviceKey = FakeDeviceKey()
    private val pounds = Currency.forCode("GBP")!!
    private val pin = "246810"

    /** The app's lifecycle, moved by hand. */
    private class FakeLifecycle : AppLifecycle {
        lateinit var listener: LifecycleListener

        override fun observe(listener: LifecycleListener) {
            this.listener = listener
        }
    }

    private val lifecycle = FakeLifecycle()

    private fun session() =
        Session(disk, crypto, deviceKey, clock, pinIterations = { 1_000 }, phraseIterations = 1_000).also { it.start(lifecycle) }

    private fun setUp(): Session = session().also {
        assertIs<SetupResult.Ready>(it.beginSetup(pin))
        it.finishSetup(pounds)
    }

    // ---- States ----

    @Test
    fun aNewDeviceHasNoData() {
        assertEquals(SessionState.NoData, session().state.value)
    }

    @Test
    fun aFinishedSetupStartsLockedNextTime() {
        setUp()
        assertEquals(SessionState.Locked(0.seconds), session().state.value)
    }

    // Decided: nothing is saved until setup finishes.
    @Test
    fun anInterruptedSetupLeavesNoData() {
        session().beginSetup(pin)
        assertEquals(SessionState.NoData, session().state.value, "the app was closed before the base currency was chosen")
    }

    @Test
    fun cancellingSetupReturnsToNoData() {
        val app = session()
        app.beginSetup(pin)
        app.cancelSetup()
        assertEquals(SessionState.NoData, app.state.value)
    }

    @Test
    fun thePhraseIsConfirmedWithSomeOfItsWords() {
        val app = session()
        app.beginSetup(pin)
        val words = assertIs<SessionState.SettingUp>(app.state.value).phrase.words
        assertTrue(app.phraseConfirmed(mapOf(1 to words[0], 12 to " ${words[11].uppercase()} ")))
        assertFalse(app.phraseConfirmed(mapOf(1 to words[0], 12 to "wrong")))
        assertFalse(app.phraseConfirmed(emptyMap()))
        assertFalse(app.phraseConfirmed(mapOf(13 to "zoo")))
    }

    // Decided: a store left without a lock state is removed on start.
    @Test
    fun aStoreWithoutALockStateIsRemovedOnStart() {
        val store = DocumentStore(disk, crypto)
        store.create(crypto.randomBytes(Crypto.KEY_SIZE), Dataset.new())
        assertTrue(disk.exists(DocumentStore.MAIN))

        assertEquals(SessionState.NoData, session().state.value)
        assertFalse(disk.exists(DocumentStore.MAIN))
    }

    @Test
    fun wrongPinsShowTheWaitOnTheLockScreen() {
        setUp()
        val app = session()
        repeat(3) { app.unlock("000000") }
        assertEquals(SessionState.Locked(1.minutes), app.state.value)
        clock.advance(25.seconds)
        app.refreshWait()
        assertEquals(SessionState.Locked(35.seconds), app.state.value)
    }

    @Test
    fun aForgottenPinIsRecoveredWithThePhrase() {
        val app = session()
        app.beginSetup(pin)
        val phrase = assertIs<SessionState.SettingUp>(app.state.value).phrase
        app.finishSetup(pounds)

        val later = session()
        assertIs<RecoveryResult.Success>(later.recover(phrase, "135791"))
        assertEquals(SessionState.Unlocked, later.state.value)
        assertEquals(pounds, later.dataset.value!!.baseCurrency)
    }

    // From the plan: no use case is reachable while locked.
    @Test
    fun noUseCaseIsReachableWhileLocked() {
        setUp()
        val app = session()
        assertNull(app.dataset.value)
        assertFailsWith<IllegalStateException> { app.record { d, _ -> d.chooseBaseCurrency(pounds) } }
        assertFailsWith<IllegalStateException> { app.read { d, _ -> d.sources } }
        assertFailsWith<IllegalStateException> { app.prepareBackup() }

        assertIs<UnlockResult.Success>(app.unlock(pin))
        app.read { d, _ -> d.sources }
    }

    @Test
    fun lockingForgetsTheData() {
        val app = setUp()
        app.lock()
        assertIs<SessionState.Locked>(app.state.value)
        assertNull(app.dataset.value)
    }

    // ---- Auto-lock: from the plan, the grace periods run against a virtual clock ----

    @Test
    fun theAppLocksAMinuteAfterLeavingTheForeground() {
        val app = setUp()
        lifecycle.listener.onLeftForeground()
        clock.advance(59.seconds)
        lifecycle.listener.onReturnedToForeground()
        assertEquals(SessionState.Unlocked, app.state.value, "back within the minute")

        lifecycle.listener.onLeftForeground()
        clock.advance(60.seconds)
        lifecycle.listener.onReturnedToForeground()
        assertIs<SessionState.Locked>(app.state.value)
    }

    @Test
    fun enteringAValueEarnsTwoMinutes() {
        val app = setUp()
        app.enteringValue = true
        lifecycle.listener.onLeftForeground()
        clock.advance(119.seconds)
        lifecycle.listener.onReturnedToForeground()
        assertEquals(SessionState.Unlocked, app.state.value, "looking up a balance in another app")

        lifecycle.listener.onLeftForeground()
        clock.advance(120.seconds)
        lifecycle.listener.onReturnedToForeground()
        assertIs<SessionState.Locked>(app.state.value)
    }

    @Test
    fun theGracePeriodIsTheOneWhenTheAppWasLeft() {
        val app = setUp()
        app.enteringValue = true
        lifecycle.listener.onLeftForeground()
        app.enteringValue = false
        clock.advance(90.seconds)
        lifecycle.listener.onReturnedToForeground()
        assertEquals(SessionState.Unlocked, app.state.value)
    }

    @Test
    fun aTimerInTheBackgroundLocksTheAppToo() {
        val app = setUp()
        lifecycle.listener.onLeftForeground()
        clock.advance(30.seconds)
        app.checkAutoLock()
        assertEquals(SessionState.Unlocked, app.state.value)
        clock.advance(30.seconds)
        app.checkAutoLock()
        assertIs<SessionState.Locked>(app.state.value)
    }

    @Test
    fun aDeviceRestartAlwaysLocks() {
        val app = setUp()
        lifecycle.listener.onLeftForeground()
        clock.restartDevice()
        app.checkAutoLock()
        assertIs<SessionState.Locked>(app.state.value)
    }

    @Test
    fun theGracePeriodsAreAsDecided() {
        assertEquals(1.minutes, Session.gracePeriod(enteringValue = false))
        assertEquals(2.minutes, Session.gracePeriod(enteringValue = true))
    }

    @Test
    fun theLockStateMarksThatDataExists() {
        setUp()
        assertTrue(disk.exists(KeyGuard.LOCK_FILE))
        assertTrue(disk.exists(DocumentStore.MAIN))
    }

    // ---- Biometric unlock ----

    @Test
    fun theDataKeyGivenForBiometricUnlockOpensTheAppNextTime() {
        val key = setUp().dataKeyForBiometricUnlock()
        val app = session()
        assertEquals(BiometricUnlockResult.Success, app.unlockWithBiometricKey(key.copyOf()))
        assertEquals(SessionState.Unlocked, app.state.value)
        assertEquals("GBP", app.dataset.value?.baseCurrency?.code)
    }

    @Test
    fun aWrongBiometricKeyChangesNothingAndCostsNoPinAttempt() {
        setUp()
        val app = session()
        assertEquals(BiometricUnlockResult.WrongKey, app.unlockWithBiometricKey(ByteArray(32) { 7 }))
        assertEquals(SessionState.Locked(0.seconds), app.state.value, "no wait: it was not a PIN")
        assertIs<UnlockResult.Success>(app.unlock(pin))
    }

    @Test
    fun theDataKeyIsOnlyGivenWhileUnlocked() {
        setUp()
        assertFailsWith<IllegalStateException> { session().dataKeyForBiometricUnlock() }
    }
}
