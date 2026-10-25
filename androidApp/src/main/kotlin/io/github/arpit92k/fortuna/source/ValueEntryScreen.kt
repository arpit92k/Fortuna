package io.github.arpit92k.fortuna.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.DateButton
import io.github.arpit92k.fortuna.ui.formatDate
import kotlinx.datetime.LocalDate

/**
 * S-12 Value entry. A new value is dated from the source's latest value up to
 * today; a correction keeps its date. The amount added is optional, and the
 * opening value has none.
 */
@Composable
fun ValueEntryScreen(
    form: ValueEntryForm,
    source: ValueEntrySource,
    name: String,
    editing: LocalDate?,
    onValue: (String) -> Unit,
    onAdded: (String) -> Unit,
    onDate: (LocalDate?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onReplace: () -> Unit,
    onKeep: () -> Unit,
) {
    val date = editing ?: form.date ?: source.today
    val opening = date == source.opening
    AdaptiveScreen(
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (editing == null) R.string.value_new_title else R.string.value_edit_title, name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(if (editing == null) R.string.value_new_body else R.string.value_edit_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = form.value,
                    onValueChange = onValue,
                    label = { Text(stringResource(R.string.value_value, source.currency.code)) },
                    singleLine = true,
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("value-value"),
                )
                if (opening) {
                    Text(
                        stringResource(R.string.value_opening_nothing_added),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = form.added,
                        onValueChange = onAdded,
                        label = { Text(stringResource(if (source.liability) R.string.value_borrowed else R.string.value_added)) },
                        supportingText = {
                            Text(stringResource(if (source.liability) R.string.value_borrowed_hint else R.string.value_added_hint))
                        },
                        singleLine = true,
                        enabled = !form.saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("value-added"),
                    )
                }
                if (editing == null) {
                    val dateText = stringResource(R.string.value_date)
                    DateButton(
                        date = form.date,
                        onDate = onDate,
                        todayText = stringResource(R.string.value_date_today),
                        dateText = { dateText.format(it) },
                        earliest = source.latest,
                        enabled = !form.saving,
                        modifier = Modifier.testTag("value-date"),
                    )
                } else {
                    Text(stringResource(R.string.value_date, formatDate(editing)), style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNote,
                    label = { Text(stringResource(R.string.add_source_note)) },
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("value-note"),
                )
                form.problem?.let { Problem(stringResource(it.message())) }
                Button(onClick = onSave, enabled = !form.saving, modifier = Modifier.fillMaxWidth().testTag("save-value")) {
                    Text(stringResource(R.string.value_save))
                }
            }
        },
    )

    if (form.confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.value_replace_title),
            text = stringResource(R.string.value_replace_text, formatDate(date)),
            confirm = stringResource(R.string.value_replace),
            onConfirm = onReplace,
            onDismiss = onKeep,
        )
    }
}

fun ValueEntryProblem.message(): Int = when (this) {
    ValueEntryProblem.NO_VALUE -> R.string.add_source_problem_no_amount
    ValueEntryProblem.NOT_AN_AMOUNT -> R.string.add_source_problem_not_an_amount
    ValueEntryProblem.TOO_MANY_DECIMAL_PLACES -> R.string.add_source_problem_decimal_places
    ValueEntryProblem.AMOUNT_TOO_LARGE -> R.string.add_source_problem_too_large
    ValueEntryProblem.DATE_IN_FUTURE -> R.string.add_source_problem_future
    ValueEntryProblem.BEFORE_LATEST -> R.string.value_problem_before_latest
    ValueEntryProblem.NOT_SAVED -> R.string.add_source_problem_not_saved
}
