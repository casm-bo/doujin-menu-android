package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class SearchQueryHistoryTest {
    @Test fun `history deduplicates conditions while retaining language choices and latest order`() {
        val korean = SearchQueryEntry("artist:a, tag:b", setOf("korean"))
        val history = emptyList<SearchQueryEntry>().recordQuery(korean)
            .recordQuery(korean.copy(languages = setOf("english")))
            .recordQuery(korean.copy(query = "tag:b artist:a"))
        assertEquals(2, history.size)
        assertEquals(setOf("korean"), history.first().languages)
        assertEquals("tag:b artist:a", history.first().query)
    }

    @Test fun `history stays bounded and includes all gallery searches`() {
        val history = (1..70).fold(emptyList<SearchQueryEntry>()) { entries, number -> entries.recordQuery(SearchQueryEntry("$number")) }
        assertEquals(50, history.size)
        assertEquals("70", history.first().query)
        assertEquals("", history.recordQuery(SearchQueryEntry("")).first().query)
    }
}
