package io.github.arpit92k.fortuna.lock

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.setUpLocked
import io.github.arpit92k.fortuna.setup.PinMessage
import io.github.arpit92k.fortuna.someoneElsesPhrase
import io.github.arpit92k.fortuna.ui.PhraseMessage
import io.github.arpit92k.fortuna.ui.emptyPhraseWords
import io.github.arpit92k.fortuna.ui.enterPhraseText
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
import kotlin.test.assertIs
import kotlin.test.assertNull

/** S-08: back in with the recovery phrase after a forgotten PIN (UF-14), through the real core. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val oldPin = "246810"
    private val newPin = "135791"
    private lateinit var words: List<String>
    private lateinit var core: Core
    private lateinit var recover: RecoverViewModel

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        words = phone.setUpLocked(oldPin)
        core = phone.start(dispatcher)
        recover = RecoverViewModel(core)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private fun TestScope.enterWords(words: List<String>) {
        advanceUntilIdle()
        recover.begin()
        recover.words(words)
        recover.phraseDone()
    }

    private fun TestScope.newPin(pin: String) {
        repeat(2) {
            pin.forEach(recover::digit)
            recover.pinOk()
        }
        advanceUntilIdle()
    }

    @Test
    fun thePhraseAndANewPinGetBackIn() = runTest(dispatcher) {
        enterWords(words)
        assertIs<RecoverStep.NewPin>(recover.step.value)
        newPin(newPin)
        assertEquals(SessionState.Unlocked, core.state.value)
        assertNull(recover.step.value, "the words are forgotten once used")

        // The new PIN opens the data from now on, and the old one does not.
        core.run { lock() }
        assertIs<UnlockResult.WrongPin>(core.run { unlock(oldPin) })
        assertIs<UnlockResult.Success>(core.run { unlock(newPin) })
        assertEquals("GBP", core.dataset.value?.baseCurrency?.code, "the data is intact")
    }

    @Test
    fun theWholePhraseMayBePastedInCapitals() = runTest(dispatcher) {
        val pasted = enterPhraseText(emptyPhraseWords(), 0, words.joinToString("  ") { it.uppercase() })
        enterWords(pasted.words)
        assertIs<RecoverStep.NewPin>(recover.step.value)
    }

    @Test
    fun aWrongPhraseChangesNothing() = runTest(dispatcher) {
        enterWords(someoneElsesPhrase)
        newPin(newPin)
        assertEquals(RecoverStep.Phrase(someoneElsesPhrase, PhraseMessage.WrongPhrase), recover.step.value, "back to the words, still filled in")
        assertIs<SessionState.Locked>(core.state.value)
        assertIs<UnlockResult.Success>(core.run { unlock(oldPin) }, "the old PIN still works")
    }

    @Test
    fun wordsThatAreNotAPhraseAreCaughtBeforeThePin() = runTest(dispatcher) {
        val misspelt = words.toMutableList().also { it[4] = "qqq" }
        enterWords(misspelt)
        assertEquals(PhraseMessage.UnknownWord(5), assertIs<RecoverStep.Phrase>(recover.step.value).message)

        val incomplete = words.toMutableList().also { it[11] = "" }
        recover.words(incomplete)
        assertNull(assertIs<RecoverStep.Phrase>(recover.step.value).message, "typing clears the message")
        recover.phraseDone()
        assertEquals(PhraseMessage.Missing(12), assertIs<RecoverStep.Phrase>(recover.step.value).message)
    }

    @Test
    fun theNewPinIsTypedTwice() = runTest(dispatcher) {
        enterWords(words)
        "13579".forEach(recover::digit)
        recover.pinOk()
        assertEquals(PinMessage.TOO_SHORT, assertIs<RecoverStep.NewPin>(recover.step.value).pad.problem)
        recover.digit('1')
        recover.pinOk()
        "999999".forEach(recover::digit)
        recover.pinOk()
        advanceUntilIdle()
        assertEquals(PinMessage.DID_NOT_MATCH, assertIs<RecoverStep.NewPin>(recover.step.value).pad.problem)
        assertIs<SessionState.Locked>(core.state.value)
    }

    @Test
    fun backGoesFromThePinToTheWordsAndThenToTheLockScreen() = runTest(dispatcher) {
        enterWords(words)
        recover.back()
        assertEquals(RecoverStep.Phrase(words), recover.step.value, "the words are still there")
        recover.back()
        assertNull(recover.step.value)
        recover.begin()
        assertEquals(RecoverStep.Phrase(), recover.step.value, "a new attempt starts empty")
    }
}
