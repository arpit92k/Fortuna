package io.github.arpit92k.fortuna.dashboard

import io.github.arpit92k.fortuna.platform.DisplayPreferences
import io.github.arpit92k.fortuna.ui.HIDDEN_AMOUNT
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmountsViewModelTest {
    private class SavedPreferences : DisplayPreferences {
        override var amountsHidden = false
    }

    @Test
    fun amountsAreShownToBeginWith() {
        assertFalse(AmountsViewModel(SavedPreferences()).hidden.value)
    }

    @Test
    fun theChoiceIsRememberedForTheNextLaunch() {
        val saved = SavedPreferences()
        AmountsViewModel(saved).toggle()
        assertTrue(saved.amountsHidden)
        val next = AmountsViewModel(saved)
        assertTrue(next.hidden.value, "the next launch starts hidden")
        next.toggle()
        assertFalse(next.hidden.value)
        assertFalse(saved.amountsHidden)
    }

    @Test
    fun hiddenAmountsAreTheSameWhateverTheirSize() {
        // Dots of one length, so not even the number of digits shows.
        assertEquals(6, HIDDEN_AMOUNT.length)
    }
}
