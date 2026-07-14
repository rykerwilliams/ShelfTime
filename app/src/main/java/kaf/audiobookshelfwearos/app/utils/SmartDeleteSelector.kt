package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem

/**
 * Pure selection logic for Smart Delete, kept separate from the DB/download-index
 * I/O in SmartDeleteManager so the "never delete what's currently playing" and
 * "oldest first" rules can be unit tested directly.
 */
object SmartDeleteSelector {
    fun selectItemsToDelete(
        downloadedItems: List<Pair<LibraryItem, Long>>,
        currentlyPlayingItemId: String?,
        maxDownloads: Int
    ): List<LibraryItem> {
        val excessCount = downloadedItems.size - maxDownloads
        if (excessCount <= 0) return emptyList()

        return downloadedItems
            .sortedBy { it.second }
            .map { it.first }
            .filter { it.id != currentlyPlayingItemId }
            .take(excessCount)
    }
}
