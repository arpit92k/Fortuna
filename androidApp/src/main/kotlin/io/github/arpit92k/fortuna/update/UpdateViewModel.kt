package io.github.arpit92k.fortuna.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.AmountProblem
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.money.MoneyParseResult
import io.github.arpit92k.fortuna.core.recording.RecordingError
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.UpdateQueueResult
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.recordRate
import io.github.arpit92k.fortuna.core.recording.updateQueue
import io.github.arpit92k.fortuna.core.session.SessionState
import io.github.arpit92k.fortuna.source.ValueEntryProblem
import io.github.arpit92k.fortuna.ui.parseAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.util.Locale

/** One source due in the update, with its last value as the screen shows it. */
data class DueSource(
    val id: SourceId,
    val name: String,
    val category: String,
    val liability: Boolean,
    val lastValue: Money,
    val lastDate: LocalDate,
)

/** A foreign currency of a due source, with the rate in force on the update's date and how many days old it is. */
data class RateToConfirm(val currency: Currency, val last: Rate?, val lastDate: LocalDate?, val ageInDays: Int?)

/**
 * What an update dated [date] would cover: the sources due, the one updated
 * longest ago first; how many are left out; and the foreign currencies of the
 * sources due, whose rates the user keeps or renews.
 */
data class UpdatePlan(
    val date: LocalDate,
    val due: List<DueSource>,
    val leftOut: Int,
    val rates: List<RateToConfirm> = emptyList(),
)

/** The entry being typed for the current source. Kept across a lock, so nothing typed is lost. */
data class StepForm(
    val value: String = "",
    /** Decided: optional; blank counts as nothing added. */
    val added: String = "",
    val note: String = "",
    val problem: ValueEntryProblem? = null,
    val saving: Boolean = false,
)

/** S-13 and S-14: the periodic update (UF-04), without rates until step 19. */
data class UpdateUi(
    /** Null means today. */
    val date: LocalDate? = null,
    val plan: UpdatePlan? = null,
    /** True once S-14 has begun; the date is then fixed for this sitting. */
    val started: Boolean = false,
    /** Skipped in this sitting. They are offered again when an update with the same date starts again. */
    val skipped: Set<SourceId> = emptySet(),
    /** How many were due when this sitting began, and how many have been entered or skipped since. */
    val total: Int = 0,
    val done: Int = 0,
    val form: StepForm = StepForm(),
    /** A rate the core refused; nothing was saved. */
    val rateNotSaved: Boolean = false,
) {
    /** The source to enter now, or null when the sitting is over. */
    val current: DueSource? get() = plan?.due?.firstOrNull { it.id !in skipped }

    /** Counting from 1, for "2 of 5". */
    val position: Int get() = done + 1
}

/** The update's plan for [date], from the core's update queue. */
fun updatePlanOf(data: Dataset, date: LocalDate, today: LocalDate): UpdatePlan? {
    val queue = (data.updateQueue(date, today) as? UpdateQueueResult.Success)?.queue ?: return null
    return UpdatePlan(
        date = date,
        due = queue.sources.map { source ->
            val latest = source.snapshots.latest()!!
            DueSource(
                id = source.id,
                name = source.name,
                category = data.category(source.categoryId)?.name.orEmpty(),
                liability = data.category(source.categoryId)?.kind == CategoryKind.LIABILITY,
                lastValue = latest.value.value,
                lastDate = latest.date,
            )
        },
        leftOut = queue.leftOut,
        rates = queue.currencies.map { RateToConfirm(it.currency, it.lastRate?.value, it.lastRate?.date, it.ageInDays) },
    )
}

/**
 * Runs a periodic update. The core's update queue decides which sources are
 * due: those with no value on or after the date. Each entry is saved as it is
 * made, so the queue shrinks as the user goes, and stopping part-way loses
 * nothing.
 */
class UpdateViewModel(private val core: Core, private val locale: () -> Locale = { Locale.getDefault() }) : ViewModel() {
    private val mutableUi = MutableStateFlow(UpdateUi())
    val ui: StateFlow<UpdateUi> = mutableUi.asStateFlow()
    private val chosenDate = MutableStateFlow<LocalDate?>(null)

    init {
        viewModelScope.launch {
            combine(core.state, core.dataset, chosenDate) { state, data, date -> Triple(state == SessionState.Unlocked && data != null, data, date) }
                .collect { (open, _, date) ->
                    // While locked the plan is kept as it was; it is worked out afresh on unlocking.
                    if (open) {
                        val plan = plan(date)
                        mutableUi.update { it.copy(plan = plan) }
                    }
                }
        }
    }

    private suspend fun plan(date: LocalDate?): UpdatePlan? = try {
        core.run { read { data, today -> updatePlanOf(data, date ?: today, today) } }
    } catch (e: IllegalStateException) {
        // Locked in the meantime.
        null
    }

    /** Starts preparing an update on S-13, unless one is already under way. */
    fun prepare() {
        if (mutableUi.value.started) return
        mutableUi.update { it.copy(skipped = emptySet(), done = 0, total = 0, form = StepForm()) }
    }

    fun date(date: LocalDate?) {
        if (mutableUi.value.started) return
        chosenDate.value = date
        mutableUi.update { it.copy(date = date) }
    }

    /** Records a fresh [rate] for [currency] on the update's date. The plan then shows it as new. */
    fun recordRate(currency: Currency, rate: Rate) {
        val plan = mutableUi.value.plan ?: return
        viewModelScope.launch {
            val result = core.run { record { data, today -> data.recordRate(currency, plan.date, rate, today) } }
            mutableUi.update { it.copy(rateNotSaved = result is RecordingResult.Failure) }
        }
    }

    /** Begins S-14 with the sources due now. */
    fun start() {
        val plan = mutableUi.value.plan ?: return
        // The date is fixed for the sitting, even if it runs past midnight.
        chosenDate.value = plan.date
        mutableUi.update { it.copy(started = true, skipped = emptySet(), total = plan.due.size, done = 0, form = StepForm()) }
    }

    fun value(text: String) = edit { it.copy(value = text) }

    fun added(text: String) = edit { it.copy(added = text) }

    fun note(text: String) = edit { it.copy(note = text) }

    /** Moves on without recording anything for the current source. */
    fun skip() {
        val ui = mutableUi.value
        val current = ui.current ?: return
        if (ui.form.saving) return
        mutableUi.value = ui.copy(skipped = ui.skipped + current.id, done = ui.done + 1, form = StepForm())
    }

    /** Saves the current source's value on the update's date and moves on. */
    fun save() {
        val ui = mutableUi.value
        val current = ui.current ?: return
        val plan = ui.plan ?: return
        val form = ui.form
        if (form.saving) return
        val currency = current.lastValue.currency
        val value = when (val parsed = parseAmount(form.value, currency, locale())) {
            is MoneyParseResult.Success -> parsed.money
            is MoneyParseResult.Failure -> return problem(parsed.problem.asStepProblem(ValueEntryProblem.NO_VALUE))
        }
        val added = if (form.added.isBlank()) {
            Money(0, currency)
        } else {
            when (val parsed = parseAmount(form.added, currency, locale())) {
                is MoneyParseResult.Success -> parsed.money
                is MoneyParseResult.Failure -> return problem(parsed.problem.asStepProblem(ValueEntryProblem.NOT_AN_AMOUNT))
            }
        }
        mutableUi.value = ui.copy(form = form.copy(saving = true, problem = null))
        viewModelScope.launch {
            val result = core.run {
                record { data, today -> data.addSnapshot(current.id, plan.date, value, added, today, note = form.note) }
            }
            when (result) {
                // The saved source now has a value on the date, so the queue moves past it.
                is RecordingResult.Success -> mutableUi.update { it.copy(done = it.done + 1, form = StepForm()) }
                is RecordingResult.Failure -> mutableUi.update {
                    it.copy(form = it.form.copy(saving = false, problem = result.error.asStepProblem()))
                }
            }
        }
    }

    /** Whether something has been typed for the current source and not yet saved, so leaving asks first. */
    val hasInput: Boolean
        get() = mutableUi.value.let { ui -> ui.started && listOf(ui.form.value, ui.form.added, ui.form.note).any { it.isNotBlank() } }

    /**
     * Leaves the walk for S-13, keeping the date. What was entered is saved;
     * starting again with the same date continues with the sources not yet done.
     */
    fun stop() {
        mutableUi.update { it.copy(started = false, skipped = emptySet(), total = 0, done = 0, form = StepForm()) }
    }

    /** Ends the sitting after the last source, ready for the next update. */
    fun finish() {
        chosenDate.value = null
        mutableUi.update { UpdateUi(plan = it.plan) }
    }

    private fun problem(problem: ValueEntryProblem) {
        mutableUi.update { it.copy(form = it.form.copy(problem = problem)) }
    }

    private fun edit(change: (StepForm) -> StepForm) {
        mutableUi.update { if (it.form.saving) it else it.copy(form = change(it.form).copy(problem = null)) }
    }
}

private fun AmountProblem.asStepProblem(whenEmpty: ValueEntryProblem): ValueEntryProblem = when (this) {
    AmountProblem.EMPTY -> whenEmpty
    AmountProblem.NOT_A_NUMBER -> ValueEntryProblem.NOT_AN_AMOUNT
    AmountProblem.TOO_MANY_DECIMAL_PLACES -> ValueEntryProblem.TOO_MANY_DECIMAL_PLACES
    AmountProblem.OUT_OF_RANGE -> ValueEntryProblem.AMOUNT_TOO_LARGE
}

private fun RecordingError.asStepProblem(): ValueEntryProblem = when (this) {
    is RecordingError.DateInFuture -> ValueEntryProblem.DATE_IN_FUTURE
    is RecordingError.BeforeLatestSnapshot -> ValueEntryProblem.BEFORE_LATEST
    else -> ValueEntryProblem.NOT_SAVED
}
