package io.github.arpit92k.fortuna.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.ui.AmountText
import io.github.arpit92k.fortuna.ui.BackButton
import io.github.arpit92k.fortuna.ui.ChartPoint
import io.github.arpit92k.fortuna.ui.ChartRange
import io.github.arpit92k.fortuna.ui.GainText
import io.github.arpit92k.fortuna.ui.ValueChart
import io.github.arpit92k.fortuna.ui.pointsFrom
import io.github.arpit92k.fortuna.ui.rangeSummary
import kotlinx.datetime.toKotlinLocalDate

/**
 * A chart full screen: [points] over the chosen range, with the value at its
 * end, the change over it, and what was earned over it. Room to grow: more
 * detail about the chart belongs here rather than on the cards.
 *
 * [liability] turns the gain round, as interest on a debt is a loss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(title: String, points: List<ChartPoint>, onBack: () -> Unit, liability: Boolean = false) {
    var range by rememberSaveable { mutableStateOf(ChartRange.ALL) }
    val today = remember { java.time.LocalDate.now().toKotlinLocalDate() }
    val shown = remember(points, range) { pointsFrom(points, range.start(today)) }
    val summary = remember(shown, liability) { rangeSummary(shown, liability) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag(CHART_SCREEN)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 16.dp)) {
            BackButton(onBack)
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (summary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                    Figure(stringResource(R.string.chart_screen_value), Modifier.weight(1f)) {
                        AmountText(summary.value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("range-value"))
                    }
                    Figure(stringResource(R.string.chart_screen_change), Modifier.weight(1f)) {
                        AmountText(summary.change, style = MaterialTheme.typography.titleMedium, signed = true, modifier = Modifier.testTag("range-change"))
                    }
                    summary.gain?.let { gain ->
                        Figure(stringResource(if (liability) R.string.chart_screen_interest else R.string.chart_screen_gain), Modifier.weight(1f)) {
                            GainText(gain, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("range-gain"))
                        }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("chart-ranges")) {
                ChartRange.entries.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = range == choice,
                        onClick = { range = choice },
                        shape = SegmentedButtonDefaults.itemShape(index, ChartRange.entries.size),
                        modifier = Modifier.testTag("range-${choice.name}"),
                    ) { Text(stringResource(choice.label())) }
                }
            }
            ValueChart(shown, Modifier.fillMaxWidth().weight(1f), fill = true)
        }
    }
}

@Composable
private fun Figure(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

private fun ChartRange.label(): Int = when (this) {
    ChartRange.THREE_MONTHS -> R.string.chart_range_3m
    ChartRange.SIX_MONTHS -> R.string.chart_range_6m
    ChartRange.YEAR_TO_DATE -> R.string.chart_range_ytd
    ChartRange.ONE_YEAR -> R.string.chart_range_1y
    ChartRange.ALL -> R.string.chart_range_all
}

const val CHART_SCREEN = "chart-screen"
