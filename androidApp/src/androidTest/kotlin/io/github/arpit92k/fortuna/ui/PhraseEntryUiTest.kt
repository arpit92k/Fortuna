package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** The twelve boxes with their suggestions, on a device: what step 20's first try found missing. */
@RunWith(AndroidJUnit4::class)
class PhraseEntryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var words = emptyPhraseWords()

    private fun show(initial: List<String> = emptyPhraseWords()) {
        words = initial
        compose.setContent {
            FortunaTheme {
                var current by remember { mutableStateOf(initial) }
                val state = rememberPhraseEntryState()
                val onWords = { changed: List<String> -> current = changed; words = changed }
                Column {
                    PhraseEntry(state, current, onWords)
                    PhraseSuggestions(state, current, onWords)
                }
            }
        }
    }

    @Test
    fun typingOffersWordsAndChoosingOneMovesOn() {
        show()
        compose.onNodeWithTag("phrase-box-1").performClick()
        compose.onNodeWithTag("phrase-box-1").performTextInput("ab")
        compose.onNodeWithTag("suggestion-abandon").assertExists().performClick()
        compose.waitForIdle()
        assertEquals("abandon", words[0])
        compose.onNodeWithTag("phrase-box-2").assertIsFocused()
    }

    @Test
    fun theLastWordKeepsTheKeyboardWhileTyped() {
        show(List(11) { "abandon" } + "")
        compose.onNodeWithTag("phrase-box-12").performClick()
        compose.onNodeWithTag("phrase-box-12").performTextInput("a")
        compose.onNodeWithTag("phrase-box-12").assertIsFocused()
        compose.onNodeWithTag("phrase-box-12").performTextInput("b")
        compose.onNodeWithTag("phrase-box-12").assertIsFocused().assertTextContains("ab", substring = true)
        compose.onNodeWithTag("suggestion-about").assertExists()
    }
}
