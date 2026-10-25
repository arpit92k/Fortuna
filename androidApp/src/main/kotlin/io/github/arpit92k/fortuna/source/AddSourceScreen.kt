package io.github.arpit92k.fortuna.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.domain.Category
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.formatDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.Instant
import java.time.ZoneOffset

/** S-11 Add source, in the base currency for now. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(
    form: AddSourceForm,
    categories: List<Category>,
    currency: Currency,
    onName: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onAmount: (String) -> Unit,
    onDate: (LocalDate?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val chosen = categories.firstOrNull { it.id == form.categoryId }

    AdaptiveScreen(
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.add_source_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.add_source_body), style = MaterialTheme.typography.bodyLarge)
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = onName,
                    label = { Text(stringResource(R.string.add_source_name)) },
                    singleLine = true,
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("source-name"),
                )

                Text(stringResource(R.string.add_source_category), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (category in categories) {
                        FilterChip(
                            selected = category.id == form.categoryId,
                            onClick = { onCategory(category) },
                            enabled = !form.saving,
                            label = { Text(category.name) },
                            modifier = Modifier.testTag("category-${category.id.value}"),
                        )
                    }
                }
                if (chosen != null) {
                    Text(
                        stringResource(
                            if (chosen.kind == CategoryKind.LIABILITY) R.string.add_source_is_liability else R.string.add_source_is_asset,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = form.amount,
                    onValueChange = onAmount,
                    label = { Text(stringResource(R.string.add_source_amount, currency.code)) },
                    singleLine = true,
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("source-amount"),
                )

                OutlinedButton(
                    onClick = { pickingDate = true },
                    enabled = !form.saving,
                    modifier = Modifier.fillMaxWidth().testTag("source-date"),
                ) {
                    val date = form.date
                    Text(
                        if (date == null) stringResource(R.string.add_source_date_today)
                        else stringResource(R.string.add_source_date, formatDate(date)),
                    )
                }

                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNote,
                    label = { Text(stringResource(R.string.add_source_note)) },
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("source-note"),
                )

                form.problem?.let { Problem(stringResource(it.message())) }

                Button(onClick = onSave, enabled = !form.saving, modifier = Modifier.fillMaxWidth().testTag("save-source")) {
                    Text(stringResource(R.string.add_source_save))
                }
            }
        },
    )

    if (pickingDate) {
        // The picker works in UTC midnights; no date after today can be chosen.
        val today = java.time.LocalDate.now()
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (form.date?.toJavaLocalDate() ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDate(if (picked == today) null else picked.toKotlinLocalDate())
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.add_source_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.add_source_date_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun AddSourceProblem.message(): Int = when (this) {
    AddSourceProblem.NO_NAME -> R.string.add_source_problem_no_name
    AddSourceProblem.NAME_IN_USE -> R.string.add_source_problem_name_in_use
    AddSourceProblem.NO_CATEGORY -> R.string.add_source_problem_no_category
    AddSourceProblem.NO_AMOUNT -> R.string.add_source_problem_no_amount
    AddSourceProblem.NOT_AN_AMOUNT -> R.string.add_source_problem_not_an_amount
    AddSourceProblem.TOO_MANY_DECIMAL_PLACES -> R.string.add_source_problem_decimal_places
    AddSourceProblem.AMOUNT_TOO_LARGE -> R.string.add_source_problem_too_large
    AddSourceProblem.DATE_IN_FUTURE -> R.string.add_source_problem_future
    AddSourceProblem.NOT_SAVED -> R.string.add_source_problem_not_saved
}
