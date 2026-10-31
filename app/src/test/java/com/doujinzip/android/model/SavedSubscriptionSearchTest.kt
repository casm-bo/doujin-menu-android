package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class SavedSubscriptionSearchTest {
    private val value = SearchSubscription(name = "My artist", query = "artist:example tag:color")
    @Test fun matchesOnlySavedNameAndTags() {
        assertTrue(value.matchesSavedSearch("MY tag:color"))
        assertFalse(value.matchesSavedSearch("missing"))
        assertFalse(value.matchesSavedSearch("artist -tag:color"))
        assertTrue(value.matchesSavedSearch("artist -tag:other"))
        assertTrue(value.matchesSavedSearch("  "))
    }
}
