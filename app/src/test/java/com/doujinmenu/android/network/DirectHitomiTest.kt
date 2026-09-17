package com.doujinmenu.android.network

import java.nio.ByteBuffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DirectHitomiTest {
    @Test fun `optional live direct search smoke test`() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("HITOMI_LIVE_TEST") == "1")
        val client = HitomiClient()
        val result = client.search("language:korean", 1)
        assertTrue(result.galleryIds.isNotEmpty())
        val next = client.search("language:korean", 2)
        assertTrue(next.galleryIds.intersect(result.galleryIds.toSet()).isEmpty())
        val gallery = client.getGallery(result.galleryIds.first())
        assertEquals(gallery.pageCount, client.getGalleryPages(gallery.id).size)
        assertTrue(client.search("test", 1).galleryIds.isNotEmpty())
    }

    @Test fun `tag resource paths preserve namespaces and escape tag names`() {
        assertEquals("/n/index-korean.nozomi", hitomiNozomiPath("language:korean"))
        assertEquals("/n/artist/a%20b-all.nozomi", hitomiNozomiPath("artist:a_b"))
        assertEquals("/n/tag/female:a%2Fb-all.nozomi", hitomiNozomiPath("female:a/b"))
    }
    @Test fun `binary search index and unsigned gallery IDs are decoded`() {
        val node = ByteBuffer.allocate(464).putInt(1).putInt(4).put(byteArrayOf(1, 2, 3, -1))
            .putInt(1).putLong(1200).putInt(12)
        repeat(17) { node.putLong(if (it == 1) 464 else 0) }
        val parsed = parseHitomiIndexNode(node.array())
        assertEquals(1200L to 12, parsed.data.single())
        assertEquals(464L, parsed.children[1])
        assertTrue(compareHitomiKeys(byteArrayOf(-1), byteArrayOf(1)) > 0)
        assertEquals(listOf(42L, 2147483648L), decodeHitomiIds(ByteBuffer.allocate(8).putInt(42).putInt(Int.MIN_VALUE).array()))
    }

    @Test(expected = IllegalArgumentException::class) fun `truncated ID list is rejected`() {
        decodeHitomiIds(byteArrayOf(1, 2, 3))
    }

    @Test fun `image host switching and metadata match direct resources`() {
        val hash = "0".repeat(61) + "123"
        val context = parseHitomiImageContext("var o = 0; switch(g) { case 786: o = 1; } b: '12345/'")
        assertEquals("https://w2.gold-usergeneratedcontent.net/12345/786/$hash.webp", context.imageUrl(hash))
        assertEquals("https://w1.gold-usergeneratedcontent.net/12345/0/${"0".repeat(64)}.webp", context.imageUrl("0".repeat(64)))
        val raw = JSONObject("""{"title":"Example","artists":[{"artist":"a"}],"parodys":[{"parody":"b"}],"tags":[{"tag":"c","female":"1"}],"files":[{"hash":"$hash"}],"language":"korean"}""")
        val gallery = parseDirectGallery(raw, 42)
        assertEquals(listOf("a"), gallery.artists)
        assertEquals(listOf("b"), gallery.series)
        assertEquals("female", gallery.tags.single().type)
        assertEquals(1, gallery.pageCount)
        assertTrue(gallery.thumbnailUrl!!.contains("/3/12/"))
    }

    @Test(expected = IllegalArgumentException::class) fun `unknown image script fails explicitly`() {
        parseHitomiImageContext("changed protocol")
    }
}
