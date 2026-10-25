package io.github.arpit92k.fortuna.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.source.message
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.DateButton
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.categoryIcon
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.formatMoney
import kotlinx.datetime.LocalDate

/** S-13 Update: the date, how many accounts are due, and how many are left out and why. */
@Composable
fun UpdateStartScreen(ui: UpdateUi, onDate: (LocalDate?) -> Unit, onStart: () -> Unit) {
    val plan = ui.plan
    AdaptiveScreen(
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.update_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.update_body), style = MaterialTheme.typography.bodyLarge)
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val dateText = stringResource(R.string.update_date)
                DateButton(
                    date = ui.date,
                    onDate = onDate,
                    todayText = stringResource(R.string.update_date_today),
                    dateText = { dateText.format(it) },
                    modifier = Modifier.testTag("update-date"),
                )
                if (plan != null) {
                    if (plan.due.isEmpty()) {
                        Text(stringResource(R.string.update_nothing_due), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("update-none-due"))
                    } else {
                        Text(
                            pluralStringResource(R.plurals.update_due, plan.due.size, plan.due.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("update-due"),
                        )
                    }
                    if (plan.leftOut > 0) {
                        Text(
                            pluralStringResource(R.plurals.update_left_out, plan.leftOut, plan.leftOut),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("update-left-out"),
                        )
                    }
                }
                Button(
                    onClick = onStart,
                    enabled = plan != null && plan.due.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("update-start"),
                ) { Text(stringResource(R.string.update_start)) }
            }
        },
    )
}

/** S-14 Update: one account at a time, its last value shown; enter the new one or skip. */
@Composable
fun UpdateStepScreen(
    ui: UpdateUi,
    onValue: (String) -> Unit,
    onAdded: (String) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
) {
    val current = ui.current ?: return
    val plan = ui.plan ?: return
    val form = ui.form
    AdaptiveScreen(
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.update_step_progress, ui.position, maxOf(ui.total, ui.position), formatDate(plan.date)),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("update-progress"),
                )
                categoryIcon(current.category)?.let { IconTile(it) }
                Text(current.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("update-source"))
                Text(
                    stringResource(R.string.update_step_last, formatMoney(current.lastValue), formatDate(current.lastDate)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = form.value,
                    onValueChange = onValue,
                    label = { Text(stringResource(R.string.value_value, current.lastValue.currency.code)) },
                    singleLine = true,
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("update-value"),
                )
                OutlinedTextField(
                    value = form.added,
                    onValueChange = onAdded,
                    label = { Text(stringResource(if (current.liability) R.string.value_borrowed else R.string.value_added)) },
                    supportingText = {
                        Text(stringResource(if (current.liability) R.string.value_borrowed_hint else R.string.value_added_hint))
                    },
                    singleLine = true,
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("update-added"),
                )
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNote,
                    label = { Text(stringResource(R.string.add_source_note)) },
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("update-note"),
                )
                form.problem?.let { Problem(stringResource(it.message())) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onSkip, enabled = !form.saving, modifier = Modifier.weight(1f).testTag("update-skip")) {
                        Text(stringResource(R.string.update_skip))
                    }
                    Button(onClick = onSave, enabled = !form.saving, modifier = Modifier.weight(1f).testTag("update-save")) {
                        Text(stringResource(R.string.update_save))
                    }
                }
            }
        },
    )
}
