package com.doujinmenu.android.network

import com.doujinmenu.android.model.SearchResult
import com.doujinmenu.android.model.combineLanguageSearchResults
import com.doujinmenu.android.model.queriesWithPreferredLanguages
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SearchResultCountTest {
    @Test fun `optional live blank search and range totals`() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("HITOMI_LIVE_TEST") == "1")
        val client = HitomiClient()
        val first = client.search("", 1)
        assertEquals(30, first.galleryIds.size)
        assertTrue(first.totalCount!! > 30)
        assertTrue(first.maxPage!! > 1)
        val oldest = client.search("sort:oldest", 1)
        assertEquals(30, oldest.galleryIds.size)
        assertTrue(oldest.totalCount!! >= first.totalCount)
        assertTrue(oldest.galleryIds.intersect(first.galleryIds.toSet()).isEmpty())
    }
    @Test fun `empty conditions still issue an all galleries query`() {
        assertEquals(listOf("sort:newest"), queriesWithPreferredLanguages("  ", emptySet()))
        assertEquals(listOf("language:korean"), queriesWithPreferredLanguages("", setOf("korean")))
        assertEquals(listOf("sort:oldest"), queriesWithPreferredLanguages(" sort:oldest ", emptySet()))
    }

    @Test fun `language totals add but maximum page follows the longest language result`() {
        val result = combineLanguageSearchResults(listOf(SearchResult(listOf(1), true, 1000), SearchResult(listOf(2), false, 10)))
        assertEquals(1010, result.totalCount)
        assertEquals(34, result.maxPage)
        assertTrue(result.hasNextPage)
    }
    @Test fun `range total includes all galleries instead of downloaded window`() {
        assertEquals(1000, nozomiResultCount("bytes 120-243/4000"))
        assertEquals(1000, nozomiResultCount("bytes */4000"))
        assertEquals(34, SearchResult(listOf(31), true, 1000).maxPage)
        assertEquals(0, SearchResult(emptyList(), false, 0).maxPage)
        assertEquals(1, SearchResult(listOf(1), false, 1).maxPage)
    }

    @Test fun `invalid partial and overflowing counts are rejected`() {
        listOf("", "bytes 0-4/*", "bytes 0-4/5", "bytes */-4", "bytes */999999999999999").forEach {
            assertTrue(runCatching { nozomiResultCount(it) }.isFailure)
        }
    }

    @Test fun `oldest windows read only their range with partial last page`() {
        assertEquals(3880L..3999L, oldestNozomiRange(1000, 1))
        assertEquals(0L..39L, oldestNozomiRange(1000, 34))
        assertNull(oldestNozomiRange(1000, 35))
        assertNull(oldestNozomiRange(0, 1))
    }
}
