package com.doujinzip.android.ui

import com.doujinzip.android.data.SearchResultIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPageIndicatorTest {
    private val index = SearchResultIndex("session", listOf("language:korean"),
        pages = listOf((1L..30).toList(), (31L..37).toList(), (38L..60).toList()))

    @Test fun `headers belong to first page and boundary follows actual page sizes`() {
        assertEquals(1, visibleSearchPage(0, index))
        assertEquals(1, visibleSearchPage(31, index))
        assertEquals(2, visibleSearchPage(32, index))
        assertEquals(2, visibleSearchPage(38, index))
        assertEquals(3, visibleSearchPage(39, index))
    }

    @Test fun `footer and overscroll stay on final loaded page`() {
        assertEquals(3, visibleSearchPage(100, index))
        assertEquals(1, visibleSearchPage(-1, index))
    }
}
