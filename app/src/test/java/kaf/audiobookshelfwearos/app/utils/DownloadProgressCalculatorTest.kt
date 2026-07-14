package kaf.audiobookshelfwearos.app.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressCalculatorTest {

    private val trackId = "track-under-test"

    @After
    fun tearDown() {
        // These calculators keep per-track state in static maps; reset between tests.
        DownloadProgressCalculator.clearSpeedHistory(trackId)
    }

    @Test
    fun `first sample with only one data point returns zero speed`() {
        val speed = DownloadProgressCalculator.calculateDownloadSpeed(
            trackId,
            bytesDownloaded = 1000,
            currentTime = 0L
        )

        assertEquals(0L, speed)
    }

    @Test
    fun `speed is computed from bytes over time once there are two samples`() {
        DownloadProgressCalculator.calculateDownloadSpeed(trackId, bytesDownloaded = 100, currentTime = 0L)
        val speed = DownloadProgressCalculator.calculateDownloadSpeed(
            trackId,
            bytesDownloaded = 10_100,
            currentTime = 1_000L // 1 second later
        )

        // 10,000 bytes over 1s = 10,000 B/s. With no prior smoothed value, the
        // smoothed result equals the instant speed exactly on this first computation.
        assertEquals(10_000L, speed)
    }

    @Test
    fun `smoothing pulls a sudden slowdown toward, but not all the way to, the new rate`() {
        DownloadProgressCalculator.calculateDownloadSpeed(trackId, bytesDownloaded = 100, currentTime = 0L)
        val fastSpeed = DownloadProgressCalculator.calculateDownloadSpeed(
            trackId, bytesDownloaded = 20_100, currentTime = 1_000L // 20,000 B/s
        )
        assertEquals(20_000L, fastSpeed)

        // Download slows down sharply on the next sample.
        val afterSlowdown = DownloadProgressCalculator.calculateDownloadSpeed(
            trackId, bytesDownloaded = 20_200, currentTime = 2_000L
        )

        assertTrue("expected $afterSlowdown to be pulled down from $fastSpeed", afterSlowdown < fastSpeed)
    }

    @Test
    fun `after a long gap trims history below two samples, the last smoothed speed is still returned`() {
        DownloadProgressCalculator.calculateDownloadSpeed(trackId, bytesDownloaded = 100, currentTime = 0L)
        DownloadProgressCalculator.calculateDownloadSpeed(trackId, bytesDownloaded = 10_100, currentTime = 1_000L)

        // More than 30s later: both prior samples get trimmed from history (leaving
        // size < 2 again), so the calculator falls back to the cached smoothed value
        // instead of resetting to 0.
        val speed = DownloadProgressCalculator.calculateDownloadSpeed(
            trackId,
            bytesDownloaded = 15_000,
            currentTime = 32_000L
        )

        assertEquals(10_000L, speed)
    }

    @Test
    fun `clearSpeedHistory resets state for a track`() {
        DownloadProgressCalculator.calculateDownloadSpeed(trackId, bytesDownloaded = 100, currentTime = 0L)
        DownloadProgressCalculator.calculateDownloadSpeed(trackId, bytesDownloaded = 10_100, currentTime = 1_000L)

        DownloadProgressCalculator.clearSpeedHistory(trackId)

        val speed = DownloadProgressCalculator.calculateDownloadSpeed(
            trackId,
            bytesDownloaded = 999,
            currentTime = 2_000L
        )
        assertEquals(0L, speed)
    }

    @Test
    fun `estimated time is unknown when speed is too low to be meaningful`() {
        assertEquals(
            Long.MAX_VALUE,
            DownloadProgressCalculator.calculateEstimatedTime(remainingBytes = 10_000, downloadSpeed = 500)
        )
    }

    @Test
    fun `estimated time is computed and capped at 24 hours`() {
        val result = DownloadProgressCalculator.calculateEstimatedTime(
            remainingBytes = 1_000_000_000L,
            downloadSpeed = 2_000
        )
        assertEquals(86_400L, result)
    }

    @Test
    fun `estimated time has a minimum of 1 second`() {
        val result = DownloadProgressCalculator.calculateEstimatedTime(
            remainingBytes = 1,
            downloadSpeed = 10_000
        )
        assertEquals(1L, result)
    }

    @Test
    fun `formatTime renders seconds, minutes, and hours`() {
        assertEquals("Unknown", DownloadProgressCalculator.formatTime(Long.MAX_VALUE))
        assertEquals("45s", DownloadProgressCalculator.formatTime(45))
        assertEquals("2m", DownloadProgressCalculator.formatTime(120))
        assertEquals("2m 5s", DownloadProgressCalculator.formatTime(125))
        assertEquals("1h", DownloadProgressCalculator.formatTime(3600))
        assertEquals("1h 1m", DownloadProgressCalculator.formatTime(3660))
    }

    @Test
    fun `formatBytes scales to the appropriate unit`() {
        assertEquals("512.0 B", DownloadProgressCalculator.formatBytes(512))
        assertEquals("1.0 KB", DownloadProgressCalculator.formatBytes(1024))
        assertEquals("1.0 MB", DownloadProgressCalculator.formatBytes(1024 * 1024))
        assertEquals("1.0 GB", DownloadProgressCalculator.formatBytes(1024L * 1024 * 1024))
    }
}
