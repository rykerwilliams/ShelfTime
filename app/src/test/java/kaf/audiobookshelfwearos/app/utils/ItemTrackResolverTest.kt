package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import kaf.audiobookshelfwearos.app.data.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemTrackResolverTest {

    @Test
    fun `returns the item unchanged when it already has tracks, without fetching`() = runTest {
        val item = LibraryItem(id = "has-tracks", media = Media(tracks = listOf(Track())))
        var fetchCalled = false

        val result = ItemTrackResolver.resolveItemWithTracks(item) {
            fetchCalled = true
            null
        }

        assertEquals(item, result)
        assertTrue("fetchExpandedItem should be skipped when tracks are already present", !fetchCalled)
    }

    @Test
    fun `fetches the expanded item when tracks are empty`() = runTest {
        val summaryItem = LibraryItem(id = "empty-tracks", media = Media(tracks = emptyList()))
        val expandedItem = summaryItem.copy(media = Media(tracks = listOf(Track())))

        val result = ItemTrackResolver.resolveItemWithTracks(summaryItem) { id ->
            assertEquals("empty-tracks", id)
            expandedItem
        }

        assertEquals(expandedItem, result)
    }

    @Test
    fun `returns null when the fetch fails, instead of the empty-tracks item`() = runTest {
        val summaryItem = LibraryItem(id = "empty-tracks", media = Media(tracks = emptyList()))

        val result = ItemTrackResolver.resolveItemWithTracks(summaryItem) { null }

        assertNull(result)
    }
}
