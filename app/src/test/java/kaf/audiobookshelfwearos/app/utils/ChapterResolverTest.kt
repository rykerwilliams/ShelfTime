package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterResolverTest {

    // Three chapters spanning [0, 100), [100, 250), [250, 400) — start/end in seconds,
    // matching the absolute-book-time unit PlayerService.updateUIMetadata() passes in.
    private val chapters = listOf(
        Chapter(id = 0, start = 0.0, end = 100.0, title = "Chapter 1"),
        Chapter(id = 1, start = 100.0, end = 250.0, title = "Chapter 2"),
        Chapter(id = 2, start = 250.0, end = 400.0, title = "Chapter 3"),
    )

    @Test
    fun `position at the very start of the first chapter resolves to it (start inclusive)`() {
        val title = ChapterResolver.currentChapterTitle(0.0, chapters)

        assertEquals("Chapter 1", title)
    }

    @Test
    fun `position in the middle of a chapter resolves to it`() {
        val title = ChapterResolver.currentChapterTitle(175.0, chapters)

        assertEquals("Chapter 2", title)
    }

    @Test
    fun `position exactly at a chapter boundary resolves to the next chapter (end exclusive)`() {
        // 100.0 is exactly the end of chapter 1 / start of chapter 2 — the boundary
        // must belong to chapter 2, not linger on chapter 1.
        val title = ChapterResolver.currentChapterTitle(100.0, chapters)

        assertEquals("Chapter 2", title)
    }

    @Test
    fun `position exactly at the last chapter boundary resolves to the last chapter`() {
        val title = ChapterResolver.currentChapterTitle(250.0, chapters)

        assertEquals("Chapter 3", title)
    }

    @Test
    fun `position before the first chapter's start resolves to no chapter`() {
        val title = ChapterResolver.currentChapterTitle(-5.0, chapters)

        assertEquals("", title)
    }

    @Test
    fun `position at or after the last chapter's end resolves to no chapter`() {
        val title = ChapterResolver.currentChapterTitle(400.0, chapters)

        assertEquals("", title)
    }

    @Test
    fun `position well past the last chapter's end resolves to no chapter`() {
        val title = ChapterResolver.currentChapterTitle(999.0, chapters)

        assertEquals("", title)
    }

    @Test
    fun `multi-track book -- chapter resolution is independent of track boundaries`() {
        // Chapters are keyed on absolute book time, same as TrackPositionResolver's
        // absolutePositionSeconds. Reuse the same three-track layout used elsewhere
        // (tracks at [0,100)/[100,250)/[250,400)) to confirm that a position deep in
        // track 2 (e.g. 260.0, which TrackPositionResolverTest resolves into track
        // index 2 at local offset 10.0) still resolves to the correct chapter here,
        // regardless of how many tracks the book is split into -- this function never
        // looks at track boundaries at all, only chapter start/end.
        val positionInsideLastTrack = 260.0

        val title = ChapterResolver.currentChapterTitle(positionInsideLastTrack, chapters)

        assertEquals("Chapter 3", title)
    }

    // currentChapter() -- same boundary semantics as currentChapterTitle(), but returns
    // the full Chapter (or null) instead of a title-with-empty-string-fallback, so
    // callers can distinguish "no chapter here" from "chapter with an empty title".

    @Test
    fun `currentChapter -- position mid-chapter resolves to that chapter`() {
        val chapter = ChapterResolver.currentChapter(175.0, chapters)

        assertEquals(chapters[1], chapter)
    }

    @Test
    fun `currentChapter -- position at a chapter's exact start resolves to it (start inclusive)`() {
        val chapter = ChapterResolver.currentChapter(100.0, chapters)

        assertEquals(chapters[1], chapter)
    }

    @Test
    fun `currentChapter -- position at a chapter's exact end resolves to the next chapter (end exclusive)`() {
        // 250.0 is exactly the end of chapter 2 / start of chapter 3 -- must resolve to
        // chapter 3, not chapter 2.
        val chapter = ChapterResolver.currentChapter(250.0, chapters)

        assertEquals(chapters[2], chapter)
    }

    @Test
    fun `currentChapter -- position before the first chapter's start resolves to null`() {
        val chapter = ChapterResolver.currentChapter(-5.0, chapters)

        assertNull(chapter)
    }

    @Test
    fun `currentChapter -- position at or after the last chapter's end resolves to null`() {
        val chapter = ChapterResolver.currentChapter(400.0, chapters)

        assertNull(chapter)
    }

    @Test
    fun `currentChapter -- position well past the last chapter's end resolves to null`() {
        val chapter = ChapterResolver.currentChapter(999.0, chapters)

        assertNull(chapter)
    }
}
