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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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

/** The widest a screen's [AdaptiveScreen] hero grows, and the action beneath it. */
val HeroWidth = 360.dp

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
 *
 * A [hero] is a picture that belongs with the screen: above the info on a
 * narrow window, above the action on a wide one, so the action is never left
 * alone on its side.
 *
 * A screen whose action scrolls starts its info at the top, on the same line
 * as the action; any other screen centres each side.
 *
 * With [onBack], the page has a back arrow at its top. A [bottomBar] stays
 * at the bottom of the window, outside the scrolling, and so just above the
 * keyboard while one is open.
 */
@Composable
fun AdaptiveScreen(
    info: @Composable () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actionScrolls: Boolean = false,
    actionAtBottom: Boolean = false,
    hero: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    if (onBack == null && bottomBar == null) {
        Sides(info, action, modifier.fillMaxSize().safeDrawingPadding(), actionScrolls, actionAtBottom, hero)
    } else {
        Column(modifier.fillMaxSize().safeDrawingPadding()) {
            // The back arrow sits above both sides, at the start of the window.
            if (onBack != null) BackButton(onBack, Modifier.padding(start = 4.dp, top = 4.dp))
            Sides(info, action, Modifier.fillMaxWidth().weight(1f), actionScrolls, actionAtBottom, hero)
            // Below the content, so it stays in view just above the keyboard.
            bottomBar?.invoke()
        }
    }
}

@Composable
private fun Sides(
    info: @Composable () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier,
    actionScrolls: Boolean,
    actionAtBottom: Boolean,
    hero: (@Composable () -> Unit)?,
) {
    BoxWithConstraints(modifier) {
        val wide = sideBySide(maxWidth, maxHeight)
        CompositionLocalProvider(LocalSideBySide provides wide) {
            if (wide) {
                WideScreen(info, action, actionScrolls, hero)
            } else {
                NarrowScreen(info, action, actionScrolls, actionAtBottom, hero, maxHeight)
            }
        }
    }
}

/** Whether the screen being drawn has its two parts side by side, for content that aligns to match. */
val LocalSideBySide = compositionLocalOf { false }

@Composable
private fun WideScreen(
    info: @Composable () -> Unit,
    action: @Composable () -> Unit,
    actionScrolls: Boolean,
    hero: (@Composable () -> Unit)?,
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp).testTag(SIDE_BY_SIDE),
        horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).widthIn(max = 480.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = if (actionScrolls) Arrangement.Top else Arrangement.Center,
        ) { info() }
        Box(
            Modifier.weight(1f).widthIn(max = ReadableWidth).fillMaxHeight(),
            contentAlignment = if (actionScrolls) Alignment.TopCenter else Alignment.Center,
        ) {
            if (hero == null) {
                action()
            } else {
                Column(
                    // As wide as the picture, so the action is exactly as wide as it.
                    Modifier.widthIn(max = HeroWidth).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    hero()
                    action()
                }
            }
        }
    }
}

@Composable
private fun NarrowScreen(
    info: @Composable () -> Unit,
    action: @Composable () -> Unit,
    actionScrolls: Boolean,
    actionAtBottom: Boolean,
    hero: (@Composable () -> Unit)?,
    screenHeight: Dp,
) {
    // The picture, when there is one, comes first and travels with the info.
    val top: @Composable () -> Unit = {
        if (hero == null) {
            info()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Centred: the picture is narrower than the column on wider phones and unfolded foldables.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { hero() }
                info()
            }
        }
    }
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
            top()
            when {
                actionScrolls -> Box(Modifier.weight(1f)) { action() }
                // The gap the arrangement would otherwise give, for when the screen scrolls.
                actionAtBottom -> Box(Modifier.padding(top = 24.dp)) { action() }
                else -> action()
            }
        }
    }
}

const val SIDE_BY_SIDE = "side-by-side"
const val STACKED = "stacked"
