package io.github.arpit92k.fortuna.ui

import io.github.arpit92k.fortuna.core.money.AmountProblem
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.money.MoneyParseResult
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.core.rates.RateProblem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Decided: amounts are shown in the phone's regional format, such as £2,500.00
// in the UK or 2.500,00 € in Germany. The core keeps exact amounts; this is
// only how they are written. BigDecimal keeps the conversion exact.

/** [money] as the phone's region writes amounts, with the currency's own decimal places. */
fun formatMoney(money: Money, locale: Locale = Locale.getDefault()): String {
    val places = money.currency.decimalPlaces
    val amount = BigDecimal.valueOf(money.minorUnits, places)
    val system = runCatching { java.util.Currency.getInstance(money.currency.code) }.getOrNull()
        ?: return "${money.toDecimalText()} ${money.currency.code}"
    val format = NumberFormat.getCurrencyInstance(locale).apply {
        currency = system
        minimumFractionDigits = places
        maximumFractionDigits = places
    }
    return format.format(amount)
}

/**
 * Reads an amount typed the way the phone's region writes numbers: the
 * region's decimal separator, with any grouping separators and spaces
 * ignored. "2,500.50" in the UK and "2.500,50" in Germany are both 2500.50.
 */
fun parseAmount(text: String, currency: Currency, locale: Locale = Locale.getDefault()): MoneyParseResult {
    val symbols = DecimalFormatSymbols.getInstance(locale)
    val decimal = symbols.decimalSeparator
    val grouping = symbols.groupingSeparator
    val plain = buildString {
        for (c in text.trim()) {
            when {
                c == decimal -> append('.')
                c == grouping || c.isWhitespace() || c == ' ' || c == ' ' -> {}
                // A minus sign typed from some keyboards.
                c == '−' -> append('-')
                else -> append(c)
            }
        }
    }
    if (plain.isEmpty()) return MoneyParseResult.Failure(AmountProblem.EMPTY)
    return Money.parse(plain, currency)
}

/** A date as the phone's region writes it, such as 25 Oct 2026. */
fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

/**
 * Reads a rate typed the way the phone's region writes numbers, as
 * [parseAmount] does: "0,75" in Germany and "0.75" in the UK are both 0.75.
 * One [unit] is worth the typed value in [quote].
 */
fun parseRate(text: String, unit: Currency, quote: Currency, locale: Locale = Locale.getDefault()): RateParseResult {
    val symbols = DecimalFormatSymbols.getInstance(locale)
    val plain = buildString {
        for (c in text.trim()) {
            when {
                c == symbols.decimalSeparator -> append('.')
                c == symbols.groupingSeparator || c.isWhitespace() || c == '\u00A0' || c == '\u202F' -> {}
                else -> append(c)
            }
        }
    }
    if (plain.isEmpty()) return RateParseResult.Failure(RateProblem.EMPTY)
    return Rate.parse(plain, unit, quote)
}

/** A rate as typed, in the region's decimal separator: "1 USD = 0.75 GBP", or "1 USD = 0,75 GBP" in Germany. */
fun formatRate(rate: Rate, locale: Locale = Locale.getDefault()): String =
    "1 ${rate.unit.code} = ${rateText(rate, locale)} ${rate.quote.code}"

/** Just the rate's figure as typed, in the region's decimal separator, for filling in a field. */
fun rateText(rate: Rate, locale: Locale = Locale.getDefault()): String =
    rate.toDecimalText().replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

