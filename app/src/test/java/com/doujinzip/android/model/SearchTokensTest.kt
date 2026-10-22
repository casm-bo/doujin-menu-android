package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class SearchTokensTest {
    @Test fun separatorsAndQuotesAreSharedAcrossTabsAndHistory() {
        val query = "tag:full_color, tag:multi-work_series title:\"A, B\""
        assertEquals(listOf("tag:full_color", "tag:multi-work_series", "title:\"A, B\""), searchQueryTokens(query))
        assertEquals("tag:full_color tag:multi-work_series title:\"A, B\"", normalizeSearchSeparators(query))
        assertEquals("tag:full_color, tag:multi-work_series, title:\"A, B\"", formatSearchQuery(query))
    }
    @Test fun suggestionsReplaceLastTokenAfterCommaAndKeepNegation() {
        assertEquals("tag:one,-artist:sample, ", applySearchSuggestion("tag:one,-artist:sa", "artist:sample"))
        assertEquals("tag:one, artist:", applySearchSuggestion("tag:one, art", "artist:"))
        assertEquals("tag:one, tag:two, ", applySearchSuggestion("tag:one, ", "tag:two"))
    }
}
