package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem

/**
 * Pure selection/ordering logic for the "Continue Listening" section
 * (matches the label/behavior of the official Audiobookshelf web/mobile
 * clients): books with real playback progress that aren't finished and
 * haven't been explicitly hidden, most-recently-listened first.
 */
object ContinueListeningSelector {
    fun select(items: List<LibraryItem>): List<LibraryItem> {
        return items
            .filter {
                it.userProgress.currentTime > 0 &&
                    !it.userProgress.isFinished &&
                    !it.userProgress.hideFromContinueListening
            }
            .sortedByDescending { it.userProgress.lastUpdate }
    }
}
