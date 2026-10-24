package io.github.arpit92k.fortuna.core.recording

import io.github.arpit92k.fortuna.core.dated.Dated
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.Source
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * What a periodic update dated [date] walks through.
 *
 * [sources] are the active sources whose latest snapshot is before [date],
 * the one updated longest ago first. [leftOut] counts the active sources that
 * already have a value on or after [date], which the update screen reports.
 * Starting again with the same date continues with the sources not yet done.
 */
data class UpdateQueue(
    val date: LocalDate,
    val sources: List<Source>,
    val leftOut: Int,
    val currencies: List<CurrencyToConfirm>,
)

/**
 * A foreign currency of a source in the update, with the rate in force on
 * the update's date and how many days old it is then. The user keeps it or
 * enters a fresh one.
 */
data class CurrencyToConfirm(
    val currency: Currency,
    val lastRate: Dated<Rate>?,
    val ageInDays: Int?,
)

/** The outcome of [updateQueue]. */
sealed interface UpdateQueueResult {
    data class Success(val queue: UpdateQueue) : UpdateQueueResult

    data class Failure(val error: RecordingError) : UpdateQueueResult
}

/** The queue for a periodic update dated [date], which may be any day up to [today]. */
fun Dataset.updateQueue(date: LocalDate, today: LocalDate): UpdateQueueResult {
    if (date > today) return UpdateQueueResult.Failure(RecordingError.DateInFuture(date, today))

    val (due, leftOut) = sources
        .filter { it.status == Status.ACTIVE }
        .partition { it.snapshots.latest()!!.date < date }
    val ordered = due.sortedBy { it.snapshots.latest()!!.date }

    val currencies = ordered.map { it.currency }
        .filter { it != baseCurrency }
        .distinct()
        .sortedBy { it.code }
        .map { currency ->
            val last = rates[currency]?.latestOnOrBefore(date)
            CurrencyToConfirm(currency, last, last?.date?.daysUntil(date))
        }
    return UpdateQueueResult.Success(UpdateQueue(date, ordered, leftOut.size, currencies))
}
