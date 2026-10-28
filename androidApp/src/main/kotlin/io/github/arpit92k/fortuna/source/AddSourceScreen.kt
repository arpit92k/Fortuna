package io.github.arpit92k.fortuna.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.dated.Dated
import io.github.arpit92k.fortuna.core.domain.Category
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.setup.Problem
import io.github.arpit92k.fortuna.ui.AdaptiveScreen
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.FortunaIconImage
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.categoryIcon
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.CurrencyPickerDialog
import io.github.arpit92k.fortuna.ui.DateButton
import io.github.arpit92k.fortuna.ui.RateDialog
import io.github.arpit92k.fortuna.ui.formatRate
import kotlinx.datetime.LocalDate

/**
 * S-11 Add source. The currency is the base currency unless another is
 * chosen. A foreign currency with no rate on or before the date asks for one;
 * otherwise the screen says which rate converts it.
 *
 * @param rateInUse the rate the chosen currency would use on the chosen date, if it has one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSourceScreen(
    form: AddSourceForm,
    categories: List<Category>,
    base: Currency,
    rateInUse: Dated<Rate>?,
    today: LocalDate,
    onName: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onCurrency: (Currency?) -> Unit,
    onAmount: (String) -> Unit,
    onOpeningGain: (String) -> Unit = {},
    onDate: (LocalDate?) -> Unit,
    onRate: (Rate?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onBack: (() -> Unit)? = null,
    /** The currencies of the accounts already added, offered first after the base currency. */
    currenciesInUse: List<Currency> = emptyList(),
) {
    val chosen = categories.firstOrNull { it.id == form.categoryId }
    val currency = form.currency ?: base
    val foreign = currency != base
    var pickingCurrency by rememberSaveable { mutableStateOf(false) }
    var enteringRate by rememberSaveable { mutableStateOf(false) }

    AdaptiveScreen(
        // Leaving without saving forgets what was typed, as the system back does.
        onBack = onBack,
        info = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(FortunaIcon.Add)
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
                        val icon = categoryIcon(category.name)
                        FilterChip(
                            selected = category.id == form.categoryId,
                            onClick = { onCategory(category) },
                            enabled = !form.saving,
                            label = { Text(category.name) },
                            leadingIcon = if (icon != null) {
                                { FortunaIconImage(icon, size = 18.dp) }
                            } else {
                                null
                            },
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

                OutlinedButton(
                    onClick = { pickingCurrency = true },
                    enabled = !form.saving,
                    modifier = Modifier.fillMaxWidth().testTag("source-currency"),
                ) {
                    Text(stringResource(R.string.add_source_currency, currency.code, currency.name))
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

                // An account that already existed may have gained or lost before it is added here.
                val owed = chosen?.kind == CategoryKind.LIABILITY
                OutlinedTextField(
                    value = form.openingGain,
                    onValueChange = onOpeningGain,
                    label = { Text(stringResource(if (owed) R.string.add_source_opening_interest else R.string.add_source_opening_gain, currency.code)) },
                    supportingText = {
                        Text(stringResource(if (owed) R.string.add_source_opening_interest_hint else R.string.add_source_opening_gain_hint))
                    },
                    singleLine = true,
                    enabled = !form.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("source-opening-gain"),
                )

                val dateText = stringResource(R.string.add_source_date)
                DateButton(
                    date = form.date,
                    onDate = onDate,
                    todayText = stringResource(R.string.add_source_date_today),
                    dateText = { dateText.format(it) },
                    enabled = !form.saving,
                    modifier = Modifier.testTag("source-date"),
                )

                if (foreign) {
                    val rate = form.rate
                    when {
                        rateInUse != null -> Text(
                            stringResource(R.string.add_source_rate_in_use, formatRate(rateInUse.value), formatDate(rateInUse.date)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("source-rate-in-use"),
                        )
                        rate != null -> TextButton(onClick = { enteringRate = true }, modifier = Modifier.testTag("source-rate")) {
                            Text(stringResource(R.string.add_source_rate_entered, formatRate(rate)))
                        }
                        else -> OutlinedButton(
                            onClick = { enteringRate = true },
                            enabled = !form.saving,
                            modifier = Modifier.fillMaxWidth().testTag("source-rate"),
                        ) {
                            Text(stringResource(R.string.add_source_rate_needed, formatDate(form.date ?: today)))
                        }
                    }
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

    if (pickingCurrency) {
        CurrencyPickerDialog(
            suggested = base,
            inUse = currenciesInUse,
            onChoose = {
                onCurrency(it.takeIf { c -> c != base })
                pickingCurrency = false
            },
            onDismiss = { pickingCurrency = false },
        )
    }
    if (enteringRate && foreign) {
        RateDialog(
            title = stringResource(R.string.add_source_rate_title, currency.code),
            foreign = currency,
            base = base,
            date = form.date ?: today,
            initial = form.rate,
            onConfirm = { rate, _ ->
                onRate(rate)
                enteringRate = false
            },
            onDismiss = { enteringRate = false },
        )
    }
}

private fun AddSourceProblem.message(): Int = when (this) {
    AddSourceProblem.NO_NAME -> R.string.add_source_problem_no_name
    AddSourceProblem.NAME_IN_USE -> R.string.add_source_problem_name_in_use
    AddSourceProblem.NO_CATEGORY -> R.string.add_source_problem_no_category
    AddSourceProblem.NO_AMOUNT -> R.string.add_source_problem_no_amount
    AddSourceProblem.NOT_AN_AMOUNT -> R.string.add_source_problem_not_an_amount
    AddSourceProblem.GAIN_NOT_AN_AMOUNT -> R.string.add_source_problem_opening_gain
    AddSourceProblem.TOO_MANY_DECIMAL_PLACES -> R.string.add_source_problem_decimal_places
    AddSourceProblem.AMOUNT_TOO_LARGE -> R.string.add_source_problem_too_large
    AddSourceProblem.DATE_IN_FUTURE -> R.string.add_source_problem_future
    AddSourceProblem.RATE_NEEDED -> R.string.add_source_problem_rate_needed
    AddSourceProblem.NOT_SAVED -> R.string.add_source_problem_not_saved
}
