package io.github.arpit92k.fortuna.core.recording

import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import kotlinx.datetime.LocalDate

/** The outcome of a recording rule: the changed dataset, or why the change was refused. */
sealed interface RecordingResult {
    /** The change was made. The dataset it was made to is unchanged. */
    data class Success(val dataset: Dataset) : RecordingResult

    /** The change breaks a rule, so nothing was changed. */
    data class Failure(val error: RecordingError) : RecordingResult
}

/** Why a change was refused. Each names the rule it would have broken. */
sealed interface RecordingError {
    // Base currency

    /** Nothing can be recorded until setup has chosen the base currency. */
    data object BaseCurrencyNotChosen : RecordingError

    /** The base currency is chosen once. Changing it is a later, deliberate settings action. */
    data object BaseCurrencyAlreadyChosen : RecordingError

    // Dates

    /** No snapshot or rate is dated in the future. */
    data class DateInFuture(val date: LocalDate, val today: LocalDate) : RecordingError

    // Sources

    /** A source needs a name. */
    data object EmptyName : RecordingError

    /** Names are unique among active sources, ignoring case and surrounding spaces. */
    data class NameInUse(val name: String) : RecordingError

    data class NoSuchCategory(val id: CategoryId) : RecordingError

    /** New sources go only into active categories. */
    data class CategoryArchived(val id: CategoryId) : RecordingError

    data class NoSuchSource(val id: SourceId) : RecordingError

    /** An archived source keeps its history and takes no new values. */
    data class SourceArchived(val id: SourceId) : RecordingError

    /** A source's snapshots are all in the source's own currency. */
    data class WrongCurrency(val expected: Currency, val given: Currency) : RecordingError

    /** A source with history is archived, not deleted. Only a source with just its opening snapshot can be deleted. */
    data class SourceHasHistory(val id: SourceId) : RecordingError

    // Snapshots

    /** The opening balance counts as neither added nor growth, so its amount added is zero. */
    data object OpeningBalanceHasNoAmountAdded : RecordingError

    /** In the first version, history is entered in date order: a new snapshot is dated on or after the latest. */
    data class BeforeLatestSnapshot(val latest: LocalDate) : RecordingError

    data class NoSnapshotOnDate(val date: LocalDate) : RecordingError

    /** A source always has at least one snapshot. To remove its only one, delete the source. */
    data class OnlySnapshot(val id: SourceId) : RecordingError

    // Rates

    /** A source in a foreign currency needs a rate on or before its opening date. */
    data class RateNeeded(val currency: Currency, val date: LocalDate) : RecordingError

    /** The base currency's rate is always one, so it has no rates of its own. */
    data object BaseCurrencyHasNoRate : RecordingError

    /** A currency's rates relate it to the base currency, in either direction. */
    data class WrongRateCurrencies(val currency: Currency, val rate: Rate) : RecordingError

    data class NoRateOnDate(val currency: Currency, val date: LocalDate) : RecordingError

    /** Deleting the rate would leave this source's earliest snapshot with no rate on or before its date. */
    data class RateNeededBySnapshot(val currency: Currency, val source: SourceId, val date: LocalDate) : RecordingError
}
