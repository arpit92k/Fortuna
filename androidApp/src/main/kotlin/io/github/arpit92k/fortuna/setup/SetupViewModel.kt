package io.github.arpit92k.fortuna.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.keys.MIN_PIN_LENGTH
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.ui.typed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Where the user is in first-time setup (UF-01). */
sealed interface SetupStep {
    /** S-01: what the app is, and what losing the PIN and phrase means. */
    data object Welcome : SetupStep

    /** S-02: the PIN, typed twice. [first] is the first entry once made. */
    data class CreatePin(
        val entered: String = "",
        val first: String? = null,
        val problem: PinMessage? = null,
        val working: Boolean = false,
    ) : SetupStep {
        val okEnabled: Boolean get() = entered.length >= MIN_PIN_LENGTH && !working
    }

    /** S-03: the twelve words to write down. The only screen that shows them. */
    data class ShowPhrase(val words: List<String>) : SetupStep

    /** S-04: three of the words asked back, at [positions] counting from 1. */
    data class ConfirmPhrase(
        val positions: List<Int>,
        val answers: Map<Int, String> = emptyMap(),
        val wrong: Boolean = false,
    ) : SetupStep {
        val confirmEnabled: Boolean get() = positions.all { answers[it].orEmpty().isNotBlank() }
    }

    /** S-05: the base currency, for totals and charts. */
    data class ChooseCurrency(
        val query: String = "",
        val suggested: Currency?,
        val working: Boolean = false,
    ) : SetupStep {
        /** The currencies matching [query] by code or name, with the suggestion first. */
        val choices: List<Currency>
            get() {
                val text = query.trim()
                val matching = Currency.all.filter {
                    text.isEmpty() || it.code.contains(text, ignoreCase = true) || it.name.contains(text, ignoreCase = true)
                }
                return if (suggested != null && suggested in matching) listOf(suggested) + (matching - suggested) else matching
            }
    }
}

enum class PinMessage { TOO_SHORT, DID_NOT_MATCH }

/**
 * Runs first-time setup. Nothing is saved until the base currency is chosen:
 * leaving the phrase screens abandons the setup, and the next one gets a new
 * phrase.
 *
 * @param suggestedCurrency the currency of the phone's region, offered first.
 * @param random chooses which words of the phrase are asked back.
 */
class SetupViewModel(
    private val core: Core,
    private val suggestedCurrency: Currency?,
    private val random: Random = Random.Default,
) : ViewModel() {
    // A setup already under way in the core, with its phrase. Android may close
    // the screen while the app is in the background and keep the app itself;
    // the new screen then picks up the same words rather than starting again.
    private val resumed = (core.state.value as? SessionState.SettingUp)?.phrase?.words

    private val mutableStep = MutableStateFlow<SetupStep>(resumed?.let { SetupStep.ShowPhrase(it) } ?: SetupStep.Welcome)
    val step: StateFlow<SetupStep> = mutableStep.asStateFlow()

    private var words: List<String> = resumed ?: emptyList()

    fun getStarted() {
        mutableStep.value = SetupStep.CreatePin()
    }

    // ---- S-02 Create PIN ----

    fun digit(digit: Char) = updatePin { it.copy(entered = typed(it.entered, digit), problem = null) }

    fun deleteDigit() = updatePin { it.copy(entered = it.entered.dropLast(1)) }

    fun pinOk() {
        val pin = mutableStep.value as? SetupStep.CreatePin ?: return
        if (pin.working) return
        when {
            pin.entered.length < MIN_PIN_LENGTH -> mutableStep.value = pin.copy(problem = PinMessage.TOO_SHORT)
            pin.first == null -> mutableStep.value = SetupStep.CreatePin(first = pin.entered)
            pin.first != pin.entered -> mutableStep.value = SetupStep.CreatePin(problem = PinMessage.DID_NOT_MATCH)
            else -> {
                mutableStep.value = pin.copy(working = true)
                viewModelScope.launch {
                    when (val result = core.run { beginSetup(pin.entered) }) {
                        is SetupResult.Ready -> {
                            words = result.setup.phrase.words
                            mutableStep.value = SetupStep.ShowPhrase(words)
                        }
                        // The pad only types digits, and OK needs six of them.
                        is SetupResult.InvalidPin -> mutableStep.value = SetupStep.CreatePin(problem = PinMessage.TOO_SHORT)
                    }
                }
            }
        }
    }

    private fun updatePin(change: (SetupStep.CreatePin) -> SetupStep.CreatePin) {
        mutableStep.update { if (it is SetupStep.CreatePin && !it.working) change(it) else it }
    }

    // ---- S-03 and S-04: the phrase ----

    fun phraseWrittenDown() {
        if (mutableStep.value !is SetupStep.ShowPhrase) return
        mutableStep.value = SetupStep.ConfirmPhrase(positions = pickPositions())
    }

    fun answer(position: Int, word: String) {
        mutableStep.update {
            if (it is SetupStep.ConfirmPhrase) it.copy(answers = it.answers + (position to word), wrong = false) else it
        }
    }

    fun confirmPhrase() {
        val confirm = mutableStep.value as? SetupStep.ConfirmPhrase ?: return
        viewModelScope.launch {
            val answers = confirm.positions.associateWith { confirm.answers[it].orEmpty() }
            mutableStep.value = if (core.run { phraseConfirmed(answers) }) {
                SetupStep.ChooseCurrency(suggested = suggestedCurrency)
            } else {
                confirm.copy(wrong = true)
            }
        }
    }

    /** Back to the phrase, after a wrong answer or on request. New words are asked next time. */
    fun showPhraseAgain() {
        mutableStep.value = SetupStep.ShowPhrase(words)
    }

    // ---- S-05 Base currency ----

    fun search(query: String) {
        mutableStep.update { if (it is SetupStep.ChooseCurrency) it.copy(query = query) else it }
    }

    fun choose(currency: Currency) {
        val choosing = mutableStep.value as? SetupStep.ChooseCurrency ?: return
        if (choosing.working) return
        mutableStep.value = choosing.copy(working = true)
        // Writes the store, then the lock state; the session is then unlocked
        // and the app moves on to the dashboard.
        viewModelScope.launch {
            core.run { finishSetup(currency) }
            words = emptyList()
            mutableStep.value = SetupStep.Welcome
        }
    }

    // ---- Back ----

    /**
     * Goes back one step. Leaving the phrase abandons the setup, so the PIN
     * is chosen again and a new phrase made. Once the phrase is confirmed,
     * setup only moves forward. Returns false at the start, where back leaves
     * the app.
     */
    fun back(): Boolean {
        when (val step = mutableStep.value) {
            SetupStep.Welcome -> return false
            is SetupStep.CreatePin -> {
                if (step.working) return true
                mutableStep.value = if (step.first != null) SetupStep.CreatePin() else SetupStep.Welcome
            }
            is SetupStep.ShowPhrase -> abandon()
            is SetupStep.ConfirmPhrase -> showPhraseAgain()
            is SetupStep.ChooseCurrency -> {}
        }
        return true
    }

    private fun abandon() {
        words = emptyList()
        core.post { cancelSetup() }
        mutableStep.value = SetupStep.CreatePin()
    }

    private fun pickPositions(): List<Int> = (1..words.size).shuffled(random).take(WORDS_ASKED).sorted()

    companion object {
        /** Decided: three words, at random positions. */
        const val WORDS_ASKED = 3
    }
}
