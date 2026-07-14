package kaf.audiobookshelfwearos.app.utils

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DebouncerTest {

    @Test
    fun `rapid triggers within the delay window collapse into a single run`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val debouncer = Debouncer(scope, delayMs = 3_000L)
        var runCount = 0

        // Simulate 5 tracks of one audiobook completing 500ms apart, as
        // MyDownloadService's onDownloadChanged would trigger per track.
        repeat(5) {
            debouncer.trigger { runCount++ }
            advanceTimeBy(500L)
        }

        // Still within 3s of the last trigger: nothing should have run yet.
        assertEquals(0, runCount)

        advanceUntilIdle()

        // Only the last trigger's action actually executes.
        assertEquals(1, runCount)
    }

    @Test
    fun `triggers spaced further apart than the delay each run independently`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val debouncer = Debouncer(scope, delayMs = 3_000L)
        var runCount = 0

        debouncer.trigger { runCount++ }
        advanceTimeBy(3_500L)
        assertEquals(1, runCount)

        debouncer.trigger { runCount++ }
        advanceUntilIdle()
        assertEquals(2, runCount)
    }
}
