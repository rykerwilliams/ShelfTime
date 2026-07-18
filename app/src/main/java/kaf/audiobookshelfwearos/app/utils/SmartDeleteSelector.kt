package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem

/**
 * Pure selection logic for Smart Delete, kept separate from the DB/download-index
 * I/O in SmartDeleteManager so the "never delete what's currently playing", "oldest
 * first", and byte-budget rules can be unit tested directly.
 */
object SmartDeleteSelector {
    /**
     * Walks the oldest-first, currently-playing-excluded candidate list, deleting
     * items until BOTH the remaining item count is within [maxDownloads] AND the
     * remaining total byte size (summed from [LibraryItem.media]'s `size`) is within
     * [maxTotalBytes]. Either limit being exceeded triggers deletions; deletions
     * continue (oldest first) until both are satisfied simultaneously.
     */
    fun selectItemsToDelete(
        downloadedItems: List<Pair<LibraryItem, Long>>,
        currentlyPlayingItemId: String?,
        maxDownloads: Int,
        maxTotalBytes: Long
    ): List<LibraryItem> {
        val candidates = downloadedItems
            .sortedBy { it.second }
            .map { it.first }
            .filter { it.id != currentlyPlayingItemId }

        // Deliberately counted from the FULL downloadedItems list, not from
        // `candidates`: the currently-playing item still occupies storage and should
        // still count toward the budget, even though it can never itself be selected
        // for deletion. Computing these from `candidates` instead would silently let
        // the effective limit grow to maxDownloads+1 (or maxTotalBytes plus its size)
        // for as long as something is playing.
        var remainingCount = downloadedItems.size
        var remainingBytes = downloadedItems.sumOf { it.first.media.size }

        val toDelete = mutableListOf<LibraryItem>()
        for (item in candidates) {
            if (remainingCount <= maxDownloads && remainingBytes <= maxTotalBytes) break
            toDelete.add(item)
            remainingCount -= 1
            remainingBytes -= item.media.size
        }

        return toDelete
    }
}
