package io.github.arpit92k.fortuna.core.domain

import io.github.arpit92k.fortuna.core.dated.Dated
import io.github.arpit92k.fortuna.core.dated.DatedValues
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.money.MoneyParseResult
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The saved form of a [Dataset]: JSON, before encryption.
 *
 * The format is described by private classes of its own, separate from the
 * domain model, so renaming something in the code never changes what is on
 * disk. Every file records its format version first. A file of a version this
 * code does not know is refused before anything else is read; reading an
 * older version is done by code written for it when a new version arrives.
 *
 * Amounts and rates are written as decimal text, such as "10000.00" and
 * "0.75", so they stay exact and readable. Dates are ISO calendar dates.
 */
object DatasetJson {
    /** The version this code writes, and the only one it reads so far. */
    const val FORMAT_VERSION: Int = 1

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
    }

    /** The dataset as JSON text in the current format version. */
    fun encode(dataset: Dataset): String = json.encodeToString(DatasetV1.serializer(), dataset.toV1())

    /**
     * Reads a dataset from JSON text.
     *
     * @throws UnsupportedFormatVersionException if the file is of a version this code does not read.
     * @throws DatasetFormatException if the text is not a well-formed dataset.
     */
    fun decode(text: String): Dataset {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw DatasetFormatException("The file is not a JSON object", e)
        }
        val version = root["formatVersion"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            ?: throw DatasetFormatException("The file has no format version")
        if (version != FORMAT_VERSION) throw UnsupportedFormatVersionException(version)

        val file = try {
            json.decodeFromJsonElement(DatasetV1.serializer(), root)
        } catch (e: SerializationException) {
            throw DatasetFormatException("The file does not match format version $FORMAT_VERSION: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw DatasetFormatException("The file does not match format version $FORMAT_VERSION: ${e.message}", e)
        }
        return try {
            file.toDataset()
        } catch (e: IllegalArgumentException) {
            throw DatasetFormatException(e.message ?: "The file breaks a rule of the domain model", e)
        }
    }
}

/** The text is not a well-formed dataset. */
open class DatasetFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The file is of a format version this code does not read, perhaps written by a newer app. */
class UnsupportedFormatVersionException(val version: Int) :
    DatasetFormatException("Format version $version is not supported; this app reads version ${DatasetJson.FORMAT_VERSION}")

// ---- Format version 1. Once released, these classes must never change. ----

@Serializable
private class DatasetV1(
    val formatVersion: Int,
    val baseCurrency: String?,
    val nextId: Long,
    val categories: List<CategoryV1>,
    val sources: List<SourceV1>,
    val rates: List<RateHistoryV1>,
    val settings: SettingsV1,
)

@Serializable
private class CategoryV1(val id: Long, val name: String, val kind: KindV1, val status: StatusV1)

@Serializable
private class SourceV1(
    val id: Long,
    val name: String,
    val category: Long,
    val currency: String,
    val updateFrequency: FrequencyV1,
    val status: StatusV1,
    val note: String?,
    val snapshots: List<SnapshotV1>,
)

@Serializable
private class SnapshotV1(val date: String, val value: String, val amountAdded: String, val note: String?)

@Serializable
private class RateHistoryV1(val currency: String, val history: List<RateV1>)

/** "1 [unit] = [rate] [quote]", exactly as typed. */
@Serializable
private class RateV1(val date: String, val unit: String, val quote: String, val rate: String)

@Serializable
private class SettingsV1(
    val defaultUpdateFrequency: FrequencyV1,
    val revision: Long,
    val lastBackupRevision: Long?,
    val lastBackup: String?,
)

@Serializable
private enum class KindV1 {
    @SerialName("asset") ASSET,
    @SerialName("liability") LIABILITY,
}

@Serializable
private enum class StatusV1 {
    @SerialName("active") ACTIVE,
    @SerialName("archived") ARCHIVED,
}

@Serializable
private enum class FrequencyV1 {
    @SerialName("weekly") WEEKLY,
    @SerialName("monthly") MONTHLY,
    @SerialName("quarterly") QUARTERLY,
    @SerialName("half-yearly") HALF_YEARLY,
    @SerialName("yearly") YEARLY,
}

// ---- Domain to version 1 ----

private fun Dataset.toV1() = DatasetV1(
    formatVersion = DatasetJson.FORMAT_VERSION,
    baseCurrency = baseCurrency?.code,
    nextId = nextId,
    categories = categories.map { CategoryV1(it.id.value, it.name, it.kind.toV1(), it.status.toV1()) },
    sources = sources.map { source ->
        SourceV1(
            id = source.id.value,
            name = source.name,
            category = source.categoryId.value,
            currency = source.currency.code,
            updateFrequency = source.updateFrequency.toV1(),
            status = source.status.toV1(),
            note = source.note,
            snapshots = source.snapshots.map { (date, snapshot) ->
                SnapshotV1(date.toString(), snapshot.value.toDecimalText(), snapshot.amountAdded.toDecimalText(), snapshot.note)
            },
        )
    },
    rates = rates.entries.sortedBy { it.key.code }.map { (currency, history) ->
        RateHistoryV1(
            currency = currency.code,
            history = history.map { (date, rate) -> RateV1(date.toString(), rate.unit.code, rate.quote.code, rate.toDecimalText()) },
        )
    },
    settings = SettingsV1(
        defaultUpdateFrequency = settings.defaultUpdateFrequency.toV1(),
        revision = settings.revision,
        lastBackupRevision = settings.lastBackupRevision,
        lastBackup = settings.lastBackup?.toString(),
    ),
)

private fun CategoryKind.toV1() = when (this) {
    CategoryKind.ASSET -> KindV1.ASSET
    CategoryKind.LIABILITY -> KindV1.LIABILITY
}

private fun Status.toV1() = when (this) {
    Status.ACTIVE -> StatusV1.ACTIVE
    Status.ARCHIVED -> StatusV1.ARCHIVED
}

private fun UpdateFrequency.toV1() = when (this) {
    UpdateFrequency.WEEKLY -> FrequencyV1.WEEKLY
    UpdateFrequency.MONTHLY -> FrequencyV1.MONTHLY
    UpdateFrequency.QUARTERLY -> FrequencyV1.QUARTERLY
    UpdateFrequency.HALF_YEARLY -> FrequencyV1.HALF_YEARLY
    UpdateFrequency.YEARLY -> FrequencyV1.YEARLY
}

// ---- Version 1 to domain. Any problem throws IllegalArgumentException. ----

private fun DatasetV1.toDataset() = Dataset(
    baseCurrency = baseCurrency?.let(::parseCurrency),
    categories = categories.map { Category(CategoryId(it.id), it.name, it.kind.toDomain(), it.status.toDomain()) },
    sources = sources.map { it.toDomain() },
    rates = rates.associate { history ->
        val currency = parseCurrency(history.currency)
        currency to datedValues("rates of ${currency.code}", history.history) { entry ->
            Dated(parseDate(entry.date), parseRate(entry))
        }
    }.also { require(it.size == rates.size) { "A currency's rates appear twice" } },
    settings = Settings(
        defaultUpdateFrequency = settings.defaultUpdateFrequency.toDomain(),
        revision = settings.revision,
        lastBackupRevision = settings.lastBackupRevision,
        lastBackup = settings.lastBackup?.let(::parseDate),
    ),
    nextId = nextId,
)

private fun SourceV1.toDomain(): Source {
    val sourceCurrency = parseCurrency(currency)
    return Source(
        id = SourceId(id),
        name = name,
        categoryId = CategoryId(category),
        currency = sourceCurrency,
        updateFrequency = updateFrequency.toDomain(),
        snapshots = datedValues("snapshots of source $id", snapshots) { entry ->
            Dated(
                parseDate(entry.date),
                Snapshot(parseMoney(entry.value, sourceCurrency), parseMoney(entry.amountAdded, sourceCurrency), entry.note),
            )
        },
        note = note,
        status = status.toDomain(),
    )
}

/** Builds a dated collection, refusing two entries on one date rather than letting one replace the other. */
private fun <T, R> datedValues(what: String, items: List<T>, read: (T) -> Dated<R>): DatedValues<R> {
    val dated = items.map(read)
    val dates = dated.map { it.date }
    require(dates.size == dates.toSet().size) { "Two $what share a date" }
    return DatedValues.of(*dated.toTypedArray())
}

private fun parseCurrency(code: String): Currency =
    requireNotNull(Currency.forCode(code)) { "Unknown currency code \"$code\"" }

private fun parseDate(text: String): LocalDate = try {
    LocalDate.parse(text)
} catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("\"$text\" is not a calendar date", e)
}

private fun parseMoney(text: String, currency: Currency): Money =
    when (val result = Money.parse(text, currency)) {
        is MoneyParseResult.Success -> result.money
        is MoneyParseResult.Failure -> throw IllegalArgumentException("\"$text\" is not an amount in $currency: ${result.problem}")
    }

private fun parseRate(rate: RateV1): Rate {
    val unit = parseCurrency(rate.unit)
    val quote = parseCurrency(rate.quote)
    require(unit != quote) { "A rate relates two different currencies, not $unit and $quote" }
    return when (val result = Rate.parse(rate.rate, unit, quote)) {
        is RateParseResult.Success -> result.rate
        is RateParseResult.Failure -> throw IllegalArgumentException("\"${rate.rate}\" is not a rate: ${result.problem}")
    }
}

private fun KindV1.toDomain() = when (this) {
    KindV1.ASSET -> CategoryKind.ASSET
    KindV1.LIABILITY -> CategoryKind.LIABILITY
}

private fun StatusV1.toDomain() = when (this) {
    StatusV1.ACTIVE -> Status.ACTIVE
    StatusV1.ARCHIVED -> Status.ARCHIVED
}

private fun FrequencyV1.toDomain() = when (this) {
    FrequencyV1.WEEKLY -> UpdateFrequency.WEEKLY
    FrequencyV1.MONTHLY -> UpdateFrequency.MONTHLY
    FrequencyV1.QUARTERLY -> UpdateFrequency.QUARTERLY
    FrequencyV1.HALF_YEARLY -> UpdateFrequency.HALF_YEARLY
    FrequencyV1.YEARLY -> UpdateFrequency.YEARLY
}
