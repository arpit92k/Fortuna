package io.github.arpit92k.fortuna.ui

import android.icu.text.CompactDecimalFormat
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import io.github.arpit92k.fortuna.R
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// The one chart of the app, a line of values over time, behind one composable
// so the library underneath can be swapped. Drawn with Vico.

/**
 * [points] as a line over time, oldest on the left. Pressing the chart shows
 * the value and date of the nearest point. Screen readers hear a summary: the
 * period, where the line starts and ends, and its lowest and highest values.
 *
 * With fewer than two dates there is no line to draw, so a short note says the
 * chart fills in as values are added.
 *
 * With [fill], the chart takes all the height [modifier] gives it, as on the
 * full-screen chart, rather than a card's fixed height.
 */
@Composable
fun ValueChart(points: List<ChartPoint>, modifier: Modifier = Modifier, fill: Boolean = false) {
    if (points.map { it.date }.distinct().size < 2) {
        val size = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(ChartHeight)
        Box(modifier.then(size).testTag(CHART_EMPTY), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.chart_one_point),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val locale = Locale.getDefault()
    val byDay = remember(points) { points.associateBy { it.date.toJavaLocalDate().toEpochDay().toDouble() } }
    // With what was put in, a second line beneath the value: the gap between them is everything gained.
    val withInvested = points.all { it.invested != null }
    val range = remember(points) { chartRange(points.map { it.amount } + points.mapNotNull { it.investedAmount }) }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        producer.runTransaction {
            lineModel {
                val days = points.map { it.date.toJavaLocalDate().toEpochDay() }
                series(x = days, y = points.map { it.amount })
                if (withInvested) series(x = days, y = points.map { it.investedAmount!! })
            }
        }
    }

    // Short dates under the line: months and years over a long period, days over a short one.
    val first = points.first().date.toJavaLocalDate()
    val last = points.last().date.toJavaLocalDate()
    val skeleton = if (ChronoUnit.DAYS.between(first, last) > 92) "MMMyy" else "dMMM"
    val axisDate = remember(locale, skeleton) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
    // Compact figures in the style of the chart's currency, as its amounts are written.
    val amountStyle = currencyLocale(points.first().value.currency, locale)
    val compact = remember(amountStyle) { CompactDecimalFormat.getInstance(amountStyle, CompactDecimalFormat.CompactStyle.SHORT) }

    val line = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val investedLine = MaterialTheme.colorScheme.outline
    // Hidden amounts keep the line's shape, but lose the figures on the axis and the marker.
    val hidden = LocalAmountsHidden.current
    val summary = if (hidden) {
        stringResource(R.string.chart_summary_hidden, formatDate(points.first().date), formatDate(points.last().date))
    } else {
        summaryOf(points)
    }

    Column(modifier) {
        if (withInvested) ChartLegend(line, investedLine)
        ProvideVicoTheme(rememberM3VicoTheme(lineCartesianLayerColors = listOf(line, investedLine), textColor = text)) {
            val label = rememberAxisLabelComponent(style = TextStyle(color = text, fontSize = 11.sp))
            val markerLabel = rememberTextComponent(
                style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                // The value, then the gain to that point on a second line.
                lineCount = 2,
                padding = Insets(8.dp, 4.dp),
                background = rememberShapeComponent(
                    fill = Fill(MaterialTheme.colorScheme.surface),
                    strokeFill = Fill(MaterialTheme.colorScheme.outline),
                    strokeThickness = 1.dp,
                ),
            )
            val gainLabel = stringResource(R.string.chart_gain)
            val marker = rememberDefaultCartesianMarker(
                label = markerLabel,
                valueFormatter = remember(byDay, hidden, gainLabel) {
                    DefaultCartesianMarker.ValueFormatter { _, targets ->
                        val point = targets.firstOrNull()?.let { byDay[it.x] }
                        val gain = point?.gain
                        val invested = point?.invested
                        // The gain to this point, as a percentage of what was put in; kept while amounts are hidden.
                        val percent = if (gain != null && invested != null && invested.minorUnits > 0) {
                            formatPercent(gain.minorUnits * 100.0 / invested.minorUnits)
                        } else {
                            null
                        }
                        when {
                            point == null -> ""
                            hidden -> listOfNotNull(formatDate(point.date), percent).joinToString("  ·  ")
                            gain == null -> "${formatDate(point.date)}  ·  ${formatMoney(point.value)}"
                            else -> "${formatDate(point.date)}  ·  ${formatMoney(point.value)}\n" +
                                gainLabel.format(formatSignedMoney(gain) + (percent?.let { " ($it)" } ?: ""))
                        }
                    }
                },
                indicator = { color -> ShapeComponent(Fill(color), CircleShape) },
                indicatorSize = 10.dp,
                guideline = rememberAxisGuidelineComponent(),
            )
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            listOfNotNull(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(Fill(line)),
                                    areaFill = LineCartesianLayer.AreaFill.single(
                                        Fill(Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent))),
                                    ),
                                ),
                                // What was put in: dashed and quiet, so the value stays the line that leads.
                                if (withInvested) {
                                    LineCartesianLayer.rememberLine(
                                        fill = LineCartesianLayer.LineFill.single(Fill(investedLine)),
                                        stroke = LineCartesianLayer.LineStroke.Dashed(),
                                    )
                                } else {
                                    null
                                },
                            ),
                        ),
                        rangeProvider = remember(range) {
                            CartesianLayerRangeProvider.fixed(minY = range.start, maxY = range.endInclusive)
                        },
                    ),
                    // Hidden amounts drop the axis of amounts altogether: Vico refuses blank labels.
                    startAxis = if (hidden) {
                        null
                    } else {
                        VerticalAxis.rememberStart(
                            label = label,
                            valueFormatter = remember(compact) { CartesianValueFormatter { _, value, _ -> compact.format(value) } },
                        )
                    },
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = label,
                        valueFormatter = remember(axisDate) {
                            CartesianValueFormatter { _, value, _ -> java.time.LocalDate.ofEpochDay(value.toLong()).format(axisDate) }
                        },
                    ),
                    marker = marker,
                ),
                modelProducer = producer,
                // The whole period fits the width: nothing to scroll or zoom.
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content, minZoom = Zoom.Content, maxZoom = Zoom.Content),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fill) Modifier.weight(1f) else Modifier.height(ChartHeight))
                    .semantics { contentDescription = summary }
                    .testTag(CHART),
            )
        }
    }
}

/** Which line is which: the value, solid, and what was put in, dashed. */
@Composable
private fun ChartLegend(value: Color, invested: Color) {
    Row(Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("chart-legend"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(value, dashed = false, stringResource(R.string.chart_value))
        LegendItem(invested, dashed = true, stringResource(R.string.chart_invested))
    }
}

@Composable
private fun LegendItem(color: Color, dashed: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 2.dp)) {
            drawLine(
                color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun summaryOf(points: List<ChartPoint>): String {
    val lowest = points.minBy { it.amount }
    val highest = points.maxBy { it.amount }
    return stringResource(
        R.string.chart_summary,
        formatDate(points.first().date),
        formatDate(points.last().date),
        formatMoney(points.first().value),
        formatMoney(points.last().value),
        formatMoney(lowest.value),
        formatMoney(highest.value),
    )
}

private val ChartHeight = 200.dp

const val CHART = "chart"
const val CHART_EMPTY = "chart-empty"

/** A chart card's title, with the button that opens the chart full screen at its end. */
@Composable
fun ChartCardTitle(title: String, onOpenChart: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        IconButton(onClick = onOpenChart, modifier = Modifier.testTag(OPEN_CHART)) {
            FortunaIconImage(FortunaIcon.Expand, contentDescription = stringResource(R.string.chart_open_full_screen))
        }
    }
}

const val OPEN_CHART = "open-chart"
