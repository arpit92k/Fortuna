package io.github.arpit92k.fortuna.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The screens of the unlocked app. Settings, the source page and the update arrive in later steps. */
sealed interface Destination {
    /** S-09. */
    data object Dashboard : Destination

    /** S-11. A screen where a value is entered, so it earns the longer grace period. */
    data object AddSource : Destination
}

/** Whether the user is entering a value on [destination], which earns the two-minute grace period. */
fun entersValue(destination: Destination): Boolean = destination is Destination.AddSource

/**
 * Where the user is in the unlocked app: a stack of screens, the dashboard at
 * the bottom.
 *
 * It lives in a view model rather than in the screens, so it survives the lock
 * screen being placed over the app: after unlocking, the user is where they were.
 */
class Navigator : ViewModel() {
    private val mutableStack = MutableStateFlow<List<Destination>>(listOf(Destination.Dashboard))
    val stack: StateFlow<List<Destination>> = mutableStack.asStateFlow()

    val current: Destination get() = mutableStack.value.last()

    fun open(destination: Destination) {
        mutableStack.update { if (it.last() == destination) it else it + destination }
    }

    /** Goes back one screen. Returns false on the dashboard, where back leaves the app. */
    fun back(): Boolean {
        if (mutableStack.value.size == 1) return false
        mutableStack.update { it.dropLast(1) }
        return true
    }

    /** Back to the dashboard alone, as after a restore or a recovery. */
    fun reset() {
        mutableStack.value = listOf(Destination.Dashboard)
    }
}
