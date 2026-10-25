package io.github.arpit92k.fortuna.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.PinEntry
import kotlin.time.Duration.Companion.hours

/** S-07 Lock: the PIN, the wait after wrong ones, and the way in with the recovery phrase. */
@Composable
fun LockScreen(ui: LockUi, onDigit: (Char) -> Unit, onDelete: () -> Unit, onOk: () -> Unit, onForgotPin: () -> Unit) {
    AdaptiveScreen(
        actionAtBottom = true,
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                IconTile(FortunaIcon.Lock)
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.lock_body), style = MaterialTheme.typography.bodyLarge)
                when (ui.message) {
                    LockMessage.WRONG_PIN -> Problem(stringResource(R.string.lock_wrong_pin))
                    LockMessage.NOT_THIS_DEVICE -> Problem(stringResource(R.string.lock_not_this_device))
                    null -> {}
                }
                if (ui.waiting) {
                    Text(
                        stringResource(R.string.lock_wait, formatWait(ui.wait)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("lock-wait"),
                    )
                }
                // Once the waits reach an hour, the phrase is the quicker way in.
                if (ui.wait >= 1.hours) {
                    Text(stringResource(R.string.lock_phrase_quicker), style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = onForgotPin, modifier = Modifier.testTag("forgot-pin")) {
                    Text(stringResource(R.string.lock_forgot_pin))
                }
            }
        },
        action = {
            PinEntry(
                digits = ui.entered.length,
                working = ui.working,
                onDigit = onDigit,
                onDelete = onDelete,
                onOk = onOk,
                okEnabled = ui.okEnabled,
                enabled = ui.padEnabled,
            )
        },
    )
}
