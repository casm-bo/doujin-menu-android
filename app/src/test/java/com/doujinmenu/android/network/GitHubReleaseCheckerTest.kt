package com.doujinmenu.android.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {
    @Test
    fun comparesReleaseVersionsNumerically() {
        assertTrue(isNewerAppVersion("v0.6.0", "0.5.9"))
        assertTrue(isNewerAppVersion("1.10.0", "1.9.9"))
        assertFalse(isNewerAppVersion("0.5.0", "0.5.0"))
        assertFalse(isNewerAppVersion("0.4.9", "0.5.0"))
    }
}
