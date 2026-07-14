package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.Library

/**
 * Pure search/filter logic for the library list, kept separate from
 * ApiViewModel so it can be unit tested without a ViewModel/ApiHandler.
 */
object LibrarySearchFilter {
    fun filter(libraries: List<Library>, query: String): List<Library> {
        if (query.isBlank()) return libraries

        return libraries
            .map { library ->
                val filteredItems = library.libraryItems.filter { item ->
                    item.title.contains(query, ignoreCase = true) ||
                        item.author.contains(query, ignoreCase = true)
                }
                library.copy(libraryItems = ArrayList(filteredItems))
            }
            .filter { it.libraryItems.isNotEmpty() }
    }
}
