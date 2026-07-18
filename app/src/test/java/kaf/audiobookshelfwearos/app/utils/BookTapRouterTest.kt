package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers UI_CHANGES_PLAN.md section 1's routing decision: BookListActivity's book-tap
 * handler should only skip straight to playback (bypassing ChapterListActivity) when
 * nothing is currently playing at all. Whether the tapped book matches the
 * currently-playing item doesn't change the result -- both "same book" and "different
 * book" cases keep the existing chapter-list navigation.
 */
class BookTapRouterTest {

    @Test
    fun `nothing currently playing means jump straight to playback`() {
        assertTrue(BookTapRouter.shouldJumpStraightToPlayback(currentlyPlayingItemId = null))
    }

    @Test
    fun `something currently playing means open chapter list instead`() {
        assertFalse(BookTapRouter.shouldJumpStraightToPlayback(currentlyPlayingItemId = "some-item-id"))
    }
}
