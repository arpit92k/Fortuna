package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Decided: the app adapts to the window it is given, not to the kind of
// device, so phones, foldables folded and unfolded, tablets, split screen and
// landscape all follow the same rules. The breakpoints are Material's.

/** How wide the window is. */
enum class WidthClass {
    /** Under 600dp: most phones held upright. */
    COMPACT,

    /** 600 to 840dp: unfolded foldables, small tablets, phones on their side. */
    MEDIUM,

    /** 840dp and over: tablets and large foldables on their side, desktops. */
    EXPANDED,
    ;

    companion object {
        fun of(width: Dp): WidthClass = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * Whether a screen made of an explanation and an action shows them side by
 * side. They do on an expanded window, and on a medium one too short to
 * stack them, such as a phone on its side.
 */
fun sideBySide(width: Dp, height: Dp): Boolean = when (WidthClass.of(width)) {
    WidthClass.COMPACT -> false
    WidthClass.MEDIUM -> height < 480.dp
    WidthClass.EXPANDED -> true
}

/** The widest a column of text or a form grows, so lines stay readable. */
val ReadableWidth = 560.dp

/**
 * A screen of two parts: [info] says what is happening, [action] is what the
 * user does. Stacked and scrollable on a narrow window, kept to a readable
 * width on a wider one, and side by side where there is room.
 *
 * An [action] that scrolls by itself, such as a long list, passes
 * [actionScrolls]: the screen then gives it the remaining height instead of
 * scrolling around it.
 *
 * With [actionAtBottom], a stacked screen keeps the info at the top and the
 * action at the bottom, within reach of the thumbs, as for a PIN pad. When
 * they do not both fit, the screen scrolls as usual.
 */
@Composable
fun AdaptiveScreen(
    info: @Composable () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actionScrolls: Boolean = false,
    actionAtBottom: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        if (sideBySide(maxWidth, maxHeight)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp).testTag(SIDE_BY_SIDE),
                horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).widthIn(max = 480.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) { info() }
                Box(
                    Modifier.weight(1f).widthIn(max = ReadableWidth).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) { action() }
            }
        } else {
            val screenHeight = maxHeight
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val column = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()
                val sized = when {
                    actionScrolls -> column.fillMaxHeight()
                    // At least the screen's height, so the action can sit at its bottom.
                    actionAtBottom -> column.verticalScroll(rememberScrollState()).heightIn(min = screenHeight)
                    else -> column.verticalScroll(rememberScrollState())
                }
                Column(
                    sized.padding(horizontal = 24.dp, vertical = 24.dp).testTag(STACKED),
                    verticalArrangement = if (actionAtBottom) Arrangement.SpaceBetween else Arrangement.spacedBy(24.dp),
                ) {
                    info()
                    when {
                        actionScrolls -> Box(Modifier.weight(1f)) { action() }
                        // The gap the arrangement would otherwise give, for when the screen scrolls.
                        actionAtBottom -> Box(Modifier.padding(top = 24.dp)) { action() }
                        else -> action()
                    }
                }
            }
        }
    }
}

const val SIDE_BY_SIDE = "side-by-side"
const val STACKED = "stacked"
