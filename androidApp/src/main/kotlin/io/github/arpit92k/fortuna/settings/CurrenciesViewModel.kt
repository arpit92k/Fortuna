package io.github.arpit92k.fortuna.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.dated.Dated
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.recording.RecordingError
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.deleteRate
import io.github.arpit92k.fortuna.core.recording.recordRate
import io.github.arpit92k.fortuna.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** A foreign currency in use, its rates newest first, and the sources in it. */
data class CurrencyRates(
    val currency: Currency,
    val rates: List<Dated<Rate>>,
    val usedBy: List<String>,
)

/** Why a rate could not be saved or deleted. */
sealed interface RateActionProblem {
    /** The rate is the only one on or before the opening value of [source], dated [date]. */
    data class NeededBy(val source: String, val date: LocalDate) : RateActionProblem

    data object InFuture : RateActionProblem

    data object NotSaved : RateActionProblem
}

/** S-16. */
data class CurrenciesUi(
    val base: Currency,
    val currencies: List<CurrencyRates>,
    val problem: RateActionProblem? = null,
)

/** The foreign currencies in use, by code: those of sources and those with rates. */
fun currenciesOf(data: Dataset): List<CurrencyRates> {
    val base = data.baseCurrency ?: return emptyList()
    val inUse = (data.sources.map { it.currency } + data.rates.keys).filter { it != base }.distinct().sortedBy { it.code }
    return inUse.map { currency ->
        CurrencyRates(
            currency = currency,
            rates = data.rates[currency]?.toList().orEmpty().reversed(),
            usedBy = data.sources.filter { it.currency == currency && it.status == Status.ACTIVE }.map { it.name },
        )
    }
}

/**
 * S-16 Currencies and rates (UF-09). The core decides what is allowed: a rate
 * cannot be dated in the future, and the rate a source's opening value
 * depends on cannot be deleted.
 */
class CurrenciesViewModel(private val core: Core) : ViewModel() {
    private val mutableUi = MutableStateFlow<CurrenciesUi?>(null)
    val ui: StateFlow<CurrenciesUi?> = mutableUi.asStateFlow()

    init {
        viewModelScope.launch {
            combine(core.state, core.dataset) { state, data -> data.takeIf { state == SessionState.Unlocked } }
                .collect { data ->
                    val base = data?.baseCurrency
                    mutableUi.update { old ->
                        if (data == null || base == null) null else CurrenciesUi(base, currenciesOf(data), old?.problem)
                    }
                }
        }
    }

    /** Adds a rate for [currency] on [date], today if null, or corrects the rate already on that date. */
    fun saveRate(currency: Currency, date: LocalDate?, rate: Rate) {
        viewModelScope.launch {
            val result = core.run { record { data, today -> data.recordRate(currency, date ?: today, rate, today) } }
            report(result)
        }
    }

    /** Deletes the rate for [currency] on [date], after the screen has asked. */
    fun deleteRate(currency: Currency, date: LocalDate) {
        viewModelScope.launch {
            val result = core.run { record { data, _ -> data.deleteRate(currency, date) } }
            val problem = (result as? RecordingResult.Failure)?.error
            if (problem is RecordingError.RateNeededBySnapshot) {
                val name = core.run { read { data, _ -> data.source(problem.source)?.name.orEmpty() } }
                mutableUi.update { it?.copy(problem = RateActionProblem.NeededBy(name, problem.date)) }
            } else {
                report(result)
            }
        }
    }

    fun clearProblem() = mutableUi.update { it?.copy(problem = null) }

    private fun report(result: RecordingResult) {
        val problem = when (val error = (result as? RecordingResult.Failure)?.error) {
            null -> null
            is RecordingError.DateInFuture -> RateActionProblem.InFuture
            else -> RateActionProblem.NotSaved
        }
        mutableUi.update { it?.copy(problem = problem) }
    }
}
