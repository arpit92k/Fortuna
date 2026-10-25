package io.github.arpit92k.fortuna.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.money.AmountProblem
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.MoneyParseResult
import io.github.arpit92k.fortuna.core.recording.RecordingError
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.ui.parseAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.util.Locale

/** What the user has typed on S-11 so far. Kept across a lock, so nothing typed is lost. */
data class AddSourceForm(
    val name: String = "",
    val categoryId: CategoryId? = null,
    val amount: String = "",
    /** Null means today. */
    val date: LocalDate? = null,
    val note: String = "",
    val problem: AddSourceProblem? = null,
    val saving: Boolean = false,
)

/** Why a source could not be added, as the form shows it. */
enum class AddSourceProblem {
    NO_NAME,
    NAME_IN_USE,
    NO_CATEGORY,
    NO_AMOUNT,
    NOT_AN_AMOUNT,
    TOO_MANY_DECIMAL_PLACES,
    AMOUNT_TOO_LARGE,
    DATE_IN_FUTURE,

    /** Anything the form does not expect; the core refused the change and nothing was saved. */
    NOT_SAVED,
}

/**
 * S-11 Add source, for now in the base currency only; other currencies come
 * with step 19. The core's recording rules decide what is allowed; the form
 * only collects and reports.
 */
class AddSourceViewModel(private val core: Core, private val locale: () -> Locale = { Locale.getDefault() }) : ViewModel() {
    private val mutableForm = MutableStateFlow(AddSourceForm())
    val form: StateFlow<AddSourceForm> = mutableForm.asStateFlow()

    fun name(text: String) = edit { it.copy(name = text) }

    fun category(id: CategoryId) = edit { it.copy(categoryId = id) }

    fun amount(text: String) = edit { it.copy(amount = text) }

    fun date(date: LocalDate?) = edit { it.copy(date = date) }

    fun note(text: String) = edit { it.copy(note = text) }

    /** Clears the form, when the user leaves it without saving. */
    fun discard() {
        mutableForm.value = AddSourceForm()
    }

    /** Saves the source. On success the form is cleared and [onSaved] runs. */
    fun save(baseCurrency: Currency, onSaved: () -> Unit) {
        val form = mutableForm.value
        if (form.saving) return
        val problem = when {
            form.name.isBlank() -> AddSourceProblem.NO_NAME
            form.categoryId == null -> AddSourceProblem.NO_CATEGORY
            else -> null
        }
        if (problem != null) {
            mutableForm.value = form.copy(problem = problem)
            return
        }
        val money = when (val parsed = parseAmount(form.amount, baseCurrency, locale())) {
            is MoneyParseResult.Success -> parsed.money
            is MoneyParseResult.Failure -> {
                mutableForm.value = form.copy(problem = parsed.problem.asFormProblem())
                return
            }
        }
        mutableForm.value = form.copy(saving = true, problem = null)
        viewModelScope.launch {
            val result = core.run {
                record { data, today ->
                    data.addSource(form.name, form.categoryId!!, form.date ?: today, money, today, note = form.note)
                }
            }
            when (result) {
                is RecordingResult.Success -> {
                    mutableForm.value = AddSourceForm()
                    onSaved()
                }
                is RecordingResult.Failure -> mutableForm.update { it.copy(saving = false, problem = result.error.asFormProblem()) }
            }
        }
    }

    private fun edit(change: (AddSourceForm) -> AddSourceForm) {
        mutableForm.update { if (it.saving) it else change(it).copy(problem = null) }
    }
}

private fun AmountProblem.asFormProblem(): AddSourceProblem = when (this) {
    AmountProblem.EMPTY -> AddSourceProblem.NO_AMOUNT
    AmountProblem.NOT_A_NUMBER -> AddSourceProblem.NOT_AN_AMOUNT
    AmountProblem.TOO_MANY_DECIMAL_PLACES -> AddSourceProblem.TOO_MANY_DECIMAL_PLACES
    AmountProblem.OUT_OF_RANGE -> AddSourceProblem.AMOUNT_TOO_LARGE
}

private fun RecordingError.asFormProblem(): AddSourceProblem = when (this) {
    RecordingError.EmptyName -> AddSourceProblem.NO_NAME
    is RecordingError.NameInUse -> AddSourceProblem.NAME_IN_USE
    is RecordingError.DateInFuture -> AddSourceProblem.DATE_IN_FUTURE
    is RecordingError.NoSuchCategory, is RecordingError.CategoryArchived -> AddSourceProblem.NO_CATEGORY
    else -> AddSourceProblem.NOT_SAVED
}
