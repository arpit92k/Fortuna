package io.github.arpit92k.fortuna.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.session.BiometricUnlockResult
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.platform.BiometricResult
import io.github.arpit92k.fortuna.platform.Biometrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why biometric unlock stopped, for the lock screen or settings to say. */
enum class BiometricMessage {
    /** Fingerprints or faces changed, so Android voided the key. */
    CHANGED,

    /** Its copy no longer opens the data. */
    STOPPED_WORKING,

    /** It could not be turned on. */
    NOT_TURNED_ON,
}

data class BiometricsUi(
    /** The phone has a strong biometric set up. */
    val available: Boolean = false,
    val enabled: Boolean = false,
    /** Ask, once, whether to turn it on: after setup or a restore. */
    val offer: Boolean = false,
    val working: Boolean = false,
    val message: BiometricMessage? = null,
    /** On the lock screen: turn biometric unlock on once this unlock with the PIN succeeds. */
    val enableAfterUnlock: Boolean = false,
) {
    /** The lock screen offers to turn it on next time: the phone can, and it is not on. */
    val canOfferOnLockScreen: Boolean get() = available && !enabled
}

/**
 * Biometric unlock (UF-02): a shortcut beside the PIN, never instead of it.
 * The lock screen asks for a biometric check once each time it appears; if
 * the user chooses the PIN, the pad is there, and a button asks again.
 */
class BiometricsViewModel(private val core: Core, private val biometrics: Biometrics) : ViewModel() {
    private val mutableUi = MutableStateFlow(BiometricsUi())
    val ui: StateFlow<BiometricsUi> = mutableUi.asStateFlow()

    /** Whether the lock screen has asked by itself since the app last locked. */
    private var askedThisLock = false

    init {
        refresh()
        viewModelScope.launch {
            var previous: SessionState? = null
            core.state.collect { state ->
                if (state == SessionState.Unlocked) {
                    askedThisLock = false
                    // The user has been in the lock screen, which said why it was turned off.
                    if (biometrics.turnedOffByChange) biometrics.acknowledgeChange()
                    if (mutableUi.value.message == BiometricMessage.CHANGED) mutableUi.update { it.copy(message = null) }
                    // Chosen on the lock screen: turned on now there is a data key to lock away.
                    if (mutableUi.value.enableAfterUnlock) {
                        mutableUi.update { it.copy(enableAfterUnlock = false) }
                        if (biometrics.available() && !biometrics.enabled()) enable()
                    }
                    // Just set up or restored: offer it once, if the phone can.
                    val fresh = previous == SessionState.NoData || previous is SessionState.SettingUp
                    if (fresh && biometrics.available() && !biometrics.enabled()) mutableUi.update { it.copy(offer = true) }
                }
                previous = state
                refresh()
            }
        }
    }

    /**
     * Fingerprints may have been set up or removed while the app was away.
     * If they changed, biometric unlock was turned off, and the lock screen
     * says so until the user is in.
     */
    fun refresh() {
        biometrics.checkStillValid()
        mutableUi.update {
            it.copy(
                available = biometrics.available(),
                enabled = biometrics.enabled(),
                message = if (biometrics.turnedOffByChange) BiometricMessage.CHANGED else it.message,
            )
        }
    }

    /** The lock screen's "use fingerprint next time" switch. */
    fun enableAfterUnlock(on: Boolean) {
        mutableUi.update { it.copy(enableAfterUnlock = on) }
    }

    /** The lock screen came to the front: asks by itself, once per lock. */
    fun lockScreenShown() {
        if (askedThisLock || !biometrics.enabled()) return
        askedThisLock = true
        unlock()
    }

    /** "Use fingerprint" on the lock screen. */
    fun unlock() {
        if (mutableUi.value.working || core.state.value !is SessionState.Locked) return
        mutableUi.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val message = when (val result = biometrics.unlock()) {
                is BiometricResult.Done -> when (core.run { unlockWithBiometricKey(result.value) }) {
                    BiometricUnlockResult.Success -> null
                    // The PIN still works and says what is wrong with the data, if anything.
                    is BiometricUnlockResult.DataUnreadable -> null
                    BiometricUnlockResult.WrongKey -> {
                        biometrics.disable()
                        BiometricMessage.STOPPED_WORKING
                    }
                }
                // Turned off, with a marker so the lock screen keeps saying why.
                BiometricResult.Invalidated -> BiometricMessage.CHANGED
                BiometricResult.Cancelled, BiometricResult.Failed -> null
            }
            mutableUi.update { it.copy(working = false, message = message) }
            refresh()
        }
    }

    /** Turns it on, from settings or the offer: a biometric check confirms. */
    fun enable() {
        if (mutableUi.value.working || core.state.value != SessionState.Unlocked) return
        mutableUi.update { it.copy(working = true, offer = false, message = null) }
        viewModelScope.launch {
            val key = try {
                core.run { dataKeyForBiometricUnlock() }
            } catch (e: IllegalStateException) {
                // Locked meanwhile.
                null
            }
            val result = if (key == null) BiometricResult.Failed else biometrics.enable(key).also { key.fill(0) }
            mutableUi.update {
                it.copy(working = false, message = if (result is BiometricResult.Failed) BiometricMessage.NOT_TURNED_ON else null)
            }
            refresh()
        }
    }

    fun disable() {
        biometrics.disable()
        mutableUi.update { it.copy(message = null) }
        refresh()
    }

    fun notNow() {
        mutableUi.update { it.copy(offer = false) }
    }

    fun clearMessage() {
        mutableUi.update { it.copy(message = null) }
    }
}
