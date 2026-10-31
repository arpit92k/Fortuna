package io.github.arpit92k.fortuna.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import io.github.arpit92k.fortuna.lock.BiometricMessage
import io.github.arpit92k.fortuna.lock.BiometricsUi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.BuildConfig
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.source.ConfirmDialog
import io.github.arpit92k.fortuna.ui.BackButton
import io.github.arpit92k.fortuna.ui.ButtonIcon
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.RateDialog
import io.github.arpit92k.fortuna.ui.ReadableWidth
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.formatRate
import kotlinx.datetime.LocalDate

/** A page of the unlocked app with a back button and a readable width. */
@Composable
private fun Page(title: String, onBack: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ReadableWidth).fillMaxWidth().padding(horizontal = 16.dp).testTag(tag)) {
            BackButton(onBack)
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            content()
        }
    }
}

/** S-15 Settings. */
@Composable
fun SettingsScreen(
    onCurrencies: () -> Unit,
    onBackup: () -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
    onAbout: () -> Unit = {},
    biometrics: BiometricsUi = BiometricsUi(),
    onBiometrics: (Boolean) -> Unit = {},
    zeroAccountsHidden: Boolean = false,
    onZeroAccountsHidden: (Boolean) -> Unit = {},
    /** Whether any account is at zero; the switch shows only then, as the dashboard's toggle does. */
    anyAtZero: Boolean = false,
) {
    Page(stringResource(R.string.settings_title), onBack, "settings") {
        // Only on a phone that can, or if it is on and the phone no longer can, so it can be turned off.
        if (biometrics.available || biometrics.enabled) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = biometrics.enabled, enabled = !biometrics.working, role = Role.Switch, onValueChange = onBiometrics)
                    .padding(8.dp, 16.dp)
                    .testTag("settings-biometrics"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(FortunaIcon.Fingerprint, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_biometrics), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_biometrics_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (biometrics.message == BiometricMessage.NOT_TURNED_ON) Problem(stringResource(R.string.biometric_not_turned_on))
                }
                Switch(checked = biometrics.enabled, onCheckedChange = null, enabled = !biometrics.working)
            }
            HorizontalDivider()
        }
        // Accounts at zero, such as a closed account or a paid-off card, off the dashboard and out of updates.
        if (anyAtZero) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = zeroAccountsHidden, role = Role.Switch, onValueChange = onZeroAccountsHidden)
                    .padding(8.dp, 16.dp)
                    .testTag("settings-hide-zero"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(FortunaIcon.Hide, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_hide_zero), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_hide_zero_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = zeroAccountsHidden, onCheckedChange = null)
            }
            HorizontalDivider()
        }
        SettingsItem(
            FortunaIcon.Currency,
            stringResource(R.string.settings_currencies),
            stringResource(R.string.settings_currencies_body),
            onCurrencies,
            "settings-currencies",
        )
        HorizontalDivider()
        SettingsItem(
            FortunaIcon.Backup,
            stringResource(R.string.settings_backup),
            stringResource(R.string.settings_backup_body),
            onBackup,
            "settings-backup",
        )
        HorizontalDivider()
        SettingsItem(
            FortunaIcon.Phone,
            stringResource(R.string.settings_about),
            stringResource(R.string.settings_about_body),
            onAbout,
            "settings-about",
        )
        HorizontalDivider()
        SettingsItem(
            FortunaIcon.Report,
            stringResource(R.string.settings_report),
            stringResource(R.string.settings_report_body),
            onReport,
            "settings-report",
        )
        HorizontalDivider()
        // Debug builds only: a crash on purpose, to check it is recorded and offered on the next start.
        if (BuildConfig.DEBUG) {
            TextButton(
                onClick = { throw IllegalStateException("A crash on purpose, with £1,234.56 in its message, which is never recorded") },
                modifier = Modifier.padding(top = 8.dp).testTag("settings-crash"),
            ) { Text("Crash the app (debug builds only)") }
        }
    }
}

@Composable
private fun SettingsItem(icon: FortunaIcon, title: String, body: String, onClick: () -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp, 16.dp).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 40.dp)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * S-17 Backup: what the file is and who can open it, when the last one was
 * made and whether it holds everything, and a way to save a new one where the
 * user picks.
 */
@Composable
fun BackupScreen(ui: BackupUi, onSave: () -> Unit, onBack: () -> Unit) {
    Page(stringResource(R.string.backup_title), onBack, "backup") {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(FortunaIcon.Backup)
            Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyLarge)
            val last = ui.lastBackup
            Text(
                when {
                    last == null -> stringResource(R.string.backup_never)
                    ui.upToDate -> stringResource(R.string.backup_last_up_to_date, formatDate(last))
                    else -> stringResource(R.string.backup_last_changed, formatDate(last))
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("backup-status"),
            )
            when (ui.message) {
                BackupMessage.SAVED -> Text(
                    stringResource(R.string.backup_saved),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("backup-saved"),
                )
                BackupMessage.NOT_SAVED -> Problem(stringResource(R.string.backup_not_saved))
                BackupMessage.CHANGED_WHILE_SAVING -> Problem(stringResource(R.string.backup_changed_while_saving))
                null -> {}
            }
            if (ui.saving) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.backup_saving), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().testTag("backup-save")) {
                    ButtonIcon(FortunaIcon.Backup)
                    Text(stringResource(R.string.backup_save))
                }
            }
        }
    }
}

/** Which rate dialog or confirmation is open on S-16. */
private sealed interface Editing {
    data class Add(val currency: String) : Editing

    data class Change(val currency: String, val date: String) : Editing

    data class Delete(val currency: String, val date: String) : Editing
}

/**
 * S-16 Currencies and rates: each foreign currency in use, the accounts in it,
 * and its rates newest first. A rate can be added for any date up to today,
 * corrected, or deleted after asking, unless an account's opening value needs it.
 */
@Composable
fun CurrenciesScreen(
    ui: CurrenciesUi?,
    onSave: (Currency, LocalDate?, Rate) -> Unit,
    onDelete: (Currency, LocalDate) -> Unit,
    onClearProblem: () -> Unit,
    onBack: () -> Unit,
) {
    // Kept as plain text so it survives rotation; looked up again against the current figures.
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingKey?.let(::decode)
    Page(stringResource(R.string.currencies_title), onBack, "currencies") {
        if (ui == null) return@Page
        if (ui.currencies.isEmpty()) {
            Text(
                stringResource(R.string.currencies_none, ui.base.code),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp).testTag("currencies-none"),
            )
            return@Page
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            for (entry in ui.currencies) {
                item(key = entry.currency.code) {
                    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("currency-${entry.currency.code}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${entry.currency.code} · ${entry.currency.name}", style = MaterialTheme.typography.titleMedium)
                            if (entry.usedBy.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.currencies_used_by, entry.usedBy.joinToString(", ")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { onClearProblem(); editingKey = encode(Editing.Add(entry.currency.code)) },
                                modifier = Modifier.testTag("rate-add-${entry.currency.code}"),
                            ) { Text(stringResource(R.string.currencies_add_rate)) }
                        }
                    }
                }
                items(entry.rates, key = { "${entry.currency.code}-${it.date}" }) { dated ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("rate-${entry.currency.code}-${dated.date}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(formatRate(dated.value), style = MaterialTheme.typography.bodyLarge)
                            Text(formatDate(dated.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onClearProblem(); editingKey = encode(Editing.Change(entry.currency.code, dated.date.toString())) }) {
                            Text(stringResource(R.string.currencies_change))
                        }
                        TextButton(
                            onClick = { onClearProblem(); editingKey = encode(Editing.Delete(entry.currency.code, dated.date.toString())) },
                            modifier = Modifier.testTag("rate-delete-${entry.currency.code}-${dated.date}"),
                        ) { Text(stringResource(R.string.source_delete)) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // A rate that could not be saved or deleted is explained in a pop-up.
    ui?.problem?.let { problem ->
        AlertDialog(
            onDismissRequest = onClearProblem,
            title = { Text(stringResource(R.string.currencies_problem_title)) },
            text = { Text(problemText(problem)) },
            confirmButton = {
                TextButton(onClick = onClearProblem, modifier = Modifier.testTag("rate-problem-ok")) {
                    Text(stringResource(R.string.date_ok))
                }
            },
            modifier = Modifier.testTag("rate-problem"),
        )
    }

    if (ui == null || editing == null) return
    val close = { editingKey = null }
    when (editing) {
        is Editing.Add -> {
            val currency = Currency.forCode(editing.currency) ?: return
            RateDialog(
                title = stringResource(R.string.currencies_add_title, currency.code),
                foreign = currency,
                base = ui.base,
                date = null,
                pickDate = true,
                initial = ui.currencies.firstOrNull { it.currency == currency }?.rates?.firstOrNull()?.value,
                onConfirm = { rate, date -> onSave(currency, date, rate); close() },
                onDismiss = close,
            )
        }
        is Editing.Change -> {
            val currency = Currency.forCode(editing.currency) ?: return
            val date = LocalDate.parse(editing.date)
            RateDialog(
                title = stringResource(R.string.currencies_change_title, currency.code),
                foreign = currency,
                base = ui.base,
                date = date,
                initial = ui.currencies.firstOrNull { it.currency == currency }?.rates?.firstOrNull { it.date == date }?.value,
                onConfirm = { rate, _ -> onSave(currency, date, rate); close() },
                onDismiss = close,
            )
        }
        is Editing.Delete -> {
            val currency = Currency.forCode(editing.currency) ?: return
            val date = LocalDate.parse(editing.date)
            ConfirmDialog(
                title = stringResource(R.string.currencies_delete_title),
                text = stringResource(R.string.currencies_delete_text, currency.code, formatDate(date)),
                confirm = stringResource(R.string.source_delete),
                onConfirm = { onDelete(currency, date); close() },
                onDismiss = close,
            )
        }
    }
}

@Composable
private fun problemText(problem: RateActionProblem): String = when (problem) {
    is RateActionProblem.NeededBy -> stringResource(R.string.currencies_problem_needed, problem.source, formatDate(problem.date))
    RateActionProblem.InFuture -> stringResource(R.string.add_source_problem_future)
    RateActionProblem.NotSaved -> stringResource(R.string.add_source_problem_not_saved)
}

private fun encode(editing: Editing): String = when (editing) {
    is Editing.Add -> "add|${editing.currency}"
    is Editing.Change -> "change|${editing.currency}|${editing.date}"
    is Editing.Delete -> "delete|${editing.currency}|${editing.date}"
}

private fun decode(key: String): Editing? {
    val parts = key.split('|')
    return when (parts.firstOrNull()) {
        "add" -> Editing.Add(parts[1])
        "change" -> Editing.Change(parts[1], parts[2])
        "delete" -> Editing.Delete(parts[1], parts[2])
        else -> null
    }
}
