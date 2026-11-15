package io.github.arpit92k.fortuna.platform

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import java.util.WeakHashMap

/**
 * Whether any of the app's windows has focus: the activity's own, or one of
 * its dialogs. The app switcher takes focus from the app while leaving it
 * resumed and showing a live picture of it, so losing focus is the one sign
 * that the app has left the front. A dialog of the app's own taking focus
 * does not count.
 *
 * [onChange] is called with true when focus leaves every window of the app,
 * and false when it comes back.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AppFocus(private val main: View, private val onChange: (lost: Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val watched = WeakHashMap<View, ViewTreeObserver.OnWindowFocusChangeListener>()
    private val check = Runnable { evaluate() }

    init {
        watch(main)
    }

    private fun watch(root: View) {
        if (watched.containsKey(root)) return
        val listener = ViewTreeObserver.OnWindowFocusChangeListener {
            // Focus moving from one window of the app to another passes through
            // neither having it, so look again once it has settled.
            handler.removeCallbacks(check)
            handler.postDelayed(check, SETTLE_MILLIS)
        }
        watched[root] = listener
        root.viewTreeObserver.addOnWindowFocusChangeListener(listener)
    }

    private fun evaluate() {
        val windows = WindowInspector.getGlobalWindowViews()
        // A dialog opened since is watched too, so its losing focus is seen.
        windows.forEach(::watch)
        onChange(windows.none { it.hasWindowFocus() })
    }

    private companion object {
        const val SETTLE_MILLIS = 100L
    }
}
