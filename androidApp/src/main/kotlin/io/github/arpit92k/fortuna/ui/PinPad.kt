package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.keys.MAX_PIN_LENGTH

// Decided: the PIN is typed on the app's own pad, never the phone's keyboard,
// so no keyboard app ever sees it.

/**
 * The digits typed so far, as dots: never the digits themselves. A PIN can
 * be longer than six digits, so the dots simply follow the count.
 */
@Composable
fun PinDots(count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.pin_digits_entered, count)
    Row(
        modifier.height(20.dp).semantics { contentDescription = description }.testTag(PIN_DOTS),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxOf(count, 0).coerceAtMost(16)) {
            Surface(Modifier.size(12.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
        }
    }
}

/**
 * Digits 1 to 9, then delete, 0 and OK. The keys grow with the space they are
 * given, up to a comfortable size, so the pad fits a phone on its side and
 * does not sprawl across a tablet.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    okEnabled: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.testTag(PIN_PAD), contentAlignment = Alignment.Center) {
        val gap = 12.dp
        // Four rows and three columns, with gaps between them.
        val byWidth = (maxWidth - gap * 2) / 3
        val byHeight = if (maxHeight == Dp.Infinity) MAX_KEY else (maxHeight - gap * 3) / 4
        val key = min(MAX_KEY, min(byWidth, byHeight)).coerceAtLeast(MIN_KEY)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in listOf("123", "456", "789")) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (digit in row) DigitKey(digit, key, enabled, onDigit)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(key), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.testTag(PIN_DELETE)) {
                        Text(stringResource(R.string.pin_delete))
                    }
                }
                DigitKey('0', key, enabled, onDigit)
                Box(Modifier.size(key), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onOk, enabled = enabled && okEnabled, modifier = Modifier.testTag(PIN_OK)) {
                        Text(stringResource(R.string.pin_ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, size: Dp, enabled: Boolean, onDigit: (Char) -> Unit) {
    FilledTonalButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(size).testTag("$PIN_KEY$digit"),
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

/** Adds [digit] to [pin], up to the longest PIN the core accepts. */
fun typed(pin: String, digit: Char): String = if (pin.length < MAX_PIN_LENGTH) pin + digit else pin

private val MAX_KEY = 76.dp
private val MIN_KEY = 44.dp

const val PIN_PAD = "pin-pad"
const val PIN_DOTS = "pin-dots"
const val PIN_DELETE = "pin-delete"
const val PIN_OK = "pin-ok"
const val PIN_KEY = "pin-key-"

/** Keeps a narrow [content] centred within the width it is given. */
@Composable
fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}
