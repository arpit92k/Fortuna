package io.github.arpit92k.fortuna.dashboard

import io.github.arpit92k.fortuna.core.insights.netWorthChange
import io.github.arpit92k.fortuna.core.insights.NetWorthChange
import io.github.arpit92k.fortuna.ui.currenciesInUse
import io.github.arpit92k.fortuna.platform.DisplayPreferences
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.insights.fromBase
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
    /** The currency the figures above are shown in: the base currency unless another was chosen. */
    val shownIn: Currency? = null,
    /** The currencies the figures can be shown in: the base currency, then those of the accounts. */
    val currencies: List<Currency> = emptyList(),
    /** How many accounts at zero are left off [sources] because the setting hides them. */
    val hiddenAtZero: Int = 0,
) {
    /** With no sources, the dashboard only invites the first one. Hidden ones still count. */
    val empty: Boolean get() = sources.isEmpty() && hiddenAtZero == 0
}

/** One source in the dashboard's list. */
data class SourceRow(
    val id: SourceId,
    val name: String,
    val category: String,
    val liability: Boolean,
    /** In the source's own currency, as its statement shows. */
    val value: Money,
    /** For a source in another currency than [DashboardUi.shownIn], the same value in that one at the latest rate. */
    val valueInBase: Money? = null,
    val lastUpdated: LocalDate,
    /** Its latest change, in its own currency, or null with only an opening value. */
    val gain: Gain? = null,
)

/**
 * The dashboard's figures, from the core's insights, shown in [shownIn]: the
 * base currency's figures converted at the rate on each one's date, today's
 * for the totals and gains. Percentages stay as they are. The base currency
 * when [shownIn] is null or has no rate.
 */
fun dashboardOf(data: Dataset, today: LocalDate, shownIn: Currency? = null, hideZero: Boolean = false): DashboardUi {
    val base = data.baseCurrency!!
    val currencies = (listOf(base) + currenciesInUse(data.sources.map { it.currency })).distinct()
    val shown = shownIn?.takeIf { it in currencies && data.fromBase(Money(0, base), it, today) != null } ?: base
    val inBase = dashboardInBase(data, today).let { ui ->
        // Hidden accounts at zero leave the list only; they add nothing to the totals anyway.
        if (hideZero) {
            val (atZero, shown) = ui.sources.partition { it.value.minorUnits == 0L }
            ui.copy(sources = shown, hiddenAtZero = atZero.size)
        } else {
            ui
        }
    }
    if (shown == base) return inBase.copy(shownIn = base, currencies = currencies)
    fun Money.shown(date: LocalDate = today) = data.fromBase(this, shown, date)!!
    fun Gain.shown() = Gain(amount.shown(), this.base?.shown())
    return inBase.copy(
        netWorth = inBase.netWorth.shown(),
        assets = inBase.assets.shown(),
        liabilities = inBase.liabilities.shown(),
        sources = inBase.sources.map { row ->
            row.copy(valueInBase = if (row.value.currency == shown) null else (row.valueInBase ?: row.value).shown())
        },
        timeline = inBase.timeline.map { point ->
            point.copy(value = point.value.shown(point.date), invested = point.invested?.shown(point.date))
        },
        latestGain = inBase.latestGain?.shown(),
        gainSinceStart = inBase.gainSinceStart?.shown(),
        shownIn = shown,
        currencies = currencies,
    )
}

/**
 * How net worth changed from [from] to [to], as [netWorthChange] splits it,
 * shown in [currency]. Net deposits, growth and new accounts are converted at
 * [to]'s rate. The change itself is net worth on [to] at [to]'s rate less
 * net worth on [from] at [from]'s, as the chart draws it, and the currency
 * effect is what is left: it then includes the base currency moving against
 * [currency], which is a currency effect too, seen from [currency].
 */
fun netWorthChangeIn(data: Dataset, from: LocalDate?, to: LocalDate, currency: Currency): NetWorthChange {
    val inBase = data.netWorthChange(from, to)
    if (currency == data.baseCurrency) return inBase
    fun Money.at(date: LocalDate) = data.fromBase(this, currency, date)!!
    val total = data.netWorthOn(to).total.at(to) - (from?.let { data.netWorthOn(it).total.at(it) } ?: Money(0, currency))
    val added = inBase.added.at(to)
    val growth = inBase.growth.at(to)
    val newlyTracked = inBase.newlyTracked.at(to)
    return NetWorthChange(total, added, growth, total - added - growth - newlyTracked, newlyTracked)
}

private fun dashboardInBase(data: Dataset, today: LocalDate): DashboardUi {
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
class DashboardViewModel(
    private val core: Core,
    private val preferences: DisplayPreferences? = null,
    private val hideZero: StateFlow<Boolean> = MutableStateFlow(false),
) : ViewModel() {
    private val mutableUi = MutableStateFlow<DashboardUi?>(null)
    val ui: StateFlow<DashboardUi?> = mutableUi.asStateFlow()

    // Remembered between launches, as hiding amounts is.
    private val shownIn = MutableStateFlow(preferences?.dashboardCurrency?.let { Currency.forCode(it) })

    init {
        viewModelScope.launch {
            // Both: setup writes the dataset a moment before the app is unlocked.
            combine(core.state, core.dataset, shownIn, hideZero) { state, data, currency, zero ->
                Triple(state == SessionState.Unlocked && data != null, currency, zero)
            }.collect { (open, currency, zero) ->
                mutableUi.value = if (open) figures(currency, zero) else null
            }
        }
    }

    /** Shows the dashboard's figures in [currency], one of [DashboardUi.currencies]. */
    fun showIn(currency: Currency) {
        val base = mutableUi.value?.currencies?.firstOrNull()
        preferences?.dashboardCurrency = currency.code.takeIf { currency != base }
        shownIn.value = currency
    }

    private suspend fun figures(currency: Currency?, hideZero: Boolean): DashboardUi? = try {
        core.run { read { data, today -> dashboardOf(data, today, currency, hideZero) } }
    } catch (e: IllegalStateException) {
        // Locked in the meantime.
        null
    }
}
