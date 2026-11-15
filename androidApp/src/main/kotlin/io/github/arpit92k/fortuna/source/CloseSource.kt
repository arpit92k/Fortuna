package io.github.arpit92k.fortuna.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.ui.DateButton
import io.github.arpit92k.fortuna.ui.formatDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Closing a source (UF-07): the date, after its latest value, and what was
 * moved out, or for a debt repaid. Left blank, that is all of the last value,
 * so the source ends at zero with nothing lost; less, such as nothing for an
 * asset written off, counts the rest as a loss. The source then leaves the
 * dashboard and the update, and keeps its history.
 */
@Composable
fun CloseSourceDialog(
    name: String,
    liability: Boolean,
    lastValue: Money,
    latestDate: LocalDate,
    today: LocalDate,
    problem: ValueEntryProblem?,
    onClose: (date: LocalDate, movedOut: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val earliest = latestDate.plus(DatePeriod(days = 1))
    var date by rememberSaveable { mutableStateOf<String?>(null) }
    // Blank is all of it, so the last value is never put in the form while amounts are hidden.
    var moved by rememberSaveable { mutableStateOf("") }
    val canClose = earliest <= today
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.close_title, name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!canClose) {
                    // Its latest value is today's: closing comes on a later date.
                    Text(stringResource(R.string.close_not_yet, formatDate(latestDate)), modifier = Modifier.testTag("close-not-yet"))
                    return@Column
                }
                Text(stringResource(if (liability) R.string.close_text_liability else R.string.close_text))
                val dateText = stringResource(R.string.close_date)
                DateButton(
                    date = date?.let(LocalDate::parse),
                    onDate = { date = it?.toString() },
                    todayText = stringResource(R.string.close_date_today),
                    dateText = { dateText.format(it) },
                    earliest = earliest,
                    modifier = Modifier.testTag("close-date"),
                )
                OutlinedTextField(
                    value = moved,
                    onValueChange = { moved = it },
                    label = { Text(stringResource(if (liability) R.string.close_repaid else R.string.close_moved_out, lastValue.currency.code)) },
                    supportingText = { Text(stringResource(if (liability) R.string.close_repaid_hint else R.string.close_moved_out_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("close-moved"),
                )
                problem?.let {
                    val text = if (it == ValueEntryProblem.BEFORE_LATEST) {
                        stringResource(R.string.close_problem_before_latest, formatDate(latestDate))
                    } else {
                        stringResource(it.message())
                    }
                    Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("close-problem"))
                }
            }
        },
        confirmButton = {
            if (canClose) {
                TextButton(
                    onClick = { onClose(date?.let(LocalDate::parse) ?: today, moved) },
                    modifier = Modifier.testTag("close-confirm"),
                ) { Text(stringResource(R.string.close_confirm)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close-dismiss")) {
                Text(stringResource(if (canClose) R.string.date_cancel else R.string.date_ok))
            }
        },
        modifier = Modifier.testTag("close-dialog"),
    )
}

