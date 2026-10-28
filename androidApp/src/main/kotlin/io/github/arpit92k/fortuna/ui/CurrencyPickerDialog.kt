package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Currency

/**
 * The currencies matching [query] by code or name: [first] at the top, then
 * those in [inUse], then the rest in the list's order. Each appears once.
 */
fun currencyChoices(query: String, first: Currency?, inUse: List<Currency> = emptyList()): List<Currency> {
    val text = query.trim()
    val matching = Currency.all.filter {
        text.isEmpty() || it.code.contains(text, ignoreCase = true) || it.name.contains(text, ignoreCase = true)
    }
    val top = (listOfNotNull(first) + inUse).distinct().filter { it in matching }
    return top + (matching - top.toSet())
}

/**
 * The currencies of [accounts]' values, the most used first, and among those
 * used as often, in code order.
 */
fun currenciesInUse(accounts: List<Currency>): List<Currency> =
    accounts.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<Currency, Int>> { it.value }.thenBy { it.key.code })
        .map { it.key }

/**
 * Choose a currency from the fixed list, searching by code or name.
 * [suggested] comes first, then the currencies [inUse] by accounts already.
 */
@Composable
fun CurrencyPickerDialog(
    suggested: Currency?,
    onChoose: (Currency) -> Unit,
    onDismiss: () -> Unit,
    inUse: List<Currency> = emptyList(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.currency_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.currency_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().testTag("currency-pick-search"),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("currency-pick-list")) {
                    items(currencyChoices(query, suggested, inUse), key = { it.code }) { currency ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(currency) }
                                .padding(vertical = 12.dp)
                                .testTag("currency-pick-${currency.code}"),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(currency.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                            Text(currency.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.date_cancel)) } },
    )
}
