package io.github.arpit92k.fortuna.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

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
