package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackPressCoordinatorTest {

    @Test
    fun `first press ever is never a double-press`() {
        assertFalse(BackPressCoordinator.isDoublePress(lastPressMillis = null, nowMillis = 1_000L))
    }

    @Test
    fun `second press within the window is a double-press`() {
        assertTrue(
            BackPressCoordinator.isDoublePress(
                lastPressMillis = 1_000L,
                nowMillis = 1_000L + BackPressCoordinator.DEFAULT_WINDOW_MILLIS,
                windowMillis = BackPressCoordinator.DEFAULT_WINDOW_MILLIS
            )
        )
    }

    @Test
    fun `second press just outside the window is treated as a new single press`() {
        assertFalse(
            BackPressCoordinator.isDoublePress(
                lastPressMillis = 1_000L,
                nowMillis = 1_000L + BackPressCoordinator.DEFAULT_WINDOW_MILLIS + 1,
                windowMillis = BackPressCoordinator.DEFAULT_WINDOW_MILLIS
            )
        )
    }

    @Test
    fun `a press earlier than the recorded last press is not a double-press`() {
        // Shouldn't happen with a real clock, but the function should stay false
        // rather than treat a negative delta as "instant double-press".
        assertFalse(BackPressCoordinator.isDoublePress(lastPressMillis = 1_000L, nowMillis = 900L))
    }
}
