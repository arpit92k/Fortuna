package io.github.arpit92k.fortuna.dashboard

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import io.github.arpit92k.fortuna.core.money.Currency
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import io.github.arpit92k.fortuna.ui.ChartCardTitle
import io.github.arpit92k.fortuna.ui.ButtonIcon
import io.github.arpit92k.fortuna.ui.FortunaIcon
import io.github.arpit92k.fortuna.ui.FortunaIconImage
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.ui.ReadableWidth
import io.github.arpit92k.fortuna.ui.ValueChart
import io.github.arpit92k.fortuna.ui.RingedMark
import io.github.arpit92k.fortuna.ui.WidthClass
import io.github.arpit92k.fortuna.ui.categoryIcon
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.LocalAmountsHidden
import io.github.arpit92k.fortuna.ui.AmountText
import io.github.arpit92k.fortuna.ui.GainText

/**
 * S-09 Dashboard: net worth, total assets and total liabilities, net worth
 * over time, then the sources. On an expanded window the totals and the chart
 * sit beside the list; otherwise the list scrolls under them, kept to a
 * comfortable width.
 */
@Composable
fun DashboardScreen(
    ui: DashboardUi?,
    onAddSource: () -> Unit,
    onOpen: (SourceId) -> Unit,
    onOpenChart: () -> Unit,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onToggleAmounts: () -> Unit,
    modifier: Modifier = Modifier,
    onShowIn: (Currency) -> Unit = {},
    onToggleZero: () -> Unit = {},
) {
    val hidden = LocalAmountsHidden.current
    Scaffold(
        modifier = modifier,
        // Clear of everything the system draws over the window: the status bar, the camera cutout, the
        // app handle in split screen and on a desktop, and the navigation bar. In split screen the status
        // bar may be hidden while the cutout and the handle still cover the top of the window.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (ui != null && !ui.empty) {
                    TextButton(onClick = onUpdate, modifier = Modifier.testTag("start-update")) {
                        ButtonIcon(FortunaIcon.Update)
                        Text(stringResource(R.string.dashboard_update))
                    }
                }
                if (ui != null && !ui.empty) {
                    // Hides every amount in the app, for when someone might see the screen.
                    IconButton(onClick = onToggleAmounts, modifier = Modifier.testTag("toggle-amounts")) {
                        FortunaIconImage(
                            if (hidden) FortunaIcon.Hide else FortunaIcon.Show,
                            contentDescription = stringResource(if (hidden) R.string.dashboard_show_amounts else R.string.dashboard_hide_amounts),
                        )
                    }
                }
                IconButton(onClick = onSettings, modifier = Modifier.testTag("open-settings")) {
                    FortunaIconImage(FortunaIcon.Settings, contentDescription = stringResource(R.string.dashboard_settings))
                }
                IconButton(onClick = onLock, modifier = Modifier.testTag("lock-now")) {
                    FortunaIconImage(FortunaIcon.Lock, contentDescription = stringResource(R.string.dashboard_lock))
                }
            }
        },
        floatingActionButton = {
            if (ui != null && !ui.empty) {
                ExtendedFloatingActionButton(
                    onClick = onAddSource,
                    icon = { FortunaIconImage(FortunaIcon.Plus, line = LocalContentColor.current, accent = LocalContentColor.current) },
                    text = { Text(stringResource(R.string.dashboard_add_source)) },
                    modifier = Modifier.testTag("add-source"),
                )
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
                    // The totals and the chart beside the list, scrolling together if the window is short.
                    Column(
                        Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Totals(ui, onShowIn, Modifier.fillMaxWidth())
                        NetWorthChart(ui, onOpenChart, Modifier.fillMaxWidth())
                    }
                    SourceList(ui, Modifier.weight(1f), header = false, onOpen = onOpen, onOpenChart = onOpenChart, onShowIn = onShowIn, onToggleZero = onToggleZero)
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    SourceList(
                        ui,
                        Modifier.widthIn(max = ReadableWidth).fillMaxWidth().padding(horizontal = 16.dp),
                        header = true,
                        onOpen = onOpen,
                        onOpenChart = onOpenChart,
                        onShowIn = onShowIn,
                        onToggleZero = onToggleZero,
                    )
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
            RingedMark(radius = 56.dp)
            Text(stringResource(R.string.dashboard_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.dashboard_empty_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = onAddSource, modifier = Modifier.testTag("add-first-source")) {
                ButtonIcon(FortunaIcon.Plus)
                Text(stringResource(R.string.dashboard_add_first))
            }
        }
    }
}

@Composable
private fun Totals(ui: DashboardUi, onShowIn: (Currency) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.testTag("totals")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dashboard_net_worth), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                ShownInChip(ui, onShowIn)
            }
            AmountText(ui.netWorth, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("net-worth"))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_assets), style = MaterialTheme.typography.labelMedium)
                    AmountText(ui.assets, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("assets"))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_liabilities), style = MaterialTheme.typography.labelMedium)
                    AmountText(ui.liabilities, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("liabilities"))
                }
            }
            // Gains and losses: what money earned, never what was paid in.
            val latest = ui.latestGain
            val sinceStart = ui.gainSinceStart
            if (latest != null && sinceStart != null) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_gain_latest), style = MaterialTheme.typography.labelMedium)
                        GainText(latest, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("gain-latest"))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_gain_since_start), style = MaterialTheme.typography.labelMedium)
                        GainText(sinceStart, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("gain-since-start"))
                    }
                }
            }
        }
    }
}

/**
 * The currency the dashboard's figures are shown in, with the others the
 * accounts use to choose from. Nothing to choose with only one currency.
 */
@Composable
private fun ShownInChip(ui: DashboardUi, onShowIn: (Currency) -> Unit) {
    val shown = ui.shownIn ?: return
    if (ui.currencies.size < 2) return
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.dashboard_shown_in, shown.name)
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text("${shown.code} ▾", fontFamily = FontFamily.Monospace) },
            modifier = Modifier
                .semantics { contentDescription = description }
                .testTag("shown-in"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (currency in ui.currencies) {
                DropdownMenuItem(
                    text = { Text("${currency.code}  ${currency.name}") },
                    onClick = {
                        open = false
                        onShowIn(currency)
                    },
                    modifier = Modifier.testTag("shown-in-${currency.code}"),
                )
            }
        }
    }
}

/**
 * Hides or shows the accounts at zero, beside the list's heading: a filter,
 * filled in while they are hidden, with how many are hidden. On a tinted
 * circle, so it reads as a button. Only when there are any.
 */
@Composable
private fun ZeroToggle(ui: DashboardUi, onToggleZero: () -> Unit) {
    if (ui.atZero == 0) return
    val description = if (ui.zeroHidden) {
        pluralStringResource(R.plurals.dashboard_show_zero, ui.atZero, ui.atZero)
    } else {
        pluralStringResource(R.plurals.dashboard_hide_zero, ui.atZero, ui.atZero)
    }
    FilledTonalIconButton(onClick = onToggleZero, modifier = Modifier.testTag("toggle-zero")) {
        BadgedBox(badge = { if (ui.zeroHidden) Badge { Text(ui.atZero.toString()) } }) {
            FortunaIconImage(if (ui.zeroHidden) FortunaIcon.Filtered else FortunaIcon.Filter, contentDescription = description)
        }
    }
}

@Composable
private fun NetWorthChart(ui: DashboardUi, onOpenChart: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.testTag("net-worth-chart")) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartCardTitle(stringResource(R.string.dashboard_chart_title), onOpenChart)
            ValueChart(ui.timeline, Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
private fun SourceList(
    ui: DashboardUi,
    modifier: Modifier,
    header: Boolean,
    onOpen: (SourceId) -> Unit,
    onOpenChart: () -> Unit,
    onShowIn: (Currency) -> Unit,
    onToggleZero: () -> Unit,
) {
    // Room at the end, so the add button never covers the last source.
    LazyColumn(modifier.testTag("source-list"), contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)) {
        if (header) {
            item { Totals(ui, onShowIn, Modifier.fillMaxWidth().padding(bottom = 16.dp)) }
            item { NetWorthChart(ui, onOpenChart, Modifier.fillMaxWidth().padding(bottom = 16.dp)) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_sources),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                ZeroToggle(ui, onToggleZero)
            }
        }
        items(ui.sources, key = { it.id.value }) { row ->
            SourceRowItem(row, onClick = { onOpen(row.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SourceRowItem(row: SourceRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).testTag("source-${row.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categoryIcon(row.category)?.let { IconTile(it, size = 40.dp) }
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.dashboard_source_detail, row.category, formatDate(row.lastUpdated)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            AmountText(row.value, style = MaterialTheme.typography.titleMedium)
            // A foreign source shows its base-currency value too, so the list can be checked against the totals.
            row.valueInBase?.let {
                AmountText(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("value-in-base-${row.id.value}"),
                )
            }
            row.gain?.let { GainText(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("gain-${row.id.value}")) }
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
