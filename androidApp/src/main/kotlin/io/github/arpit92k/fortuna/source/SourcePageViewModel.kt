package io.github.arpit92k.fortuna.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.insights.Gain
import io.github.arpit92k.fortuna.core.insights.sourceInBase
import io.github.arpit92k.fortuna.core.insights.changeOver
import io.github.arpit92k.fortuna.core.insights.ChangeSplit
import io.github.arpit92k.fortuna.core.insights.HistoryEntry
import io.github.arpit92k.fortuna.core.insights.SourceHistory
import io.github.arpit92k.fortuna.core.insights.history
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.deleteLatestSnapshot
import io.github.arpit92k.fortuna.core.recording.deleteSource
import io.github.arpit92k.fortuna.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import io.github.arpit92k.fortuna.ui.ChartPoint
import io.github.arpit92k.fortuna.ui.sourcePoints
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate

/** S-10: one source and its history, in its own currency. */
data class SourcePageUi(
    val id: SourceId,
    val name: String,
    val category: String,
    val liability: Boolean,
    val note: String?,
    val value: Money,
    /** Newest first. */
    val entries: List<HistoryRow>,
    val totalAdded: Money,
    val totalGrowth: Money,
    /** The value over time, oldest first, carried on to today, with what was put in. */
    val chart: List<ChartPoint> = emptyList(),
    /** What it has earned (for a debt, its interest, as a loss), including before it was added, or null with nothing yet. */
    val growth: Gain? = null,
    /** What it had gained or lost before it was added, or null when its whole opening value was put in. */
    val openingGain: Gain? = null,
    /** The same source in the base currency, or null for a source already in it. */
    val inBase: InBase? = null,
    /** Whether the page shows [inBase] rather than the source's own currency. */
    val showingBase: Boolean = false,
) {
    /** The chart the page shows: in the base currency when [showingBase]. */
    val shownChart: List<ChartPoint> get() = inBase?.takeIf { showingBase }?.chart ?: chart

    /** The latest value can be deleted unless it is the only one. */
    val canDeleteLatest: Boolean get() = entries.size > 1

    /** A source is deleted only while it has just its opening value; one with history is kept. */
    val canDeleteSource: Boolean get() = entries.size == 1
}

/**
 * A foreign source in the base currency: its value today at today's rate,
 * the value over time, and every change since it opened split into net
 * deposits, growth and currency effect.
 */
data class InBase(
    val value: Money,
    val chart: List<ChartPoint>,
    /** From the opening value to today. */
    val sinceOpening: ChangeSplit,
    /** Each value at the rate on its date, and its change from the value before; null for the opening value. */
    val rows: Map<LocalDate, BaseRow>,
)

data class BaseRow(val value: Money, val change: ChangeSplit?)

/** One snapshot in the history. For the opening balance, the three parts are null. */
data class HistoryRow(
    val date: LocalDate,
    val value: Money,
    val change: Money?,
    val added: Money?,
    val growth: Money?,
    val note: String?,
    val opening: Boolean,
)

/** The source page's figures on [today], from the core's insights. Null if the source is gone. */
fun sourcePageOf(data: Dataset, id: SourceId, today: LocalDate): SourcePageUi? {
    val source = data.source(id) ?: return null
    val history = data.history(id)
    val rows = history.entries.mapIndexed { index, entry ->
        HistoryRow(entry.date, entry.value, entry.change, entry.amountAdded, entry.growth, entry.note, opening = index == 0)
    }
    return SourcePageUi(
        id = id,
        name = source.name,
        category = data.category(source.categoryId)?.name.orEmpty(),
        liability = data.category(source.categoryId)?.kind == CategoryKind.LIABILITY,
        note = source.note,
        value = rows.last().value,
        entries = rows.reversed(),
        totalAdded = history.totalAdded,
        totalGrowth = history.totalGrowth,
        chart = sourcePoints(investedPoints(history.entries, source.openingPutIn!!), today),
        growth = growthOf(history, liability = data.category(source.categoryId)?.kind == CategoryKind.LIABILITY),
        inBase = if (source.currency == data.baseCurrency) null else inBaseOf(data, id, history.entries, today),
        openingGain = history.openingGain.takeIf { it.minorUnits != 0L }?.let { gained ->
            val liability = data.category(source.categoryId)?.kind == CategoryKind.LIABILITY
            Gain(if (liability) -gained else gained, source.openingPutIn!!.takeIf { it.minorUnits > 0 })
        },
    )
}

private fun inBaseOf(data: Dataset, id: SourceId, entries: List<HistoryEntry>, today: LocalDate): InBase {
    val opening = entries.first().date
    // Today, or the latest value's date should the phone's clock be behind it.
    val end = maxOf(today, entries.last().date)
    val points = data.sourceInBase(id, end)
    val byDate = points.associateBy { it.date }
    return InBase(
        value = points.last().value,
        chart = points.map { ChartPoint(it.date, it.value, it.invested) },
        sinceOpening = data.changeOver(id, opening, end),
        rows = entries.zipWithNext().associate { (before, after) ->
            after.date to BaseRow(byDate.getValue(after.date).value, data.changeOver(id, before.date, after.date))
        } + (opening to BaseRow(byDate.getValue(opening).value, null)),
    )
}

/** Follows the open dataset and the source being shown. */
class SourcePageViewModel(
    private val core: Core,
    private val today: () -> LocalDate = { java.time.LocalDate.now().toKotlinLocalDate() },
) : ViewModel() {
    private val shown = MutableStateFlow<SourceId?>(null)
    private val inBase = MutableStateFlow(false)
    private val mutableUi = MutableStateFlow<SourcePageUi?>(null)
    val ui: StateFlow<SourcePageUi?> = mutableUi.asStateFlow()

    init {
        viewModelScope.launch {
            combine(core.state, core.dataset, shown, inBase) { state, data, id, base -> Shown(state == SessionState.Unlocked, data, id, base) }
                .collect { (open, data, id, base) ->
                    mutableUi.value = if (open && data != null && id != null) {
                        sourcePageOf(data, id, today())?.let { it.copy(showingBase = base && it.inBase != null) }
                    } else {
                        null
                    }
                }
        }
    }

    fun show(id: SourceId) {
        // Another source starts in its own currency.
        if (shown.value != id) inBase.value = false
        shown.value = id
    }

    /** Shows a foreign source in the base currency, or back in its own. */
    fun showInBase(show: Boolean) {
        inBase.value = show
    }

    /** Deletes the latest value, after the screen has asked. */
    fun deleteLatest(id: SourceId) {
        viewModelScope.launch { core.run { record { data, _ -> data.deleteLatestSnapshot(id) } } }
    }

    /** Deletes a source created by mistake, after the screen has asked. [onDeleted] runs if it was deleted. */
    fun deleteSource(id: SourceId, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = core.run { record { data, _ -> data.deleteSource(id) } }
            if (result is RecordingResult.Success) onDeleted()
        }
    }
}

/** Each value with what had been put in by then: [openingPutIn], then each amount added on top. */
private fun investedPoints(entries: List<HistoryEntry>, openingPutIn: Money): List<ChartPoint> {
    var put: Money? = null
    return entries.map { entry ->
        val now = put?.let { it + entry.amountAdded!! } ?: openingPutIn
        put = now
        ChartPoint(entry.date, entry.value, now)
    }
}

/**
 * What a source has earned since its opening value, against that value plus
 * everything added since; for a debt, its interest, as a loss. Null with only
 * an opening value.
 */
private fun growthOf(history: SourceHistory, liability: Boolean): Gain? {
    if (history.entries.size < 2 && history.openingGain.minorUnits == 0L) return null
    val put = history.source.openingPutIn!! + history.totalAdded
    val earned = if (liability) -history.totalGrowth else history.totalGrowth
    return Gain(earned, put.takeIf { it.minorUnits > 0 })
}

private data class Shown(val open: Boolean, val data: Dataset?, val id: SourceId?, val base: Boolean)
