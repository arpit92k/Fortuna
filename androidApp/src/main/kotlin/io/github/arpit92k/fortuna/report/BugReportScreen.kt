package io.github.arpit92k.fortuna.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.arpit92k.fortuna.source.ConfirmDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.ButtonIcon
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.IconTile

/**
 * S-18 Report a bug: the user describes the problem, sees the whole report,
 * and sends it from their email app. Reached from settings, from the welcome
 * and lock screens without unlocking, and from the offer after a crash.
 */
@Composable
fun BugReportScreen(
    ui: BugReportUi,
    report: String,
    onDescription: (String) -> Unit,
    onSend: () -> Unit,
    onDeleteCrashes: () -> Unit,
    onBack: () -> Unit,
) {
    // A description typed and not sent asks before it is thrown away.
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    val tryLeave: () -> Unit = {
        if (ui.description.isNotBlank()) {
            confirmingLeave = true
        } else {
            onBack()
        }
    }
    if (confirmingLeave) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_text),
            confirm = stringResource(R.string.discard_confirm),
            onConfirm = {
                confirmingLeave = false
                onBack()
            },
            onDismiss = { confirmingLeave = false },
            dismiss = stringResource(R.string.discard_keep),
        )
    }
    BackHandler(onBack = tryLeave)
    AdaptiveScreen(
        onBack = tryLeave,
        actionScrolls = false,
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(FortunaIcon.Report)
                Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.report_body), style = MaterialTheme.typography.bodyLarge)
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = ui.description,
                    onValueChange = onDescription,
                    label = { Text(stringResource(R.string.report_description)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("report-description"),
                )
                Text(stringResource(R.string.report_preview), style = MaterialTheme.typography.titleSmall)
                // Exactly what the email will hold, so nothing leaves unseen.
                Card(Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
                    Text(
                        report,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp).testTag("report-preview"),
                    )
                }
                if (ui.noEmailApp) {
                    Problem(stringResource(R.string.report_no_email_app, SUPPORT_EMAIL))
                    val clipboard = LocalClipboardManager.current
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(report)) },
                        modifier = Modifier.fillMaxWidth().testTag("report-copy"),
                    ) { Text(stringResource(R.string.report_copy)) }
                }
                Button(onClick = onSend, modifier = Modifier.fillMaxWidth().testTag("report-send")) {
                    ButtonIcon(FortunaIcon.Report)
                    Text(stringResource(R.string.report_send))
                }
                if (ui.crashes.isNotEmpty()) {
                    TextButton(onClick = onDeleteCrashes, modifier = Modifier.fillMaxWidth().testTag("report-delete-crashes")) {
                        Text(stringResource(R.string.report_delete_crashes))
                    }
                }
            }
        },
    )
}

/** On start after a crash: report it, delete the record, or decide next time. */
@Composable
fun CrashOfferDialog(onReport: () -> Unit, onDelete: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.crash_offer_title)) },
        text = { Text(stringResource(R.string.crash_offer_body)) },
        confirmButton = {
            TextButton(onClick = onReport, modifier = Modifier.testTag("crash-report")) { Text(stringResource(R.string.crash_offer_report)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, modifier = Modifier.testTag("crash-delete")) { Text(stringResource(R.string.crash_offer_delete)) }
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.crash_offer_not_now)) }
            }
        },
        modifier = Modifier.testTag("crash-offer"),
    )
}

/** The small link on the welcome and lock screens. */
@Composable
fun ReportProblemLink(onReport: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onReport, modifier = modifier.testTag("report-bug")) {
        Text(stringResource(R.string.report_link), style = MaterialTheme.typography.bodyMedium)
    }
}
