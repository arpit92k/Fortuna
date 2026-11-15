package io.github.arpit92k.fortuna.source

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.ui.ChartCardTitle
import io.github.arpit92k.fortuna.ui.BackButton
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.ReadableWidth
import io.github.arpit92k.fortuna.ui.ValueChart
import io.github.arpit92k.fortuna.ui.formatPercent
import io.github.arpit92k.fortuna.ui.gainColor
import io.github.arpit92k.fortuna.ui.gainText
import io.github.arpit92k.fortuna.core.insights.Gain
import io.github.arpit92k.fortuna.ui.percent
import io.github.arpit92k.fortuna.ui.categoryIcon
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.AmountText
import io.github.arpit92k.fortuna.ui.change
import io.github.arpit92k.fortuna.ui.formatSignedMoney
import io.github.arpit92k.fortuna.ui.readsHidden
import kotlinx.datetime.LocalDate

/** A change written with its sign, so growth and losses read at a glance: +£500.00, -£20.00. */
fun formatChange(money: Money): String = formatSignedMoney(money)

/**
 * S-10 Source: the value now, the totals since the opening balance, the value
 * over time, and every value with what changed. Each value can be corrected;
 * the latest can be deleted, and a source with only its opening value can be
 * deleted outright. Deleting asks first. A source can be closed, and a
 * closed one reopened (UF-07).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcePageScreen(
    ui: SourcePageUi?,
    onAddValue: () -> Unit,
    onOpenChart: () -> Unit,
    onShowInBase: (Boolean) -> Unit,
    onEdit: (LocalDate) -> Unit,
    onDeleteLatest: () -> Unit,
    onDeleteSource: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    closing: SourceClosing? = null,
) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var closeOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        if (ui == null) {
            CircularProgressIndicator(Modifier.padding(48.dp))
            return@Box
        }
        LazyColumn(
            Modifier.widthIn(max = ReadableWidth).fillMaxWidth().testTag("source-page"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBack) BackButton(onBack)
                }
            }
            item { Header(ui, onShowInBase) }
            item {
                Card(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("source-chart")) {
                    Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartCardTitle(stringResource(R.string.source_chart_title), onOpenChart)
                        // In the source's own currency, as its statements show it, unless the page shows the base currency.
                        ValueChart(ui.shownChart, Modifier.padding(end = 12.dp))
                    }
                }
            }
            item {
                FlowRow(
                    Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.closed) {
                        // Closed: its history stays as it was, until it is reopened.
                        if (closing != null) {
                            Button(onClick = closing.onReopen, modifier = Modifier.testTag("reopen-source")) {
                                Text(stringResource(R.string.source_reopen))
                            }
                        }
                    } else {
                        Button(onClick = onAddValue, modifier = Modifier.testTag("add-value")) { Text(stringResource(R.string.source_add_value)) }
                        if (closing != null) {
                            OutlinedButton(onClick = { closing.onClearProblem(); closeOpen = true }, modifier = Modifier.testTag("close-source")) {
                                Text(stringResource(R.string.source_close))
                            }
                        }
                        if (ui.canDeleteSource) {
                            OutlinedButton(onClick = { confirm = DELETE_SOURCE }, modifier = Modifier.testTag("delete-source")) {
                                Text(stringResource(R.string.source_delete_source))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.source_history),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(ui.entries, key = { it.date.toString() }) { row ->
                HistoryItem(
                    row = row,
                    liability = ui.liability,
                    latest = row == ui.entries.first(),
                    canDelete = ui.canDeleteLatest,
                    // A closed source's values are not changed.
                    onEdit = if (ui.closed) null else { { onEdit(row.date) } },
                    onDelete = { confirm = DELETE_LATEST },
                    openingGain = if (row.opening) ui.openingGain else null,
                    inBase = ui.inBase?.takeIf { ui.showingBase }?.rows?.get(row.date),
                )
                HorizontalDivider()
            }
        }
    }

    if (closeOpen && ui != null && closing != null && !ui.closed) {
        CloseSourceDialog(
            name = ui.name,
            liability = ui.liability,
            lastValue = ui.entries.first().value,
            latestDate = ui.latestDate,
            today = closing.today,
            problem = closing.problem,
            onClose = { date, moved -> closing.onClose(date, moved) { closeOpen = false } },
            onDismiss = { closeOpen = false; closing.onClearProblem() },
        )
    }
    closing?.reopenRefused?.let { name ->
        AlertDialog(
            onDismissRequest = closing.onClearReopenRefused,
            title = { Text(stringResource(R.string.source_reopen_refused_title)) },
            text = { Text(stringResource(R.string.source_reopen_refused, name)) },
            confirmButton = {
                TextButton(onClick = closing.onClearReopenRefused, modifier = Modifier.testTag("reopen-refused-ok")) {
                    Text(stringResource(R.string.date_ok))
                }
            },
        )
    }

    when (confirm) {
        DELETE_LATEST -> ConfirmDialog(
            title = stringResource(R.string.source_delete_value_title),
            text = stringResource(R.string.source_delete_value_text),
            confirm = stringResource(R.string.source_delete),
            onConfirm = { confirm = null; onDeleteLatest() },
            onDismiss = { confirm = null },
        )
        DELETE_SOURCE -> ConfirmDialog(
            title = stringResource(R.string.source_delete_source_title, ui?.name.orEmpty()),
            text = stringResource(R.string.source_delete_source_text),
            confirm = stringResource(R.string.source_delete),
            onConfirm = { confirm = null; onDeleteSource() },
            onDismiss = { confirm = null },
        )
    }
}

/** Closing and reopening a source (UF-07), from its page. */
class SourceClosing(
    val today: LocalDate,
    val problem: ValueEntryProblem?,
    val onClose: (date: LocalDate, movedOut: String, onClosed: () -> Unit) -> Unit,
    val onClearProblem: () -> Unit,
    val onReopen: () -> Unit,
    /** The open source whose name keeps this one closed, until the user has read why. */
    val reopenRefused: String?,
    val onClearReopenRefused: () -> Unit,
)

private const val DELETE_LATEST = "latest"
private const val DELETE_SOURCE = "source"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(ui: SourcePageUi, onShowInBase: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // As on the dashboard: the category's picture, if it has one.
            categoryIcon(ui.category)?.let { IconTile(it) }
            Text(ui.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(if (ui.liability) R.string.source_kind_liability else R.string.source_kind_asset, ui.category),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.closedOn?.let { closed ->
                Text(
                    stringResource(R.string.source_closed_on, formatDate(closed)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("source-closed"),
                )
            }
            val inBase = ui.inBase
            // A foreign source: its own currency, or the base currency with the currency's effect on it.
            if (inBase != null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("currency-view")) {
                    SegmentedButton(
                        selected = !ui.showingBase,
                        onClick = { onShowInBase(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("view-own"),
                    ) { Text(ui.value.currency.code) }
                    SegmentedButton(
                        selected = ui.showingBase,
                        onClick = { onShowInBase(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("view-base"),
                    ) { Text(inBase.value.currency.code) }
                }
            }
            if (inBase != null && ui.showingBase) {
                BaseFigures(inBase, ui.liability)
            } else {
                OwnFigures(ui)
            }
            ui.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** The value in the base currency, and its change since opening split into its three parts. */
@Composable
private fun BaseFigures(inBase: InBase, liability: Boolean) {
    AmountText(inBase.value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("source-value"))
    Text(
        stringResource(R.string.source_base_since_opening),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val split = inBase.sinceOpening
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Figure(stringResource(if (liability) R.string.source_base_borrowed else R.string.source_base_deposits), split.added, "base-added")
        Figure(stringResource(if (liability) R.string.source_base_interest else R.string.source_base_growth), split.growth, "base-growth")
        Figure(stringResource(R.string.source_base_currency), split.currencyEffect, "currency-effect")
    }
}

@Composable
private fun RowScope.Figure(label: String, amount: Money, tag: String) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        AmountText(amount, signed = true, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
    }
}

/** The value in the source's own currency, with what was added and what it earned since opening. */
@Composable
private fun OwnFigures(ui: SourcePageUi) {
    AmountText(ui.value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("source-value"))
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (ui.liability) R.string.source_total_borrowed else R.string.source_total_added), style = MaterialTheme.typography.labelMedium)
            AmountText(ui.totalAdded, signed = true, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("total-added"))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (ui.liability) R.string.source_total_interest else R.string.source_total_growth), style = MaterialTheme.typography.labelMedium)
            AmountText(ui.totalGrowth, signed = true, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("total-growth"))
            // As a percentage of what was there at the start plus what was added; shown even while amounts are hidden.
            val growth = ui.growth
            val percent = growth?.percent
            if (growth != null && percent != null) {
                // A debt's figures are as the debt sees them: interest adds to what is owed,
                // so its percentage has the interest's sign, and its colour says it is a loss.
                Text(
                    formatPercent(if (ui.liability) -percent else percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = gainColor(growth),
                    modifier = Modifier.testTag("growth-percent"),
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    row: HistoryRow,
    liability: Boolean,
    latest: Boolean,
    canDelete: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    openingGain: Gain? = null,
    inBase: BaseRow? = null,
) {
    Column(
        Modifier.fillMaxWidth().then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier).padding(vertical = 12.dp).testTag("history-${row.date}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(row.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            AmountText(inBase?.value ?: row.value, style = MaterialTheme.typography.titleMedium)
        }
        if (row.opening) {
            if (openingGain == null) {
                Text(stringResource(R.string.source_opening), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // An account added part-way through its life: what it had already gained or lost.
                val text = stringResource(R.string.source_opening_gain, gainText(openingGain))
                Text(text, style = MaterialTheme.typography.bodySmall, color = gainColor(openingGain), modifier = Modifier.readsHidden(text))
            }
        } else if (inBase?.change != null) {
            // In the base currency: the change split three ways, the currency's part last.
            val split = inBase.change
            val parts = stringResource(
                if (liability) R.string.source_row_liability_base else R.string.source_row_asset_base,
                change(split.added),
                change(split.growth),
                change(split.currencyEffect),
            )
            Text(
                parts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.readsHidden(parts),
            )
        } else {
            val added = row.added
            val growth = row.growth
            if (added != null && growth != null) {
                val parts = stringResource(
                    if (liability) R.string.source_row_liability else R.string.source_row_asset,
                    change(added),
                    change(growth),
                )
                Text(
                    parts,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.readsHidden(parts),
                )
            }
        }
        row.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (latest && canDelete) {
            TextButton(onClick = onDelete, modifier = Modifier.testTag("delete-latest")) { Text(stringResource(R.string.source_delete_value)) }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = stringResource(R.string.date_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss")) { Text(dismiss) } },
    )
}
