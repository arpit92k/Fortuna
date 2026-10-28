package io.github.arpit92k.fortuna.setup

import org.junit.Test
import kotlin.test.assertEquals

/** S-04: the words typed to confirm the phrase. */
class ConfirmWordTest {
    @Test
    fun typingKeepsTheBoxAndASpaceFinishesIt() {
        assertEquals(ConfirmWordEdit("aba", finished = false), confirmWordEdit("Aba"))
        assertEquals(ConfirmWordEdit("abandon", finished = true), confirmWordEdit("abandon "))
    }

    @Test
    fun aSpaceAloneFinishesNothing() {
        assertEquals(ConfirmWordEdit("", finished = false), confirmWordEdit(" "))
        assertEquals(ConfirmWordEdit("", finished = false), confirmWordEdit(""))
    }

    @Test
    fun onlyTheFirstWordOfAPasteIsKept() {
        assertEquals(ConfirmWordEdit("abandon", finished = true), confirmWordEdit(" abandon ability able "))
    }
}
