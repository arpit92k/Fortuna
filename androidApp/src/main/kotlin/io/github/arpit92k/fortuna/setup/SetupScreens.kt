package io.github.arpit92k.fortuna.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.AllowScreenshots
import io.github.arpit92k.fortuna.ui.ButtonIcon
import io.github.arpit92k.fortuna.ui.Centred
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.LocalSideBySide
import io.github.arpit92k.fortuna.ui.PinEntry
import io.github.arpit92k.fortuna.ui.PromiseRow
import io.github.arpit92k.fortuna.ui.WelcomeHero

/** First-time setup, S-01 to S-05, one step at a time; or a restore, S-06, from S-01. */
@Composable
fun SetupFlow(viewModel: SetupViewModel, restore: RestoreViewModel) {
    val step by viewModel.step.collectAsState()
    val restoring by restore.step.collectAsState()
    if (restoring != null) {
        RestoreFlow(restore)
        return
    }
    BackHandler(enabled = step != SetupStep.Welcome) { viewModel.back() }
    when (val current = step) {
        SetupStep.Welcome -> WelcomeScreen(onGetStarted = viewModel::getStarted, onRestore = restore::begin)
        is SetupStep.CreatePin -> CreatePinScreen(
            current,
            onDigit = viewModel::digit,
            onDelete = viewModel::deleteDigit,
            onOk = viewModel::pinOk,
        )
        is SetupStep.ShowPhrase -> PhraseScreen(current, onWrittenDown = viewModel::phraseWrittenDown)
        is SetupStep.ConfirmPhrase -> ConfirmPhraseScreen(
            current,
            onAnswer = viewModel::answer,
            onConfirm = viewModel::confirmPhrase,
            onShowPhrase = viewModel::showPhraseAgain,
        )
        is SetupStep.ChooseCurrency -> CurrencyScreen(current, onSearch = viewModel::search, onChoose = viewModel::choose)
    }
}

@Composable
private fun Heading(text: String, body: String? = null, icon: FortunaIcon? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) IconTile(icon)
        Text(text, style = MaterialTheme.typography.headlineMedium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---- S-01 Welcome ----

/**
 * What Fortuna is, and the three things to know before starting: the data stays
 * on the phone, there is no account, and the PIN and recovery phrase are the
 * only way in. The picture above and the rows below say the same three things.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onRestore: () -> Unit) {
    AdaptiveScreen(
        hero = { WelcomeHero() },
        actionAtBottom = true,
        info = {
            // Centred under the picture when stacked, at the start when beside it.
            val align = if (LocalSideBySide.current) TextAlign.Start else TextAlign.Center
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = align,
                        // TalkBack can jump straight to it.
                        modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag("welcome-title"),
                    )
                    Text(
                        stringResource(R.string.welcome_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PromiseRow(FortunaIcon.Phone, stringResource(R.string.welcome_phone_title), stringResource(R.string.welcome_phone_body), Modifier.testTag("promise-phone"))
                PromiseRow(FortunaIcon.NoCloud, stringResource(R.string.welcome_account_title), stringResource(R.string.welcome_account_body), Modifier.testTag("promise-account"))
                PromiseRow(FortunaIcon.Lock, stringResource(R.string.welcome_keys_title), stringResource(R.string.welcome_keys_body), Modifier.testTag("promise-keys"))
                Text(
                    stringResource(R.string.welcome_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("welcome-warning"),
                )
            }
        },
        action = {
            // A new start first and filled; a restore below it, plainly the other way in.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth().testTag("get-started")) {
                    Text(stringResource(R.string.welcome_start))
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().testTag("restore")) {
                    ButtonIcon(FortunaIcon.Restore)
                    Text(stringResource(R.string.welcome_restore))
                }
            }
        },
    )
}

// ---- S-02 Create PIN ----

/**
 * The pad for a new PIN, typed twice: in setup, and again after recovering
 * with the phrase or restoring a backup, which pass their own [title] and
 * [body] for the first entry and a way [onBack].
 */
@Composable
fun CreatePinScreen(
    step: SetupStep.CreatePin,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    title: String = stringResource(R.string.pin_create_title),
    body: String = stringResource(R.string.pin_create_body),
    onBack: (() -> Unit)? = null,
) {
    AdaptiveScreen(
        actionAtBottom = true,
        onBack = onBack,
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Heading(
                    if (step.first == null) title else stringResource(R.string.pin_repeat_title),
                    if (step.first == null) body else stringResource(R.string.pin_repeat_body),
                    icon = FortunaIcon.Pin,
                )
                when (step.problem) {
                    PinMessage.TOO_SHORT -> Problem(stringResource(R.string.pin_too_short))
                    PinMessage.DID_NOT_MATCH -> Problem(stringResource(R.string.pin_did_not_match))
                    null -> {}
                }
            }
        },
        action = {
            PinEntry(
                digits = step.entered.length,
                working = step.working,
                onDigit = onDigit,
                onDelete = onDelete,
                onOk = onOk,
                okEnabled = step.okEnabled,
                enabled = !step.working,
            )
        },
    )
}

@Composable
fun Problem(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("problem"))
}

// ---- S-03 Recovery phrase ----

@Composable
fun PhraseScreen(step: SetupStep.ShowPhrase, onWrittenDown: () -> Unit) {
    // The words may be kept as a screenshot, even where the rest of the app blocks them.
    AllowScreenshots()
    AdaptiveScreen(
        info = { Heading(stringResource(R.string.phrase_title), stringResource(R.string.phrase_body), icon = FortunaIcon.Phrase) },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Two columns of six, numbered, so the words are copied in order.
                Row(Modifier.fillMaxWidth().testTag("phrase-words"), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (half in step.words.withIndex().chunked(6)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((index, word) in half) {
                                Row {
                                    Text("${index + 1}.", modifier = Modifier.width(32.dp), style = MaterialTheme.typography.bodyLarge)
                                    Text(word, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                Button(onClick = onWrittenDown, modifier = Modifier.fillMaxWidth().testTag("written-down")) {
                    Text(stringResource(R.string.phrase_written_down))
                }
            }
        },
    )
}

// ---- S-04 Confirm phrase ----

@Composable
fun ConfirmPhraseScreen(
    step: SetupStep.ConfirmPhrase,
    onAnswer: (Int, String) -> Unit,
    onConfirm: () -> Unit,
    onShowPhrase: () -> Unit,
) {
    AdaptiveScreen(
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Heading(stringResource(R.string.confirm_title), stringResource(R.string.confirm_body), icon = FortunaIcon.Phrase)
                if (step.wrong) Problem(stringResource(R.string.confirm_wrong))
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((index, position) in step.positions.withIndex()) {
                    OutlinedTextField(
                        value = step.answers[position].orEmpty(),
                        onValueChange = { onAnswer(position, it) },
                        label = { Text(stringResource(R.string.confirm_word_label, position)) },
                        singleLine = true,
                        // A password field, so the keyboard neither suggests nor learns the words.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = if (index == step.positions.lastIndex) ImeAction.Done else ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("word-$position"),
                    )
                }
                Button(onClick = onConfirm, enabled = step.confirmEnabled, modifier = Modifier.fillMaxWidth().testTag("confirm")) {
                    Text(stringResource(R.string.confirm_button))
                }
                TextButton(onClick = onShowPhrase, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.confirm_show_again))
                }
            }
        },
    )
}

// ---- S-05 Base currency ----

@Composable
fun CurrencyScreen(step: SetupStep.ChooseCurrency, onSearch: (String) -> Unit, onChoose: (Currency) -> Unit) {
    AdaptiveScreen(
        actionScrolls = true,
        info = { Heading(stringResource(R.string.currency_title), stringResource(R.string.currency_body), icon = FortunaIcon.Currency) },
        action = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = step.query,
                    onValueChange = onSearch,
                    label = { Text(stringResource(R.string.currency_search)) },
                    singleLine = true,
                    enabled = !step.working,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().testTag("currency-search"),
                )
                if (step.working) Centred { CircularProgressIndicator() }
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("currency-list")) {
                    items(step.choices, key = { it.code }) { currency ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !step.working) { onChoose(currency) }
                                .padding(vertical = 14.dp, horizontal = 4.dp)
                                .testTag("currency-${currency.code}"),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(currency.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                            Column {
                                Text(currency.name, style = MaterialTheme.typography.bodyLarge)
                                if (currency == step.suggested) {
                                    Text(
                                        stringResource(R.string.currency_suggested),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}
