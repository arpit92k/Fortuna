package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Decided: the icons are drawn in code, not loaded from a library or from
// image files. Each is a few lines on a 24 by 24 grid, in one line colour with
// one accent, and takes both from the theme, so a single definition serves the
// light and the dark theme. The app needs no icon library, and no image
// resources beyond the launcher icon.

/** The icons the app uses. */
enum class FortunaIcon {
    // On this phone, no account, PIN and phrase: the welcome promises.
    Phone,
    NoCloud,
    Lock,

    // Screens.
    Pin,
    Phrase,
    Currency,
    Add,

    // On buttons, beside the words.
    Plus,
    Update,

    // On buttons with no words: the screen's label is read aloud instead.
    Settings,
    Back,

    // The starter categories. Debt, a bill, is drawn in the error colour.
    Cash,
    Investments,
    Property,
    Pension,
    Debt,
}

/** The icon for a starter category, or null for a category the app has no picture for. */
fun categoryIcon(name: String): FortunaIcon? = when (name) {
    "Cash" -> FortunaIcon.Cash
    "Investments" -> FortunaIcon.Investments
    "Property" -> FortunaIcon.Property
    "Pension" -> FortunaIcon.Pension
    "Debt" -> FortunaIcon.Debt
    else -> null
}

/** One stroke or dot of an icon. Coordinates are on the 24 by 24 grid. */
private sealed class Part(val accent: Boolean) {
    abstract fun render(scope: DrawScope, color: Color, stroke: Stroke)
}

private class StrokePath(d: String, accent: Boolean = false) : Part(accent) {
    private val path: Path = PathParser().parsePathString(d).toPath()
    override fun render(scope: DrawScope, color: Color, stroke: Stroke) = scope.drawPath(path, color, style = stroke)
}

private class StrokeCircle(val cx: Float, val cy: Float, val r: Float, accent: Boolean = false) : Part(accent) {
    override fun render(scope: DrawScope, color: Color, stroke: Stroke) =
        scope.drawCircle(color, radius = r, center = Offset(cx, cy), style = stroke)
}

private class StrokeRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val corner: Float,
    accent: Boolean = false,
) : Part(accent) {
    override fun render(scope: DrawScope, color: Color, stroke: Stroke) =
        scope.drawRoundRect(color, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(corner), style = stroke)
}

private class FilledCircle(val cx: Float, val cy: Float, val r: Float, accent: Boolean = false) : Part(accent) {
    override fun render(scope: DrawScope, color: Color, stroke: Stroke) =
        scope.drawCircle(color, radius = r, center = Offset(cx, cy), style = Fill)
}

private fun FortunaIcon.parts(): List<Part> = when (this) {
    FortunaIcon.Phone -> listOf(
        StrokeRect(7f, 2.5f, 10f, 19f, 2.5f),
        StrokePath("M10.5 18.5 H13.5"),
    )
    // A cloud with a line through it: nothing goes online.
    FortunaIcon.NoCloud -> listOf(
        StrokePath("M6.5 17.5 a3.5 3.5 0 0 1 -0.3 -7 a5 5 0 0 1 9.6 -1.3 a4 4 0 0 1 1.7 8.3 Z"),
        StrokePath("M4 4 L20 20", accent = true),
    )
    FortunaIcon.Lock -> listOf(
        StrokeRect(5f, 11f, 14f, 9.5f, 2f),
        StrokePath("M8 11 V8 a4 4 0 0 1 8 0 V11"),
        FilledCircle(12f, 15.5f, 1.3f, accent = true),
    )
    // The nine keys of a pad, the middle one lit.
    FortunaIcon.Pin -> buildList {
        for (row in listOf(6f, 12f, 18f)) {
            for (column in listOf(6f, 12f, 18f)) {
                add(FilledCircle(column, row, 1.5f, accent = row == 12f && column == 12f))
            }
        }
    }
    FortunaIcon.Phrase -> listOf(
        StrokeRect(5f, 3.5f, 14f, 17f, 2f),
        StrokePath("M8.5 9 H15.5 M8.5 12.5 H15.5"),
        StrokePath("M8.5 16 H12", accent = true),
    )
    // Two arrows, one each way: one currency into another.
    FortunaIcon.Currency -> listOf(
        StrokePath("M5 9 H18 M15 6 L18 9 L15 12"),
        StrokePath("M19 15 H6 M9 12 L6 15 L9 18", accent = true),
    )
    FortunaIcon.Add -> listOf(
        StrokeRect(3.5f, 3.5f, 17f, 17f, 4f),
        StrokePath("M12 8 V16 M8 12 H16", accent = true),
    )
    FortunaIcon.Plus -> listOf(
        StrokePath("M12 5 V19 M5 12 H19"),
    )
    // An arrow going back round a clock: a new reading, dated.
    FortunaIcon.Update -> listOf(
        StrokePath("M4.05 11 A8 8 0 1 1 4.55 15"),
        StrokePath("M4.05 20 V15 H9.05"),
        StrokePath("M12 8 V12 L14 14", accent = true),
    )
    // A cogwheel with a lit hub.
    FortunaIcon.Settings -> listOf(
        StrokePath("M10.4 5.0 L10.5 2.6 L13.5 2.6 L13.6 5.0 L15.8 5.9 L17.6 4.3 L19.7 6.4 L18.1 8.2 L19.0 10.4 L21.4 10.5 L21.4 13.5 L19.0 13.6 L18.1 15.8 L19.7 17.6 L17.6 19.7 L15.8 18.1 L13.6 19.0 L13.5 21.4 L10.5 21.4 L10.4 19.0 L8.2 18.1 L6.4 19.7 L4.3 17.6 L5.9 15.8 L5.0 13.6 L2.6 13.5 L2.6 10.5 L5.0 10.4 L5.9 8.2 L4.3 6.4 L6.4 4.3 L8.2 5.9 Z"),
        StrokeCircle(12f, 12f, 3f, accent = true),
    )
    // Pointing left; mirrored where text runs right to left.
    FortunaIcon.Back -> listOf(
        StrokePath("M19 12 H5 M11 6 L5 12 L11 18"),
    )
    FortunaIcon.Cash -> listOf(
        StrokeRect(2.5f, 6f, 19f, 12f, 2f),
        StrokeCircle(12f, 12f, 2.8f, accent = true),
        StrokePath("M6 12 H6.01 M18 12 H18.01"),
    )
    FortunaIcon.Investments -> listOf(
        StrokePath("M3 17 L9 11 L13 15 L20 8"),
        StrokePath("M15 8 H20 V13", accent = true),
    )
    FortunaIcon.Property -> listOf(
        StrokePath("M4 11 L12 4 L20 11 V20 H4 Z"),
        StrokePath("M10 20 V14.5 H14 V20", accent = true),
    )
    // An umbrella: what a pension keeps over you.
    FortunaIcon.Pension -> listOf(
        StrokePath("M3 12 a9 9 0 0 1 18 0 Z"),
        StrokePath("M12 12 V18 a2 2 0 0 1 -4 0", accent = true),
    )
    // A bill: something to be paid.
    FortunaIcon.Debt -> listOf(
        StrokePath("M6 3 H18 V21 L15.5 19 L13 21 L10.5 19 L8 21 L6 19 Z"),
        StrokePath("M9 8 H15 M9 12 H15"),
    )
}

/**
 * [icon], [size] square, drawn in [line] with its accent in [accent]. Debt
 * defaults to the error colour throughout. Decorative unless
 * [contentDescription] is given, as when the icon is a button's only label.
 */
@Composable
fun FortunaIconImage(
    icon: FortunaIcon,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    line: Color = if (icon == FortunaIcon.Debt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    accent: Color = if (icon == FortunaIcon.Debt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
) {
    val parts = remember(icon) { icon.parts() }
    val described = if (contentDescription == null) modifier else modifier.semantics { this.contentDescription = contentDescription }
    Canvas(described.size(size)) {
        val unit = this.size.minDimension / 24f
        // Drawn on the 24 by 24 grid and scaled up, so the 1.5 line is the same fraction at any size.
        scale(unit, Offset.Zero) {
            val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (part in parts) part.render(this, if (part.accent) accent else line, stroke)
        }
    }
}

/**
 * [icon] in the colour of the button it sits in, at the start of its label,
 * followed by the gap Material leaves before the words.
 */
@Composable
fun RowScope.ButtonIcon(icon: FortunaIcon) {
    val tint = LocalContentColor.current
    FortunaIconImage(icon, size = 18.dp, line = tint, accent = tint)
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
}

/** [icon] on a rounded tile, as at the top of a screen. */
@Composable
fun IconTile(icon: FortunaIcon, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        FortunaIconImage(icon, size = size * 0.55f)
    }
}

/**
 * The app's mark: a shield, which is the privacy, holding an F, with its middle
 * arm in the accent colour. Drawn from the launcher icon's own shapes.
 */
@Composable
fun FortunaMark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    line: Color = MaterialTheme.colorScheme.onBackground,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val shield = remember { PathParser().parsePathString("M90 34 L138 52 V90 C138 118 115 136 90 146 C65 136 42 118 42 90 V52 Z").toPath() }
    val letter = remember { PathParser().parsePathString("M70 62 H110 V76 H84 V120 H70 Z").toPath() }
    // The artwork spans 104 by 120 units, with its stroke.
    Canvas(modifier.size(width = size * (MARK_WIDTH / MARK_HEIGHT), height = size)) {
        val unit = this.size.height / MARK_HEIGHT
        scale(unit, Offset.Zero) {
            translate(-MARK_LEFT, -MARK_TOP) {
                drawPath(shield, line, style = Stroke(width = 7f, join = StrokeJoin.Round))
                drawPath(letter, line, style = Fill)
                drawRect(accent, topLeft = Offset(84f, 86f), size = Size(22f, 13f))
            }
        }
    }
}

private const val MARK_LEFT = 38f
private const val MARK_TOP = 30f
private const val MARK_WIDTH = 104f
private const val MARK_HEIGHT = 120f

/**
 * The mark inside two thin rings, which say "kept in here". [radius] is that of
 * the outer ring.
 */
@Composable
fun RingedMark(modifier: Modifier = Modifier, radius: Dp = 60.dp) {
    val ring = MaterialTheme.colorScheme.primary
    Box(modifier.size(radius * 2), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(radius * 2)) {
            val outer = this.size.minDimension / 2 - 1.dp.toPx()
            drawCircle(ring.copy(alpha = 0.3f), radius = outer, style = Stroke(width = 1.dp.toPx()))
            drawCircle(ring.copy(alpha = 0.5f), radius = outer * 0.65f, style = Stroke(width = 1.dp.toPx()))
        }
        FortunaMark(size = radius * 1.1f)
    }
}
