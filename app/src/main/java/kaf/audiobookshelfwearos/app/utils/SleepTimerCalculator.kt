package kaf.audiobookshelfwearos.app.utils

/**
 * Pure delay-computation seam for the "sleep at end of chapter" sleep-timer option
 * (see docs/UI_CHANGES_PLAN.md section 5). Deliberately no Context/Service/ExoPlayer
 * dependency, same seam-extraction pattern as ChapterResolver/TrackPositionResolver,
 * so it's unit-testable and so PlayerService's various recompute-and-reschedule call
 * sites (initial arm, onPositionDiscontinuity seeks, speed changes, pause/resume) can
 * all funnel through the same tested math instead of duplicating it inline.
 *
 * Wall-clock delay is playback-position delay divided by playback speed: at 2x speed
 * half as much wall-clock time elapses per second of chapter remaining, so the
 * wall-clock delay needed is halved; at 0.5x it doubles.
 */
object SleepTimerCalculator {

    fun secondsUntilChapterEnd(
        positionSeconds: Double,
        chapterEnd: Double,
        playbackSpeed: Float
    ): Double {
        val remainingPlaybackSeconds = chapterEnd - positionSeconds
        return remainingPlaybackSeconds / playbackSpeed
    }
}
