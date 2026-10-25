package io.github.arpit92k.fortuna.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.report.ReportProblemLink
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.ButtonIcon
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.FortunaIconImage
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.PinEntry
import kotlin.time.Duration.Companion.hours

/** S-07 Lock: the PIN, the wait after wrong ones, and the way in with the recovery phrase. */
@Composable
fun LockScreen(
    ui: LockUi,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    onForgotPin: () -> Unit,
    onReport: () -> Unit = {},
    biometrics: BiometricsUi = BiometricsUi(),
    onBiometric: () -> Unit = {},
    onShown: () -> Unit = {},
    onEnableAfterUnlock: (Boolean) -> Unit = {},
) {
    // Each time the lock screen comes to the front, it asks for a fingerprint by itself, once per lock.
    LifecycleResumeEffect(Unit) {
        onShown()
        onPauseOrDispose {}
    }
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
                    LockMessage.DATA_DAMAGED -> Problem(stringResource(R.string.lock_data_damaged))
                    LockMessage.DATA_FROM_NEWER_APP -> Problem(stringResource(R.string.lock_data_newer))
                    null -> {}
                }
                when (biometrics.message) {
                    BiometricMessage.CHANGED -> Problem(stringResource(R.string.biometric_changed))
                    BiometricMessage.STOPPED_WORKING -> Problem(stringResource(R.string.biometric_stopped))
                    BiometricMessage.NOT_TURNED_ON, null -> {}
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
                // Not on yet, on a phone that can: it can be chosen here, and is turned on once the PIN is right.
                if (biometrics.canOfferOnLockScreen) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = biometrics.enableAfterUnlock, role = Role.Switch, onValueChange = onEnableAfterUnlock)
                            .testTag("biometric-next-time"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FortunaIconImage(FortunaIcon.Fingerprint)
                        Text(stringResource(R.string.biometric_next_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = biometrics.enableAfterUnlock, onCheckedChange = null)
                    }
                }
                if (biometrics.enabled) {
                    OutlinedButton(onClick = onBiometric, enabled = !biometrics.working, modifier = Modifier.testTag("use-biometric")) {
                        ButtonIcon(FortunaIcon.Fingerprint)
                        Text(stringResource(R.string.biometric_use))
                    }
                }
                TextButton(onClick = onForgotPin, modifier = Modifier.testTag("forgot-pin")) {
                    Text(stringResource(R.string.lock_forgot_pin))
                }
                // A problem with unlocking can be reported without getting in.
                ReportProblemLink(onReport)
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
