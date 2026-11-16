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
import io.github.arpit92k.fortuna.core.domain.Category
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.recording.RecordingError.CategoryArchived
import io.github.arpit92k.fortuna.core.recording.RecordingError.CategoryHasSources
import io.github.arpit92k.fortuna.core.recording.RecordingError.CategoryOfOtherKind
import io.github.arpit92k.fortuna.core.recording.RecordingError.CloseNotAfterLatest
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
import io.github.arpit92k.fortuna.core.recording.RecordingError.SnapshotOnDate
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

/**
 * Closes a source when the account is closed, the asset sold or the debt
 * repaid (UF-07): a final snapshot of zero on [date], then the source is
 * archived. It leaves the list of active sources and the periodic update,
 * and keeps its history, which still counts in net worth up to [date].
 *
 * [amountAdded] defaults to minus the last value: the money was moved out,
 * or for a debt, repaid. A different amount, such as nothing for an asset
 * written off, counts the rest as a loss.
 *
 * The date must be after the latest snapshot, so closing never replaces a
 * value already recorded.
 */
fun Dataset.closeSource(
    id: SourceId,
    date: LocalDate,
    today: LocalDate,
    amountAdded: Money? = null,
    note: String? = null,
): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.status != Status.ACTIVE) return fail(SourceArchived(id))
    if (amountAdded != null) checkCurrency(source, amountAdded)?.let { return fail(it) }
    if (date > today) return fail(DateInFuture(date, today))
    val latest = source.snapshots.latest()!!
    if (date <= latest.date) return fail(CloseNotAfterLatest(latest.date))
    val moved = amountAdded ?: -latest.value.value
    val closing = Snapshot(Money(0, source.currency), moved, clean(note))
    return ok(replaceSource(source.copy(snapshots = source.snapshots.with(date, closing), status = Status.ARCHIVED)))
}

/**
 * Reopens a closed source. Its history, closing value included, is kept: new
 * values are added after it, or the closing value is deleted as the latest.
 * Names are unique among active sources, so a source opened since under the
 * same name keeps it closed.
 */
fun Dataset.reopenSource(id: SourceId): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.status == Status.ACTIVE) return ok(this)
    if (isNameInUse(source.name)) return fail(NameInUse(source.name))
    return ok(replaceSource(source.copy(status = Status.ACTIVE)))
}

// ---- Categories (UF-08) ----

/**
 * Adds a category of [kind], last among the categories. Names are unique
 * among active categories, ignoring case and surrounding spaces.
 */
fun Dataset.addCategory(name: String, kind: CategoryKind): RecordingResult {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return fail(EmptyName)
    if (isCategoryNameInUse(cleanName, except = null)) return fail(NameInUse(cleanName))
    val category = Category(CategoryId(nextId), cleanName, kind)
    return ok(copy(categories = categories + category, nextId = nextId + 1))
}

/** Renames a category. Its sources, and their history, stay in it. */
fun Dataset.renameCategory(id: CategoryId, name: String): RecordingResult {
    val category = category(id) ?: return fail(NoSuchCategory(id))
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return fail(EmptyName)
    if (category.status == Status.ACTIVE && isCategoryNameInUse(cleanName, except = id)) return fail(NameInUse(cleanName))
    return ok(replaceCategory(category.copy(name = cleanName)))
}

/**
 * Changes whether a category's sources are assets or debts. Only while it
 * has no sources at all, closed ones included: changing it later would turn
 * history round.
 */
fun Dataset.setCategoryKind(id: CategoryId, kind: CategoryKind): RecordingResult {
    val category = category(id) ?: return fail(NoSuchCategory(id))
    if (category.kind == kind) return ok(this)
    if (sources.any { it.categoryId == id }) return fail(CategoryHasSources(id))
    return ok(replaceCategory(category.copy(kind = kind)))
}

/**
 * Archives a category, so new sources cannot go in it. Refused while an
 * open source is in it: move or close those first. Closed sources stay in it,
 * with their history.
 */
fun Dataset.archiveCategory(id: CategoryId): RecordingResult {
    val category = category(id) ?: return fail(NoSuchCategory(id))
    if (sources.any { it.categoryId == id && it.status == Status.ACTIVE }) return fail(CategoryHasSources(id))
    return ok(replaceCategory(category.copy(status = Status.ARCHIVED)))
}

/** Brings an archived category back. Refused while an active category has its name. */
fun Dataset.restoreCategory(id: CategoryId): RecordingResult {
    val category = category(id) ?: return fail(NoSuchCategory(id))
    if (category.status == Status.ACTIVE) return ok(this)
    if (isCategoryNameInUse(category.name, except = id)) return fail(NameInUse(category.name))
    return ok(replaceCategory(category.copy(status = Status.ACTIVE)))
}

/**
 * Moves a source into another active category of the same kind, so an
 * asset stays an asset and a debt a debt, and its history reads the same.
 */
fun Dataset.moveSource(id: SourceId, to: CategoryId): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    val target = category(to) ?: return fail(NoSuchCategory(to))
    if (target.status != Status.ACTIVE) return fail(CategoryArchived(to))
    val from = category(source.categoryId)
    if (from != null && from.kind != target.kind) return fail(CategoryOfOtherKind(to))
    return ok(replaceSource(source.copy(categoryId = to)))
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

// ---- Backfilling (UF-06) ----

/**
 * What a change in the middle of a source's history does to the snapshot
 * after it: the amount added on [date] changes from [current] to [proposed],
 * which the screen shows for the user to accept or change.
 */
data class NextAdjustment(val date: LocalDate, val current: Money, val proposed: Money)

/**
 * The adjustment inserting a snapshot of [value], with [amountAdded], on
 * [date] would make to the snapshot after it, or null if none comes after.
 *
 * Amount added covers the period since the previous snapshot, so the next
 * one now covers less: what the new one added comes off it. Inserted before
 * the opening value, the new snapshot becomes the opening one; the old
 * opening then gets what was put in between, so what was put in overall, and
 * so every gain, stays the same.
 */
fun Dataset.insertAdjustment(id: SourceId, date: LocalDate, value: Money, amountAdded: Money): NextAdjustment? {
    val source = source(id) ?: return null
    val next = source.snapshots.firstOrNull { it.date > date } ?: return null
    val current = next.value.amountAdded
    val proposed = if (next.date == source.snapshots.earliest()!!.date) {
        source.openingPutIn!! - value
    } else {
        current - amountAdded
    }
    return NextAdjustment(next.date, current, proposed)
}

/**
 * Inserts a snapshot at any past [date] without one (UF-06), lifting the
 * rule that history is entered in date order. A date that already has one is
 * corrected with [editSnapshot] instead. The snapshot after it gets
 * [nextAmountAdded], or without it the adjustment [insertAdjustment] proposes.
 *
 * Before the opening value, the new snapshot is the opening one: it has no
 * amount added, all of it counts as put in, and a foreign source needs a
 * rate on or before its date.
 */
fun Dataset.insertSnapshot(
    id: SourceId,
    date: LocalDate,
    value: Money,
    amountAdded: Money,
    today: LocalDate,
    note: String? = null,
    nextAmountAdded: Money? = null,
): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.status != Status.ACTIVE) return fail(SourceArchived(id))
    checkCurrency(source, value, amountAdded)?.let { return fail(it) }
    if (nextAmountAdded != null) checkCurrency(source, nextAmountAdded)?.let { return fail(it) }
    if (date > today) return fail(DateInFuture(date, today))
    if (source.snapshots.on(date) != null) return fail(SnapshotOnDate(date))
    val opening = source.snapshots.earliest()!!
    val becomesOpening = date < opening.date
    if (becomesOpening && amountAdded.minorUnits != 0L) return fail(OpeningBalanceHasNoAmountAdded)
    if (becomesOpening) {
        val base = baseCurrency ?: return fail(BaseCurrencyNotChosen)
        if (source.currency != base && rates[source.currency]?.latestOnOrBefore(date) == null) {
            return fail(RateNeeded(source.currency, date))
        }
    }
    var snapshots = source.snapshots.with(date, Snapshot(value, amountAdded, clean(note)))
    val adjustment = insertAdjustment(id, date, value, amountAdded)
    if (adjustment != null) {
        val next = source.snapshots.on(adjustment.date)!!
        snapshots = snapshots.with(adjustment.date, next.copy(amountAdded = nextAmountAdded ?: adjustment.proposed))
    }
    // A new opening value is all put in; what the old one had gained is now in the period after it.
    val changed = if (becomesOpening) source.copy(snapshots = snapshots, openingPrincipal = null) else source.copy(snapshots = snapshots)
    return ok(replaceSource(changed))
}

/**
 * The adjustment deleting the snapshot on [date] would make to the one after
 * it, or null if none comes after. That one now covers the deleted period
 * too, so it gets the deleted amount added. Deleting the opening value makes
 * the next the opening one, with no amount added: what it had added counts as
 * put in at the start instead.
 */
fun Dataset.deleteAdjustment(id: SourceId, date: LocalDate): NextAdjustment? {
    val source = source(id) ?: return null
    val deleted = source.snapshots.on(date) ?: return null
    val next = source.snapshots.firstOrNull { it.date > date } ?: return null
    val current = next.value.amountAdded
    val proposed = if (date == source.snapshots.earliest()!!.date) Money(0, source.currency) else current + deleted.amountAdded
    return NextAdjustment(next.date, current, proposed)
}

/**
 * Deletes the snapshot on any date (UF-06). Its only snapshot cannot be
 * deleted; delete the source instead. The one after it gets
 * [nextAmountAdded], or without it the adjustment [deleteAdjustment]
 * proposes. Deleting the opening value makes the next the opening one, and
 * what had been put in by then stays the same, so gains do too.
 */
fun Dataset.deleteSnapshot(id: SourceId, date: LocalDate, nextAmountAdded: Money? = null): RecordingResult {
    val source = source(id) ?: return fail(NoSuchSource(id))
    if (source.status != Status.ACTIVE) return fail(SourceArchived(id))
    if (source.snapshots.on(date) == null) return fail(NoSnapshotOnDate(date))
    if (source.snapshots.size == 1) return fail(OnlySnapshot(id))
    if (nextAmountAdded != null) checkCurrency(source, nextAmountAdded)?.let { return fail(it) }
    val adjustment = deleteAdjustment(id, date)
    var snapshots = source.snapshots.without(date)
    val wasOpening = date == source.snapshots.earliest()!!.date
    if (adjustment != null) {
        val next = source.snapshots.on(adjustment.date)!!
        val added = if (wasOpening) Money(0, source.currency) else nextAmountAdded ?: adjustment.proposed
        snapshots = snapshots.with(adjustment.date, next.copy(amountAdded = added))
    }
    if (!wasOpening) return ok(replaceSource(source.copy(snapshots = snapshots)))
    // The next value is the opening one now: what had been put in by its date is its principal.
    val next = source.snapshots.on(adjustment!!.date)!!
    val putIn = source.openingPutIn!! + adjustment.current
    val principal = putIn.takeIf { it != next.value }
    return ok(replaceSource(source.copy(snapshots = snapshots, openingPrincipal = principal)))
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

private fun Dataset.replaceCategory(changed: Category): Dataset =
    copy(categories = categories.map { if (it.id == changed.id) changed else it })

private fun Dataset.isCategoryNameInUse(name: String, except: CategoryId?): Boolean =
    categories.any { it.id != except && it.status == Status.ACTIVE && it.name.trim().equals(name, ignoreCase = true) }

private fun Rate.relates(currency: Currency, base: Currency): Boolean =
    setOf(unit, quote) == setOf(currency, base)

private fun clean(note: String?): String? = note?.trim()?.takeIf { it.isNotEmpty() }
