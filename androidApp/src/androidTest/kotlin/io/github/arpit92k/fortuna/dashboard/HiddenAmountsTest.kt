package io.github.arpit92k.fortuna.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.ui.ChartPoint
import io.github.arpit92k.fortuna.ui.FortunaTheme
import io.github.arpit92k.fortuna.ui.HIDDEN_AMOUNT
import io.github.arpit92k.fortuna.ui.LocalAmountsHidden
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** The dashboard's eye, on a device: every amount becomes dots, and comes back. */
@RunWith(AndroidJUnit4::class)
class HiddenAmountsTest {
    @get:Rule
    val compose = createComposeRule()

    private val pounds = Currency.forCode("GBP")!!
    private fun gbp(pence: Long) = Money(pence, pounds)

    private val ui = DashboardUi(
        netWorth = gbp(1_050_00),
        assets = gbp(1_250_00),
        liabilities = gbp(200_00),
        sources = listOf(
            SourceRow(SourceId(6), "Savings", "Cash", liability = false, value = gbp(1_250_00), lastUpdated = LocalDate(2026, 10, 1)),
            SourceRow(SourceId(7), "Card", "Debt", liability = true, value = gbp(200_00), lastUpdated = LocalDate(2026, 10, 1)),
        ),
        timeline = listOf(ChartPoint(LocalDate(2026, 1, 31), gbp(900_00)), ChartPoint(LocalDate(2026, 10, 1), gbp(1_050_00))),
    )

    private fun show(startHidden: Boolean) {
        compose.setContent {
            var hidden by remember { mutableStateOf(startHidden) }
            FortunaTheme {
                CompositionLocalProvider(LocalAmountsHidden provides hidden) {
                    DashboardScreen(
                        ui = ui,
                        onAddSource = {},
                        onOpen = {},
                        onOpenChart = {},
                        onUpdate = {},
                        onSettings = {},
                        onLock = {},
                        onToggleAmounts = { hidden = !hidden },
                    )
                }
            }
        }
    }

    @Test
    fun hiddenShowsNoAmountAnywhere() {
        show(startHidden = true)
        compose.onNodeWithTag("net-worth").assertTextEquals(HIDDEN_AMOUNT)
        compose.onAllNodesWithText("£", substring = true).assertCountEquals(0)
        // TalkBack says "hidden", not six bullets.
        val spoken = compose.onNodeWithTag("net-worth").fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf("hidden"), spoken)
    }

    @Test
    fun theEyeHidesAndShowsAgain() {
        show(startHidden = false)
        compose.onNodeWithTag("net-worth").assertTextEquals("£1,050.00")
        compose.onNodeWithTag("toggle-amounts").performClick()
        compose.onNodeWithTag("net-worth").assertTextEquals(HIDDEN_AMOUNT)
        compose.onNodeWithTag("toggle-amounts").performClick()
        compose.onNodeWithTag("net-worth").assertTextEquals("£1,050.00")
    }
}
