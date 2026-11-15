package io.github.arpit92k.fortuna.core.importing

import io.github.arpit92k.fortuna.core.domain.Category
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.Source
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.money.MoneyParseResult
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.recording.RecordingError
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.recording.editSnapshot
import io.github.arpit92k.fortuna.core.recording.insertSnapshot
import io.github.arpit92k.fortuna.core.recording.recordRate
import kotlinx.datetime.LocalDate

// Importing history from a spreadsheet (UF-15). The template is a plain CSV
// file, one row per value, so any spreadsheet app can fill it in and save
// it. Reading it is strict: only files that follow the template are
// accepted, and every problem is named by its row. Nothing is written until
// the user confirms, and then the whole import goes in as one change or not
// at all.

/** The template's columns, in order. The header row must be exactly this. */
val IMPORT_COLUMNS: List<String> = listOf("Account", "Category", "Date", "Value", "Added", "Currency")

/** The empty template the app gives the user: the header row alone. */
val IMPORT_TEMPLATE: String = IMPORT_COLUMNS.joinToString(",") + "\n"

/** One value from the file. [line] is its line in the file, the header being line 1. */
data class ImportRow(
    val line: Int,
    val account: String,
    val category: String,
    val date: LocalDate,
    val value: Money,
    val added: Money,
)

/** Why a row, or the whole file, was not accepted. */
sealed interface ImportProblem {
    /** The line the problem is on; 1 is the header. */
    val line: Int

    /** The first row is not the template's header. */
    data class NotTheTemplate(override val line: Int = 1) : ImportProblem

    /** The file has the header but no values. */
    data class NoRows(override val line: Int = 1) : ImportProblem

    data class WrongColumnCount(override val line: Int, val found: Int) : ImportProblem

    data class NoAccount(override val line: Int) : ImportProblem

    data class BadDate(override val line: Int, val text: String) : ImportProblem

    data class DateInFuture(override val line: Int, val date: LocalDate) : ImportProblem

    data class UnknownCurrency(override val line: Int, val text: String) : ImportProblem

    data class BadAmount(override val line: Int, val column: String, val text: String) : ImportProblem

    /** A new account's category must be one of the active categories. */
    data class UnknownCategory(override val line: Int, val text: String) : ImportProblem

    /** An account already in the app is in one currency; its rows must be too. */
    data class WrongCurrency(override val line: Int, val account: String, val expected: Currency) : ImportProblem

    /** Rows of one new account must agree on its category and currency. */
    data class Inconsistent(override val line: Int, val account: String) : ImportProblem

    /** Two rows give the same account a value on the same date. */
    data class DuplicateDate(override val line: Int, val account: String, val date: LocalDate) : ImportProblem

    /** A new account's first value is its opening value, with nothing added. */
    data class OpeningHasAdded(override val line: Int, val account: String) : ImportProblem

    /** A closed account takes no new values; reopen it first. */
    data class AccountClosed(override val line: Int, val account: String) : ImportProblem
}

/** What the import would do, and the decisions it needs (UF-15, step 4). */
data class ImportPlan(
    val rows: List<ImportRow>,
    /** Accounts the file names that do not exist yet, to be created, with their category and currency. */
    val newAccounts: List<NewAccount>,
    /** Rows on a date that already has a value: replaced or skipped, as the user chooses. */
    val conflicts: List<ImportRow>,
    /** Foreign currencies with no rate early enough, and the earliest date each needs one by. */
    val ratesNeeded: Map<Currency, LocalDate>,
)

data class NewAccount(val name: String, val category: Category, val currency: Currency)

/** The file read: a plan, or every problem found. */
sealed interface ImportCheck {
    data class Ready(val plan: ImportPlan) : ImportCheck

    data class Problems(val problems: List<ImportProblem>) : ImportCheck
}

/** Reads [text], a filled-in template, against [data] on [today]. Nothing is changed. */
fun Dataset.checkImport(text: String, today: LocalDate): ImportCheck {
    val base = baseCurrency ?: return ImportCheck.Problems(listOf(ImportProblem.NotTheTemplate()))
    val lines = text.removePrefix("﻿").lines().map { it.trimEnd('\r') }
    val header = lines.firstOrNull()?.let(::splitCsvLine)?.map { it.trim() }
    if (header == null || header.map { it.lowercase() } != IMPORT_COLUMNS.map { it.lowercase() }) {
        return ImportCheck.Problems(listOf(ImportProblem.NotTheTemplate()))
    }
    val problems = mutableListOf<ImportProblem>()
    val rows = mutableListOf<ImportRow>()
    val currencies = mutableMapOf<String, Currency>()
    lines.drop(1).forEachIndexed { index, raw ->
        val line = index + 2
        if (raw.isBlank()) return@forEachIndexed
        val cells = splitCsvLine(raw).map { it.trim() }
        // A spreadsheet may save trailing empty columns.
        val used = cells.dropLastWhile { it.isEmpty() }
        if (used.size > IMPORT_COLUMNS.size || used.size < 4) {
            problems += ImportProblem.WrongColumnCount(line, used.size)
            return@forEachIndexed
        }
        val (account, category, dateText, valueText) = cells.padded()
        val addedText = cells.padded()[4]
        val currencyText = cells.padded()[5]
        if (account.isEmpty()) {
            problems += ImportProblem.NoAccount(line)
            return@forEachIndexed
        }
        val existing = activeOrClosedSource(account)
        val currency = when {
            currencyText.isEmpty() -> existing?.currency ?: base
            else -> Currency.forCode(currencyText.uppercase()) ?: run {
                problems += ImportProblem.UnknownCurrency(line, currencyText)
                return@forEachIndexed
            }
        }
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: run {
            problems += ImportProblem.BadDate(line, dateText)
            return@forEachIndexed
        }
        if (date > today) {
            problems += ImportProblem.DateInFuture(line, date)
            return@forEachIndexed
        }
        val value = amount(valueText, currency) ?: run {
            problems += ImportProblem.BadAmount(line, "Value", valueText)
            return@forEachIndexed
        }
        val added = if (addedText.isEmpty()) Money(0, currency) else amount(addedText, currency) ?: run {
            problems += ImportProblem.BadAmount(line, "Added", addedText)
            return@forEachIndexed
        }
        currencies[account.lowercase()] = currency
        rows += ImportRow(line, account, category, date, value, added)
    }
    if (problems.isEmpty() && rows.isEmpty()) return ImportCheck.Problems(listOf(ImportProblem.NoRows()))

    // Per account: the account it is, or a new one with one category and currency.
    val newAccounts = mutableListOf<NewAccount>()
    val conflicts = mutableListOf<ImportRow>()
    val ratesNeeded = mutableMapOf<Currency, LocalDate>()
    for ((key, accountRows) in rows.groupBy { it.account.lowercase() }) {
        val sorted = accountRows.sortedBy { it.date }
        val first = sorted.first()
        val currency = currencies.getValue(key)
        sorted.groupBy { it.date }.values.filter { it.size > 1 }.forEach { same ->
            problems += ImportProblem.DuplicateDate(same[1].line, first.account, same[1].date)
        }
        val existing = activeOrClosedSource(first.account)
        if (existing != null) {
            if (existing.status != Status.ACTIVE) {
                problems += ImportProblem.AccountClosed(first.line, existing.name)
                continue
            }
            accountRows.filter { it.value.currency != existing.currency }.forEach {
                problems += ImportProblem.WrongCurrency(it.line, existing.name, existing.currency)
            }
            conflicts += sorted.filter { existing.snapshots.on(it.date) != null }
            val opening = existing.snapshots.earliest()!!.date
            if (first.date < opening) needRate(existing.currency, first.date, base, ratesNeeded)
            continue
        }
        val category = activeCategory(first.category) ?: run {
            problems += ImportProblem.UnknownCategory(first.line, first.category)
            continue
        }
        sorted.drop(1).filter { !it.category.equals(first.category, ignoreCase = true) && it.category.isNotEmpty() }.forEach {
            problems += ImportProblem.Inconsistent(it.line, first.account)
        }
        if (first.added.minorUnits != 0L) problems += ImportProblem.OpeningHasAdded(first.line, first.account)
        newAccounts += NewAccount(first.account.trim(), category, currency)
        needRate(currency, first.date, base, ratesNeeded)
    }
    if (problems.isNotEmpty()) return ImportCheck.Problems(problems.sortedBy { it.line })
    return ImportCheck.Ready(ImportPlan(rows.sortedWith(compareBy({ it.account.lowercase() }, { it.date })), newAccounts, conflicts, ratesNeeded))
}

/**
 * Applies [plan] as one change: the rates given in [rates], each recorded on
 * the date it is first needed, then every account and value. A date that
 * already has a value is replaced when [replace], and skipped otherwise.
 * Any rule broken refuses the whole import, leaving [this] as it was.
 */
fun Dataset.applyImport(plan: ImportPlan, rates: Map<Currency, Rate>, replace: Boolean, today: LocalDate): RecordingResult {
    var data = this
    for ((currency, date) in plan.ratesNeeded) {
        val rate = rates[currency] ?: return RecordingResult.Failure(RecordingError.RateNeeded(currency, date))
        data = when (val result = data.recordRate(currency, date, rate, today)) {
            is RecordingResult.Success -> result.dataset
            is RecordingResult.Failure -> return result
        }
    }
    val conflicts = plan.conflicts.map { it.line }.toSet()
    for (row in plan.rows) {
        val source = data.activeOrClosedSource(row.account)
        val result = when {
            source == null -> {
                val account = plan.newAccounts.first { it.name.equals(row.account.trim(), ignoreCase = true) }
                data.addSource(account.name, account.category.id, row.date, row.value, today)
            }
            row.line in conflicts -> if (replace) {
                val opening = row.date == source.snapshots.earliest()!!.date
                data.editSnapshot(source.id, row.date, row.value, if (opening) Money(0, source.currency) else row.added)
            } else {
                continue
            }
            row.date > source.snapshots.latest()!!.date -> data.addSnapshot(source.id, row.date, row.value, row.added, today)
            else -> data.insertSnapshot(source.id, row.date, row.value, if (row.date < source.snapshots.earliest()!!.date) Money(0, source.currency) else row.added, today)
        }
        data = when (result) {
            is RecordingResult.Success -> result.dataset
            is RecordingResult.Failure -> return result
        }
    }
    return RecordingResult.Success(data)
}

private fun Dataset.activeOrClosedSource(name: String): Source? {
    val clean = name.trim()
    return sources.firstOrNull { it.status == Status.ACTIVE && it.name.equals(clean, ignoreCase = true) }
        ?: sources.lastOrNull { it.name.equals(clean, ignoreCase = true) }
}

private fun Dataset.activeCategory(name: String): Category? =
    categories.firstOrNull { it.status == Status.ACTIVE && it.name.equals(name.trim(), ignoreCase = true) }

private fun Dataset.needRate(currency: Currency, date: LocalDate, base: Currency, needed: MutableMap<Currency, LocalDate>) {
    if (currency == base) return
    if (rates[currency]?.latestOnOrBefore(date) != null) return
    needed[currency] = minOf(needed[currency] ?: date, date)
}

private fun amount(text: String, currency: Currency): Money? =
    // A dot for decimals and no separators, so 1,234 or 1234,50 is never misread.
    (Money.parse(text, currency) as? MoneyParseResult.Success)?.money

private fun List<String>.padded(): List<String> = this + List((IMPORT_COLUMNS.size - size).coerceAtLeast(0)) { "" }

/** One CSV line into its cells: commas separate, double quotes enclose, and "" is a quote. */
internal fun splitCsvLine(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
            else -> cell.append(c)
        }
        i++
    }
    cells += cell.toString()
    return cells
}
