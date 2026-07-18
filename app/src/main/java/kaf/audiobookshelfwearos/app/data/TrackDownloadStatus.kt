package kaf.audiobookshelfwearos.app.data

/**
 * The two booleans callers actually need about a track's download row, derived from
 * a single [DownloadState] read.
 *
 * Extracted so a single `downloadIndex.getDownload(id)` lookup (see
 * `MyDownloadService.getDownloadStatus`) can be checked twice in memory instead of
 * issuing two separate SQLite reads for the same row (the old
 * `Track.isDownloaded()` + `Track.isDownloading()` pattern) - and so the mapping
 * itself is a pure function that can be unit tested without a Context or
 * DownloadManager.
 */
data class TrackDownloadStatus(
    val isDownloaded: Boolean,
    val isDownloading: Boolean
) {
    companion object {
        val NOT_DOWNLOADED = TrackDownloadStatus(isDownloaded = false, isDownloading = false)

        fun fromState(state: DownloadState?): TrackDownloadStatus {
            if (state == null) return NOT_DOWNLOADED
            return TrackDownloadStatus(
                isDownloaded = state == DownloadState.COMPLETED,
                isDownloading = state == DownloadState.DOWNLOADING
            )
        }
    }
}
