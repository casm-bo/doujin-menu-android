package com.doujinmenu.android.data

import org.junit.Assert.*
import org.junit.Test

class SubscriptionPrivacyTest {
    @Test fun defaultPrivacyProtectsLockedAndColdProcessSessionsButAllowsOptOut() {
        assertTrue(hideSubscriptionContent(lockEnabled=true,unlocked=false,hideWhenLocked=true))
        assertFalse(hideSubscriptionContent(lockEnabled=true,unlocked=true,hideWhenLocked=true))
        assertFalse(hideSubscriptionContent(lockEnabled=true,unlocked=false,hideWhenLocked=false))
        assertFalse(hideSubscriptionContent(lockEnabled=false,unlocked=false,hideWhenLocked=true))
    }
}
