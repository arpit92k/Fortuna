package io.github.arpit92k.fortuna.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** The chart on a device, with one value, two, and several years of them. */
@RunWith(AndroidJUnit4::class)
class ValueChartTest {
    @get:Rule
    val compose = createComposeRule()

    private val pounds = Currency.forCode("GBP")!!
    private val start = LocalDate(2022, 1, 31)

    private fun point(months: Int, pounds: Long) = ChartPoint(start.plus(DatePeriod(months = months)), Money(pounds * 100, this.pounds))

    private fun show(points: List<ChartPoint>) {
        compose.setContent { FortunaTheme { ValueChart(points) } }
    }

    @Test
    fun oneValueSaysTheChartFillsIn() {
        show(listOf(point(0, 1_000)))
        compose.onNodeWithTag(CHART_EMPTY).assertExists()
        compose.onNodeWithTag(CHART).assertDoesNotExist()
    }

    @Test
    fun twoValuesDrawALine() {
        show(listOf(point(0, 1_000), point(1, 1_200)))
        compose.onNodeWithTag(CHART).assertExists().assertHeightIsAtLeast(150.dp)
    }

    @Test
    fun yearsOfValuesDrawAndAreSummarisedForScreenReaders() {
        show((0 until 60).map { point(it, 10_000L + it * 250L - (it % 7) * 400L) })
        val node = compose.onNodeWithTag(CHART).assertExists().fetchSemanticsNode()
        val description = node.config[SemanticsProperties.ContentDescription].single()
        assertTrue(description.contains("£10,000.00"), "the summary starts from the first value: $description")
    }
}
