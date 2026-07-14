package kaf.audiobookshelfwearos.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryItemTest {

    @Test
    fun `title falls back when metadata title is missing`() {
        val item = LibraryItem(media = Media(metadata = Metadata(title = null)))
        assertEquals("Unknown Title", item.title)

        val withTitle = LibraryItem(media = Media(metadata = Metadata(title = "Dune")))
        assertEquals("Dune", withTitle.title)
    }

    @Test
    fun `author falls back when metadata author is missing`() {
        val item = LibraryItem(media = Media(metadata = Metadata(authorName = null)))
        assertEquals("Unknown Author", item.author)

        val withAuthor = LibraryItem(media = Media(metadata = Metadata(authorName = "Frank Herbert")))
        assertEquals("Frank Herbert", withAuthor.author)
    }

    @Test
    fun `title and author setters write through to the underlying metadata`() {
        val item = LibraryItem()
        item.title = "Foundation"
        item.author = "Isaac Asimov"

        assertEquals("Foundation", item.media.metadata.title)
        assertEquals("Isaac Asimov", item.media.metadata.authorName)
    }
}
