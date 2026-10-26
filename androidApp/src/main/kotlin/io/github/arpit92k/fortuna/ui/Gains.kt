package io.github.arpit92k.fortuna.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.arpit92k.fortuna.core.insights.Gain
import java.text.NumberFormat
import java.util.Locale

/** A percentage with its sign and one decimal place, as the phone writes percentages: +2.1%, -0.4%. */
fun formatPercent(percent: Double, phone: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getPercentInstance(phone).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    val text = format.format(percent / 100)
    return if (percent > 0) "+$text" else text
}

/**
 * A gain as the screens show it: "+£1,240.00 (+2.1%)". While amounts are
 * hidden the percentage stays, as decided for shared pictures: "•••••• (+2.1%)".
 */
@Composable
fun gainText(gain: Gain): String {
    val amount = change(gain.amount)
    val percent = gain.percent?.let { formatPercent(it) }
    return if (percent == null) amount else "$amount ($percent)"
}

/** The colour of a gain: the accent for a gain, the error colour for a loss, plain for none. */
@Composable
fun gainColor(gain: Gain): Color = when {
    gain.amount.minorUnits > 0 -> MaterialTheme.colorScheme.primary
    gain.amount.minorUnits < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** [gain] in its colour; TalkBack says "hidden" for a hidden amount. */
@Composable
fun GainText(gain: Gain, style: TextStyle, modifier: Modifier = Modifier) {
    val text = gainText(gain)
    Text(text, style = style, color = gainColor(gain), modifier = modifier.readsHidden(text))
}
