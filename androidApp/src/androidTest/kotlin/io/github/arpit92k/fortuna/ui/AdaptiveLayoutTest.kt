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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.arpit92k.fortuna.lock.LockScreen
import io.github.arpit92k.fortuna.lock.LockUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The lock screen at the sizes of a phone, an unfolded foldable, a tablet and
 * a phone on its side. Layout follows the space given, so a box of each size
 * stands in for each device.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    private companion object {
        const val FRAME = "frame"
    }

    @get:Rule
    val compose = createComposeRule()

    /** The bottom of the safe area: from Android 15 the app draws under the gesture bar, so the frame includes it. */
    private var bottomInset: Dp = 0.dp

    private fun showLockAt(width: Dp, height: Dp, ui: LockUi = LockUi()) {
        compose.setContent {
            FortunaTheme {
                bottomInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getBottom(this).toDp() }
                // Anchored at the top left, so a box larger than the emulator's screen is not shifted.
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(width, height).testTag(FRAME)) {
                    LockScreen(ui, onDigit = {}, onDelete = {}, onOk = {}, onForgotPin = {})
                }
            }
        }
    }

    @Test
    fun onAPhoneThePadIsBelowTheText() {
        showLockAt(411.dp, 891.dp)
        compose.onNodeWithTag(STACKED).assertExists()
        compose.onNodeWithTag(SIDE_BY_SIDE).assertDoesNotExist()
    }

    // The pad sits in the middle of the screen, not against its left edge.
    @Test
    fun onAPhoneThePadIsCentred() {
        showLockAt(411.dp, 891.dp)
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val pad = compose.onNodeWithTag(PIN_PAD).getBoundsInRoot()
        val padMiddle = (pad.left + pad.right) / 2
        val frameMiddle = (frame.left + frame.right) / 2
        assertTrue(abs((padMiddle - frameMiddle).value) <= 1f, "the pad's middle is at $padMiddle, the screen's at $frameMiddle")
    }

    // Within reach of the thumbs, with only the screen's margin below it.
    @Test
    fun onAPhoneThePadIsAtTheBottom() {
        showLockAt(411.dp, 891.dp)
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val pad = compose.onNodeWithTag(PIN_PAD).getBoundsInRoot()
        val gap = frame.bottom - bottomInset - pad.bottom
        assertTrue(gap <= 48.dp, "the pad ends $gap above the bottom of the safe area")
    }

    @Test
    fun onAnUnfoldedFoldableTheScreenKeepsAReadableWidth() {
        showLockAt(673.dp, 841.dp)
        val column = compose.onNodeWithTag(STACKED).getBoundsInRoot()
        assertTrue(column.right - column.left <= ReadableWidth, "the column stops at $ReadableWidth")
    }

    @Test
    fun onATabletThePadIsBesideTheText() {
        showLockAt(1280.dp, 800.dp)
        compose.onNodeWithTag(SIDE_BY_SIDE).assertExists()
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val pad = compose.onNodeWithTag(PIN_PAD).getBoundsInRoot()
        assertTrue(pad.left > frame.left + (frame.right - frame.left) / 2 - 1.dp, "the pad is on the right half")
    }

    @Test
    fun onAPhoneOnItsSideThePadStillFits() {
        showLockAt(891.dp, 411.dp)
        compose.onNodeWithTag(SIDE_BY_SIDE).assertExists()
        val frame = compose.onNodeWithTag(FRAME).getBoundsInRoot()
        val pad = compose.onNodeWithTag(PIN_PAD).getBoundsInRoot()
        assertTrue(pad.bottom <= frame.bottom, "the pad ends at ${pad.bottom}, inside ${frame.bottom}")
    }

    @Test
    fun thePadIsOffWhileWaiting() {
        showLockAt(411.dp, 891.dp, LockUi(wait = kotlin.time.Duration.parse("1m")))
        compose.onNodeWithTag("${PIN_KEY}5").assertIsNotEnabled()
        compose.onNodeWithTag("lock-wait").assertExists()
    }

    @Test
    fun okWaitsForSixDigits() {
        showLockAt(411.dp, 891.dp, LockUi(entered = "12345"))
        compose.onNodeWithTag(PIN_OK).assertIsNotEnabled()
        compose.onNodeWithTag("${PIN_KEY}5").assertIsEnabled()
    }
}
