package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import io.github.arpit92k.fortuna.R

/**
 * Follows the system light/dark setting. The background comes from the same
 * colour resource as the window and splash screen background, so there is no
 * flash when the splash screen hands over to the app.
 */
@Composable
fun FortunaTheme(content: @Composable () -> Unit) {
    val background = colorResource(R.color.fortuna_background)
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(background = background, surface = background)
    } else {
        lightColorScheme(background = background, surface = background)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
