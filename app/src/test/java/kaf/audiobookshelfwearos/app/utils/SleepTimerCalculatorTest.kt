package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerCalculatorTest {

    private val DELTA = 0.0001

    @Test
    fun `normal mid-chapter position at 1x speed returns the raw remaining seconds`() {
        val seconds = SleepTimerCalculator.secondsUntilChapterEnd(
            positionSeconds = 100.0,
            chapterEnd = 250.0,
            playbackSpeed = 1.0f
        )

        assertEquals(150.0, seconds, DELTA)
    }

    @Test
    fun `position very close to chapter end returns a small positive delay`() {
        val seconds = SleepTimerCalculator.secondsUntilChapterEnd(
            positionSeconds = 249.5,
            chapterEnd = 250.0,
            playbackSpeed = 1.0f
        )

        assertEquals(0.5, seconds, DELTA)
    }

    @Test
    fun `double speed halves the wall-clock delay needed`() {
        val seconds = SleepTimerCalculator.secondsUntilChapterEnd(
            positionSeconds = 100.0,
            chapterEnd = 250.0,
            playbackSpeed = 2.0f
        )

        assertEquals(75.0, seconds, DELTA)
    }

    @Test
    fun `half speed doubles the wall-clock delay needed`() {
        val seconds = SleepTimerCalculator.secondsUntilChapterEnd(
            positionSeconds = 100.0,
            chapterEnd = 250.0,
            playbackSpeed = 0.5f
        )

        assertEquals(300.0, seconds, DELTA)
    }

    @Test
    fun `position already at chapter end returns zero`() {
        val seconds = SleepTimerCalculator.secondsUntilChapterEnd(
            positionSeconds = 250.0,
            chapterEnd = 250.0,
            playbackSpeed = 1.0f
        )

        assertEquals(0.0, seconds, DELTA)
    }
}
