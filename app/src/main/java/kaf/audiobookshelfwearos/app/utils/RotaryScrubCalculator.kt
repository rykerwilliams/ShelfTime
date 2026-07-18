package kaf.audiobookshelfwearos.app.utils

/**
 * Pure rotary-delta -> seek-seconds mapping for the Player screen's bezel-scrub mode
 * (see docs/UI_CHANGES_PLAN.md section 7). Deliberately no Context/Compose/
 * RotaryScrollEvent dependency, same seam-extraction pattern as ChapterResolver/
 * TrackPositionResolver/SleepTimerCalculator, so the sensitivity mapping is
 * unit-testable and PlaybackControls' `rotaryScrollable` handler can funnel every
 * scroll-delta callback through the same tested math instead of computing it inline.
 *
 * Wear Compose's rotary scroll events report `verticalScrollPixels` -- a raw pixel
 * delta, positive for one rotation direction and negative for the other, already
 * scaled by the platform's `ViewConfiguration.scaledVerticalScrollFactor` so it's
 * roughly comparable across devices. A single detent/notch on hardware that has
 * discrete detents (e.g. Galaxy Watch's physical bezel) typically reports on the
 * order of tens of pixels per notch; [SECONDS_PER_PIXEL] is picked so that a ~50px
 * notch maps to about 5 seconds of seek -- coarse enough to be useful for scrubbing
 * through a chapter, fine enough that a single notch doesn't blow past a short
 * chapter boundary.
 */
object RotaryScrubCalculator {

    const val SECONDS_PER_PIXEL: Double = 0.1

    /**
     * @param delta raw rotary scroll delta (pixels) from a single scroll callback;
     * sign indicates direction (positive = forward/fast-forward, negative =
     * backward/rewind, matching this project's existing `seekRelativeSeconds`
     * convention in `PlayerService`).
     * @return seek delta in seconds, same sign as [delta].
     */
    fun secondsForRotaryDelta(delta: Float): Double {
        return delta * SECONDS_PER_PIXEL
    }
}
