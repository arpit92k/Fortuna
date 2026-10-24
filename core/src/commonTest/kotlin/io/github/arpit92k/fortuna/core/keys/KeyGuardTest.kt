package io.github.arpit92k.fortuna.core.keys

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase
import io.github.arpit92k.fortuna.core.ports.Clock
import io.github.arpit92k.fortuna.core.ports.DeviceKey
import io.github.arpit92k.fortuna.core.ports.DeviceKeyException
import io.github.arpit92k.fortuna.core.ports.OkioPrivateFiles
import io.github.arpit92k.fortuna.core.ports.PrivateFiles
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A device key made of a random key held in memory: each instance is a different device. */
class FakeDeviceKey(private val crypto: Crypto = PlatformCrypto) : DeviceKey {
    private val key = crypto.randomBytes(Crypto.KEY_SIZE)

    override fun wrap(secret: ByteArray): ByteArray {
        val nonce = crypto.randomBytes(Crypto.NONCE_SIZE)
        return nonce + crypto.encrypt(key, nonce, secret, ByteArray(0))
    }

    override fun unwrap(wrapped: ByteArray): ByteArray = try {
        crypto.decrypt(key, wrapped.copyOf(Crypto.NONCE_SIZE), wrapped.copyOfRange(Crypto.NONCE_SIZE, wrapped.size), ByteArray(0))
    } catch (e: DecryptionFailedException) {
        throw DeviceKeyException("Not wrapped by this device", e)
    }
}

/** A clock the test moves by hand. */
class FakeClock : Clock {
    var date = LocalDate(2026, 10, 24)
    var startUpCount = 1L
    var millis = 5_000L

    override fun today() = date

    override fun sinceStartMillis() = millis

    override fun startUp() = startUpCount

    fun advance(by: Duration) {
        millis += by.inWholeMilliseconds
    }

    fun restartDevice() {
        startUpCount += 1
        millis = 2_000
    }
}

class KeyGuardTest {
    private val crypto = PlatformCrypto
    private val clock = FakeClock()
    private val deviceKey = FakeDeviceKey()
    private val disk = OkioPrivateFiles(FakeFileSystem(), "/data".toPath())

    private val pin = "246810"
    private val otherPin = "135791"

    // Few rounds, so the tests run quickly. The real numbers are tested on their own below.
    private fun guard(files: PrivateFiles = disk, key: DeviceKey = deviceKey, withCrypto: Crypto = crypto) =
        KeyGuard(files, withCrypto, key, clock, pinIterations = { 1_000 }, phraseIterations = 1_000).also { it.recover() }

    private fun setUp(guard: KeyGuard = guard()): PendingSetup {
        val setup = assertIs<SetupResult.Ready>(guard.beginSetup(pin)).setup
        guard.finishSetup(setup)
        return setup
    }

    private fun savedState(files: PrivateFiles = disk) = LockStateJson.decode(files.read(KeyGuard.LOCK_FILE).decodeToString())

    private fun wrong(guard: KeyGuard): UnlockResult.WrongPin = assertIs<UnlockResult.WrongPin>(guard.unlock(otherPin))

    // ---- Setup ----

    // From the plan: set up, then unlock with the right PIN.
    @Test
    fun setUpThenUnlockWithTheRightPin() {
        val setup = setUp()
        val unlocked = assertIs<UnlockResult.Success>(guard().unlock(pin))
        assertContentEquals(setup.dataKey, unlocked.dataKey)
        assertEquals(RecoveryPhrase.WORD_COUNT, setup.phrase.words.size)
    }

    // Decided: nothing is saved until setup finishes.
    @Test
    fun nothingIsSavedUntilSetupFinishes() {
        val guard = guard()
        val setup = assertIs<SetupResult.Ready>(guard.beginSetup(pin)).setup
        assertFalse(guard.exists())
        for (name in listOf(KeyGuard.LOCK_FILE, "${KeyGuard.LOCK_FILE}.partial", "${KeyGuard.LOCK_FILE}.new")) {
            assertFalse(disk.exists(name), "$name written before setup finished")
        }
        guard.finishSetup(setup)
        assertTrue(guard.exists())
        assertFailsWith<IllegalStateException> { guard.finishSetup(setup) }
    }

    @Test
    fun eachSetupMakesItsOwnKeyAndPhrase() {
        val first = assertIs<SetupResult.Ready>(guard().beginSetup(pin)).setup
        val second = assertIs<SetupResult.Ready>(guard().beginSetup(pin)).setup
        assertFalse(first.dataKey.contentEquals(second.dataKey))
        assertFalse(first.phrase == second.phrase)
    }

    // From the plan: PIN rules, digits only and six or more.
    @Test
    fun aPinIsSixOrMoreDigits() {
        assertEquals(SetupResult.InvalidPin(PinProblem.TOO_SHORT), guard().beginSetup("12345"))
        assertEquals(SetupResult.InvalidPin(PinProblem.TOO_SHORT), guard().beginSetup(""))
        assertEquals(SetupResult.InvalidPin(PinProblem.NOT_DIGITS), guard().beginSetup("12a456"))
        assertEquals(SetupResult.InvalidPin(PinProblem.NOT_DIGITS), guard().beginSetup("123 456"))
        assertEquals(SetupResult.InvalidPin(PinProblem.TOO_LONG), guard().beginSetup("1".repeat(65)))
        assertIs<SetupResult.Ready>(guard().beginSetup("000000"))
        assertIs<SetupResult.Ready>(guard().beginSetup("1234567890"))
    }

    // ---- Wrong PINs and the growing delay ----

    // From the plan: a wrong PIN is refused, the count rises and the wait grows.
    @Test
    fun wrongPinsAreCountedAndTheWaitGrows() {
        setUp()
        val guard = guard()
        assertEquals(UnlockResult.WrongPin(1, Duration.ZERO), wrong(guard))
        assertEquals(UnlockResult.WrongPin(2, Duration.ZERO), wrong(guard))
        assertEquals(UnlockResult.WrongPin(3, 1.minutes), wrong(guard))

        for ((attempts, wait) in listOf(4 to 5.minutes, 5 to 15.minutes, 6 to 1.hours, 7 to 24.hours, 8 to 24.hours)) {
            assertEquals(UnlockResult.MustWait(waitAfter(attempts - 1)), guard.unlock(otherPin), "during the wait")
            clock.advance(waitAfter(attempts - 1))
            assertEquals(UnlockResult.WrongPin(attempts, wait), wrong(guard))
        }
        assertEquals(8, savedState().failedAttempts)
    }

    @Test
    fun theWaitScheduleIsAsDecided() {
        assertEquals(
            listOf(Duration.ZERO, Duration.ZERO, Duration.ZERO, 1.minutes, 5.minutes, 15.minutes, 1.hours, 24.hours, 24.hours, 24.hours),
            (0..9).map { waitAfter(it) },
        )
    }

    // From the plan: a right PIN during the wait is still refused.
    @Test
    fun aRightPinDuringAWaitIsRefusedWithoutBeingChecked() {
        setUp()
        val guard = guard()
        repeat(3) { wrong(guard) }
        assertEquals(UnlockResult.MustWait(1.minutes), guard.unlock(pin))
        assertEquals(3, savedState().failedAttempts, "the refused attempt was not counted")
        clock.advance(1.minutes)
        assertIs<UnlockResult.Success>(guard.unlock(pin))
    }

    @Test
    fun theWaitCountsDown() {
        setUp()
        val guard = guard()
        repeat(3) { wrong(guard) }
        clock.advance(20.seconds)
        assertEquals(40.seconds, guard.waitRemaining())
        clock.advance(40.seconds)
        assertEquals(Duration.ZERO, guard.waitRemaining())
    }

    @Test
    fun aRightPinClearsTheCount() {
        setUp()
        val guard = guard()
        repeat(2) { wrong(guard) }
        assertIs<UnlockResult.Success>(guard.unlock(pin))
        assertEquals(0, savedState().failedAttempts)
        assertEquals(UnlockResult.WrongPin(1, Duration.ZERO), wrong(guard))
    }

    // From the plan: the count and the wait survive a restart.
    @Test
    fun theCountAndWaitSurviveClosingTheApp() {
        setUp()
        repeat(3) { wrong(guard()) }
        clock.advance(30.seconds)
        val reopened = guard()
        assertEquals(30.seconds, reopened.waitRemaining())
        assertEquals(UnlockResult.MustWait(30.seconds), reopened.unlock(pin))
    }

    // Decided: restarting the device makes the wait begin again; it never shortens it.
    @Test
    fun restartingTheDeviceMakesTheWaitBeginAgain() {
        setUp()
        val guard = guard()
        repeat(3) { wrong(guard) }
        clock.advance(50.seconds)
        clock.restartDevice()
        assertEquals(1.minutes, guard().waitRemaining(), "the full minute again, not the 10 seconds that were left")
        clock.advance(59.seconds)
        assertEquals(1.seconds, guard().waitRemaining())
    }

    // Decided: changing the device's date and time does not skip a wait.
    @Test
    fun changingTheDateDoesNotSkipAWait() {
        setUp()
        val guard = guard()
        repeat(3) { wrong(guard) }
        clock.date = LocalDate(2030, 1, 1)
        assertEquals(UnlockResult.MustWait(1.minutes), guard.unlock(pin))
    }

    // From the plan: the count is saved before a PIN is checked.
    @Test
    fun anAttemptIsCountedBeforeThePinIsChecked() {
        setUp()
        class Crash : RuntimeException()
        val crashing = object : Crypto by crypto {
            override fun deriveKey(secret: ByteArray, salt: ByteArray, iterations: Int, size: Int): ByteArray = throw Crash()
        }
        assertFailsWith<Crash> { guard(withCrypto = crashing).unlock(pin) }
        assertEquals(1, savedState().failedAttempts, "closing the app mid-check did not avoid the count")
        assertIs<UnlockResult.Success>(guard().unlock(pin))
    }

    @Test
    fun aMalformedPinIsRefusedWithoutBeingCounted() {
        setUp()
        assertEquals(UnlockResult.InvalidPin(PinProblem.NOT_DIGITS), guard().unlock("abcdef"))
        assertEquals(0, savedState().failedAttempts)
    }

    @Test
    fun unlockingBeforeSetupFindsNothing() {
        assertEquals(UnlockResult.NoLockState, guard().unlock(pin))
        assertEquals(Duration.ZERO, guard().waitRemaining())
    }

    // ---- Recovery with the phrase ----

    // From the plan: recovery sets a new PIN, leaves the phrase and the data unchanged, and clears the count.
    @Test
    fun recoveryWithThePhraseSetsANewPin() {
        val setup = setUp()
        val guard = guard()
        repeat(5) {
            wrong(guard)
            clock.advance(1.hours)
        }
        val phraseCopyBefore = savedState().phrase

        val recovered = assertIs<RecoveryResult.Success>(guard.recoverWithPhrase(setup.phrase, otherPin))
        assertContentEquals(setup.dataKey, recovered.dataKey)
        assertEquals(phraseCopyBefore, savedState().phrase, "the phrase's copy is unchanged")
        assertEquals(0, savedState().failedAttempts)

        assertIs<UnlockResult.WrongPin>(guard.unlock(pin), "the old PIN no longer works")
        assertContentEquals(setup.dataKey, assertIs<UnlockResult.Success>(guard.unlock(otherPin)).dataKey)
    }

    // From the plan: a wrong phrase changes nothing.
    @Test
    fun aWrongPhraseChangesNothing() {
        setUp()
        val before = disk.read(KeyGuard.LOCK_FILE)
        val somebodyElsesPhrase = RecoveryPhrase.fromSecret(crypto.randomBytes(16), crypto)
        assertEquals(RecoveryResult.WrongPhrase, guard().recoverWithPhrase(somebodyElsesPhrase, otherPin))
        assertContentEquals(before, disk.read(KeyGuard.LOCK_FILE))
    }

    @Test
    fun theRecoveryPhraseNeverWaits() {
        val setup = setUp()
        val guard = guard()
        repeat(3) { wrong(guard) }
        assertIs<RecoveryResult.Success>(guard.recoverWithPhrase(setup.phrase, otherPin))
    }

    @Test
    fun aNewPinFromRecoveryMustBeValid() {
        val setup = setUp()
        assertEquals(RecoveryResult.InvalidPin(PinProblem.TOO_SHORT), guard().recoverWithPhrase(setup.phrase, "123"))
    }

    // ---- Another device ----

    // From the plan: a lock state copied to a device with a different device key cannot be opened with the PIN.
    @Test
    fun aLockStateCopiedToAnotherDeviceDoesNotOpenWithThePin() {
        val setup = setUp()
        val otherDisk = OkioPrivateFiles(FakeFileSystem(), "/data".toPath())
        otherDisk.write(KeyGuard.LOCK_FILE, disk.read(KeyGuard.LOCK_FILE))
        val otherDevice = guard(otherDisk, FakeDeviceKey())

        assertEquals(UnlockResult.NotThisDevice, otherDevice.unlock(pin))

        // The phrase's copy is not tied to a device, so the phrase still gets in there.
        assertIs<RecoveryResult.Success>(otherDevice.recoverWithPhrase(setup.phrase, otherPin))
        assertContentEquals(setup.dataKey, assertIs<UnlockResult.Success>(otherDevice.unlock(otherPin)).dataKey)
    }

    // ---- The numbers ----

    /** Crypto whose key derivation takes [millisPerTrial] on the fake clock, and nothing else. */
    private fun slowCrypto(millisPerTrial: Long) = object : Crypto by crypto {
        override fun deriveKey(secret: ByteArray, salt: ByteArray, iterations: Int, size: Int): ByteArray {
            clock.advance(millisPerTrial.milliseconds)
            return ByteArray(size)
        }
    }

    // Decided: the PIN's rounds take about a quarter of a second on the device, never below 100,000.
    @Test
    fun thePinsRoundsAreMeasuredOnTheDevice() {
        assertEquals(125_000, calibratePinIterations(slowCrypto(100), clock), "50,000 rounds in 100 ms")
        assertEquals(625_000, calibratePinIterations(slowCrypto(20), clock), "50,000 rounds in 20 ms")
        assertEquals(100_000, calibratePinIterations(slowCrypto(1_000), clock), "a slow device still gets the floor")
        assertEquals(10_000_000, calibratePinIterations(slowCrypto(0), clock), "an implausibly fast one is capped")
    }

    @Test
    fun theRoundsUsedAreStoredWithEachCopy() {
        setUp(KeyGuard(disk, crypto, deviceKey, clock, pinIterations = { 1_234 }, phraseIterations = 2_000))
        val state = savedState()
        assertEquals(1_234, state.pin.iterations)
        assertEquals(2_000, state.phrase.derivation.iterations)
        assertEquals(100_000, PHRASE_ITERATIONS, "the real phrase rounds")
    }

    // Decided: the phrase's 16 secret bytes are what is fed in, as hex text, not the typed words.
    @Test
    fun thePhraseIsFedInAsItsSecretBytesInHex() {
        val phrase = RecoveryPhrase.fromSecret(ByteArray(16) { (it * 17).toByte() }, crypto)
        assertEquals("00112233445566778899aabbccddeeff", phraseInput(phrase).decodeToString())
    }

    @Test
    fun aPhraseCopyOpensWithTheSamePhraseTypedDifferently() {
        val phrase = RecoveryPhrase.fromSecret(crypto.randomBytes(16), crypto)
        val dataKey = crypto.randomBytes(32)
        val locked = lockWithPhrase(phrase, dataKey, crypto, iterations = 1_000)
        val retyped = assertIs<io.github.arpit92k.fortuna.core.phrase.PhraseParseResult.Success>(
            RecoveryPhrase.parse("  " + phrase.toText().uppercase().replace(" ", "\n ") + " ", crypto),
        ).phrase
        assertContentEquals(dataKey, openWithPhrase(locked, retyped, crypto))
    }

    // ---- The lock state file ----

    @Test
    fun theLockStateSurvivesBeingWrittenAndReadBack() {
        setUp()
        repeat(3) { wrong(guard()) }
        val state = savedState()
        assertEquals(state, LockStateJson.decode(LockStateJson.encode(state)))
        assertEquals(3, state.failedAttempts)
    }

    @Test
    fun anUnknownLockStateVersionIsRefused() {
        val failure = assertFailsWith<UnsupportedLockStateVersionException> { LockStateJson.decode("""{"formatVersion": 2}""") }
        assertEquals(2, failure.version)
        assertFailsWith<LockStateFormatException> { LockStateJson.decode("""{"formatVersion": 1}""") }
        assertFailsWith<LockStateFormatException> { LockStateJson.decode("not json") }
    }

    @Test
    fun aHalfWrittenLockStateIsThrownAwayOnStart() {
        setUp()
        val before = disk.read(KeyGuard.LOCK_FILE)
        disk.write("${KeyGuard.LOCK_FILE}.partial", ByteArray(10))
        guard()
        assertFalse(disk.exists("${KeyGuard.LOCK_FILE}.partial"))
        assertContentEquals(before, disk.read(KeyGuard.LOCK_FILE))
    }

    @Test
    fun theLockStateHoldsNoSecretsInTheClear() {
        val setup = setUp()
        val text = disk.read(KeyGuard.LOCK_FILE).decodeToString()
        assertFalse(pin in text, "the PIN")
        assertFalse(setup.phrase.toText() in text, "the phrase")
        assertFalse(setup.phrase.words.first() in text && setup.phrase.words.last() in text, "the phrase's words")
    }
}
