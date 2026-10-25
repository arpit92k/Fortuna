package io.github.arpit92k.fortuna.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import io.github.arpit92k.fortuna.ui.BackButton
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.ReadableWidth
import io.github.arpit92k.fortuna.ui.categoryIcon
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.formatMoney
import kotlinx.datetime.LocalDate

/** A change written with its sign, so growth and losses read at a glance: +£500.00, -£20.00. */
fun formatChange(money: Money): String = if (money.minorUnits > 0) "+" + formatMoney(money) else formatMoney(money)

/**
 * S-10 Source: the value now, the totals since the opening balance, and every
 * value with what changed. Each value can be corrected; the latest can be
 * deleted, and a source with only its opening value can be deleted outright.
 * Deleting asks first.
 */
@Composable
fun SourcePageScreen(
    ui: SourcePageUi?,
    onAddValue: () -> Unit,
    onEdit: (LocalDate) -> Unit,
    onDeleteLatest: () -> Unit,
    onDeleteSource: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
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
            item { Header(ui) }
            item {
                Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onAddValue, modifier = Modifier.testTag("add-value")) { Text(stringResource(R.string.source_add_value)) }
                    if (ui.canDeleteSource) {
                        OutlinedButton(onClick = { confirm = DELETE_SOURCE }, modifier = Modifier.testTag("delete-source")) {
                            Text(stringResource(R.string.source_delete_source))
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
                    onEdit = { onEdit(row.date) },
                    onDelete = { confirm = DELETE_LATEST },
                )
                HorizontalDivider()
            }
        }
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

private const val DELETE_LATEST = "latest"
private const val DELETE_SOURCE = "source"

@Composable
private fun Header(ui: SourcePageUi) {
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
            Text(formatMoney(ui.value), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("source-value"))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (ui.liability) R.string.source_total_borrowed else R.string.source_total_added), style = MaterialTheme.typography.labelMedium)
                    Text(formatChange(ui.totalAdded), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("total-added"))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (ui.liability) R.string.source_total_interest else R.string.source_total_growth), style = MaterialTheme.typography.labelMedium)
                    Text(formatChange(ui.totalGrowth), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("total-growth"))
                }
            }
            ui.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, liability: Boolean, latest: Boolean, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 12.dp).testTag("history-${row.date}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(row.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(formatMoney(row.value), style = MaterialTheme.typography.titleMedium)
        }
        if (row.opening) {
            Text(stringResource(R.string.source_opening), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val added = row.added
            val growth = row.growth
            if (added != null && growth != null) {
                Text(
                    stringResource(
                        if (liability) R.string.source_row_liability else R.string.source_row_asset,
                        formatChange(added),
                        formatChange(growth),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.date_cancel)) } },
    )
}
