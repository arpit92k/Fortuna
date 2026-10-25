package io.github.arpit92k.fortuna.core.keys

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException
import io.github.arpit92k.fortuna.core.file.KeyDerivation
import io.github.arpit92k.fortuna.core.file.KeyDerivationAlgorithm
import io.github.arpit92k.fortuna.core.file.LockedKey
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase
import io.github.arpit92k.fortuna.core.ports.Clock
import io.github.arpit92k.fortuna.core.ports.DeviceKey
import io.github.arpit92k.fortuna.core.ports.DeviceKeyException
import io.github.arpit92k.fortuna.core.ports.PrivateFiles
import io.github.arpit92k.fortuna.core.ports.finishInterruptedReplace
import io.github.arpit92k.fortuna.core.ports.now
import io.github.arpit92k.fortuna.core.ports.replaceSafely
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Guards the data key: sets up the PIN and the recovery phrase, unlocks with
 * the PIN behind the growing delay, and recovers with the phrase.
 *
 * The data key is random and never shown. It is kept in two locked copies in
 * the lock state: one opened by the PIN on this device only, and one opened by
 * the recovery phrase anywhere.
 *
 * Setup comes in two parts, because nothing is saved until setup finishes:
 * [beginSetup] makes the keys and the phrase in memory, and [finishSetup]
 * writes the lock state once the store has been written. The lock state is
 * what marks that data exists, so it is always written last.
 */
class KeyGuard(
    private val files: PrivateFiles,
    private val crypto: Crypto,
    private val deviceKey: DeviceKey,
    private val clock: Clock,
    /** The PIN's rounds, chosen whenever a PIN is set. Tests pass a small fixed number. */
    private val pinIterations: () -> Int = { calibratePinIterations(crypto, clock) },
    private val phraseIterations: Int = PHRASE_ITERATIONS,
) {
    /** Tidies an interrupted write of the lock state. Call once on start. */
    fun recover() = files.finishInterruptedReplace(LOCK_FILE)

    /** Whether setup has finished on this device: the lock state exists. */
    fun exists(): Boolean = files.exists(LOCK_FILE)

    /** Makes the data key and recovery phrase for a new setup, in memory only. */
    fun beginSetup(pin: String): SetupResult {
        pinProblem(pin)?.let { return SetupResult.InvalidPin(it) }
        val dataKey = crypto.randomBytes(Crypto.KEY_SIZE)
        val phrase = RecoveryPhrase.fromSecret(crypto.randomBytes(RecoveryPhrase.SECRET_SIZE), crypto)
        val state = LockState(lockWithPin(pin, dataKey), lockWithPhrase(phrase, dataKey, crypto, phraseIterations), 0, null)
        return SetupResult.Ready(PendingSetup(dataKey, phrase, state))
    }

    /** Saves the lock state of a setup whose phrase has been confirmed and whose store has been written. */
    fun finishSetup(setup: PendingSetup) {
        check(!exists()) { "Setup has already finished on this device" }
        save(setup.lockState)
    }

    /**
     * Prepares the lock state for data restored from a backup: [newPin] for
     * this device, and the backup's own copy of the data key locked by the
     * phrase. Nothing is saved until [finishSetup], after the store is written.
     */
    fun beginRestore(phrase: RecoveryPhrase, phraseCopy: LockedKey, dataKey: ByteArray, newPin: String): SetupResult {
        pinProblem(newPin)?.let { return SetupResult.InvalidPin(it) }
        val state = LockState(lockWithPin(newPin, dataKey), phraseCopy, 0, null)
        return SetupResult.Ready(PendingSetup(dataKey, phrase, state))
    }

    /** The data key locked by the recovery phrase, for a backup's header; null before setup. */
    fun phraseCopy(): LockedKey? = load()?.phrase

    /** How long until the next PIN may be tried; zero if it may be tried now. */
    fun waitRemaining(): Duration = load()?.let { remainingWait(it).second } ?: Duration.ZERO

    /**
     * Unlocks with [pin], giving the data key.
     *
     * During a wait the PIN is not even checked. Otherwise the attempt is
     * counted and saved before the PIN is checked, so closing the app in the
     * middle cannot avoid it; a right PIN then clears the count.
     */
    fun unlock(pin: String): UnlockResult {
        val loaded = load() ?: return UnlockResult.NoLockState
        val (state, remaining) = remainingWait(loaded)
        if (remaining > Duration.ZERO) return UnlockResult.MustWait(remaining)
        pinProblem(pin)?.let { return UnlockResult.InvalidPin(it) }

        val attempt = state.copy(failedAttempts = state.failedAttempts + 1, lastFailure = clock.now())
        save(attempt)

        val sealed = try {
            deviceKey.unwrap(state.pin.wrapped)
        } catch (e: DeviceKeyException) {
            return UnlockResult.NotThisDevice
        }
        val pinKey = crypto.deriveKey(pin.encodeToByteArray(), state.pin.salt, state.pin.iterations)
        val dataKey = try {
            crypto.decrypt(pinKey, state.pin.nonce, sealed, PIN_CONTEXT)
        } catch (e: DecryptionFailedException) {
            return UnlockResult.WrongPin(attempt.failedAttempts, waitAfter(attempt.failedAttempts))
        }
        save(state.copy(failedAttempts = 0, lastFailure = null))
        return UnlockResult.Success(dataKey)
    }

    /**
     * Gets back in with the recovery phrase and sets [newPin]. The phrase and
     * the data key stay the same; the count of wrong PINs is cleared. A wrong
     * phrase changes nothing. This also works on a new device, since the
     * phrase's copy is not tied to one.
     */
    fun recoverWithPhrase(phrase: RecoveryPhrase, newPin: String): RecoveryResult {
        pinProblem(newPin)?.let { return RecoveryResult.InvalidPin(it) }
        val state = load() ?: return RecoveryResult.NoLockState
        val dataKey = try {
            openWithPhrase(state.phrase, phrase, crypto)
        } catch (e: DecryptionFailedException) {
            return RecoveryResult.WrongPhrase
        }
        save(LockState(lockWithPin(newPin, dataKey), state.phrase, 0, null))
        return RecoveryResult.Success(dataKey)
    }

    // The wait still to run, and the state to use, which records a new start of the wait after a restart.
    private fun remainingWait(state: LockState): Pair<LockState, Duration> {
        val last = state.lastFailure ?: return state to Duration.ZERO
        val wait = waitAfter(state.failedAttempts)
        if (wait == Duration.ZERO) return state to Duration.ZERO
        val now = clock.now()
        if (now.startUp != last.startUp) {
            // The since-start clock began again at zero, so the wait begins again too.
            val restarted = state.copy(lastFailure = now)
            save(restarted)
            return restarted to wait
        }
        val elapsed = (now.sinceStartMillis - last.sinceStartMillis).milliseconds
        return state to (wait - elapsed).coerceIn(Duration.ZERO, wait)
    }

    private fun lockWithPin(pin: String, dataKey: ByteArray): PinLockedKey {
        val iterations = pinIterations()
        val salt = crypto.randomBytes(SALT_SIZE)
        val nonce = crypto.randomBytes(Crypto.NONCE_SIZE)
        val pinKey = crypto.deriveKey(pin.encodeToByteArray(), salt, iterations)
        val sealed = crypto.encrypt(pinKey, nonce, dataKey, PIN_CONTEXT)
        return PinLockedKey(iterations, salt, nonce, deviceKey.wrap(sealed))
    }

    private fun load(): LockState? =
        if (files.exists(LOCK_FILE)) LockStateJson.decode(files.read(LOCK_FILE).decodeToString()) else null

    private fun save(state: LockState) = files.replaceSafely(LOCK_FILE, LockStateJson.encode(state).encodeToByteArray())

    companion object {
        const val LOCK_FILE: String = "fortuna.lock"
        private const val SALT_SIZE = 16
        private val PIN_CONTEXT = "Fortuna PIN copy of the data key, v1".encodeToByteArray()
    }
}

// ---- The recovery phrase's copy, shared with backups (step 12) ----

private val PHRASE_CONTEXT = "Fortuna phrase copy of the data key, v1".encodeToByteArray()

/**
 * Locks [dataKey] with the recovery phrase: a key derived with PBKDF2 from the
 * phrase's 16 secret bytes, never the typed words, encrypts the data key.
 * PBKDF2 takes its secret as text on some platforms, so the bytes are fed in
 * as 32 lowercase hex characters.
 */
fun lockWithPhrase(phrase: RecoveryPhrase, dataKey: ByteArray, crypto: Crypto, iterations: Int = PHRASE_ITERATIONS): LockedKey {
    val salt = crypto.randomBytes(16)
    val nonce = crypto.randomBytes(Crypto.NONCE_SIZE)
    val key = crypto.deriveKey(phraseInput(phrase), salt, iterations)
    val sealed = crypto.encrypt(key, nonce, dataKey, PHRASE_CONTEXT)
    return LockedKey(KeyDerivation(KeyDerivationAlgorithm.PBKDF2_HMAC_SHA256, iterations), salt, nonce, sealed)
}

/**
 * Opens a copy of the data key locked by [lockWithPhrase].
 *
 * @throws DecryptionFailedException if [phrase] is not the one it was locked with.
 */
fun openWithPhrase(locked: LockedKey, phrase: RecoveryPhrase, crypto: Crypto): ByteArray {
    val key = when (locked.derivation.algorithm) {
        KeyDerivationAlgorithm.PBKDF2_HMAC_SHA256 -> crypto.deriveKey(phraseInput(phrase), locked.salt, locked.derivation.iterations)
    }
    return crypto.decrypt(key, locked.nonce, locked.sealedKey, PHRASE_CONTEXT)
}

/** The phrase's 16 secret bytes as 32 lowercase hex characters: what PBKDF2 is given. */
internal fun phraseInput(phrase: RecoveryPhrase): ByteArray {
    val digits = "0123456789abcdef"
    return phrase.toSecret().joinToString("") { byte ->
        val b = byte.toInt() and 0xFF
        "${digits[b shr 4]}${digits[b and 0xF]}"
    }.encodeToByteArray()
}

// ---- Results ----

sealed interface SetupResult {
    data class Ready(val setup: PendingSetup) : SetupResult

    data class InvalidPin(val problem: PinProblem) : SetupResult
}

/** A setup made in memory: the data key for the store, the phrase to show, and the lock state to save last. */
class PendingSetup internal constructor(
    val dataKey: ByteArray,
    val phrase: RecoveryPhrase,
    internal val lockState: LockState,
) {
    override fun toString(): String = "PendingSetup"
}

sealed interface UnlockResult {
    class Success(val dataKey: ByteArray) : UnlockResult

    /** Wrong. [wait] is how long until the next attempt, perhaps none. */
    data class WrongPin(val failedAttempts: Int, val wait: Duration) : UnlockResult

    /** A wait is running, so the PIN was not checked. */
    data class MustWait(val remaining: Duration) : UnlockResult

    /** The PIN's copy was made on another device. Use the recovery phrase. */
    data object NotThisDevice : UnlockResult

    data class InvalidPin(val problem: PinProblem) : UnlockResult

    data object NoLockState : UnlockResult

    /**
     * The PIN was right, but the data on this phone could not be read: it is
     * damaged, or [newerApp] a newer version of the app wrote it. Nothing was
     * changed, and the app stays locked.
     */
    data class DataUnreadable(val newerApp: Boolean) : UnlockResult
}

sealed interface RecoveryResult {
    class Success(val dataKey: ByteArray) : RecoveryResult

    data object WrongPhrase : RecoveryResult

    data class InvalidPin(val problem: PinProblem) : RecoveryResult

    data object NoLockState : RecoveryResult

    /** The phrase was right and the new PIN is set, but the data could not be read, as for [UnlockResult.DataUnreadable]. */
    data class DataUnreadable(val newerApp: Boolean) : RecoveryResult
}
