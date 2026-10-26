package io.github.arpit92k.fortuna.lock

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.FortunaIconImage

/** Offered once, after setup or a restore: turn on biometric unlock, or not now. */
@Composable
fun BiometricOfferDialog(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        icon = { FortunaIconImage(FortunaIcon.Fingerprint) },
        title = { Text(stringResource(R.string.biometric_offer_title)) },
        text = { Text(stringResource(R.string.biometric_offer_body)) },
        confirmButton = { TextButton(onClick = onYes, modifier = Modifier.testTag("biometric-offer-yes")) { Text(stringResource(R.string.biometric_offer_yes)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.biometric_offer_no)) } },
        modifier = Modifier.testTag("biometric-offer"),
    )
}
