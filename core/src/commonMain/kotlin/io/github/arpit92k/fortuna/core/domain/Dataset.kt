package io.github.arpit92k.fortuna.core.domain

import io.github.arpit92k.fortuna.core.dated.DatedValues
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate

/**
 * Everything the user has recorded, as one immutable value: the single source
 * of truth that the encrypted store saves and backups copy.
 *
 * Nothing calculated is kept here. Net worth, history and every split are
 * worked out from these records when needed.
 *
 * Creating a dataset checks its structural integrity: unique ids, references
 * that resolve, and rates that relate a currency to the base currency. A
 * dataset that exists is therefore always well formed.
 */
data class Dataset(
    /** The currency totals and charts are reported in. Null only until setup chooses it. */
    val baseCurrency: Currency?,
    val categories: List<Category>,
    /** In the order they were added. */
    val sources: List<Source>,
    /**
     * The exchange rate history of each foreign currency in use, keyed by that
     * currency. Each rate is kept as typed, either way round against the base.
     */
    val rates: Map<Currency, DatedValues<Rate>>,
    val settings: Settings,
    /** The next id to give a new category or source. Ids are never reused. */
    val nextId: Long,
) {
    init {
        val categoryIds = categories.map { it.id }
        require(categoryIds.size == categoryIds.toSet().size) { "Two categories share an id" }
        val sourceIds = sources.map { it.id }
        require(sourceIds.size == sourceIds.toSet().size) { "Two sources share an id" }

        val known = categoryIds.toSet()
        sources.forEach { source ->
            require(source.categoryId in known) { "Source ${source.id.value} belongs to a missing category ${source.categoryId.value}" }
        }

        val highestId = (categoryIds.map { it.value } + sourceIds.map { it.value }).maxOrNull() ?: 0L
        require(nextId > highestId) { "The next id $nextId must be above every id in use ($highestId)" }

        require(baseCurrency != null || rates.isEmpty()) { "Rates need a base currency" }
        rates.forEach { (currency, history) ->
            require(currency != baseCurrency) { "The base currency $currency has no rates of its own" }
            history.forEach { (date, rate) ->
                val pair = setOf(rate.unit, rate.quote)
                require(pair == setOf(currency, baseCurrency)) {
                    "The $currency rate on $date relates ${rate.unit} and ${rate.quote}, not $currency and $baseCurrency"
                }
            }
        }
    }

    /** The category with this id, or null if there is none. */
    fun category(id: CategoryId): Category? = categories.firstOrNull { it.id == id }

    /** The source with this id, or null if there is none. */
    fun source(id: SourceId): Source? = sources.firstOrNull { it.id == id }

    companion object {
        /**
         * A new, empty dataset: no base currency yet, no sources, and the
         * starter categories, which are fixed in the first version.
         */
        fun new(): Dataset = Dataset(
            baseCurrency = null,
            categories = starterCategories,
            sources = emptyList(),
            rates = emptyMap(),
            settings = Settings(),
            nextId = starterCategories.size + 1L,
        )

        /** Cash, investments, property, pension and debt, as offered at setup. */
        val starterCategories: List<Category> = listOf(
            Category(CategoryId(1), "Cash", CategoryKind.ASSET),
            Category(CategoryId(2), "Investments", CategoryKind.ASSET),
            Category(CategoryId(3), "Property", CategoryKind.ASSET),
            Category(CategoryId(4), "Pension", CategoryKind.ASSET),
            Category(CategoryId(5), "Debt", CategoryKind.LIABILITY),
        )
    }
}
