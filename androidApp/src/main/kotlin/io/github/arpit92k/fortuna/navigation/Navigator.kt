package io.github.arpit92k.fortuna.navigation

import androidx.lifecycle.ViewModel
import io.github.arpit92k.fortuna.core.domain.SourceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** The screens of the unlocked app. */
sealed interface Destination {
    /** S-09. */
    data object Dashboard : Destination

    /** S-11. */
    data object AddSource : Destination

    /** S-10, one source's page. */
    data class Source(val id: SourceId) : Destination

    /** S-12: a new value for a source, or with [editing], a correction to the value on that date. */
    data class ValueEntry(val id: SourceId, val editing: LocalDate? = null) : Destination

    /** S-13, the start of a periodic update. */
    data object Update : Destination

    /** S-14, one source at a time. */
    data object UpdateStep : Destination

    /** S-15. */
    data object Settings : Destination

    /** S-16. */
    data object Currencies : Destination

    /** S-17. */
    data object Backup : Destination

    /** About Fortuna: the tour of what the app does; [firstRun] once after the app is first opened, otherwise from Settings. */
    data class About(val firstRun: Boolean = false) : Destination

    /** Growth: net worth now against earlier years, whatever moved it. */
    data object Growth : Destination

    /** A chart full screen: net worth with no [source], or one source's value. */
    data class Chart(val source: SourceId? = null) : Destination
}

/**
 * Whether the user is entering a value on [destination], which earns the
 * two-minute grace period. Saving a backup counts too: the system's file
 * picker takes the user out of the app while they choose where it goes.
 */
fun entersValue(destination: Destination): Boolean =
    destination is Destination.AddSource ||
        destination is Destination.ValueEntry ||
        destination is Destination.UpdateStep ||
        destination is Destination.Backup

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

    /**
     * Opens [destination]. Opening one source's page from another's replaces
     * it, as when the list beside it on a wide window picks a different source.
     */
    fun open(destination: Destination) {
        mutableStack.update {
            when {
                it.last() == destination -> it
                destination is Destination.Source && it.last() is Destination.Source -> it.dropLast(1) + destination
                else -> it + destination
            }
        }
    }

    /** Goes back one screen. Returns false on the dashboard, where back leaves the app. */
    fun back(): Boolean {
        if (mutableStack.value.size == 1) return false
        mutableStack.update { it.dropLast(1) }
        return true
    }

    /** Leaves every screen of a source that no longer exists. */
    fun forget(id: SourceId) {
        mutableStack.update { stack ->
            stack.filterNot {
                (it is Destination.Source && it.id == id) || (it is Destination.ValueEntry && it.id == id) ||
                    (it is Destination.Chart && it.source == id)
            }
        }
    }

    /** Back to the dashboard alone, as after a restore or a recovery. */
    fun reset() {
        mutableStack.value = listOf(Destination.Dashboard)
    }
}
