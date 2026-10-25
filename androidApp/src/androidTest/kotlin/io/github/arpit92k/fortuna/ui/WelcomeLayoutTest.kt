package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.arpit92k.fortuna.setup.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The welcome screen at the size of a phone and of a tablet: the picture
 * leads, and the button is never left alone away from it.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeLayoutTest {
    private companion object {
        const val FRAME = "frame"
        const val GET_STARTED = "get-started"
        const val RESTORE = "restore"
    }

    @get:Rule
    val compose = createComposeRule()

    /** The bottom of the safe area: from Android 15 the app draws under the gesture bar, so the frame includes it. */
    private var bottomInset: Dp = 0.dp

    private fun showWelcomeAt(width: Dp, height: Dp) {
        compose.setContent {
            FortunaTheme {
                bottomInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getBottom(this).toDp() }
                // Anchored at the top left, so a box larger than the emulator's screen is not shifted.
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(width, height).testTag(FRAME)) {
                    WelcomeScreen(onGetStarted = {}, onRestore = {})
                }
            }
        }
    }

    @Test
    fun onAPhoneThePictureIsAtTheTopAndTheButtonsAtTheBottom() {
        showWelcomeAt(411.dp, 891.dp)
        compose.onNodeWithTag(STACKED).assertExists()
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val hero = compose.onNodeWithTag(WELCOME_HERO).getBoundsInRoot()
        val button = compose.onNodeWithTag(GET_STARTED).getBoundsInRoot()
        val restore = compose.onNodeWithTag(RESTORE).getBoundsInRoot()
        assertTrue(hero.top < frame.top + 96.dp, "the picture starts ${hero.top - frame.top} below the top")
        val gap = frame.bottom - bottomInset - restore.bottom
        assertTrue(gap <= 48.dp, "the buttons end $gap above the bottom of the safe area")
        assertTrue(button.top > hero.bottom, "the buttons are below the picture")
        assertTrue(restore.top >= button.bottom, "a restore comes after a new start")
    }

    @Test
    fun onAPhoneThePictureIsCentred() {
        showWelcomeAt(411.dp, 891.dp)
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val hero = compose.onNodeWithTag(WELCOME_HERO).getBoundsInRoot()
        val heroMiddle = (hero.left + hero.right) / 2
        val frameMiddle = (frame.left + frame.right) / 2
        assertTrue(abs((heroMiddle - frameMiddle).value) <= 1f, "the picture's middle is at $heroMiddle, the screen's at $frameMiddle")
    }

    // Still centred where the column is much wider than the picture.
    @Test
    fun onAnUnfoldedFoldableThePictureIsCentred() {
        showWelcomeAt(673.dp, 841.dp)
        compose.onNodeWithTag(STACKED).assertExists()
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val hero = compose.onNodeWithTag(WELCOME_HERO).getBoundsInRoot()
        val heroMiddle = (hero.left + hero.right) / 2
        val frameMiddle = (frame.left + frame.right) / 2
        assertTrue(abs((heroMiddle - frameMiddle).value) <= 1f, "the picture's middle is at $heroMiddle, the screen's at $frameMiddle")
    }

    // The button sits under the picture, as wide as it, not alone across the screen.
    @Test
    fun onATabletTheButtonIsUnderThePicture() {
        showWelcomeAt(1280.dp, 800.dp)
        compose.onNodeWithTag(SIDE_BY_SIDE).assertExists()
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val hero = compose.onNodeWithTag(WELCOME_HERO).getBoundsInRoot()
        val button = compose.onNodeWithTag(GET_STARTED).getBoundsInRoot()
        assertTrue(hero.left > frame.left + (frame.right - frame.left) / 2 - 1.dp, "the picture is on the right half")
        assertTrue(button.top >= hero.bottom, "the button is below the picture")
        assertTrue(abs((button.left - hero.left).value) <= 1f, "the button starts where the picture does")
        assertTrue(abs((button.right - hero.right).value) <= 1f, "the button ends where the picture does")
        val restore = compose.onNodeWithTag(RESTORE).getBoundsInRoot()
        assertTrue(restore.top >= button.bottom, "a restore comes after a new start")
        assertTrue(abs((restore.right - hero.right).value) <= 1f, "the restore button is as wide as the picture")
    }
}
