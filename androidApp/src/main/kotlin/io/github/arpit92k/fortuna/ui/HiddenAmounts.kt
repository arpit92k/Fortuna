package io.github.arpit92k.fortuna.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.money.Money

// Decided: hiding amounts reaches the whole app. Every amount shown becomes
// the same row of dots, whatever its size, so not even the number of digits
// shows. Amounts being typed into a form stay visible; rates, dates and names
// are not amounts and stay too.

/** Whether amounts are hidden on the screens below. */
val LocalAmountsHidden = compositionLocalOf { false }

/** What an amount shows as while hidden. */
const val HIDDEN_AMOUNT = "••••••"

/** [money] as the screen shows it: formatted, or dots while amounts are hidden. */
@Composable
@ReadOnlyComposable
fun amount(money: Money): String = if (LocalAmountsHidden.current) HIDDEN_AMOUNT else formatMoney(money)

/** A change with its sign, +£500.00 or -£20.00, or dots while amounts are hidden. */
@Composable
@ReadOnlyComposable
fun change(money: Money): String = if (LocalAmountsHidden.current) HIDDEN_AMOUNT else formatSignedMoney(money)

/** A change written with its sign, so growth and losses read at a glance: +£500.00, -£20.00. */
fun formatSignedMoney(money: Money): String = if (money.minorUnits > 0) "+" + formatMoney(money) else formatMoney(money)

/**
 * For text that holds hidden amounts: TalkBack says "hidden" for each, rather
 * than reading out every dot.
 */
@Composable
fun Modifier.readsHidden(text: String): Modifier {
    if (!LocalAmountsHidden.current || HIDDEN_AMOUNT !in text) return this
    val spoken = text.replace(HIDDEN_AMOUNT, stringResource(R.string.amount_hidden))
    return semantics { contentDescription = spoken }
}

/** [money] as text, or dots that TalkBack reads as "hidden" while amounts are hidden. A [signed] amount is a change, with + or -. */
@Composable
fun AmountText(
    money: Money,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    signed: Boolean = false,
) {
    val text = if (signed) change(money) else amount(money)
    androidx.compose.material3.Text(text, style = style, color = color, modifier = modifier.readsHidden(text))
}
