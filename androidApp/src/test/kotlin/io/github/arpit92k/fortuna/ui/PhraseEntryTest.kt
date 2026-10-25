package io.github.arpit92k.fortuna.ui

import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.someoneElsesPhrase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhraseEntryTest {
    private val empty = emptyPhraseWords()

    @Test
    fun typingFillsOneBoxAndASpaceMovesOn() {
        val typing = enterPhraseText(empty, 0, "Aba")
        assertEquals("aba", typing.words[0], "kept in lower case")
        assertNull(typing.next)
        val done = enterPhraseText(typing.words, 0, "abandon ")
        assertEquals("abandon", done.words[0])
        assertEquals(1, done.next)
    }

    @Test
    fun aSpaceInTheLastBoxStaysThere() {
        assertNull(enterPhraseText(empty, 11, "zoo ").next)
    }

    @Test
    fun clearingABoxEmptiesIt() {
        val filled = enterPhraseText(empty, 3, "zoo").words
        assertEquals("", enterPhraseText(filled, 3, "").words[3])
    }

    @Test
    fun aWholePhrasePastedAnywhereFillsEveryBox() {
        val pasted = enterPhraseText(empty, 4, "  Abandon, abandon abandon\nabandon abandon abandon abandon abandon abandon abandon abandon ABOUT ")
        assertEquals(someoneElsesPhrase, pasted.words)
        assertNull(pasted.next)
    }

    @Test
    fun aFewWordsPastedFillTheBoxesFromThere() {
        val pasted = enterPhraseText(empty, 3, "zoo zone zero")
        assertEquals(listOf("", "", "", "zoo", "zone", "zero", "", "", "", "", "", ""), pasted.words)
        assertEquals(6, pasted.next)
    }

    @Test
    fun wordsBeyondTheLastBoxAreDropped() {
        val pasted = enterPhraseText(empty, 10, "zoo zone zero")
        assertEquals("zoo", pasted.words[10])
        assertEquals("zone", pasted.words[11])
        assertNull(pasted.next)
    }

    @Test
    fun suggestionsAreWordsStartingWithWhatIsTyped() {
        assertEquals(listOf("abandon", "ability", "able", "about"), phraseSuggestions("ab"))
        assertEquals(emptyList(), phraseSuggestions(""))
        assertEquals(emptyList(), phraseSuggestions("qx"))
        // A whole word with nothing longer has nothing left to suggest.
        assertEquals(emptyList(), phraseSuggestions("zoo"))
        assertEquals(listOf("act", "action", "actor", "actress"), phraseSuggestions("act"), "a whole word still offers the longer ones")
    }

    @Test
    fun aWordIsWrongWhenNothingStartsWithItOrOnceLeftUnfinished() {
        assertFalse(phraseWordWrong("", finished = true))
        assertFalse(phraseWordWrong("aba", finished = false), "still being typed")
        assertTrue(phraseWordWrong("aba", finished = true), "left unfinished")
        assertTrue(phraseWordWrong("qx", finished = false), "no word starts with it")
        assertFalse(phraseWordWrong("abandon", finished = true))
    }

    @Test
    fun theWordsAreCheckedBeforeUse() {
        val missing = someoneElsesPhrase.toMutableList().also { it[6] = "" }
        assertEquals(PhraseMessage.Missing(7), assertIs<PhraseCheck.Problem>(checkPhrase(missing, PlatformCrypto)).message)

        val unknown = someoneElsesPhrase.toMutableList().also { it[2] = "abandonn" }
        assertEquals(PhraseMessage.UnknownWord(3), assertIs<PhraseCheck.Problem>(checkPhrase(unknown, PlatformCrypto)).message)

        // Every word on the list, but the checksum is wrong.
        val swapped = someoneElsesPhrase.reversed()
        assertEquals(PhraseMessage.NotAPhrase, assertIs<PhraseCheck.Problem>(checkPhrase(swapped, PlatformCrypto)).message)

        assertEquals(someoneElsesPhrase, assertIs<PhraseCheck.Ready>(checkPhrase(someoneElsesPhrase, PlatformCrypto)).phrase.words)
    }
}
