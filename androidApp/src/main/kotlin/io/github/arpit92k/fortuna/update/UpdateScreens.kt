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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.source.message
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.DateButton
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.RateDialog
import io.github.arpit92k.fortuna.ui.formatRate
import io.github.arpit92k.fortuna.ui.categoryIcon
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.amount
import io.github.arpit92k.fortuna.ui.readsHidden
import kotlinx.datetime.LocalDate

/** S-13 Update: the date, how many accounts are due, and how many are left out and why. */
@Composable
fun UpdateStartScreen(ui: UpdateUi, base: Currency?, onDate: (LocalDate?) -> Unit, onRate: (Currency, Rate) -> Unit, onStart: () -> Unit) {
    val plan = ui.plan
    var renewing by rememberSaveable { mutableStateOf<String?>(null) }
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
                    if (plan.rates.isNotEmpty() && plan.due.isNotEmpty()) {
                        Text(stringResource(R.string.update_rates), style = MaterialTheme.typography.titleSmall)
                        for (rate in plan.rates) {
                            RateRow(rate, onRenew = { renewing = rate.currency.code })
                        }
                        if (ui.rateNotSaved) Problem(stringResource(R.string.add_source_problem_not_saved))
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

    val renew = plan?.rates?.firstOrNull { it.currency.code == renewing }
    if (plan != null && renew != null && base != null) {
        RateDialog(
            title = stringResource(R.string.update_rate_title, renew.currency.code),
            foreign = renew.currency,
            base = base,
            date = plan.date,
            initial = renew.last,
            onConfirm = { rate, _ ->
                onRate(renew.currency, rate)
                renewing = null
            },
            onDismiss = { renewing = null },
        )
    }
}

/** A currency's rate in force on the update's date, how old it is, and a way to enter a newer one. Keeping it needs nothing. */
@Composable
private fun RateRow(rate: RateToConfirm, onRenew: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("update-rate-${rate.currency.code}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val last = rate.last
            Text(if (last != null) formatRate(last) else rate.currency.code, style = MaterialTheme.typography.bodyLarge)
            val age = rate.ageInDays
            Text(
                when (age) {
                    null -> stringResource(R.string.update_rate_none)
                    0 -> stringResource(R.string.update_rate_today)
                    else -> pluralStringResource(R.plurals.update_rate_age, age, age)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRenew, modifier = Modifier.testTag("update-rate-new-${rate.currency.code}")) {
            Text(stringResource(R.string.update_rate_new))
        }
    }
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
                val last = stringResource(R.string.update_step_last, amount(current.lastValue), formatDate(current.lastDate))
                Text(last, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.readsHidden(last))
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
