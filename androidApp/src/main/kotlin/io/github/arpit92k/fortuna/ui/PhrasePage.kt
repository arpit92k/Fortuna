package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.setup.Problem

/**
 * A page that asks for the recovery phrase: S-08 after a forgotten PIN, and
 * S-06 for a backup. It never shows a phrase, only the words the user types.
 */
@Composable
fun PhrasePage(
    title: String,
    body: String,
    words: List<String>,
    message: PhraseMessage?,
    onWords: (List<String>) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val entry = rememberPhraseEntryState()
    AdaptiveScreen(
        onBack = onBack,
        bottomBar = { PhraseSuggestions(entry, words, onWords) },
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(FortunaIcon.Phrase)
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(body, style = MaterialTheme.typography.bodyLarge)
                message?.let { Problem(it.text()) }
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PhraseEntry(entry, words, onWords)
                Button(
                    onClick = onContinue,
                    enabled = words.none { it.isEmpty() },
                    modifier = Modifier.fillMaxWidth().testTag("phrase-continue"),
                ) { Text(stringResource(R.string.phrase_entry_continue)) }
            }
        },
    )
}
