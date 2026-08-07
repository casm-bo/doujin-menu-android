package com.doujinmenu.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCompletionTest {
    @Test
    fun onlyLastPageCompletesReading() {
        assertFalse(reachedLastPage(0, 0))
        assertFalse(reachedLastPage(8, 10))
        assertTrue(reachedLastPage(9, 10))
        assertTrue(reachedLastPage(10, 10))
    }
}
