package com.doujinmenu.android.ui

import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterSuggestionsTest {
    @Test fun libraryIndexNormalizesAndDeduplicatesWithoutLosingNamespaces() {
        val book = LibraryBook("id", "Title", "Title", "root", "Root", "folder", emptyList(), 0,
            LibraryMetadata(artists = listOf(" A  B "), groups = listOf("Circle"), series = listOf("Series"),
                characters = listOf("Character"), tags = listOf("female:Big Breasts", "full color", "full color"), language = "Korean"))
        assertEquals(listOf("artist:a_b", "group:circle", "series:series", "character:character",
            "female:big_breasts", "tag:full_color", "language:korean"), libraryFilterTokens(listOf(book, book)))
        assertEquals(emptyList<String>(), libraryFilterTokens(emptyList()))
    }
}
