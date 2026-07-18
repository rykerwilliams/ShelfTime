package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.Chapter

/**
 * Resolves an absolute position within an audiobook (seconds from the start of the
 * whole book, same unit PlayerService.updateUIMetadata() already computes as
 * `getCurrentTotalPositionInS() + START_OFFSET_SECONDS + 1`) down to the title of the
 * chapter that position currently falls in.
 *
 * Deliberately pure/no Context or Service dependency (same seam-extraction pattern as
 * TrackPositionResolver/PlayerMediaSourceBuilder) so it's unit-testable and safe to call
 * every tick from PlayerActivity's existing position poll loop, not just from the
 * discrete ExoPlayer listener callbacks (onMediaItemTransition/onMediaMetadataChanged/
 * first STATE_READY) that PlayerService.updateUIMetadata() was previously limited to.
 * Those callbacks never fire again mid-book for a single-file (e.g. M4B) audiobook with
 * embedded chapters, which is what left the chapter title frozen after the first one.
 *
 * Chapter boundaries are start-inclusive, end-exclusive, matching the loop this was
 * extracted from. If no chapter contains the position (before the first chapter's
 * start, or after the last chapter's end), `currentChapterTitle` returns the
 * empty-title default — the same fallback `Chapter()` already produced — while
 * `currentChapter` returns `null`, since callers of that function need to be able to
 * tell "no chapter here" apart from "chapter with an empty title".
 */
object ChapterResolver {

    fun currentChapterTitle(positionSeconds: Double, chapters: List<Chapter>): String {
        return currentChapter(positionSeconds, chapters)?.title ?: Chapter().title
    }

    fun currentChapter(positionSeconds: Double, chapters: List<Chapter>): Chapter? {
        var currentChapter: Chapter? = null
        for (chapter in chapters) {
            if (positionSeconds >= chapter.start && positionSeconds < chapter.end) {
                currentChapter = chapter
            }
        }
        return currentChapter
    }
}
