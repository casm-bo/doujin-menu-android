package com.doujinmenu.android.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitomiApiCompatibilityTest {
    @Test
    fun `node-hitomi v9 search references are parsed`() {
        val result = parseHitomiSearchResult(
            JSONObject(
                """
                {
                  "data": [101, {"id": 202}, "303", {"invalid": true}],
                  "hasNextPage": true
                }
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(101L, 202L, 303L), result.galleryIds)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `node-hitomi v9 gallery dto is parsed for the search screen`() {
        val gallery = parseHitomiGallery(
            JSONObject(
                """
                {
                  "id": 123,
                  "title": {"display": "표시 제목", "japanese": "原題"},
                  "type": "manga",
                  "languageName": {"english": "Korean", "local": "한국어"},
                  "artists": ["artist one"],
                  "series": ["series one"],
                  "tags": [
                    {"type": "male", "name": "sample", "isNegative": false},
                    {"type": "female", "name": "excluded", "isNegative": true}
                  ],
                  "files": [{"index": 0}, {"index": 1}],
                  "publishedDate": "2026-07-18T00:00:00.000Z",
                  "thumbnailUrl": "https://tn.hitomi.la/sample.webp"
                }
                """.trimIndent(),
            ),
            fallbackId = 999,
        )

        assertEquals(123L, gallery.id)
        assertEquals("표시 제목", gallery.title)
        assertEquals(listOf("artist one"), gallery.artists)
        assertEquals(listOf("series one"), gallery.series)
        assertEquals("한국어", gallery.language)
        assertEquals(2, gallery.pageCount)
        assertEquals("2026-07-18T00:00:00.000Z", gallery.publishedDate)
        assertEquals("https://tn.hitomi.la/sample.webp", gallery.thumbnailUrl)
        assertFalse(gallery.tags.first().isNegative)
        assertTrue(gallery.tags.last().isNegative)
        assertNull(gallery.loadError)
    }

    @Test
    fun `legacy gallery fields remain readable during desktop upgrades`() {
        val gallery = parseHitomiGallery(
            JSONObject(
                """
                {
                  "title": "Legacy title",
                  "language": {"name": "English", "localName": "English"},
                  "artists": [{"name": "legacy artist"}],
                  "series": [{"name": "legacy series"}],
                  "tags": ["legacy tag"],
                  "images": [{}, {}, {}],
                  "publishedAt": "2025-01-01T00:00:00.000Z",
                  "thumbnail": "https://tn.hitomi.la/legacy.webp"
                }
                """.trimIndent(),
            ),
            fallbackId = 456,
        )

        assertEquals(456L, gallery.id)
        assertEquals("Legacy title", gallery.title)
        assertEquals(listOf("legacy artist"), gallery.artists)
        assertEquals(listOf("legacy series"), gallery.series)
        assertEquals("English", gallery.language)
        assertEquals(3, gallery.pageCount)
        assertEquals("legacy tag", gallery.tags.single().name)
    }
}
