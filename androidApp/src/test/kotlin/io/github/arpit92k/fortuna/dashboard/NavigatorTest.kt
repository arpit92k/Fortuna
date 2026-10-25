package io.github.arpit92k.fortuna.dashboard

import io.github.arpit92k.fortuna.core.domain.SourceId
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
        assertTrue(entersValue(Destination.ValueEntry(SourceId(6))))
        assertTrue(entersValue(Destination.Backup), "the file picker takes the user out of the app")
        assertFalse(entersValue(Destination.Dashboard))
        assertFalse(entersValue(Destination.Source(SourceId(6))))
    }

    // On a wide window, picking another source in the list beside the page replaces it.
    @Test
    fun anotherSourceReplacesTheOneShown() {
        val navigator = Navigator()
        navigator.open(Destination.Source(SourceId(6)))
        navigator.open(Destination.Source(SourceId(7)))
        assertEquals(listOf(Destination.Dashboard, Destination.Source(SourceId(7))), navigator.stack.value)
    }

    @Test
    fun aDeletedSourceTakesItsScreensWithIt() {
        val navigator = Navigator()
        navigator.open(Destination.Source(SourceId(6)))
        navigator.open(Destination.ValueEntry(SourceId(6)))
        navigator.forget(SourceId(6))
        assertEquals(listOf(Destination.Dashboard), navigator.stack.value)
    }
}
