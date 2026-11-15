package io.github.arpit92k.fortuna.source

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
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.editSnapshot
import io.github.arpit92k.fortuna.core.recording.NextAdjustment
import io.github.arpit92k.fortuna.core.recording.insertAdjustment
import io.github.arpit92k.fortuna.core.recording.insertSnapshot
import io.github.arpit92k.fortuna.ui.parseAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.text.DecimalFormatSymbols
import java.util.Locale

/** What the user has typed on S-12 so far. Kept across a lock, so nothing typed is lost. */
data class ValueEntryForm(
    val value: String = "",
    /** Decided: optional; blank counts as nothing added. */
    val added: String = "",
    /** For a new value, null means today. For a correction, the date being corrected, which cannot change. */
    val date: LocalDate? = null,
    val note: String = "",
    val problem: ValueEntryProblem? = null,
    val saving: Boolean = false,
    /** The date already has a value; the screen asks before replacing it. */
    val confirmReplace: Boolean = false,
    /**
     * A value dated before a later one (UF-06): what the later one's amount
     * added becomes, for the user to accept or change before saving again.
     */
    val adjustment: NextAdjustment? = null,
    /** The later value's amount added, as typed, once [adjustment] is shown. */
    val nextAdded: String = "",
)

/** What the form needs to know about the source: its currency and dates. */
data class ValueEntrySource(
    val id: SourceId,
    val currency: Currency,
    val opening: LocalDate,
    val latest: LocalDate,
    val liability: Boolean,
    /** Today, for a new value with no date chosen. */
    val today: LocalDate,
    /** Every date with a value, so an earlier one is told apart from a new one in the middle. */
    val dates: Set<LocalDate> = setOf(opening, latest),
)

/** What S-12 needs to know about a source, or null if it is gone. */
fun valueEntrySourceOf(data: Dataset, id: SourceId, today: LocalDate): ValueEntrySource? {
    val source = data.source(id) ?: return null
    return ValueEntrySource(
        id = id,
        currency = source.currency,
        opening = source.snapshots.earliest()!!.date,
        latest = source.snapshots.latest()!!.date,
        liability = data.category(source.categoryId)?.kind == CategoryKind.LIABILITY,
        today = today,
        dates = source.snapshots.map { it.date }.toSet(),
    )
}

enum class ValueEntryProblem {
    NO_VALUE,
    NOT_AN_AMOUNT,
    TOO_MANY_DECIMAL_PLACES,
    AMOUNT_TOO_LARGE,
    DATE_IN_FUTURE,
    BEFORE_LATEST,

    /** A foreign source's new opening value has no rate on or before its date. */
    RATE_NEEDED,

    /** Anything the form does not expect; the core refused the change and nothing was saved. */
    NOT_SAVED,
}

/**
 * S-12 Value entry, for a new value or, with an editing date, a correction.
 * The core's recording rules decide what is allowed; the form collects and
 * reports, and asks before a new value replaces one already recorded.
 */
class ValueEntryViewModel(private val core: Core, private val locale: () -> Locale = { Locale.getDefault() }) : ViewModel() {
    private val mutableForm = MutableStateFlow(ValueEntryForm())
    val form: StateFlow<ValueEntryForm> = mutableForm.asStateFlow()

    /** Which entry the form holds, so returning to it after a lock keeps what was typed. */
    private var openFor: Pair<SourceId, LocalDate?>? = null

    /**
     * Prepares the form for a new value for [source], or a correction of its
     * value on [editing]. Does nothing if the form is already for that entry.
     */
    /** The form as it opened, to tell whether anything has changed since. */
    private var opened = ValueEntryForm()

    /** Whether anything has been entered or changed, so leaving asks first. */
    val hasChanges: Boolean
        get() = mutableForm.value.copy(problem = null, saving = false, confirmReplace = false, adjustment = null, nextAdded = "") != opened

    fun open(source: ValueEntrySource, editing: LocalDate?, existing: Pair<Money, Money>?, note: String?) {
        if (openFor == source.id to editing) return
        openFor = source.id to editing
        mutableForm.value = if (editing != null && existing != null) {
            val (value, added) = existing
            ValueEntryForm(
                value = typedText(value),
                added = if (added.minorUnits == 0L) "" else typedText(added),
                date = editing,
                note = note.orEmpty(),
            )
        } else {
            ValueEntryForm()
        }
        opened = mutableForm.value
    }

    /** Clears the form, when the user leaves it without saving. */
    fun discard() {
        openFor = null
        mutableForm.value = ValueEntryForm()
        opened = ValueEntryForm()
    }

    fun value(text: String) = edit { it.copy(value = text) }

    fun added(text: String) = edit { it.copy(added = text) }

    fun date(date: LocalDate?) = edit { it.copy(date = date) }

    fun note(text: String) = edit { it.copy(note = text) }

    /** The later value's amount added, changed from what the app proposed. */
    fun nextAdded(text: String) = mutableForm.update { if (it.saving) it else it.copy(nextAdded = text, problem = null) }

    /** The user decided not to replace the value already recorded for that date. */
    fun keepExisting() = mutableForm.update { it.copy(confirmReplace = false) }

    /**
     * Saves the value. For a new value on a date that already has one, the form
     * first asks; [replace] is the user's yes. On success the form is cleared
     * and [onSaved] runs.
     */
    fun save(source: ValueEntrySource, editing: LocalDate?, replace: Boolean = false, onSaved: () -> Unit) {
        val form = mutableForm.value
        if (form.saving) return
        val value = when (val parsed = parseAmount(form.value, source.currency, locale())) {
            is MoneyParseResult.Success -> parsed.money
            is MoneyParseResult.Failure -> return fail(parsed.problem.asFormProblem(ValueEntryProblem.NO_VALUE))
        }
        val date = editing ?: form.date ?: source.today
        // On or before the opening value's date, it is the opening value.
        val opening = date <= source.opening
        val added = if (opening || form.added.isBlank()) {
            // The opening balance counts as neither added nor growth.
            Money(0, source.currency)
        } else {
            when (val parsed = parseAmount(form.added, source.currency, locale())) {
                is MoneyParseResult.Success -> parsed.money
                is MoneyParseResult.Failure -> return fail(parsed.problem.asFormProblem(ValueEntryProblem.NOT_AN_AMOUNT))
            }
        }
        // A date that already has a value: the new figures replace it, after asking.
        val existing = editing == null && date in source.dates
        if (existing && !replace) {
            mutableForm.value = form.copy(confirmReplace = true, problem = null)
            return
        }
        // Before the latest value and on a new date, it goes in the middle (UF-06): the
        // later value's amount added changes, so it is shown before anything is saved.
        val inserting = editing == null && !existing && date < source.latest
        val nextAdded = if (inserting && form.adjustment != null && form.adjustment.date > date) {
            when (val parsed = parseAmount(form.nextAdded.ifBlank { "0" }, source.currency, locale())) {
                is MoneyParseResult.Success -> parsed.money
                is MoneyParseResult.Failure -> return fail(parsed.problem.asFormProblem(ValueEntryProblem.NOT_AN_AMOUNT))
            }
        } else {
            null
        }
        if (inserting && nextAdded == null) {
            viewModelScope.launch {
                val adjustment = core.run { read { data, _ -> data.insertAdjustment(source.id, date, value, added) } }
                mutableForm.update { it.copy(adjustment = adjustment, nextAdded = adjustment?.let { a -> typedText(a.proposed) }.orEmpty(), problem = null) }
            }
            return
        }
        mutableForm.value = form.copy(saving = true, problem = null, confirmReplace = false)
        viewModelScope.launch {
            val result = core.run {
                record { data, today ->
                    when {
                        editing != null -> data.editSnapshot(source.id, editing, value, added, note = form.note)
                        // Replacing a value before the latest is a correction of it.
                        existing && date < source.latest -> data.editSnapshot(source.id, date, value, added, note = form.note)
                        inserting -> data.insertSnapshot(source.id, date, value, added, today, note = form.note, nextAmountAdded = nextAdded)
                        else -> data.addSnapshot(source.id, date, value, added, today, note = form.note)
                    }
                }
            }
            when (result) {
                is RecordingResult.Success -> {
                    discard()
                    onSaved()
                }
                is RecordingResult.Failure -> mutableForm.update { it.copy(saving = false, problem = result.error.asFormProblem()) }
            }
        }
    }

    private fun fail(problem: ValueEntryProblem) {
        mutableForm.update { it.copy(problem = problem) }
    }

    private fun edit(change: (ValueEntryForm) -> ValueEntryForm) {
        // The proposal for a later value depends on what is typed, so it is worked out again on saving.
        mutableForm.update { if (it.saving) it else change(it).copy(problem = null, confirmReplace = false, adjustment = null, nextAdded = "") }
    }

    /** An amount as the user would type it here: the region's decimal separator, no grouping. */
    private fun typedText(money: Money): String {
        val separator = DecimalFormatSymbols.getInstance(locale()).decimalSeparator
        return money.toDecimalText().replace('.', separator)
    }
}

private fun AmountProblem.asFormProblem(whenEmpty: ValueEntryProblem): ValueEntryProblem = when (this) {
    AmountProblem.EMPTY -> whenEmpty
    AmountProblem.NOT_A_NUMBER -> ValueEntryProblem.NOT_AN_AMOUNT
    AmountProblem.TOO_MANY_DECIMAL_PLACES -> ValueEntryProblem.TOO_MANY_DECIMAL_PLACES
    AmountProblem.OUT_OF_RANGE -> ValueEntryProblem.AMOUNT_TOO_LARGE
}

private fun RecordingError.asFormProblem(): ValueEntryProblem = when (this) {
    is RecordingError.DateInFuture -> ValueEntryProblem.DATE_IN_FUTURE
    is RecordingError.BeforeLatestSnapshot -> ValueEntryProblem.BEFORE_LATEST
    is RecordingError.RateNeeded -> ValueEntryProblem.RATE_NEEDED
    else -> ValueEntryProblem.NOT_SAVED
}
