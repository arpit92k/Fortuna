package io.github.arpit92k.fortuna.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.insights.Gain
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
    /** What it has earned since its opening value (for a debt, its interest, as a loss), or null with only an opening value. */
    val growth: Gain? = null,
) {
    /** The latest value can be deleted unless it is the only one. */
    val canDeleteLatest: Boolean get() = entries.size > 1

    /** A source is deleted only while it has just its opening value; one with history is kept. */
    val canDeleteSource: Boolean get() = entries.size == 1
}

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
        chart = sourcePoints(investedPoints(history.entries), today),
        growth = growthOf(history, liability = data.category(source.categoryId)?.kind == CategoryKind.LIABILITY),
    )
}

/** Follows the open dataset and the source being shown. */
class SourcePageViewModel(
    private val core: Core,
    private val today: () -> LocalDate = { java.time.LocalDate.now().toKotlinLocalDate() },
) : ViewModel() {
    private val shown = MutableStateFlow<SourceId?>(null)
    private val mutableUi = MutableStateFlow<SourcePageUi?>(null)
    val ui: StateFlow<SourcePageUi?> = mutableUi.asStateFlow()

    init {
        viewModelScope.launch {
            combine(core.state, core.dataset, shown) { state, data, id -> Triple(state == SessionState.Unlocked, data, id) }
                .collect { (open, data, id) ->
                    mutableUi.value = if (open && data != null && id != null) sourcePageOf(data, id, today()) else null
                }
        }
    }

    fun show(id: SourceId) {
        shown.value = id
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

/** Each value with what had been put in by then: the opening value, then each amount added on top. */
private fun investedPoints(entries: List<HistoryEntry>): List<ChartPoint> {
    var put: Money? = null
    return entries.map { entry ->
        val now = put?.let { it + entry.amountAdded!! } ?: entry.value
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
    if (history.entries.size < 2) return null
    val put = history.entries.first().value + history.totalAdded
    val earned = if (liability) -history.totalGrowth else history.totalGrowth
    return Gain(earned, if (put.minorUnits > 0) earned.minorUnits * 100.0 / put.minorUnits else null)
}
