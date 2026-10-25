package io.github.arpit92k.fortuna.dashboard

import io.github.arpit92k.fortuna.navigation.Destination
import io.github.arpit92k.fortuna.navigation.Navigator
import io.github.arpit92k.fortuna.navigation.entersValue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {
    @Test
    fun screensStackOnTheDashboard() {
        val navigator = Navigator()
        assertEquals(Destination.Dashboard, navigator.current)
        assertFalse(navigator.back(), "back on the dashboard leaves the app")

        navigator.open(Destination.AddSource)
        navigator.open(Destination.AddSource)
        assertEquals(listOf(Destination.Dashboard, Destination.AddSource), navigator.stack.value, "opening the same screen twice adds it once")
        assertTrue(navigator.back())
        assertEquals(Destination.Dashboard, navigator.current)
    }

    @Test
    fun onlyScreensWhereAValueIsEnteredEarnTheLongerGrace() {
        assertTrue(entersValue(Destination.AddSource))
        assertFalse(entersValue(Destination.Dashboard))
    }
}
