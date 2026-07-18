package kaf.audiobookshelfwearos.app.utils

/**
 * Pure routing decision for BookListActivity's book-tap handler (UI_CHANGES_PLAN.md
 * section 1): whether tapping a book in the main list should skip the chapter/detail
 * screen and jump straight into playback.
 *
 * Besides the user-configurable [tapToPlayEnabled] setting, the decision only depends
 * on whether *anything* is currently playing, not on which book was tapped or which
 * book is playing -- both "tapped the currently-playing book" and "a different book
 * is playing" keep the existing chapter-list navigation, so they collapse to the same
 * `false` result here.
 */
object BookTapRouter {
    /**
     * @param currentlyPlayingItemId [kaf.audiobookshelfwearos.app.services.PlayerService.currentlyPlayingItemId],
     * i.e. the id of the LibraryItem currently loaded for playback, or null if nothing is playing.
     * @param tapToPlayEnabled [kaf.audiobookshelfwearos.app.userdata.UserDataManager.tapToPlayEnabled],
     * the Settings toggle for this behavior -- when false, tapping always opens the
     * chapter/detail screen regardless of playback state.
     * @return true if the setting is on and nothing is currently playing, so the tapped
     * book should jump straight into playback instead of opening the chapter/detail screen.
     */
    fun shouldJumpStraightToPlayback(currentlyPlayingItemId: String?, tapToPlayEnabled: Boolean): Boolean {
        return tapToPlayEnabled && currentlyPlayingItemId == null
    }
}
