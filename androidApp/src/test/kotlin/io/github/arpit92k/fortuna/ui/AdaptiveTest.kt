package io.github.arpit92k.fortuna.ui

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveTest {
    @Test
    fun widthClassesUseMaterialsBreakpoints() {
        assertEquals(WidthClass.COMPACT, WidthClass.of(411.dp))
        assertEquals(WidthClass.COMPACT, WidthClass.of(599.dp))
        assertEquals(WidthClass.MEDIUM, WidthClass.of(600.dp))
        assertEquals(WidthClass.MEDIUM, WidthClass.of(673.dp), "an unfolded foldable held upright")
        assertEquals(WidthClass.EXPANDED, WidthClass.of(840.dp))
        assertEquals(WidthClass.EXPANDED, WidthClass.of(1280.dp))
    }

    @Test
    fun twoPartScreensGoSideBySideWhereThereIsRoom() {
        assertFalse(sideBySide(411.dp, 891.dp), "a phone")
        assertFalse(sideBySide(673.dp, 841.dp), "an unfolded foldable: stacked, at a readable width")
        assertTrue(sideBySide(891.dp, 411.dp), "a phone on its side")
        assertTrue(sideBySide(720.dp, 360.dp), "a short medium window")
        assertTrue(sideBySide(1280.dp, 800.dp), "a tablet")
        assertTrue(sideBySide(841.dp, 673.dp), "an unfolded foldable on its side")
    }

    @Test
    fun aPinNeverGrowsPastTheLongestTheCoreAccepts() {
        val long = "1".repeat(64)
        assertEquals(long, typed(long, '2'))
        assertEquals("12", typed("1", '2'))
    }
}
