package io.github.arpit92k.fortuna.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.ButtonIcon
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.PhrasePage

/** S-06 Restore: the file, the phrase, then the PIN for the restored data (UF-13). */
@Composable
fun RestoreFlow(viewModel: RestoreViewModel) {
    val step by viewModel.step.collectAsState()
    BackHandler { viewModel.back() }
    // The system picker: the app sees only the file the user chooses. Backups
    // have no type the phone knows, so every file is offered.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.fileChosen(uri?.toString())
    }
    when (val current = step) {
        is RestoreStep.ChooseFile -> RestoreChooseScreen(current, onChoose = { pick.launch(arrayOf("*/*")) }, onBack = viewModel::back)
        is RestoreStep.Phrase -> PhrasePage(
            title = stringResource(R.string.restore_phrase_title),
            body = stringResource(R.string.restore_phrase_body),
            words = current.words,
            message = current.message,
            onWords = viewModel::words,
            onContinue = viewModel::phraseDone,
            onBack = viewModel::back,
        )
        is RestoreStep.NewPin -> CreatePinScreen(
            current.pad,
            onDigit = viewModel::digit,
            onDelete = viewModel::deleteDigit,
            onOk = viewModel::pinOk,
            onBack = viewModel::back,
        )
        null -> {}
    }
}

@Composable
fun RestoreChooseScreen(step: RestoreStep.ChooseFile, onChoose: () -> Unit, onBack: () -> Unit) {
    AdaptiveScreen(
        onBack = onBack,
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(FortunaIcon.Restore)
                Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.restore_body), style = MaterialTheme.typography.bodyLarge)
                step.problem?.let { Problem(stringResource(it.message())) }
            }
        },
        action = {
            if (step.reading) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.restore_reading), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().testTag("restore-choose")) {
                    ButtonIcon(FortunaIcon.Restore)
                    Text(stringResource(R.string.restore_choose))
                }
            }
        },
    )
}

private fun RestoreProblem.message(): Int = when (this) {
    RestoreProblem.CANNOT_READ -> R.string.restore_cannot_read
    RestoreProblem.NOT_A_BACKUP -> R.string.restore_not_a_backup
    RestoreProblem.DAMAGED -> R.string.restore_damaged
    RestoreProblem.FROM_NEWER_APP -> R.string.restore_from_newer_app
}
