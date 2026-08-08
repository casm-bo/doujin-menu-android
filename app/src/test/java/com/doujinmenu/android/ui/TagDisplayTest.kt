package com.doujinmenu.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TagDisplayTest {
    @Test
    fun `gender prefixes become color styles without prefix text`() {
        assertEquals(TagDisplayInfo("glasses", TagStyle.FEMALE), tagDisplayInfo("female:glasses"))
        assertEquals(TagDisplayInfo("muscle", TagStyle.MALE), tagDisplayInfo("male:muscle"))
    }

    @Test
    fun `gallery tag types use the same gender styles and facets`() {
        assertEquals(TagDisplayInfo("glasses", TagStyle.FEMALE), tagDisplayInfo("glasses", "female"))
        assertEquals("female:glasses", tagSearchFacet("glasses", "female"))
        assertEquals("male:muscle", tagSearchFacet("male:muscle", "tag"))
    }
}
