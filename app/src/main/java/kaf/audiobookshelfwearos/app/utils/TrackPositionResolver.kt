package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.Track

/**
 * Resolves an absolute position within an audiobook (seconds from the start of the
 * whole book) down to (trackIndex, trackLocalOffsetSeconds) — which track that
 * position falls in, and how far into that track it is.
 *
 * Deliberately pure/no Context dependency (same pattern as PlayerMediaSourceBuilder)
 * so it's unit-testable and reusable — PlayerService.setAudiobook()'s resume path
 * uses this, and boundary-crossing seeks need the exact same resolution logic.
 *
 * trackIndex is always coerced into 0..tracks.lastIndex: if absolutePositionSeconds
 * is at or beyond the book's total duration (e.g. a finished or near-finished book's
 * saved progress), the naive summing loop would otherwise walk trackIndex all the way
 * to tracks.size, which is not a valid index into `tracks` and would throw
 * IndexOutOfBoundsException on the caller's next `tracks[trackIndex]` access.
 */
object TrackPositionResolver {

    data class TrackPosition(
        val trackIndex: Int,
        val trackLocalOffsetSeconds: Double
    )

    fun resolve(tracks: List<Track>, absolutePositionSeconds: Double): TrackPosition {
        require(tracks.isNotEmpty()) { "tracks must not be empty" }

        var cumulativeDuration = 0.0
        var trackIndex = 0
        for (track in tracks) {
            cumulativeDuration += track.duration
            if (cumulativeDuration > absolutePositionSeconds) {
                break
            }
            trackIndex++
        }
        trackIndex = trackIndex.coerceIn(0, tracks.lastIndex)

        val trackLocalOffsetSeconds = absolutePositionSeconds - tracks[trackIndex].startOffset
        return TrackPosition(trackIndex, trackLocalOffsetSeconds)
    }
}
