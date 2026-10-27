package io.github.arpit92k.fortuna.core.recording

import io.github.arpit92k.fortuna.core.dated.Dated
import io.github.arpit92k.fortuna.core.dated.DatedValues
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.Snapshot
import io.github.arpit92k.fortuna.core.domain.Source
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.domain.UpdateFrequency
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.recording.RecordingError.BaseCurrencyAlreadyChosen
import io.github.arpit92k.fortuna.core.recording.RecordingError.BaseCurrencyHasNoRate
import io.github.arpit92k.fortuna.core.recording.RecordingError.BaseCurrencyNotChosen
import io.github.arpit92k.fortuna.core.recording.RecordingError.BeforeLatestSnapshot
import io.github.arpit92k.fortuna.core.recording.RecordingError.CategoryArchived
import io.github.arpit92k.fortuna.core.recording.RecordingError.DateInFuture
import io.github.arpit92k.fortuna.core.recording.RecordingError.EmptyName
import io.github.arpit92k.fortuna.core.recording.RecordingError.NameInUse
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoRateOnDate
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoSnapshotOnDate
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoSuchCategory
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoSuchSource
import io.github.arpit92k.fortuna.core.recording.RecordingError.OnlySnapshot
import io.github.arpit92k.fortuna.core.recording.RecordingError.OpeningBalanceHasNoAmountAdded
import io.github.arpit92k.fortuna.core.recording.RecordingError.RateNeeded
import io.github.arpit92k.fortuna.core.recording.RecordingError.RateNeededBySnapshot
import io.github.arpit92k.fortuna.core.recording.RecordingError.SourceArchived
import io.github.arpit92k.fortuna.core.recording.RecordingError.SourceHasHistory
import io.github.arpit92k.fortuna.core.recording.RecordingError.WrongCurrency
import io.github.arpit92k.fortuna.core.recording.RecordingError.WrongRateCurrencies
import kotlinx.datetime.LocalDate

// Every change the first version allows. Each takes a dataset and returns
// either a new dataset or the rule the change would break; nothing is ever
// changed in place, so a refused change leaves no trace. Functions that take
// a date are also given today's date, which keeps the rules free of the clock.
//
// Raising the revision counter is not done here: the store does it once for
// every change it saves (step 10).

// ---- Base currency ----

/** Records the base currency chosen at setup. It can be chosen only once. */
fun Dataset.chooseBaseCurrency(currency: Currency): RecordingResult {
    if (baseCurrency != null) return fail(BaseCurrencyAlreadyChosen)
    return ok(copy(baseCurrency = currency))
}

// ---- Sources ----

/**
 * Adds a source with its opening snapshot: [openingValue] on [openingDate],
 * with nothing added. The source's currency is the opening value's currency.
 *
 * An account that already existed may have gained or lost before it was
 * added: [openingPrincipal] is what had been put in (for a debt, borrowed),
 * and the rest of the opening value counts as gained already. Without it the
 * whole opening value was put in.
 *
 * A source in a foreign currency needs a rate on or before [openingDate]. If
 * there is none yet, pass one as [rate]; it is recorded on [openingDate] in
 * the same change, so the source and its rate are added together or not at all.
 *
 * The new source is the last in [Dataset.sources].
 */
fun Dataset.addSource(
    name: String,
    categoryId: CategoryId,
    openingDate: LocalDate,
    openingValue: Money,
    today: LocalDate,
    note: String? = null,
    updateFrequency: UpdateFrequency? = null,
    rate: Rate? = null,
    openingPrincipal: Money? = null,
): RecordingResult {
    val base = baseCurrency ?: return fail(BaseCurrencyNotChosen)
    if (openingPrincipal != null && openingPrincipal.currency != openingValue.currency) {
        return fail(WrongCurrency(openingValue.currency, openingPrincipal.currency))
    }
    if (openingDate > today) return fail(DateInFuture(openingDate, today))

    val cleanName = name.trim()
    if (cleanName.isEmpty()) return fail(EmptyName)
    if (isNameInUse(cleanName)) return fail(NameInUse(cleanName))

    val category = category(categoryId) ?: return fail(NoSuchCategory(categoryId))
    if (category.status != Status.ACTIVE) return fail(CategoryArchived(categoryId))

    val currency = openingValue.currency
    var newRates = rates
    if (currency == base) {
        if (rate != null) return fail(BaseCurrencyHasNoRate)
    } else {
        if (rate != null) {
            if (!rate.relates(currency, base)) return fail(WrongRateCurrencies(currency, rate))
            newRates = newRates + (currency to (newRates[currency] ?: DatedValues.empty()).with(openingDate, rate))
        }
        if (newRates[currency]?.latestOnOrBefore(openingDate) == null) return fail(RateNeeded(currency, openingDate))
    }

    val source = Source(
        id = SourceId(nextId),
        name = cleanName,
        categoryId = categoryId,
        currency = currency,
        updateFrequency = updateFrequency ?: settings.defaultUpdateFrequency,
        snapshots = DatedValues.of(Dated(openingDate, Snapshot(openingValue, Money(0, currency)))),
        note = clean(note),
        // The same as the value says nothing more, so it is not kept.
        openingPrincipal = openingPrincipal?.takeIf { it != openingValue },
    )
    return ok(copy(sources = sources + source, rates = newRates, nextId = nextId + 1))
}

/** Deletes a source created by mistake. Only a source with just its opening snapshot can be deleted. */
fun Dataset.deleteSource(id: SourceId): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.snapshots.size > 1) return fail(SourceHasHistory(id))
    return ok(copy(sources = sources.filter { it.id != id }))
}

// ---- Snapshots ----

/**
 * Records [value] for a source on [date], with [amountAdded] since the
 * previous snapshot. The date must be on or after the source's latest
 * snapshot. On the same date as the latest, the new values replace it; the
 * screen asks the user first.
 */
fun Dataset.addSnapshot(
    id: SourceId,
    date: LocalDate,
    value: Money,
    amountAdded: Money,
    today: LocalDate,
    note: String? = null,
): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.status != Status.ACTIVE) return fail(SourceArchived(id))
    checkCurrency(source, value, amountAdded)?.let { return fail(it) }
    if (date > today) return fail(DateInFuture(date, today))

    val latest = source.snapshots.latest()!!
    if (date < latest.date) return fail(BeforeLatestSnapshot(latest.date))
    if (date == source.snapshots.earliest()!!.date && amountAdded.minorUnits != 0L) {
        return fail(OpeningBalanceHasNoAmountAdded)
    }
    return ok(replaceSource(source.copy(snapshots = source.snapshots.with(date, Snapshot(value, amountAdded, clean(note))))))
}

/**
 * Changes the figures of the snapshot on [date]. Its date cannot change:
 * moving a snapshot in time is backfilling, which comes later.
 */
fun Dataset.editSnapshot(
    id: SourceId,
    date: LocalDate,
    value: Money,
    amountAdded: Money,
    note: String? = null,
): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.status != Status.ACTIVE) return fail(SourceArchived(id))
    checkCurrency(source, value, amountAdded)?.let { return fail(it) }
    if (source.snapshots.on(date) == null) return fail(NoSnapshotOnDate(date))
    if (date == source.snapshots.earliest()!!.date && amountAdded.minorUnits != 0L) {
        return fail(OpeningBalanceHasNoAmountAdded)
    }
    return ok(replaceSource(source.copy(snapshots = source.snapshots.with(date, Snapshot(value, amountAdded, clean(note))))))
}

/** Deletes a source's latest snapshot. Its only snapshot cannot be deleted; delete the source instead. */
fun Dataset.deleteLatestSnapshot(id: SourceId): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.status != Status.ACTIVE) return fail(SourceArchived(id))
    if (source.snapshots.size == 1) return fail(OnlySnapshot(id))
    return ok(replaceSource(source.copy(snapshots = source.snapshots.without(source.snapshots.latest()!!.date))))
}

// ---- Exchange rates ----

/**
 * Records [rate] for a foreign [currency] on [date], typed either way round
 * against the base currency. A rate already on that date is replaced, which
 * is also how a rate is corrected.
 */
fun Dataset.recordRate(currency: Currency, date: LocalDate, rate: Rate, today: LocalDate): RecordingResult {
    val base = baseCurrency ?: return fail(BaseCurrencyNotChosen)
    if (currency == base) return fail(BaseCurrencyHasNoRate)
    if (!rate.relates(currency, base)) return fail(WrongRateCurrencies(currency, rate))
    if (date > today) return fail(DateInFuture(date, today))
    val history = (rates[currency] ?: DatedValues.empty()).with(date, rate)
    return ok(copy(rates = rates + (currency to history)))
}

/**
 * Deletes the rate of [currency] on [date], unless a source's earliest
 * snapshot would then have no rate on or before its date. That is the only
 * snapshot that can lose its rate, since later snapshots have at least the same.
 */
fun Dataset.deleteRate(currency: Currency, date: LocalDate): RecordingResult {
    val history = rates[currency]
    if (history?.on(date) == null) return fail(NoRateOnDate(currency, date))
    val remaining = history.without(date)
    for (source in sources.filter { it.currency == currency }) {
        val opening = source.snapshots.earliest()!!.date
        if (remaining.latestOnOrBefore(opening) == null) return fail(RateNeededBySnapshot(currency, source.id, opening))
    }
    return ok(copy(rates = if (remaining.isEmpty()) rates - currency else rates + (currency to remaining)))
}

// ---- Helpers ----

private fun ok(dataset: Dataset) = RecordingResult.Success(dataset)

private fun fail(error: RecordingError) = RecordingResult.Failure(error)

private fun Dataset.isNameInUse(name: String): Boolean =
    sources.any { it.status == Status.ACTIVE && it.name.trim().equals(name, ignoreCase = true) }

private fun checkCurrency(source: Source, vararg amounts: Money): RecordingError? =
    amounts.firstOrNull { it.currency != source.currency }?.let { WrongCurrency(source.currency, it.currency) }

private fun Dataset.replaceSource(changed: Source): Dataset =
    copy(sources = sources.map { if (it.id == changed.id) changed else it })

private fun Rate.relates(currency: Currency, base: Currency): Boolean =
    setOf(unit, quote) == setOf(currency, base)

private fun clean(note: String?): String? = note?.trim()?.takeIf { it.isNotEmpty() }
