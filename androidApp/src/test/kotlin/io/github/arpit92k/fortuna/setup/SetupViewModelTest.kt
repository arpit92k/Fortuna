package io.github.arpit92k.fortuna.setup

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.keys.KeyGuard
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.core.store.DocumentStore
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
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pounds = Currency.forCode("GBP")!!
    private lateinit var core: Core
    private lateinit var setup: SetupViewModel

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        core = phone.start(dispatcher)
        setup = SetupViewModel(core, suggestedCurrency = pounds, random = Random(7))
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private fun type(pin: String) {
        pin.forEach(setup::digit)
        setup.pinOk()
    }

    private fun TestScope.choosePin(pin: String = "246810") {
        setup.getStarted()
        type(pin)
        type(pin)
        advanceUntilIdle()
    }

    private fun answerCorrectly(words: List<String>, viewModel: SetupViewModel = setup) {
        val confirm = assertIs<SetupStep.ConfirmPhrase>(viewModel.step.value)
        for (position in confirm.positions) viewModel.answer(position, words[position - 1])
    }

    @Test
    fun aNewUserSetsUpAndEndsUpUnlocked() = runTest(dispatcher) {
        assertEquals(SetupStep.Welcome, setup.step.value)
        choosePin()

        val words = assertIs<SetupStep.ShowPhrase>(setup.step.value).words
        assertEquals(12, words.size)
        setup.phraseWrittenDown()

        val confirm = assertIs<SetupStep.ConfirmPhrase>(setup.step.value)
        assertEquals(3, confirm.positions.size, "decided: three words")
        assertEquals(confirm.positions.sorted().distinct(), confirm.positions)
        assertFalse(confirm.confirmEnabled)
        // Case and spaces do not matter.
        for (position in confirm.positions) setup.answer(position, "  ${words[position - 1].uppercase()} ")
        setup.confirmPhrase()
        advanceUntilIdle()

        val currency = assertIs<SetupStep.ChooseCurrency>(setup.step.value)
        assertEquals(pounds, currency.choices.first(), "the region's currency first")
        assertTrue(setup.back())
        assertEquals(currency, setup.step.value, "once the phrase is confirmed, setup only moves forward")
        setup.choose(pounds)
        advanceUntilIdle()

        assertEquals(SessionState.Unlocked, core.state.value)
        assertEquals(pounds, core.dataset.value!!.baseCurrency)
    }

    // Android may close the screen in the background while the app keeps running.
    @Test
    fun aNewScreenReturnsToTheSameWords() = runTest(dispatcher) {
        choosePin()
        setup.phraseWrittenDown()
        val words = core.run { (state.value as SessionState.SettingUp).phrase.words }

        val again = SetupViewModel(core, suggestedCurrency = pounds, random = Random(7))
        assertEquals(SetupStep.ShowPhrase(words), again.step.value, "back to the same list of words")
        again.phraseWrittenDown()
        answerCorrectly(words, again)
        again.confirmPhrase()
        advanceUntilIdle()
        assertIs<SetupStep.ChooseCurrency>(again.step.value)
    }

    @Test
    fun aPinNeedsSixDigits() = runTest(dispatcher) {
        setup.getStarted()
        "12345".forEach(setup::digit)
        assertFalse(assertIs<SetupStep.CreatePin>(setup.step.value).okEnabled)
        setup.pinOk()
        assertEquals(PinMessage.TOO_SHORT, assertIs<SetupStep.CreatePin>(setup.step.value).problem)
        setup.digit('6')
        assertTrue(assertIs<SetupStep.CreatePin>(setup.step.value).okEnabled)
    }

    @Test
    fun twoDifferentPinsStartAgain() = runTest(dispatcher) {
        setup.getStarted()
        type("246810")
        type("246811")
        val pin = assertIs<SetupStep.CreatePin>(setup.step.value)
        assertEquals(PinMessage.DID_NOT_MATCH, pin.problem)
        assertEquals(null, pin.first)
        assertEquals("", pin.entered)
    }

    @Test
    fun deleteRemovesTheLastDigit() = runTest(dispatcher) {
        setup.getStarted()
        "1234".forEach(setup::digit)
        setup.deleteDigit()
        assertEquals("123", assertIs<SetupStep.CreatePin>(setup.step.value).entered)
    }

    @Test
    fun wrongWordsSendTheUserBackToThePhrase() = runTest(dispatcher) {
        choosePin()
        val words = assertIs<SetupStep.ShowPhrase>(setup.step.value).words
        setup.phraseWrittenDown()
        val confirm = assertIs<SetupStep.ConfirmPhrase>(setup.step.value)
        for (position in confirm.positions) setup.answer(position, "wrong")
        setup.confirmPhrase()
        advanceUntilIdle()
        assertTrue(assertIs<SetupStep.ConfirmPhrase>(setup.step.value).wrong)

        setup.showPhraseAgain()
        assertEquals(words, assertIs<SetupStep.ShowPhrase>(setup.step.value).words, "the same phrase")
        setup.phraseWrittenDown()
        answerCorrectly(words)
        setup.confirmPhrase()
        advanceUntilIdle()
        assertIs<SetupStep.ChooseCurrency>(setup.step.value)
    }

    // Decided: nothing is saved until setup finishes.
    @Test
    fun nothingIsSavedBeforeTheCurrencyIsChosen() = runTest(dispatcher) {
        choosePin()
        setup.phraseWrittenDown()
        assertFalse(phone.files.exists(KeyGuard.LOCK_FILE))
        assertFalse(phone.files.exists(DocumentStore.MAIN))
        assertEquals(SessionState.NoData, phone.start(dispatcher).state.value, "closing the app here leaves no data")
    }

    @Test
    fun leavingThePhraseAbandonsSetupAndTheNextGetsANewPhrase() = runTest(dispatcher) {
        choosePin()
        val first = assertIs<SetupStep.ShowPhrase>(setup.step.value).words
        assertTrue(setup.back())
        advanceUntilIdle()
        assertEquals(SetupStep.CreatePin(), setup.step.value)
        assertEquals(SessionState.NoData, core.state.value)

        type("246810")
        type("246810")
        advanceUntilIdle()
        assertNotEquals(first, assertIs<SetupStep.ShowPhrase>(setup.step.value).words)
    }

    @Test
    fun backStepsThroughSetup() = runTest(dispatcher) {
        assertFalse(setup.back(), "at the start, back leaves the app")
        setup.getStarted()
        type("246810")
        assertTrue(setup.back())
        assertEquals(SetupStep.CreatePin(), setup.step.value, "back to the first PIN entry")
        assertTrue(setup.back())
        assertEquals(SetupStep.Welcome, setup.step.value)
    }

    @Test
    fun currenciesAreFoundByCodeOrName() {
        val choosing = SetupStep.ChooseCurrency(suggested = pounds)
        assertEquals(Currency.all.size, choosing.choices.size)
        assertEquals(listOf("JPY"), choosing.copy(query = "yen").choices.map { it.code })
        assertTrue(choosing.copy(query = "eur").choices.any { it.code == "EUR" })
        assertEquals(listOf("GBP"), choosing.copy(query = " gbp ").choices.map { it.code })
        assertTrue(SetupStep.ChooseCurrency(query = "zzz", suggested = pounds).choices.isEmpty())
    }
}
