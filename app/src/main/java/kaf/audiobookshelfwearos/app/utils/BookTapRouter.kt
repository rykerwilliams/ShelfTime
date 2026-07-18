package kaf.audiobookshelfwearos.app.utils

/**
 * Pure routing decision for BookListActivity's book-tap handler (UI_CHANGES_PLAN.md
 * section 1): whether tapping a book in the main list should skip the chapter/detail
 * screen and jump straight into playback.
 *
 * The decision only depends on whether *anything* is currently playing, not on which
 * book was tapped or which book is playing -- both "tapped the currently-playing book"
 * and "a different book is playing" keep the existing chapter-list navigation, so they
 * collapse to the same `false` result here.
 */
object BookTapRouter {
    /**
     * @param currentlyPlayingItemId [kaf.audiobookshelfwearos.app.services.PlayerService.currentlyPlayingItemId],
     * i.e. the id of the LibraryItem currently loaded for playback, or null if nothing is playing.
     * @return true if nothing is currently playing, so the tapped book should jump straight
     * into playback instead of opening the chapter/detail screen.
     */
    fun shouldJumpStraightToPlayback(currentlyPlayingItemId: String?): Boolean {
        return currentlyPlayingItemId == null
    }
}
