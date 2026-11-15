package io.github.arpit92k.fortuna.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Lets screenshots be taken while the calling screen is showing, in a build
 * that otherwise blocks them. Decided: the recovery phrase on S-03 can be
 * captured, so the user may keep the words as a picture. The block comes back
 * as soon as the screen is left.
 */
@Composable
fun AllowScreenshots() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val secure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (secure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Covers the whole app while [covered]. The app switcher can show an app's
 * live window, which blocking screenshots does not hide, so swiping from the
 * app straight into the switcher would show its figures. Covered, its card
 * shows only the app's background.
 */
@Composable
fun PrivacyCover(covered: Boolean) {
    if (covered) {
        FortunaTheme { Surface(Modifier.fillMaxSize().testTag(PRIVACY_COVER)) {} }
    }
}

const val PRIVACY_COVER = "privacy-cover"
