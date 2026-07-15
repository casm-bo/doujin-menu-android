package com.doujinmenu.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InfoTxtParserTest {
    @Test
    fun parsesDesktopArchiveMetadataFormat() {
        val parsed = InfoTxtParser.parse(
            """
            갤러리 넘버: 12345
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
        assertEquals("12345", parsed.metadata.hitomiId)
        assertEquals(listOf("ABC", "DEF_artist"), parsed.metadata.artists)
        assertEquals(listOf("full_color", "sole_female"), parsed.metadata.tags)
        assertEquals("korean", parsed.metadata.language)
    }
}
