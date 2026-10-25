package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.phrase.PhraseParseResult
import io.github.arpit92k.fortuna.core.phrase.PhraseProblem
import io.github.arpit92k.fortuna.core.phrase.RecoveryPhrase

// The twelve-word entry shared by S-06 Restore and S-08 Enter recovery phrase.
// The words are held by the screen's view model, as a list of twelve, so they
// survive rotation and folding; nothing here keeps them anywhere else.

private const val WORDS = RecoveryPhrase.WORD_COUNT

private val knownWords: Set<String> = RecoveryPhrase.wordList.toHashSet()

/** Twelve empty boxes. */
fun emptyPhraseWords(): List<String> = List(WORDS) { "" }

/**
 * The words after an edit; whether it [finished] a word, with a space, a
 * suggestion or a paste; and the box to move to then, if there is one after.
 */
data class PhraseEdit(val words: List<String>, val next: Int?, val finished: Boolean = false)

/**
 * Puts [text], typed or pasted into box [index], into [words]. A word followed
 * by a space finishes that box. Several words fill the boxes from [index] on,
 * and a whole phrase of twelve fills them all, wherever it was pasted. Words
 * keep only their letters, in lower case, as the word list has them.
 */
fun enterPhraseText(words: List<String>, index: Int, text: String): PhraseEdit {
    val parts = text.split(Regex("\\s+")).map(::clean).filter { it.isNotEmpty() }
    val result = words.toMutableList()
    if (parts.size <= 1) {
        result[index] = parts.firstOrNull().orEmpty()
        val finished = parts.size == 1 && text.last().isWhitespace()
        return PhraseEdit(result, if (finished && index < WORDS - 1) index + 1 else null, finished)
    }
    val start = if (parts.size >= WORDS) 0 else index
    val placed = parts.take(WORDS - start)
    placed.forEachIndexed { offset, word -> result[start + offset] = word }
    val after = start + placed.size
    return PhraseEdit(result, if (after < WORDS) after else null, finished = true)
}

private fun clean(word: String): String = word.lowercase().filter { it in 'a'..'z' }

/** Up to four words of the list starting with [prefix]; none once the box holds the only one. */
fun phraseSuggestions(prefix: String): List<String> {
    if (prefix.isEmpty()) return emptyList()
    val matching = RecoveryPhrase.wordList.filter { it.startsWith(prefix) }.take(4)
    return if (matching == listOf(prefix)) emptyList() else matching
}

/**
 * Whether a box shows its word as wrong: no word of the list starts with it,
 * or, once the user has left the box, it is not a whole word of the list.
 */
fun phraseWordWrong(word: String, finished: Boolean): Boolean =
    word.isNotEmpty() && if (finished) word !in knownWords else phraseSuggestions(word).isEmpty() && word !in knownWords

/** Why the words cannot be used yet. */
sealed interface PhraseMessage {
    /** A box is empty; [position] counts from 1. */
    data class Missing(val position: Int) : PhraseMessage

    /** A word is not one of the list; [position] counts from 1. */
    data class UnknownWord(val position: Int) : PhraseMessage

    /** Every word is on the list, but together they are not a recovery phrase: one is wrong, or two are swapped. */
    data object NotAPhrase : PhraseMessage

    /** A real phrase, but not the one that opens this data. */
    data object WrongPhrase : PhraseMessage
}

sealed interface PhraseCheck {
    data class Ready(val phrase: RecoveryPhrase) : PhraseCheck

    data class Problem(val message: PhraseMessage) : PhraseCheck
}

/** Checks the twelve [words] before they are used. */
fun checkPhrase(words: List<String>, crypto: Crypto): PhraseCheck {
    val empty = words.indexOfFirst { it.isEmpty() }
    if (empty >= 0) return PhraseCheck.Problem(PhraseMessage.Missing(empty + 1))
    return when (val parsed = RecoveryPhrase.parse(words.joinToString(" "), crypto)) {
        is PhraseParseResult.Success -> PhraseCheck.Ready(parsed.phrase)
        is PhraseParseResult.Failure -> PhraseCheck.Problem(
            when (val problem = parsed.problem) {
                is PhraseProblem.UnknownWord -> PhraseMessage.UnknownWord(problem.position)
                // Twelve boxes always give twelve words.
                is PhraseProblem.WrongWordCount, PhraseProblem.ChecksumMismatch -> PhraseMessage.NotAPhrase
            },
        )
    }
}

@Composable
fun PhraseMessage.text(): String = when (this) {
    is PhraseMessage.Missing -> stringResource(R.string.phrase_entry_missing, position)
    is PhraseMessage.UnknownWord -> stringResource(R.string.phrase_entry_unknown, position)
    PhraseMessage.NotAPhrase -> stringResource(R.string.phrase_entry_not_a_phrase)
    PhraseMessage.WrongPhrase -> stringResource(R.string.phrase_entry_wrong)
}

/** Which box has the keyboard, shared by the boxes and the suggestions above the keyboard. */
@Stable
class PhraseEntryState {
    internal val focus: List<FocusRequester> = List(WORDS) { FocusRequester() }

    /** The box being typed in, counting from 0, or -1 when none is. */
    var focused: Int by mutableIntStateOf(-1)
        internal set
}

@Composable
fun rememberPhraseEntryState(): PhraseEntryState = remember { PhraseEntryState() }

/**
 * Applies [edit]: moves to the next box when a word was finished, and closes
 * the keyboard when the last word was finished with all twelve filled in.
 * Typing within a word never moves the keyboard.
 */
private fun PhraseEntryState.apply(edit: PhraseEdit, onWords: (List<String>) -> Unit, focusManager: FocusManager) {
    onWords(edit.words)
    val next = edit.next
    when {
        next != null -> focus[next].requestFocus()
        edit.finished && edit.words.none { it.isEmpty() } -> focusManager.clearFocus()
    }
}

/**
 * Twelve numbered boxes, in two columns of six as S-03 showed them, or three
 * of four on a wider window. [PhraseSuggestions] offers the words for the box
 * being typed in.
 *
 * The keyboard is told the boxes hold a password, so it neither suggests nor
 * learns the words.
 */
@Composable
fun PhraseEntry(
    state: PhraseEntryState,
    words: List<String>,
    onWords: (List<String>) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    BoxWithConstraints(modifier.fillMaxWidth().testTag("phrase-entry")) {
        val columns = if (maxWidth >= 480.dp) 3 else 2
        val perColumn = WORDS / columns
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (column in 0 until columns) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (index in column * perColumn until (column + 1) * perColumn) {
                        val word = words[index]
                        OutlinedTextField(
                            value = word,
                            onValueChange = { state.apply(enterPhraseText(words, index, it), onWords, focusManager) },
                            prefix = { Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            enabled = enabled,
                            isError = phraseWordWrong(word, finished = state.focused != index),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = if (index == WORDS - 1) ImeAction.Done else ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { state.focus[index + 1].requestFocus() },
                                onDone = { focusManager.clearFocus() },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(state.focus[index])
                                .onFocusChanged { focus ->
                                    if (focus.hasFocus) {
                                        state.focused = index
                                    } else if (state.focused == index) {
                                        state.focused = -1
                                    }
                                }
                                .testTag("phrase-box-${index + 1}"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The words of the list starting with what is typed in the box being typed
 * in. Meant for the bottom of the page, so it sits just above the keyboard
 * and is never hidden by it. Choosing a word fills the box and moves to the
 * next. Nothing shows while no box is being typed in.
 */
@Composable
fun PhraseSuggestions(state: PhraseEntryState, words: List<String>, onWords: (List<String>) -> Unit) {
    val focused = state.focused
    if (focused !in words.indices) return
    val focusManager = LocalFocusManager.current
    val suggestions = phraseSuggestions(words[focused])
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        // One row's height whether or not there is anything to offer, so the page does not jump as the user types.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("phrase-suggestions"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (suggestion in suggestions) {
                SuggestionChip(
                    onClick = { state.apply(enterPhraseText(words, focused, "$suggestion "), onWords, focusManager) },
                    label = { Text(suggestion, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.testTag("suggestion-$suggestion"),
                )
            }
        }
    }
}
