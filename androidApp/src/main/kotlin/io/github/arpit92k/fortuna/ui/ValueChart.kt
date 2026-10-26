package io.github.arpit92k.fortuna.ui

import android.icu.text.CompactDecimalFormat
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
 */
@Composable
fun ValueChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    if (points.map { it.date }.distinct().size < 2) {
        Box(modifier.fillMaxWidth().height(ChartHeight).testTag(CHART_EMPTY), contentAlignment = Alignment.Center) {
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
    val range = remember(points) { chartRange(points.map { it.amount }) }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        producer.runTransaction {
            lineModel { series(x = points.map { it.date.toJavaLocalDate().toEpochDay() }, y = points.map { it.amount }) }
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
    // Hidden amounts keep the line's shape, but lose the figures on the axis and the marker.
    val hidden = LocalAmountsHidden.current
    val summary = if (hidden) {
        stringResource(R.string.chart_summary_hidden, formatDate(points.first().date), formatDate(points.last().date))
    } else {
        summaryOf(points)
    }

    ProvideVicoTheme(rememberM3VicoTheme(lineCartesianLayerColors = listOf(line), textColor = text)) {
        val label = rememberAxisLabelComponent(style = TextStyle(color = text, fontSize = 11.sp))
        val markerLabel = rememberTextComponent(
            style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
            padding = Insets(8.dp, 4.dp),
            background = rememberShapeComponent(
                fill = Fill(MaterialTheme.colorScheme.surface),
                strokeFill = Fill(MaterialTheme.colorScheme.outline),
                strokeThickness = 1.dp,
            ),
        )
        val marker = rememberDefaultCartesianMarker(
            label = markerLabel,
            valueFormatter = remember(byDay, hidden) {
                DefaultCartesianMarker.ValueFormatter { _, targets ->
                    val point = targets.firstOrNull()?.let { byDay[it.x] }
                    when {
                        point == null -> ""
                        hidden -> formatDate(point.date)
                        else -> "${formatDate(point.date)}  ·  ${formatMoney(point.value)}"
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
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(line)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                Fill(Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent))),
                            ),
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
            modifier = modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = summary }
                .testTag(CHART),
        )
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
