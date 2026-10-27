package io.github.arpit92k.fortuna.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.insights.netWorthOn
import io.github.arpit92k.fortuna.core.insights.sourceList
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import io.github.arpit92k.fortuna.core.insights.Gain
import io.github.arpit92k.fortuna.core.insights.gainSinceStart
import io.github.arpit92k.fortuna.core.insights.latestGain
import io.github.arpit92k.fortuna.core.insights.sourceLatestGain
import io.github.arpit92k.fortuna.ui.ChartPoint
import io.github.arpit92k.fortuna.ui.netWorthPoints
import kotlinx.datetime.LocalDate

/** S-09, as of today. */
data class DashboardUi(
    val netWorth: Money,
    val assets: Money,
    val liabilities: Money,
    val sources: List<SourceRow>,
    /** Net worth over time, from the first value recorded to today, with what was put in. */
    val timeline: List<ChartPoint> = emptyList(),
    /** Gained across every source's latest change, or null before any source has changed. */
    val latestGain: Gain? = null,
    /** Gained since the first value was recorded. */
    val gainSinceStart: Gain? = null,
) {
    /** With no sources, the dashboard only invites the first one. */
    val empty: Boolean get() = sources.isEmpty()
}

/** One source in the dashboard's list. */
data class SourceRow(
    val id: SourceId,
    val name: String,
    val category: String,
    val liability: Boolean,
    /** In the source's own currency, as its statement shows. */
    val value: Money,
    /** For a source in a foreign currency, the same value in the base currency at the latest rate. */
    val valueInBase: Money? = null,
    val lastUpdated: LocalDate,
    /** Its latest change, in its own currency, or null with only an opening value. */
    val gain: Gain? = null,
)

/** The dashboard's figures, from the core's insights. */
fun dashboardOf(data: Dataset, today: LocalDate): DashboardUi {
    val worth = data.netWorthOn(today)
    val rows = data.sourceList(today).map { summary ->
        SourceRow(
            id = summary.source.id,
            name = summary.source.name,
            category = data.category(summary.source.categoryId)?.name.orEmpty(),
            liability = summary.kind == CategoryKind.LIABILITY,
            value = summary.value,
            valueInBase = summary.valueInBase,
            lastUpdated = summary.lastUpdated,
            gain = data.sourceLatestGain(summary.source.id),
        )
    }
    // Before any source has a second value there is nothing gained yet to show.
    // An account added with a gain it had already made counts too.
    val changed = rows.any { it.gain != null } || data.sources.any { it.openingPrincipal != null }
    return DashboardUi(
        netWorth = worth.total,
        assets = worth.assets,
        liabilities = worth.liabilities,
        sources = rows,
        timeline = netWorthPoints(data, today),
        latestGain = if (changed) data.latestGain() else null,
        gainSinceStart = if (changed) data.gainSinceStart(today) else null,
    )
}

/**
 * Follows the open dataset and works out the dashboard each time it changes.
 * Null while locked, when there is nothing to show.
 */
class DashboardViewModel(private val core: Core) : ViewModel() {
    private val mutableUi = MutableStateFlow<DashboardUi?>(null)
    val ui: StateFlow<DashboardUi?> = mutableUi.asStateFlow()

    init {
        viewModelScope.launch {
            // Both: setup writes the dataset a moment before the app is unlocked.
            combine(core.state, core.dataset) { state, data -> state == SessionState.Unlocked && data != null }
                .collect { open ->
                    mutableUi.value = if (open) figures() else null
                }
        }
    }

    private suspend fun figures(): DashboardUi? = try {
        core.run { read { data, today -> dashboardOf(data, today) } }
    } catch (e: IllegalStateException) {
        // Locked in the meantime.
        null
    }
}
