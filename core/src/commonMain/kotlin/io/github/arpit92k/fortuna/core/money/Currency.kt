package io.github.arpit92k.fortuna.core.money

/**
 * A currency from the fixed list built into the core, taken from ISO 4217.
 *
 * There is exactly one instance per code, and the only way to get one is from
 * the list, so a currency always has the right number of decimal places.
 */
class Currency private constructor(
    /** The three-letter ISO 4217 code, such as GBP. */
    val code: String,
    /** The English name in the ISO 4217 list, such as Pound Sterling. */
    val name: String,
    /** How many digits follow the decimal point: 2 for pounds, 0 for yen, 3 for dinars. */
    val decimalPlaces: Int,
) {
    override fun equals(other: Any?): Boolean = other is Currency && other.code == code

    override fun hashCode(): Int = code.hashCode()

    override fun toString(): String = code

    companion object {
        /** Every currency the app offers, in order of code. */
        val all: List<Currency> = isoCurrencies.map { Currency(it.code, it.name, it.decimalPlaces) }

        private val byCode: Map<String, Currency> = all.associateBy { it.code }

        /** The currency with this exact code, such as "GBP", or null if it is not in the list. */
        fun forCode(code: String): Currency? = byCode[code]
    }
}
