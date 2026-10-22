package com.doujinmenu.android.security

import org.junit.Assert.*
import org.junit.Test

class SubscriptionSyncScheduleTest {
    private val now=100_000_000L
    @Test fun shortForegroundChecksWaitFiveOrTenMinutesIncludingAfterFailures() {
        for(minutes in listOf(5,10)) {
            val options=SubscriptionOptions(intervalMinutes=minutes)
            assertFalse(subscriptionForegroundCheckDue(options,now+60_000,now))
            assertFalse(subscriptionForegroundCheckDue(options,now+minutes*60_000-1,now))
            assertTrue(subscriptionForegroundCheckDue(options,now+minutes*60_000,now))
        }
        assertFalse(subscriptionForegroundCheckDue(SubscriptionOptions(),now+60*60_000,now))
    }
    @Test fun repeatedForegroundAndStatusChecksWaitForTheConfiguredInterval() {
        val options=SubscriptionOptions()
        assertTrue(subscriptionSyncDue(options,now,null,false))
        assertFalse(subscriptionSyncDue(options,now+2_000,now,false))
        assertFalse(subscriptionSyncDue(options,now+899_999,now,false))
        assertTrue(subscriptionSyncDue(options,now+900_000,now,false))
        assertFalse(subscriptionSyncDue(options.copy(syncIntervalMinutes=60),now+900_000,now,false))
    }
    @Test fun unsupportedDesktopWaitsADayButManualSyncAlwaysWorks() {
        assertFalse(subscriptionSyncDue(SubscriptionOptions(),now+3_600_000,now,true))
        assertTrue(subscriptionSyncDue(SubscriptionOptions(),now+86_400_000,now,true))
        assertTrue(subscriptionSyncDue(SubscriptionOptions(),now,now,true,manual=true))
    }
    @Test fun disabledSyncStaysOffAndClockChangesCannotBlockItForever() {
        assertFalse(subscriptionSyncDue(SubscriptionOptions(syncEnabled=false),now,null,false))
        assertTrue(subscriptionSyncDue(SubscriptionOptions(syncEnabled=false),now,now,true,manual=true))
        assertTrue(subscriptionSyncDue(SubscriptionOptions(),now,now+1,false))
    }
}
