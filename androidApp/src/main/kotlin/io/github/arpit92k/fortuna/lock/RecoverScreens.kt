package io.github.arpit92k.fortuna.lock

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.setup.CreatePinScreen
import io.github.arpit92k.fortuna.ui.PhrasePage

/** S-08 Enter recovery phrase, then a new PIN on the S-02 pad (UF-14). */
@Composable
fun RecoverFlow(viewModel: RecoverViewModel) {
    val step by viewModel.step.collectAsState()
    BackHandler { viewModel.back() }
    when (val current = step) {
        is RecoverStep.Phrase -> PhrasePage(
            title = stringResource(R.string.recover_title),
            body = stringResource(R.string.recover_body),
            words = current.words,
            message = current.message,
            onWords = viewModel::words,
            onContinue = viewModel::phraseDone,
            onBack = viewModel::back,
        )
        is RecoverStep.NewPin -> CreatePinScreen(
            current.pad,
            onDigit = viewModel::digit,
            onDelete = viewModel::deleteDigit,
            onOk = viewModel::pinOk,
            title = stringResource(R.string.recover_pin_title),
            body = stringResource(R.string.recover_pin_body),
            onBack = viewModel::back,
        )
        null -> {}
    }
}
