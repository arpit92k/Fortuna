package io.github.arpit92k.fortuna.dashboard

import androidx.lifecycle.ViewModel
import io.github.arpit92k.fortuna.platform.DisplayPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether amounts are hidden across the app, remembered between launches. */
class AmountsViewModel(private val preferences: DisplayPreferences) : ViewModel() {
    private val mutableHidden = MutableStateFlow(preferences.amountsHidden)
    val hidden: StateFlow<Boolean> = mutableHidden.asStateFlow()

    /** The eye on the dashboard. */
    fun toggle() {
        val hide = !mutableHidden.value
        preferences.amountsHidden = hide
        mutableHidden.value = hide
    }
}
