package io.github.arpit92k.fortuna.dashboard

import androidx.lifecycle.ViewModel
import io.github.arpit92k.fortuna.platform.DisplayPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether accounts at zero are hidden: left off the dashboard's list and out
 * of periodic updates. Remembered between launches. They still count in every
 * total, where they add nothing, and keep their pages and history.
 */
class ZeroAccountsViewModel(private val preferences: DisplayPreferences) : ViewModel() {
    private val mutableHidden = MutableStateFlow(preferences.zeroAccountsHidden)
    val hidden: StateFlow<Boolean> = mutableHidden.asStateFlow()

    /** The switch in Settings, and the toggle beside the dashboard's list. */
    fun hide(hide: Boolean) {
        preferences.zeroAccountsHidden = hide
        mutableHidden.value = hide
    }
}
