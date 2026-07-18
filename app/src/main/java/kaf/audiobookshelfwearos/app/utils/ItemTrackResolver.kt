package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem

/**
 * Pure decision logic behind BookListActivity's resolveItemWithTracks(): a
 * main-list LibraryItem can have empty media.tracks (the list endpoint isn't
 * "expanded"), which used to reach PlayerService.setAudiobook() ->
 * TrackPositionResolver.resolve() and crash on its "tracks must not be empty"
 * require(), or silently no-op a swipe-to-download. Fetch the expanded item
 * first when tracks are missing; skip the extra round-trip otherwise.
 */
object ItemTrackResolver {
    suspend fun resolveItemWithTracks(
        item: LibraryItem,
        fetchExpandedItem: suspend (String) -> LibraryItem?
    ): LibraryItem? {
        if (item.media.tracks.isNotEmpty()) return item
        return fetchExpandedItem(item.id)
    }
}
