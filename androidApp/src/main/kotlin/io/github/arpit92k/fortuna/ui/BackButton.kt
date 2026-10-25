package io.github.arpit92k.fortuna.ui

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import io.github.arpit92k.fortuna.R

/**
 * The back button of a page: an arrow, with "Back" as its label for screen
 * readers. The arrow points the way back, so it is mirrored where text runs
 * right to left.
 */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    IconButton(onClick = onBack, modifier = modifier.testTag(BACK_BUTTON)) {
        FortunaIconImage(
            FortunaIcon.Back,
            modifier = Modifier.graphicsLayer { scaleX = if (mirrored) -1f else 1f },
            contentDescription = stringResource(R.string.back),
        )
    }
}

const val BACK_BUTTON = "back"
