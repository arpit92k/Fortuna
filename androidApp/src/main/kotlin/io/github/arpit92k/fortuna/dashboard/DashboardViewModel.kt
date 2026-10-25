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
import kotlinx.datetime.LocalDate

/** S-09, as of today. */
data class DashboardUi(
    val netWorth: Money,
    val assets: Money,
    val liabilities: Money,
    val sources: List<SourceRow>,
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
    val lastUpdated: LocalDate,
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
            lastUpdated = summary.lastUpdated,
        )
    }
    return DashboardUi(worth.total, worth.assets, worth.liabilities, rows)
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
