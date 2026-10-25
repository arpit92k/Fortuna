package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.core.rates.RateProblem
import kotlinx.datetime.LocalDate

/**
 * Enter or correct an exchange rate between [foreign] and [base] (UF-09). The
 * rate is typed either way round, as the user's source quotes it, and kept as
 * typed: "1 USD = 0.75 GBP" or "1 GBP = 1.33 USD". It starts with the way
 * [initial] was typed, or one unit of the foreign currency.
 *
 * With [pickDate], the user also chooses the date, no later than today; otherwise
 * the rate is for [date].
 */
@Composable
fun RateDialog(
    title: String,
    foreign: Currency,
    base: Currency,
    date: LocalDate?,
    onConfirm: (Rate, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    initial: Rate? = null,
    pickDate: Boolean = false,
) {
    var foreignFirst by rememberSaveable { mutableStateOf(initial?.unit != base) }
    var text by rememberSaveable { mutableStateOf(initial?.let { rateText(it) } ?: "") }
    var chosenDate by rememberSaveable { mutableStateOf(date?.toString()) }
    var problem by rememberSaveable { mutableStateOf<RateProblem?>(null) }
    val unit = if (foreignFirst) foreign else base
    val quote = if (foreignFirst) base else foreign

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pickDate) {
                    val dateText = stringResource(R.string.rate_date)
                    DateButton(
                        date = chosenDate?.let { LocalDate.parse(it) },
                        onDate = { chosenDate = it?.toString() },
                        todayText = stringResource(R.string.rate_date_today),
                        dateText = { dateText.format(it) },
                        modifier = Modifier.testTag("rate-date"),
                    )
                } else if (date != null) {
                    Text(stringResource(R.string.rate_date, formatDate(date)), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1 ${unit.code} =", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; problem = null },
                        singleLine = true,
                        suffix = { Text(quote.code) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("rate-value"),
                    )
                }
                TextButton(
                    onClick = { foreignFirst = !foreignFirst; problem = null },
                    modifier = Modifier.fillMaxWidth().testTag("rate-swap"),
                ) {
                    Text(stringResource(R.string.rate_swap, quote.code, unit.code))
                }
                problem?.let {
                    Text(stringResource(it.message()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val parsed = parseRate(text, unit, quote)) {
                        is RateParseResult.Success -> onConfirm(parsed.rate, if (pickDate) chosenDate?.let { LocalDate.parse(it) } else date)
                        is RateParseResult.Failure -> problem = parsed.problem
                    }
                },
                modifier = Modifier.testTag("rate-save"),
            ) { Text(stringResource(R.string.rate_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.date_cancel)) } },
    )
}

private fun RateProblem.message(): Int = when (this) {
    RateProblem.EMPTY -> R.string.rate_problem_empty
    RateProblem.NOT_A_NUMBER -> R.string.rate_problem_not_a_number
    RateProblem.NOT_POSITIVE -> R.string.rate_problem_not_positive
    RateProblem.TOO_MANY_DECIMAL_PLACES, RateProblem.TOO_MANY_DIGITS -> R.string.rate_problem_too_long
}
