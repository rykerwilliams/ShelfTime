package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmartDeleteSelectorTest {

    private fun item(id: String) = LibraryItem(id = id)

    @Test
    fun `no deletions when under the limit`() {
        val downloaded = listOf(
            item("a") to 1_000L,
            item("b") to 2_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(downloaded, currentlyPlayingItemId = null, maxDownloads = 5)

        assertEquals(emptyList<LibraryItem>(), result)
    }

    @Test
    fun `deletes oldest items first when over the limit`() {
        val downloaded = listOf(
            item("newest") to 3_000L,
            item("oldest") to 1_000L,
            item("middle") to 2_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(downloaded, currentlyPlayingItemId = null, maxDownloads = 2)

        assertEquals(listOf("oldest"), result.map { it.id })
    }

    @Test
    fun `never selects the currently playing item even if it is the oldest`() {
        val downloaded = listOf(
            item("playing") to 1_000L, // oldest, but actively playing
            item("second-oldest") to 2_000L,
            item("newest") to 3_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = "playing",
            maxDownloads = 2
        )

        assertFalse(result.any { it.id == "playing" })
        assertEquals(listOf("second-oldest"), result.map { it.id })
    }

    @Test
    fun `currently playing item does not block deleting others past the limit`() {
        val downloaded = listOf(
            item("playing") to 1_000L,
            item("second-oldest") to 2_000L,
            item("third-oldest") to 3_000L,
            item("newest") to 4_000L,
        )

        // max=2, so 2 items are in excess; "playing" must be skipped even though
        // it would otherwise be the first candidate.
        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = "playing",
            maxDownloads = 2
        )

        assertEquals(listOf("second-oldest", "third-oldest"), result.map { it.id })
    }
}
