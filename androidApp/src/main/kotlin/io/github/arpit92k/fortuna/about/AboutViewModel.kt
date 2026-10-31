package io.github.arpit92k.fortuna.about

import androidx.lifecycle.ViewModel
import io.github.arpit92k.fortuna.platform.DisplayPreferences

/** Whether the About tour has been seen, so it shows once, the first time the app is open. */
class AboutViewModel(private val preferences: DisplayPreferences) : ViewModel() {
    val seen: Boolean get() = preferences.aboutSeen

    /** Done, Skip or back, on the first showing. */
    fun markSeen() {
        preferences.aboutSeen = true
    }
}
