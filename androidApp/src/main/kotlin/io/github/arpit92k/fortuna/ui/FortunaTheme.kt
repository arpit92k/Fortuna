package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import io.github.arpit92k.fortuna.R

// Decided: the app's colour is mint, the accent of the launcher icon. Dark uses
// the icon's own mint; light needs a deeper one, because the pale mint cannot
// be read as lines or carry white text on an off-white page. Liabilities and
// warnings use the scheme's error colour, so an amount owed looks different
// from an amount owned.

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF0B2E22),
    primaryContainer = Color(0xFF0F3D2E),
    onPrimaryContainer = Color(0xFFB7F5DC),
    secondary = Color(0xFF9CCFB8),
    onSecondary = Color(0xFF0B2E22),
    secondaryContainer = Color(0xFF2B3A35),
    onSecondaryContainer = Color(0xFFD5F0E5),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    onBackground = Color(0xFFE4ECE8),
    onSurface = Color(0xFFE4ECE8),
    surfaceVariant = Color(0xFF242A31),
    onSurfaceVariant = Color(0xFFB4BEB9),
    surfaceContainer = Color(0xFF1E2328),
    surfaceContainerHigh = Color(0xFF20262C),
    surfaceContainerHighest = Color(0xFF242A31),
    outline = Color(0xFF7F8A85),
    outlineVariant = Color(0xFF3A423E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6F0DD),
    onPrimaryContainer = Color(0xFF062C20),
    secondary = Color(0xFF3F6B5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8DF),
    onSecondaryContainer = Color(0xFF0B2E22),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    onBackground = Color(0xFF14201B),
    onSurface = Color(0xFF14201B),
    surfaceVariant = Color(0xFFE7EEEA),
    onSurfaceVariant = Color(0xFF46524C),
    surfaceContainer = Color(0xFFF0F4F2),
    surfaceContainerHigh = Color(0xFFEDF2EF),
    surfaceContainerHighest = Color(0xFFE7EEEA),
    outline = Color(0xFF74807A),
    outlineVariant = Color(0xFFC2CCC7),
)

/**
 * Follows the system light/dark setting. The background comes from the same
 * colour resource as the window and splash screen background, so there is no
 * flash when the splash screen hands over to the app.
 */
@Composable
fun FortunaTheme(content: @Composable () -> Unit) {
    val background = colorResource(R.color.fortuna_background)
    val colors = (if (isSystemInDarkTheme()) DarkColors else LightColors)
        .copy(background = background, surface = background)
    MaterialTheme(colorScheme = colors, content = content)
}
