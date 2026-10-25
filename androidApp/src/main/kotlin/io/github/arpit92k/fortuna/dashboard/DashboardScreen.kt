package io.github.arpit92k.fortuna.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.ui.ReadableWidth
import io.github.arpit92k.fortuna.ui.WidthClass
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.formatMoney

/**
 * S-09 Dashboard: net worth, total assets and total liabilities, then the
 * sources. On an expanded window the totals sit beside the list; otherwise the
 * list scrolls under them, kept to a comfortable width.
 */
@Composable
fun DashboardScreen(ui: DashboardUi?, onAddSource: () -> Unit, onLock: () -> Unit) {
    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onLock, modifier = Modifier.testTag("lock-now")) { Text(stringResource(R.string.dashboard_lock)) }
            }
        },
        floatingActionButton = {
            if (ui != null && !ui.empty) {
                ExtendedFloatingActionButton(onClick = onAddSource, modifier = Modifier.testTag("add-source")) {
                    Text(stringResource(R.string.dashboard_add_source))
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ui.empty -> EmptyDashboard(onAddSource)
                WidthClass.of(maxWidth) == WidthClass.EXPANDED -> Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("dashboard-side-by-side"),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Totals(ui, Modifier.width(360.dp).padding(top = 16.dp))
                    SourceList(ui, Modifier.weight(1f), header = false)
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    SourceList(ui, Modifier.widthIn(max = ReadableWidth).fillMaxWidth().padding(horizontal = 16.dp), header = true)
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboard(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp).testTag("dashboard-empty"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = ReadableWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.dashboard_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.dashboard_empty_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = onAddSource, modifier = Modifier.testTag("add-first-source")) {
                Text(stringResource(R.string.dashboard_add_first))
            }
        }
    }
}

@Composable
private fun Totals(ui: DashboardUi, modifier: Modifier = Modifier) {
    Card(modifier.testTag("totals")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dashboard_net_worth), style = MaterialTheme.typography.labelLarge)
            Text(formatMoney(ui.netWorth), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("net-worth"))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_assets), style = MaterialTheme.typography.labelMedium)
                    Text(formatMoney(ui.assets), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("assets"))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_liabilities), style = MaterialTheme.typography.labelMedium)
                    Text(formatMoney(ui.liabilities), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("liabilities"))
                }
            }
        }
    }
}

@Composable
private fun SourceList(ui: DashboardUi, modifier: Modifier, header: Boolean) {
    // Room at the end, so the add button never covers the last source.
    LazyColumn(modifier.testTag("source-list"), contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)) {
        if (header) item { Totals(ui, Modifier.fillMaxWidth().padding(bottom = 16.dp)) }
        item {
            Text(
                stringResource(R.string.dashboard_sources),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(ui.sources, key = { it.id.value }) { row ->
            SourceRowItem(row)
            HorizontalDivider()
        }
    }
}

@Composable
private fun SourceRowItem(row: SourceRow) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("source-${row.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.dashboard_source_detail, row.category, formatDate(row.lastUpdated)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(row.value), style = MaterialTheme.typography.titleMedium)
            if (row.liability) {
                Text(
                    stringResource(R.string.dashboard_owed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
