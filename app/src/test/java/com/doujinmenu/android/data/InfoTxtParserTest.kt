package com.doujinmenu.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InfoTxtParserTest {
    @Test
    fun parsesDesktopArchiveMetadataFormat() {
        val parsed = InfoTxtParser.parse(
            """
            갤러리 넘버: 12345
            UUID: 550e8400-e29b-41d4-a716-446655440000
            제목: 테스트 갤러리
            작가: ABC, DEF artist
            그룹: sample group
            타입: doujinshi
            시리즈: Test Series
            캐릭터: Alice, Bob
            태그: full color, sole female
            언어: korean
            """.trimIndent(),
        )

        assertEquals("테스트 갤러리", parsed.title)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", parsed.uuid)
        assertEquals("12345", parsed.metadata.hitomiId)
        assertEquals(listOf("ABC", "DEF_artist"), parsed.metadata.artists)
        assertEquals(listOf("full_color", "sole_female"), parsed.metadata.tags)
        assertEquals("korean", parsed.metadata.language)
    }

    @Test
    fun parsesMetadataKeysWrittenByAndroidArchiveExporter() {
        val parsed = InfoTxtParser.parse(
            """
            갤러리 번호: 67890
            종류: manga
            """.trimIndent(),
        )

        assertEquals("67890", parsed.metadata.hitomiId)
        assertEquals("manga", parsed.metadata.galleryType)
    }
}
