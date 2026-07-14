package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.DownloadProgress
import kaf.audiobookshelfwearos.app.data.DownloadState
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import kaf.audiobookshelfwearos.app.data.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookProgressCalculatorTest {

    private fun libraryItemWithTracks(count: Int) = LibraryItem(
        id = "book-1",
        media = Media(tracks = List(count) { Track(index = it) })
    )

    private fun progress(
        trackId: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        speed: Long = 0L,
        percentComplete: Float = if (totalBytes > 0) (bytesDownloaded * 100f / totalBytes) else 0f
    ) = DownloadProgress(
        trackId = trackId,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        percentComplete = percentComplete,
        downloadSpeed = speed,
        estimatedTimeRemaining = 0L,
        state = DownloadState.DOWNLOADING
    )

    @Test
    fun `empty progress list returns zeroed-out result`() {
        val result = AudiobookProgressCalculator.calculateAudiobookProgress(
            libraryItemWithTracks(3),
            emptyList()
        )

        assertEquals(0f, result.overallProgress)
        assertEquals(0L, result.totalBytesDownloaded)
        assertEquals(0L, result.totalBytes)
        assertEquals(0L, result.averageDownloadSpeed)
        assertEquals(Long.MAX_VALUE, result.estimatedTimeRemaining)
    }

    @Test
    fun `overall progress is based on total bytes when available`() {
        val result = AudiobookProgressCalculator.calculateAudiobookProgress(
            libraryItemWithTracks(2),
            listOf(
                progress("t1", bytesDownloaded = 50, totalBytes = 100),
                progress("t2", bytesDownloaded = 25, totalBytes = 100),
            )
        )

        // 75 downloaded out of 200 total = 37.5%
        assertEquals(37.5f, result.overallProgress)
        assertEquals(75L, result.totalBytesDownloaded)
        assertEquals(200L, result.totalBytes)
    }

    @Test
    fun `falls back to track-count progress when total bytes is unknown`() {
        val result = AudiobookProgressCalculator.calculateAudiobookProgress(
            libraryItemWithTracks(4),
            listOf(
                progress("t1", bytesDownloaded = 0, totalBytes = 0, percentComplete = 100f),
                progress("t2", bytesDownloaded = 0, totalBytes = 0, percentComplete = 100f),
                progress("t3", bytesDownloaded = 0, totalBytes = 0, percentComplete = 40f),
                progress("t4", bytesDownloaded = 0, totalBytes = 0, percentComplete = 0f),
            )
        )

        // 2 out of 4 tracks fully complete = 50%
        assertEquals(50f, result.overallProgress)
    }

    @Test
    fun `average speed excludes stalled tracks and weights active ones by bytes downloaded`() {
        val result = AudiobookProgressCalculator.calculateAudiobookProgress(
            libraryItemWithTracks(3),
            listOf(
                // stalled track (speed must be > 1000 to count as "active"): excluded
                progress("stalled", bytesDownloaded = 10, totalBytes = 1000, speed = 500),
                progress("active-a", bytesDownloaded = 100, totalBytes = 1000, speed = 3000),
                progress("active-b", bytesDownloaded = 300, totalBytes = 1000, speed = 1500),
            )
        )

        // weighted = (3000*100 + 1500*300) / (100+300) = 750000 / 400 = 1875
        assertEquals(1875L, result.averageDownloadSpeed)
    }

    @Test
    fun `estimated time remaining applies a 10 percent buffer`() {
        val result = AudiobookProgressCalculator.calculateAudiobookProgress(
            libraryItemWithTracks(1),
            listOf(progress("t1", bytesDownloaded = 0, totalBytes = 7_700, speed = 1_100))
        )

        // remaining = 7700, speed = 1100 -> 7s, +10% buffer = 7.7s, truncated to 7s
        assertEquals(7L, result.estimatedTimeRemaining)
    }

    @Test
    fun `estimated time remaining is capped at 24 hours`() {
        val result = AudiobookProgressCalculator.calculateAudiobookProgress(
            libraryItemWithTracks(1),
            listOf(progress("t1", bytesDownloaded = 0, totalBytes = 1_000_000_000L, speed = 2_000))
        )

        assertEquals(86_400L, result.estimatedTimeRemaining)
    }

    @Test
    fun `no remaining bytes yields an unknown estimate rather than zero`() {
        val result = AudiobookProgressCalculator.calculateAudiobookProgress(
            libraryItemWithTracks(1),
            listOf(progress("t1", bytesDownloaded = 1000, totalBytes = 1000, speed = 5000))
        )

        assertEquals(Long.MAX_VALUE, result.estimatedTimeRemaining)
    }
}
