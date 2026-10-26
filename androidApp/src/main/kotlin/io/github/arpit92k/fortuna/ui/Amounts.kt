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

// Decided: each amount is written the way its own currency's country writes
// it: pounds as in Britain (£100,000.00), rupees as in India (₹1,00,000.00),
// dollars as in the US. Euros, shared by many countries, follow the phone's
// language where it is one of theirs (100.000,00 € in German), and Ireland's
// style otherwise. Amounts are typed in the same style as they are shown.
// Dates stay in the phone's own style. The core keeps exact amounts; this is
// only how they are written. BigDecimal keeps the conversion exact.

/** Where a currency is used by more than one country, the one whose style it takes. */
private val homeCountries = mapOf(
    "USD" to "US", "GBP" to "GB", "EUR" to "IE", "CHF" to "CH", "AUD" to "AU", "NZD" to "NZ",
    "INR" to "IN", "XOF" to "SN", "XAF" to "CM", "XCD" to "AG", "DKK" to "DK", "NOK" to "NO",
    "ZAR" to "ZA", "ILS" to "IL", "MAD" to "MA",
)

private val availableLocales: Set<Locale> by lazy { Locale.getAvailableLocales().toSet() }

/** The countries that use [code] as their currency, as the phone knows them. */
private val countriesByCurrency: Map<String, List<String>> by lazy {
    Locale.getISOCountries().groupBy { country ->
        runCatching { java.util.Currency.getInstance(Locale("", country))?.currencyCode }.getOrNull()
    }.filterKeys { it != null }.mapKeys { it.key!! }
}

/**
 * The style amounts in [currency] are written in, given the [phone]'s own
 * locale: the phone's own, where its country uses the currency; otherwise
 * the currency's country, in the phone's language if the phone knows that
 * pair, and in English if not.
 */
fun currencyLocale(currency: Currency, phone: Locale = Locale.getDefault()): Locale {
    val countries = countriesByCurrency[currency.code].orEmpty()
    if (phone.country in countries) return phone
    val home = homeCountries[currency.code]
    // The phone's language in a country of the currency: German phones write euros as Germany does.
    val inLanguage = countries.map { Locale(phone.language, it) }.filter { it in availableLocales }
    inLanguage.firstOrNull { it.country == home }?.let { return it }
    if (currency.code == "EUR") inLanguage.firstOrNull()?.let { return it }
    val country = home ?: countries.firstOrNull { Locale("en", it) in availableLocales } ?: countries.firstOrNull()
    return when {
        country == null -> phone
        Locale(phone.language, country) in availableLocales -> Locale(phone.language, country)
        Locale("en", country) in availableLocales -> Locale("en", country)
        else -> availableLocales.firstOrNull { it.country == country } ?: phone
    }
}

/** [money] as its currency's country writes amounts, with the currency's own decimal places. */
fun formatMoney(money: Money, phone: Locale = Locale.getDefault()): String {
    val locale = currencyLocale(money.currency, phone)
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
 * Reads an amount typed the way [formatMoney] shows that currency: its
 * decimal separator, with any grouping separators and spaces ignored.
 * "2,500.50" in pounds and "2.500,50" in euros on a German phone are both 2500.50.
 */
fun parseAmount(text: String, currency: Currency, phone: Locale = Locale.getDefault()): MoneyParseResult {
    val symbols = DecimalFormatSymbols.getInstance(currencyLocale(currency, phone))
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
 * Reads a rate typed as an amount of [quote] is typed: "0,75" for euros on a
 * German phone, "0.75" for pounds. One [unit] is worth the typed value in [quote].
 */
fun parseRate(text: String, unit: Currency, quote: Currency, phone: Locale = Locale.getDefault()): RateParseResult {
    val symbols = DecimalFormatSymbols.getInstance(currencyLocale(quote, phone))
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

/** A rate as typed, in its quote currency's decimal separator: "1 USD = 0.75 GBP", or "1 USD = 0,93 EUR" on a German phone. */
fun formatRate(rate: Rate, phone: Locale = Locale.getDefault()): String =
    "1 ${rate.unit.code} = ${rateText(rate, phone)} ${rate.quote.code}"

/** Just the rate's figure as typed, for filling in a field. */
fun rateText(rate: Rate, phone: Locale = Locale.getDefault()): String =
    rate.toDecimalText().replace('.', DecimalFormatSymbols.getInstance(currencyLocale(rate.quote, phone)).decimalSeparator)

