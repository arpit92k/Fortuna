package io.github.arpit92k.fortuna.growth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.ui.AmountText
import io.github.arpit92k.fortuna.ui.BackButton
import io.github.arpit92k.fortuna.ui.ReadableWidth
import io.github.arpit92k.fortuna.ui.ValueChart
import io.github.arpit92k.fortuna.ui.change
import io.github.arpit92k.fortuna.ui.formatDate
import io.github.arpit92k.fortuna.ui.formatPercent
import io.github.arpit92k.fortuna.ui.readsHidden
import kotlin.math.abs

/**
 * Growth: net worth now against 1, 3 and 5 years ago, and on this date in
 * each of the last five years. Money paid in and money earned count alike.
 */
@Composable
fun GrowthScreen(ui: GrowthUi?, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = ReadableWidth).fillMaxWidth().testTag("growth"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackButton(onBack)
                    Text(stringResource(R.string.growth_title), style = MaterialTheme.typography.titleLarge)
                }
            }
            if (ui == null) return@LazyColumn
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.growth_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.dashboard_net_worth), style = MaterialTheme.typography.labelLarge)
                        AmountText(ui.now, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("growth-now"))
                        HorizontalDivider()
                        for (comparison in ui.comparisons) ComparisonRow(comparison)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().testTag("growth-chart")) {
                    Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.growth_chart_title),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        ValueChart(ui.chart)
                    }
                }
            }
            if (ui.steps.size > 1) {
                item {
                    Text(
                        stringResource(if (ui.step == Step.YEAR) R.string.growth_each_year else R.string.growth_each_month),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                val largest = ui.steps.maxOf { abs(it.value.minorUnits) }.coerceAtLeast(1)
                items(ui.steps, key = { it.date.toString() }) { point -> StepRow(point, largest) }
            }
        }
    }
}

@Composable
private fun ComparisonRow(comparison: Comparison) {
    val months = comparison.lookback.months
    Row(Modifier.fillMaxWidth().testTag("growth-${months ?: "start"}"), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                months == null -> stringResource(R.string.growth_since_start, formatDate(comparison.date))
                months % 12 == 0 -> pluralStringResource(R.plurals.growth_vs_years, months / 12, months / 12)
                else -> pluralStringResource(R.plurals.growth_vs_months, months, months)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        val delta = comparison.change
        val percent = comparison.percent?.let { " (${formatPercent(it)})" }.orEmpty()
        val text = change(delta) + percent
        Text(text, style = MaterialTheme.typography.titleSmall, color = colourOf(delta), modifier = Modifier.readsHidden(text))
    }
}

/** One step: the date, a bar for net worth then, and the change on the step before. */
@Composable
private fun StepRow(point: StepPoint, largest: Long) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxWidth().testTag("growth-step-${point.date}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(point.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            point.change?.let { delta ->
                val text = change(delta)
                Text(text, style = MaterialTheme.typography.bodySmall, color = colourOf(delta), modifier = Modifier.padding(end = 12.dp).readsHidden(text))
            }
            AmountText(point.value, style = MaterialTheme.typography.titleSmall)
        }
        // The bar keeps its length while amounts are hidden: the shape of growth, without the figures.
        val share = abs(point.value.minorUnits).toFloat() / largest
        val colour = if (point.value.minorUnits < 0) negative else positive
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(colour.copy(alpha = 0.15f), size = size, cornerRadius = CornerRadius(5.dp.toPx()))
            drawRoundRect(colour, topLeft = Offset.Zero, size = Size(size.width * share, size.height), cornerRadius = CornerRadius(5.dp.toPx()))
        }
    }
}

@Composable
private fun colourOf(change: Money): Color = when {
    change.minorUnits > 0 -> MaterialTheme.colorScheme.primary
    change.minorUnits < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
