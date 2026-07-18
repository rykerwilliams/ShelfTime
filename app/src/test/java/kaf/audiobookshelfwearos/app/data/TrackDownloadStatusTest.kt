package kaf.audiobookshelfwearos.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrackDownloadStatusTest {

    @Test
    fun `null state (no download row) maps to neither downloaded nor downloading`() {
        val status = TrackDownloadStatus.fromState(null)

        assertFalse(status.isDownloaded)
        assertFalse(status.isDownloading)
        assertEquals(TrackDownloadStatus.NOT_DOWNLOADED, status)
    }

    @Test
    fun `COMPLETED maps to downloaded and not downloading`() {
        val status = TrackDownloadStatus.fromState(DownloadState.COMPLETED)

        assertEquals(TrackDownloadStatus(isDownloaded = true, isDownloading = false), status)
    }

    @Test
    fun `DOWNLOADING maps to downloading and not downloaded`() {
        val status = TrackDownloadStatus.fromState(DownloadState.DOWNLOADING)

        assertEquals(TrackDownloadStatus(isDownloaded = false, isDownloading = true), status)
    }

    @Test
    fun `QUEUED maps to neither downloaded nor downloading`() {
        val status = TrackDownloadStatus.fromState(DownloadState.QUEUED)

        assertEquals(TrackDownloadStatus.NOT_DOWNLOADED, status)
    }

    @Test
    fun `FAILED maps to neither downloaded nor downloading`() {
        val status = TrackDownloadStatus.fromState(DownloadState.FAILED)

        assertEquals(TrackDownloadStatus.NOT_DOWNLOADED, status)
    }

    @Test
    fun `CANCELLED maps to neither downloaded nor downloading`() {
        val status = TrackDownloadStatus.fromState(DownloadState.CANCELLED)

        assertEquals(TrackDownloadStatus.NOT_DOWNLOADED, status)
    }

    @Test
    fun `PAUSED maps to neither downloaded nor downloading`() {
        // PAUSED currently has no producer in MyDownloadService.mapDownloadState (Media3's
        // Download.STATE_* values all map to QUEUED/DOWNLOADING/COMPLETED/FAILED/CANCELLED),
        // but the mapping here is defined for every DownloadState value regardless.
        val status = TrackDownloadStatus.fromState(DownloadState.PAUSED)

        assertEquals(TrackDownloadStatus.NOT_DOWNLOADED, status)
    }
}
