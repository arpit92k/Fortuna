package io.github.arpit92k.fortuna.core.session

import io.github.arpit92k.fortuna.core.backup.RestoreResult
import io.github.arpit92k.fortuna.core.backup.restoreFromBackup
import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.DatasetFormatException
import io.github.arpit92k.fortuna.core.domain.UnsupportedFormatVersionException
import io.github.arpit92k.fortuna.core.file.DamagedFileException
import io.github.arpit92k.fortuna.core.file.NotAnEncryptedFileException
import io.github.arpit92k.fortuna.core.file.UnsupportedFileVersionException
import io.github.arpit92k.fortuna.core.keys.KeyGuard
import io.github.arpit92k.fortuna.core.keys.PHRASE_ITERATIONS
import io.github.arpit92k.fortuna.core.keys.PendingSetup
import io.github.arpit92k.fortuna.core.keys.RecoveryResult
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.keys.calibratePinIterations
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase
import io.github.arpit92k.fortuna.core.ports.AppLifecycle
import io.github.arpit92k.fortuna.core.ports.Clock
import io.github.arpit92k.fortuna.core.ports.DeviceKey
import io.github.arpit92k.fortuna.core.ports.LifecycleListener
import io.github.arpit92k.fortuna.core.ports.Moment
import io.github.arpit92k.fortuna.core.ports.PrivateFiles
import io.github.arpit92k.fortuna.core.ports.now
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.chooseBaseCurrency
import io.github.arpit92k.fortuna.core.store.DocumentStore
import io.github.arpit92k.fortuna.core.store.PreparedBackup
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import okio.IOException

/** Where the app is. The interface shows the screens that fit. */
sealed interface SessionState {
    /** Nothing on this device: offer a new start or a restore. */
    data object NoData : SessionState

    /** Setup is under way, held in memory: show [phrase], ask some words back, then the base currency. */
    data class SettingUp(val phrase: RecoveryPhrase) : SessionState

    /** Data exists and is locked. [wait] is how long until the next PIN may be tried. */
    data class Locked(val wait: Duration) : SessionState

    /** Unlocked: the dataset is open. */
    data object Unlocked : SessionState
}

/**
 * The one entry point for the interface. It joins the store and the keys,
 * runs the app's states, offers the use cases for recording, insights and
 * backup while unlocked, and locks the app when it has been in the
 * background too long.
 *
 * Calls must come one at a time, as they do from a single screen.
 */
class Session(
    files: PrivateFiles,
    private val crypto: Crypto,
    deviceKey: DeviceKey,
    private val clock: Clock,
    pinIterations: () -> Int = { calibratePinIterations(crypto, clock) },
    phraseIterations: Int = PHRASE_ITERATIONS,
) : LifecycleListener {
    private val store = DocumentStore(files, crypto)
    private val keys = KeyGuard(files, crypto, deviceKey, clock, pinIterations, phraseIterations)
    private val mutableState = MutableStateFlow<SessionState>(SessionState.NoData)
    private var pendingSetup: PendingSetup? = null
    private var leftForegroundAt: Moment? = null
    private var leftWhileEnteringValue = false

    /** Whether the user is on a screen where a value is being entered (S-11 to S-14), which earns a longer grace period. */
    var enteringValue: Boolean = false

    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    /** The open dataset, or null while not unlocked. */
    val dataset: StateFlow<Dataset?> = store.dataset

    // ---- Start ----

    /**
     * Call once when the app starts. Tidies up after any interrupted write,
     * removes a store left without a lock state by an unfinished setup or
     * restore, and starts locked if there is data.
     */
    fun start(lifecycle: AppLifecycle? = null) {
        store.recover()
        keys.recover()
        if (!keys.exists() && store.exists()) store.deleteUnfinished()
        mutableState.value = if (keys.exists()) SessionState.Locked(keys.waitRemaining()) else SessionState.NoData
        lifecycle?.observe(this)
    }

    // ---- Setup ----

    /** Starts setup with [pin]. Nothing is saved until [finishSetup]. */
    fun beginSetup(pin: String): SetupResult {
        check(state.value == SessionState.NoData) { "Setup starts only when there is no data" }
        val result = keys.beginSetup(pin)
        if (result is SetupResult.Ready) {
            pendingSetup = result.setup
            mutableState.value = SessionState.SettingUp(result.setup.phrase)
        }
        return result
    }

    /**
     * Whether the words given at [answers] (positions counting from 1) match
     * the phrase being set up, ignoring case and surrounding spaces. The
     * screen asks for some words back before setup can go on.
     */
    fun phraseConfirmed(answers: Map<Int, String>): Boolean {
        val words = checkNotNull(pendingSetup) { "No setup under way" }.phrase.words
        return answers.isNotEmpty() && answers.all { (position, word) ->
            position in 1..words.size && words[position - 1] == word.trim().lowercase()
        }
    }

    /**
     * Finishes setup with the base currency: writes the store, then the lock
     * state, which marks that data now exists. The app is then unlocked.
     */
    fun finishSetup(baseCurrency: Currency) {
        val setup = checkNotNull(pendingSetup) { "No setup under way" }
        val initial = (Dataset.new().chooseBaseCurrency(baseCurrency) as RecordingResult.Success).dataset
        store.create(setup.dataKey, initial)
        keys.finishSetup(setup)
        pendingSetup = null
        mutableState.value = SessionState.Unlocked
    }

    /** Abandons setup. Nothing was saved, so nothing needs undoing. */
    fun cancelSetup() {
        pendingSetup = null
        mutableState.value = SessionState.NoData
    }

    // ---- Restore ----

    /** Restores a backup on a device with no data. On success the app is unlocked. */
    fun restore(file: ByteArray, phrase: RecoveryPhrase, newPin: String): RestoreResult {
        check(state.value == SessionState.NoData) { "Restoring is only for a device with no data" }
        val result = restoreFromBackup(file, phrase, newPin, store, keys, crypto, clock.today())
        if (result == RestoreResult.Success) mutableState.value = SessionState.Unlocked
        return result
    }

    // ---- Locking ----

    fun unlock(pin: String): UnlockResult {
        check(state.value is SessionState.Locked) { "The app is not locked" }
        val result = keys.unlock(pin)
        if (result is UnlockResult.Success) {
            openStore(result.dataKey)?.let { newerApp ->
                mutableState.value = SessionState.Locked(keys.waitRemaining())
                return UnlockResult.DataUnreadable(newerApp)
            }
            mutableState.value = SessionState.Unlocked
        } else {
            mutableState.value = SessionState.Locked(keys.waitRemaining())
        }
        return result
    }

    /** Gets back in with the recovery phrase after a forgotten PIN, and sets [newPin]. */
    fun recover(phrase: RecoveryPhrase, newPin: String): RecoveryResult {
        check(state.value is SessionState.Locked) { "The app is not locked" }
        val result = keys.recoverWithPhrase(phrase, newPin)
        if (result is RecoveryResult.Success) {
            openStore(result.dataKey)?.let { newerApp -> return RecoveryResult.DataUnreadable(newerApp) }
            mutableState.value = SessionState.Unlocked
        }
        return result
    }

    /**
     * Opens the store with [key], then clears the key. Returns null if it
     * opened, or, if it cannot be read, whether a newer app wrote it. A store
     * that cannot be read is left exactly as it is, so a later version of the
     * app, or the user's own copy of the file, can still be tried.
     */
    private fun openStore(key: ByteArray): Boolean? = try {
        store.open(key)
        null
    } catch (e: UnsupportedFileVersionException) {
        true
    } catch (e: UnsupportedFormatVersionException) {
        true
    } catch (e: DecryptionFailedException) {
        false
    } catch (e: DatasetFormatException) {
        false
    } catch (e: NotAnEncryptedFileException) {
        false
    } catch (e: DamagedFileException) {
        false
    } catch (e: IOException) {
        false
    } finally {
        key.fill(0)
    }

    /** Locks now: the data key and the dataset are forgotten. */
    fun lock() {
        if (state.value != SessionState.Unlocked) return
        store.lock()
        mutableState.value = SessionState.Locked(keys.waitRemaining())
    }

    /** Refreshes the wait shown on the lock screen. */
    fun refreshWait() {
        if (state.value is SessionState.Locked) mutableState.value = SessionState.Locked(keys.waitRemaining())
    }

    // ---- Use cases, only while unlocked ----

    /** Applies a recording rule, given today's date, and saves the result. */
    fun record(rule: (dataset: Dataset, today: LocalDate) -> RecordingResult): RecordingResult {
        requireUnlocked()
        val today = clock.today()
        return store.apply { rule(it, today) }
    }

    /** Reads from the open dataset, for insights such as net worth or a source's history. */
    fun <T> read(query: (dataset: Dataset, today: LocalDate) -> T): T {
        requireUnlocked()
        return query(store.dataset.value!!, clock.today())
    }

    /** Makes a backup file, dated today. Call [confirmBackupSaved] once it has been saved. */
    fun prepareBackup(): PreparedBackup {
        requireUnlocked()
        return store.prepareBackup(checkNotNull(keys.phraseCopy()), clock.today())
    }

    fun confirmBackupSaved(backup: PreparedBackup): Boolean {
        requireUnlocked()
        return store.confirmBackupSaved(backup)
    }

    private fun requireUnlocked() = check(state.value == SessionState.Unlocked) { "The app is locked" }

    // ---- Auto-lock ----

    override fun onLeftForeground() {
        leftForegroundAt = clock.now()
        leftWhileEnteringValue = enteringValue
    }

    override fun onReturnedToForeground() {
        checkAutoLock()
        leftForegroundAt = null
    }

    /**
     * Locks the app if it has been in the background longer than its grace
     * period: one minute, or two if a value was being entered. The platform
     * also calls this from a timer while the app is in the background.
     */
    override fun checkAutoLock() {
        val left = leftForegroundAt ?: return
        val now = clock.now()
        val away = if (now.startUp != left.startUp) Duration.INFINITE else (now.sinceStartMillis - left.sinceStartMillis).milliseconds
        if (away >= gracePeriod(leftWhileEnteringValue)) lock()
    }

    companion object {
        /** How long the app may stay in the background before it locks. */
        fun gracePeriod(enteringValue: Boolean): Duration = if (enteringValue) 2.minutes else 1.minutes
    }
}
