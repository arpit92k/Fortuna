package io.github.arpit92k.fortuna.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.keys.MIN_PIN_LENGTH
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.ui.typed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** S-07 Lock. */
data class LockUi(
    val entered: String = "",
    /** How long until the next PIN may be tried; zero when it may be tried now. */
    val wait: Duration = Duration.ZERO,
    val message: LockMessage? = null,
    val working: Boolean = false,
) {
    val waiting: Boolean get() = wait > Duration.ZERO
    val padEnabled: Boolean get() = !waiting && !working
    val okEnabled: Boolean get() = padEnabled && entered.length >= MIN_PIN_LENGTH
}

enum class LockMessage {
    WRONG_PIN,

    /** This phone's key cannot open the data; only the recovery phrase can. */
    NOT_THIS_DEVICE,
}

/**
 * Unlocks with the PIN. While a wait runs, the pad is off and the wait counts
 * down; the core decides how long it is, from the clock the user cannot change.
 */
class LockViewModel(private val core: Core) : ViewModel() {
    private val mutableUi = MutableStateFlow(LockUi())
    val ui: StateFlow<LockUi> = mutableUi.asStateFlow()

    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            core.state.collect { state ->
                when (state) {
                    is SessionState.Locked -> {
                        mutableUi.update { it.copy(wait = state.wait) }
                        if (state.wait > Duration.ZERO) startTicker()
                    }
                    // Forget the PIN once it has done its work, or when there is nothing to unlock.
                    else -> mutableUi.value = LockUi()
                }
            }
        }
    }

    fun digit(digit: Char) = updatePad { it.copy(entered = typed(it.entered, digit), message = null) }

    fun deleteDigit() = updatePad { it.copy(entered = it.entered.dropLast(1)) }

    fun ok() {
        val ui = mutableUi.value
        if (!ui.okEnabled) return
        mutableUi.value = ui.copy(working = true, message = null)
        viewModelScope.launch {
            val result = core.run { unlock(ui.entered) }
            mutableUi.update {
                when (result) {
                    is UnlockResult.Success -> LockUi()
                    is UnlockResult.WrongPin -> it.copy(entered = "", working = false, message = LockMessage.WRONG_PIN)
                    is UnlockResult.MustWait -> it.copy(entered = "", working = false, wait = result.remaining)
                    UnlockResult.NotThisDevice -> it.copy(entered = "", working = false, message = LockMessage.NOT_THIS_DEVICE)
                    // Neither can happen from this pad, which types only digits and needs six.
                    is UnlockResult.InvalidPin, UnlockResult.NoLockState -> it.copy(entered = "", working = false)
                }
            }
            if (result is UnlockResult.MustWait) startTicker()
        }
    }

    private fun updatePad(change: (LockUi) -> LockUi) {
        mutableUi.update { if (it.padEnabled) change(it) else it }
    }

    /** Asks the core for the wait once a second until it has run out. */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (mutableUi.value.waiting) {
                delay(1.seconds)
                core.run { refreshWait() }
            }
        }
    }
}

/** A wait as the lock screen shows it: 0:45, 4:59, 1:00:00 or 23:59:59. */
fun formatWait(wait: Duration): String {
    // Rounded up, so the screen never shows 0:00 while the pad is still off.
    val total = (wait + 999_999_999.nanoseconds).inWholeSeconds
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
