package io.github.arpit92k.fortuna.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.Centred
import io.github.arpit92k.fortuna.ui.PinDots
import io.github.arpit92k.fortuna.ui.PinPad

/** S-07 Lock: the PIN, and the wait after wrong ones. */
@Composable
fun LockScreen(ui: LockUi, onDigit: (Char) -> Unit, onDelete: () -> Unit, onOk: () -> Unit) {
    AdaptiveScreen(
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            }
        },
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                PinDots(ui.entered.length)
                if (ui.working) Centred { CircularProgressIndicator() }
                PinPad(onDigit = onDigit, onDelete = onDelete, onOk = onOk, okEnabled = ui.okEnabled, enabled = ui.padEnabled)
            }
        },
    )
}
