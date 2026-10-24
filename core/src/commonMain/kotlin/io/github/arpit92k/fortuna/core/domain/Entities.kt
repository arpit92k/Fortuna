package io.github.arpit92k.fortuna.core.domain

import io.github.arpit92k.fortuna.core.dated.DatedValues
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

// The entities of the architecture's domain model. Each is immutable; the
// recording rules (step 07) make changes by returning new copies.
//
// Only the shape of the data and its structural integrity are enforced here.
// Rules such as unique names among active sources are the recording rules' job.

/** Identifies a category. Assigned by the dataset, never reused. */
@JvmInline
value class CategoryId(val value: Long)

/** Identifies a source. Assigned by the dataset, never reused. */
@JvmInline
value class SourceId(val value: Long)

/** Whether a category or source is in use or kept only for its history. */
enum class Status { ACTIVE, ARCHIVED }

/** Whether a category's sources add to net worth or take away from it. */
enum class CategoryKind { ASSET, LIABILITY }

/** How often the user expects to update a source, used to mark it as out of date. */
enum class UpdateFrequency { WEEKLY, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY }

/**
 * A grouping of sources such as cash, investments or debt. Its [kind] decides
 * whether its sources are assets or liabilities, so the rule lives in one place.
 */
data class Category(
    val id: CategoryId,
    val name: String,
    val kind: CategoryKind,
    val status: Status = Status.ACTIVE,
)

/**
 * The value of one source on one date, in the source's currency.
 *
 * [amountAdded] is the net amount put in since the previous snapshot:
 * deposits minus withdrawals, or for a liability new borrowing minus
 * repayments. A source's first snapshot is its opening balance, for which
 * it is zero.
 */
data class Snapshot(
    val value: Money,
    val amountAdded: Money,
    val note: String? = null,
) {
    init {
        require(amountAdded.currency == value.currency) {
            "A snapshot's value and amount added must be in one currency, not ${value.currency} and ${amountAdded.currency}"
        }
    }
}

/**
 * Anything that holds value, such as a bank account, pension, property or
 * loan. It belongs to one category, has one currency, and keeps its history
 * as at most one snapshot per date.
 */
data class Source(
    val id: SourceId,
    val name: String,
    val categoryId: CategoryId,
    val currency: Currency,
    val updateFrequency: UpdateFrequency,
    val snapshots: DatedValues<Snapshot>,
    val note: String? = null,
    val status: Status = Status.ACTIVE,
) {
    init {
        val wrong = snapshots.firstOrNull { it.value.value.currency != currency }
        require(wrong == null) { "Source ${id.value} is in $currency but has a snapshot in ${wrong?.value?.value?.currency} on ${wrong?.date}" }
    }
}

/**
 * The single record of app-wide values.
 *
 * [revision] rises by one with every saved change. A backup records the
 * revision it was made at in [lastBackupRevision], so the backup is up to date
 * exactly when the two match. [lastBackup] is only for showing to the user.
 */
data class Settings(
    val defaultUpdateFrequency: UpdateFrequency = UpdateFrequency.MONTHLY,
    val revision: Long = 0,
    val lastBackupRevision: Long? = null,
    val lastBackup: LocalDate? = null,
) {
    init {
        require(revision >= 0) { "The revision cannot be negative: $revision" }
        require(lastBackupRevision == null || lastBackupRevision in 0..revision) {
            "The last backup's revision $lastBackupRevision is not between 0 and the current revision $revision"
        }
        require((lastBackupRevision == null) == (lastBackup == null)) {
            "The last backup's revision and date are recorded together"
        }
    }
}
