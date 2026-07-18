package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryScrubCalculatorTest {

    private val DELTA = 0.0001

    @Test
    fun `positive delta maps to a positive (fast-forward) seek`() {
        val seconds = RotaryScrubCalculator.secondsForRotaryDelta(50f)

        assertEquals(5.0, seconds, DELTA)
    }

    @Test
    fun `negative delta maps to a negative (rewind) seek`() {
        val seconds = RotaryScrubCalculator.secondsForRotaryDelta(-50f)

        assertEquals(-5.0, seconds, DELTA)
    }

    @Test
    fun `zero delta maps to zero seek`() {
        val seconds = RotaryScrubCalculator.secondsForRotaryDelta(0f)

        assertEquals(0.0, seconds, DELTA)
    }

    @Test
    fun `mapping is linear -- doubling the delta doubles the seconds`() {
        val small = RotaryScrubCalculator.secondsForRotaryDelta(20f)
        val doubled = RotaryScrubCalculator.secondsForRotaryDelta(40f)

        assertEquals(small * 2, doubled, DELTA)
    }

    @Test
    fun `a small single-notch delta stays within a fraction of a second per pixel`() {
        val seconds = RotaryScrubCalculator.secondsForRotaryDelta(1f)

        assertEquals(RotaryScrubCalculator.SECONDS_PER_PIXEL, seconds, DELTA)
    }
}
