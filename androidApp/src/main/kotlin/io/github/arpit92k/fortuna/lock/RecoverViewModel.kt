package io.github.arpit92k.fortuna.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.keys.RecoveryResult
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.setup.NewPinOk
import io.github.arpit92k.fortuna.setup.PinMessage
import io.github.arpit92k.fortuna.setup.SetupStep
import io.github.arpit92k.fortuna.setup.ok
import io.github.arpit92k.fortuna.setup.withDigit
import io.github.arpit92k.fortuna.setup.withoutDigit
import io.github.arpit92k.fortuna.ui.PhraseCheck
import io.github.arpit92k.fortuna.ui.PhraseMessage
import io.github.arpit92k.fortuna.ui.checkPhrase
import io.github.arpit92k.fortuna.ui.emptyPhraseWords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the user is in getting back in after a forgotten PIN (UF-14). */
sealed interface RecoverStep {
    /** S-08: the twelve words. */
    data class Phrase(val words: List<String> = emptyPhraseWords(), val message: PhraseMessage? = null) : RecoverStep

    /** S-02, again: the new PIN, typed twice. */
    data class NewPin(val pad: SetupStep.CreatePin = SetupStep.CreatePin()) : RecoverStep
}

/**
 * Gets back in with the recovery phrase and sets a new PIN. The phrase is
 * checked as typed before the PIN is asked; whether it is the right one is
 * known only when the core tries it, after the new PIN. A wrong phrase changes
 * nothing, and brings the user back to the words, still filled in.
 */
class RecoverViewModel(private val core: Core, private val crypto: Crypto = PlatformCrypto) : ViewModel() {
    private val mutableStep = MutableStateFlow<RecoverStep?>(null)

    /** Null while the lock screen is showing. */
    val step: StateFlow<RecoverStep?> = mutableStep.asStateFlow()

    private var phrase: RecoveryPhrase? = null
    private var words: List<String> = emptyPhraseWords()

    init {
        viewModelScope.launch {
            // Once unlocked, or with nothing to unlock, the words are forgotten.
            core.state.collect { if (it !is SessionState.Locked) forget() }
        }
    }

    /** "Forgot your PIN?" on the lock screen. */
    fun begin() {
        mutableStep.value = RecoverStep.Phrase()
    }

    fun words(words: List<String>) {
        mutableStep.update { if (it is RecoverStep.Phrase) it.copy(words = words, message = null) else it }
    }

    /** Checks the words as typed, then asks for the new PIN. */
    fun phraseDone() {
        val step = mutableStep.value as? RecoverStep.Phrase ?: return
        when (val check = checkPhrase(step.words, crypto)) {
            is PhraseCheck.Ready -> {
                phrase = check.phrase
                words = step.words
                mutableStep.value = RecoverStep.NewPin()
            }
            is PhraseCheck.Problem -> mutableStep.value = step.copy(message = check.message)
        }
    }

    fun digit(digit: Char) = updatePad { it.withDigit(digit) }

    fun deleteDigit() = updatePad { it.withoutDigit() }

    fun pinOk() {
        val step = mutableStep.value as? RecoverStep.NewPin ?: return
        if (step.pad.working) return
        val phrase = phrase ?: return
        when (val ok = step.pad.ok()) {
            is NewPinOk.Again -> mutableStep.value = RecoverStep.NewPin(ok.pad)
            is NewPinOk.Chosen -> {
                mutableStep.value = RecoverStep.NewPin(step.pad.copy(working = true))
                viewModelScope.launch {
                    val result = try {
                        core.run { recover(phrase, ok.pin) }
                    } catch (e: IllegalStateException) {
                        // No longer locked: there is nothing to recover.
                        null
                    }
                    when (result) {
                        // The session is unlocked, and the app moves on; the words are forgotten then.
                        // Unreadable data: the new PIN is set, and the lock screen says what is wrong once it is used.
                        is RecoveryResult.Success, is RecoveryResult.DataUnreadable, RecoveryResult.NoLockState, null -> forget()
                        RecoveryResult.WrongPhrase -> mutableStep.value = RecoverStep.Phrase(words, PhraseMessage.WrongPhrase)
                        // The pad only types digits, and OK needs six.
                        is RecoveryResult.InvalidPin -> mutableStep.value = RecoverStep.NewPin(SetupStep.CreatePin(problem = PinMessage.TOO_SHORT))
                    }
                }
            }
        }
    }

    /** Back one step: from the PIN to the words, still filled in, and from the words to the lock screen. */
    fun back() {
        when (val step = mutableStep.value) {
            is RecoverStep.Phrase -> forget()
            is RecoverStep.NewPin -> when {
                step.pad.working -> {}
                step.pad.first != null -> mutableStep.value = RecoverStep.NewPin()
                else -> mutableStep.value = RecoverStep.Phrase(words)
            }
            null -> {}
        }
    }

    private fun updatePad(change: (SetupStep.CreatePin) -> SetupStep.CreatePin) {
        mutableStep.update { if (it is RecoverStep.NewPin && !it.pad.working) RecoverStep.NewPin(change(it.pad)) else it }
    }

    private fun forget() {
        phrase = null
        words = emptyPhraseWords()
        mutableStep.value = null
    }
}
