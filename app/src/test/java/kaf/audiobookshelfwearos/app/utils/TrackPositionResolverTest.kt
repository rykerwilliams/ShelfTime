package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPositionResolverTest {

    // Three tracks: [0, 100), [100, 250), [250, 400) — startOffset/duration in seconds.
    private val tracks = listOf(
        Track(index = 0, startOffset = 0.0, duration = 100.0),
        Track(index = 1, startOffset = 100.0, duration = 150.0),
        Track(index = 2, startOffset = 250.0, duration = 150.0),
    )

    @Test
    fun `position exactly at total duration resolves to the last track without throwing`() {
        val totalDuration = 400.0

        val result = TrackPositionResolver.resolve(tracks, totalDuration)

        assertEquals(2, result.trackIndex)
        assertTrue(result.trackIndex <= tracks.lastIndex)
    }

    @Test
    fun `position beyond total duration resolves to the last track without throwing`() {
        val result = TrackPositionResolver.resolve(tracks, 999.0)

        assertEquals(2, result.trackIndex)
        assertTrue(result.trackIndex <= tracks.lastIndex)
    }

    @Test
    fun `position exactly at a track boundary resolves to the start of the next track`() {
        // 100.0 is exactly the end of track 0 / start of track 1.
        val result = TrackPositionResolver.resolve(tracks, 100.0)

        assertEquals(1, result.trackIndex)
        assertEquals(0.0, result.trackLocalOffsetSeconds, 0.0001)
    }

    @Test
    fun `normal in-range position resolves to the containing track with correct local offset`() {
        // 175.0 seconds is 75 seconds into track 1 (which starts at 100.0).
        val result = TrackPositionResolver.resolve(tracks, 175.0)

        assertEquals(1, result.trackIndex)
        assertEquals(75.0, result.trackLocalOffsetSeconds, 0.0001)
    }

    @Test
    fun `rewind target 10s before the start of the last track resolves into the previous track`() {
        // Track 2 starts at 250.0; 10s before that is 240.0, which is still inside
        // track 1 ([100, 250)) — a rewind crossing this boundary must land there,
        // not be clamped to the start of track 2.
        val result = TrackPositionResolver.resolve(tracks, 240.0)

        assertEquals(1, result.trackIndex)
        assertEquals(140.0, result.trackLocalOffsetSeconds, 0.0001)
    }

    @Test
    fun `fast-forward target 10s past the end of the second-to-last track resolves into the last track`() {
        // Track 1 ([100, 250)) ends at 250.0; 10s past that is 260.0, which falls
        // inside track 2 ([250, 400)) — a fast-forward crossing this boundary must
        // land there instead of being truncated at the end of track 1.
        val result = TrackPositionResolver.resolve(tracks, 260.0)

        assertEquals(2, result.trackIndex)
        assertEquals(10.0, result.trackLocalOffsetSeconds, 0.0001)
    }
}
