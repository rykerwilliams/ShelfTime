package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.Library
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import kaf.audiobookshelfwearos.app.data.Metadata
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchFilterTest {

    private fun item(title: String, author: String) = LibraryItem(
        media = Media(metadata = Metadata(title = title, authorName = author))
    )

    private fun library(vararg items: LibraryItem) = Library(
        id = "lib",
        libraryItems = ArrayList(items.toList())
    )

    @Test
    fun `blank query returns libraries unchanged`() {
        val libraries = listOf(library(item("Dune", "Frank Herbert")))

        assertEquals(libraries, LibrarySearchFilter.filter(libraries, ""))
        assertEquals(libraries, LibrarySearchFilter.filter(libraries, "   "))
    }

    @Test
    fun `matches by title case-insensitively`() {
        val libraries = listOf(
            library(item("Dune", "Frank Herbert"), item("Foundation", "Isaac Asimov"))
        )

        val result = LibrarySearchFilter.filter(libraries, "dune")

        assertEquals(listOf("Dune"), result.single().libraryItems.map { it.title })
    }

    @Test
    fun `matches by author case-insensitively`() {
        val libraries = listOf(
            library(item("Dune", "Frank Herbert"), item("Foundation", "Isaac Asimov"))
        )

        val result = LibrarySearchFilter.filter(libraries, "ASIMOV")

        assertEquals(listOf("Foundation"), result.single().libraryItems.map { it.title })
    }

    @Test
    fun `libraries with no matching items are dropped entirely`() {
        val libraries = listOf(
            library(item("Dune", "Frank Herbert")),
            library(item("Foundation", "Isaac Asimov"))
        )

        val result = LibrarySearchFilter.filter(libraries, "dune")

        assertEquals(1, result.size)
        assertEquals(listOf("Dune"), result.single().libraryItems.map { it.title })
    }

    @Test
    fun `no matches returns an empty list`() {
        val libraries = listOf(library(item("Dune", "Frank Herbert")))

        assertEquals(emptyList<Library>(), LibrarySearchFilter.filter(libraries, "nonexistent"))
    }
}
