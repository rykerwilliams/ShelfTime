package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.UserMediaProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueListeningSelectorTest {

    private fun item(
        id: String,
        currentTime: Double = 0.0,
        isFinished: Boolean = false,
        hideFromContinueListening: Boolean = false,
        lastUpdate: Long = 0L
    ) = LibraryItem(
        id = id,
        userMediaProgress = UserMediaProgress(
            id = id,
            libraryItemId = id,
            currentTime = currentTime,
            isFinished = isFinished,
            hideFromContinueListening = hideFromContinueListening,
            lastUpdate = lastUpdate
        )
    )

    @Test
    fun `excludes items with no progress`() {
        val items = listOf(item("never-started", currentTime = 0.0))

        assertEquals(emptyList<LibraryItem>(), ContinueListeningSelector.select(items))
    }

    @Test
    fun `excludes finished items`() {
        val items = listOf(item("finished", currentTime = 500.0, isFinished = true))

        assertEquals(emptyList<LibraryItem>(), ContinueListeningSelector.select(items))
    }

    @Test
    fun `excludes items explicitly hidden from continue listening`() {
        val items = listOf(item("hidden", currentTime = 500.0, hideFromContinueListening = true))

        assertEquals(emptyList<LibraryItem>(), ContinueListeningSelector.select(items))
    }

    @Test
    fun `includes in-progress items, most recently listened first`() {
        val items = listOf(
            item("older", currentTime = 100.0, lastUpdate = 1_000L),
            item("newer", currentTime = 200.0, lastUpdate = 2_000L),
            item("not-started", currentTime = 0.0, lastUpdate = 3_000L),
        )

        val result = ContinueListeningSelector.select(items)

        assertEquals(listOf("newer", "older"), result.map { it.id })
    }
}
