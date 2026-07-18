package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmartDeleteSelectorTest {

    private fun item(id: String, sizeBytes: Long = 0L) =
        LibraryItem(id = id, media = Media(size = sizeBytes))

    // Effectively unlimited byte budget, used by tests that only care about the
    // count constraint.
    private val noByteBudget = Long.MAX_VALUE

    @Test
    fun `no deletions when under the limit`() {
        val downloaded = listOf(
            item("a") to 1_000L,
            item("b") to 2_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = null,
            maxDownloads = 5,
            maxTotalBytes = noByteBudget
        )

        assertEquals(emptyList<LibraryItem>(), result)
    }

    @Test
    fun `deletes oldest items first when over the limit`() {
        val downloaded = listOf(
            item("newest") to 3_000L,
            item("oldest") to 1_000L,
            item("middle") to 2_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = null,
            maxDownloads = 2,
            maxTotalBytes = noByteBudget
        )

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
            maxDownloads = 2,
            maxTotalBytes = noByteBudget
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
            maxDownloads = 2,
            maxTotalBytes = noByteBudget
        )

        assertEquals(listOf("second-oldest", "third-oldest"), result.map { it.id })
    }

    @Test
    fun `byte budget triggers deletion even when under the count limit`() {
        // 3 items, well under a maxDownloads of 10, but each is 1GB so the total
        // (3GB) blows past a 2GB byte budget. Deletion should continue oldest-first
        // until the remaining bytes fit, even though the count never approached the
        // limit.
        val oneGb = 1_000_000_000L
        val downloaded = listOf(
            item("newest", oneGb) to 3_000L,
            item("oldest", oneGb) to 1_000L,
            item("middle", oneGb) to 2_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = null,
            maxDownloads = 10,
            maxTotalBytes = 2 * oneGb
        )

        // Only the oldest needs to go: removing it brings the remaining 2 items
        // (2GB) within the 2GB budget.
        assertEquals(listOf("oldest"), result.map { it.id })
    }

    @Test
    fun `byte budget keeps deleting past a single item until both constraints are satisfied`() {
        // 4 items, count limit of 3 (so only 1 item is in count-excess), but the
        // byte budget requires removing 2 items before the running total fits.
        val oneGb = 1_000_000_000L
        val downloaded = listOf(
            item("newest", oneGb) to 4_000L,
            item("oldest", oneGb) to 1_000L,
            item("second-oldest", oneGb) to 2_000L,
            item("third-oldest", oneGb) to 3_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = null,
            maxDownloads = 3,
            maxTotalBytes = 2 * oneGb
        )

        // Count constraint alone would stop after removing just "oldest" (4 -> 3
        // items), but the byte budget (2GB) isn't satisfied until 2 items are gone
        // (remaining 2 items = 2GB).
        assertEquals(listOf("oldest", "second-oldest"), result.map { it.id })
    }

    @Test
    fun `count limit keeps deleting past the byte budget until both constraints are satisfied`() {
        // Inverse of the above: byte budget is satisfied after removing one small
        // item, but the count limit requires removing a second item too.
        val downloaded = listOf(
            item("newest", sizeBytes = 100L) to 3_000L,
            item("oldest", sizeBytes = 100L) to 1_000L,
            item("middle", sizeBytes = 100L) to 2_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = null,
            maxDownloads = 1,
            maxTotalBytes = 200L
        )

        // Byte budget (200) is satisfied once remaining bytes are 200 (2 items),
        // but maxDownloads=1 forces a second deletion down to 1 remaining item.
        assertEquals(listOf("oldest", "middle"), result.map { it.id })
    }

    @Test
    fun `no deletions when both count and byte budget are already satisfied`() {
        val downloaded = listOf(
            item("a", sizeBytes = 500L) to 1_000L,
            item("b", sizeBytes = 500L) to 2_000L,
        )

        val result = SmartDeleteSelector.selectItemsToDelete(
            downloaded,
            currentlyPlayingItemId = null,
            maxDownloads = 5,
            maxTotalBytes = 2_000L
        )

        assertEquals(emptyList<LibraryItem>(), result)
    }
}
