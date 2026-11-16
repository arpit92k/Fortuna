package io.github.arpit92k.fortuna.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.importing.IMPORT_COLUMNS
import io.github.arpit92k.fortuna.core.importing.ImportProblem
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.ui.RateDialog
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.formatRate

/**
 * Import history (UF-15), from Settings: save the empty template, fill it in
 * elsewhere, choose it, then see what it would do and decide before anything
 * is written.
 */
@Composable
fun ImportScreen(
    step: ImportStep,
    base: Currency,
    onSaveTemplate: () -> Unit,
    onChooseFile: () -> Unit,
    onReplace: (Boolean) -> Unit,
    onRate: (Currency, Rate) -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var rateFor by rememberSaveable { mutableStateOf<String?>(null) }
    Page(stringResource(R.string.import_title), onBack, "import") {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                is ImportStep.Start -> {
                    Text(stringResource(R.string.import_body), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.import_columns, IMPORT_COLUMNS.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onSaveTemplate, enabled = !step.working, modifier = Modifier.testTag("import-template")) {
                        Text(stringResource(R.string.import_save_template))
                    }
                    Button(onClick = onChooseFile, enabled = !step.working, modifier = Modifier.testTag("import-choose")) {
                        Text(stringResource(R.string.import_choose))
                    }
                    if (step.working) CircularProgressIndicator()
                    step.message?.let {
                        Text(
                            stringResource(
                                when (it) {
                                    ImportMessage.TEMPLATE_SAVED -> R.string.import_template_saved
                                    ImportMessage.TEMPLATE_NOT_SAVED -> R.string.import_template_not_saved
                                    ImportMessage.CANNOT_READ -> R.string.import_cannot_read
                                },
                            ),
                            color = if (it == ImportMessage.TEMPLATE_SAVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("import-message"),
                        )
                    }
                }
                is ImportStep.Problems -> {
                    Text(stringResource(R.string.import_problems_title), style = MaterialTheme.typography.titleMedium)
                    step.problems.forEach { problem ->
                        Text(problemText(problem), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import-problem"))
                    }
                    Text(stringResource(R.string.import_problems_nothing_changed), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onReset, modifier = Modifier.testTag("import-again")) { Text(stringResource(R.string.import_again)) }
                }
                is ImportStep.Preview -> {
                    val plan = step.plan
                    val accounts = plan.rows.map { it.account.lowercase() }.distinct().size
                    Text(
                        pluralStringResource(R.plurals.import_preview_values, plan.rows.size, plan.rows.size) + " " +
                            pluralStringResource(R.plurals.import_preview_accounts, accounts, accounts),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("import-summary"),
                    )
                    if (plan.newAccounts.isNotEmpty()) {
                        Text(stringResource(R.string.import_new_accounts), style = MaterialTheme.typography.titleSmall)
                        plan.newAccounts.forEach {
                            Text(stringResource(R.string.import_new_account, it.name, it.category.name, it.currency.code))
                        }
                    }
                    if (plan.conflicts.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = step.replace, role = Role.Switch, onValueChange = onReplace)
                                .testTag("import-replace"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                pluralStringResource(R.plurals.import_conflicts, plan.conflicts.size, plan.conflicts.size),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = step.replace, onCheckedChange = null)
                        }
                    }
                    plan.ratesNeeded.forEach { (currency, date) ->
                        val given = step.rates[currency]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (given == null) {
                                    stringResource(R.string.import_rate_needed, currency.code, formatDate(date))
                                } else {
                                    stringResource(R.string.import_rate_given, formatRate(given), formatDate(date))
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { rateFor = currency.code }, modifier = Modifier.testTag("import-rate-${currency.code}")) {
                                Text(stringResource(if (given == null) R.string.import_enter_rate else R.string.currencies_change))
                            }
                        }
                    }
                    if (step.failed) Text(stringResource(R.string.add_source_problem_not_saved), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onConfirm, enabled = step.ready && !step.working, modifier = Modifier.testTag("import-confirm")) {
                        Text(stringResource(R.string.import_confirm))
                    }
                    TextButton(onClick = onReset) { Text(stringResource(R.string.date_cancel)) }
                }
                is ImportStep.Done -> {
                    Text(
                        pluralStringResource(R.plurals.import_done, step.values, step.values, step.accounts),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("import-done"),
                    )
                    // The file is outside the app's protection.
                    Text(stringResource(R.string.import_delete_file), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onBack) { Text(stringResource(R.string.date_ok)) }
                }
            }
        }
    }

    val preview = step as? ImportStep.Preview ?: return
    val currency = rateFor?.let { Currency.forCode(it) } ?: return
    val date = preview.plan.ratesNeeded[currency] ?: return
    RateDialog(
        title = stringResource(R.string.import_rate_title, currency.code),
        foreign = currency,
        base = base,
        date = date,
        initial = preview.rates[currency],
        onConfirm = { rate, _ -> onRate(currency, rate); rateFor = null },
        onDismiss = { rateFor = null },
    )
}

@Composable
private fun problemText(problem: ImportProblem): String {
    val line = problem.line
    return when (problem) {
        is ImportProblem.NotTheTemplate -> stringResource(R.string.import_problem_template, IMPORT_COLUMNS.joinToString(","))
        is ImportProblem.NoRows -> stringResource(R.string.import_problem_no_rows)
        is ImportProblem.WrongColumnCount -> stringResource(R.string.import_problem_columns, line, problem.found)
        is ImportProblem.NoAccount -> stringResource(R.string.import_problem_no_account, line)
        is ImportProblem.BadDate -> stringResource(R.string.import_problem_date, line, problem.text)
        is ImportProblem.DateInFuture -> stringResource(R.string.import_problem_future, line, formatDate(problem.date))
        is ImportProblem.UnknownCurrency -> stringResource(R.string.import_problem_currency, line, problem.text)
        is ImportProblem.BadAmount -> stringResource(R.string.import_problem_amount, line, problem.column, problem.text)
        is ImportProblem.UnknownCategory -> stringResource(R.string.import_problem_category, line, problem.text)
        is ImportProblem.WrongCurrency -> stringResource(R.string.import_problem_wrong_currency, line, problem.account, problem.expected.code)
        is ImportProblem.Inconsistent -> stringResource(R.string.import_problem_inconsistent, line, problem.account)
        is ImportProblem.DuplicateDate -> stringResource(R.string.import_problem_duplicate, line, problem.account, formatDate(problem.date))
        is ImportProblem.OpeningHasAdded -> stringResource(R.string.import_problem_opening, line, problem.account)
        is ImportProblem.AccountClosed -> stringResource(R.string.import_problem_closed, line, problem.account)
    }
}
