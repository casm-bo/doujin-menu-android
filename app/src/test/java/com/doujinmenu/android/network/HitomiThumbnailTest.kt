package com.doujinmenu.android.network

import org.junit.Assert.*
import org.junit.Test

class HitomiThumbnailTest {
    private val hash = "0".repeat(61) + "123"

    @Test fun `thumbnails use both CDN shards and either default mapping`() {
        for (default in 0..1) {
            val context = HitomiImageContext(setOf(786), default, "12345/")
            val listedHost = if (default == 0) "btn" else "atn"
            val otherHost = if (default == 0) "atn" else "btn"
            assertEquals("https://$listedHost.gold-usergeneratedcontent.net/webpbigtn/3/12/$hash.webp", context.thumbnailUrl(hash, "webpbigtn"))
            assertEquals("https://$otherHost.gold-usergeneratedcontent.net/webpsmalltn/0/00/${"0".repeat(64)}.webp", context.thumbnailUrl("0".repeat(64), "webpsmalltn"))
        }
    }

    @Test fun `saved and previously routed thumbnail addresses are recognized`() {
        for (host in listOf("tn", "atn", "btn")) for (domain in listOf("gold-usergeneratedcontent.net", "hitomi.la")) {
            assertEquals("webpbigtn" to hash, hitomiThumbnailParts("https://$host.$domain/webpbigtn/3/12/$hash.webp"))
        }
    }

    @Test fun `unrelated or malformed addresses are never rewritten`() {
        val valid = "https://tn.gold-usergeneratedcontent.net/webpbigtn/3/12/$hash.webp"
        for (url in listOf(valid.replace(".net/", ".net.example/"), valid.replace("/3/12/", "/1/23/"),
            valid.replace("https:", "http:"), valid + "?token=synthetic", valid.replace("webpbigtn", "12345"),
            "content://example/cover", "https://example.invalid/cover.webp")) assertNull(url, hitomiThumbnailParts(url))
    }
}
