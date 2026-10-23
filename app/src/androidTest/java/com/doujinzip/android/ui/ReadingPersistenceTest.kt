package com.doujinzip.android.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.security.*
import org.junit.Assert.*
import org.junit.Test

class ReadingPersistenceTest {
    @Test fun pageWritesOverrideLegacyAndBulkReplacementRemovesOldPages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "page-fixture-${System.nanoTime()}"
        val store = LibraryPreferenceStore(context, name)
        try {
            store.saveProgress(mapOf("A" to 3, "B" to 8))
            val before = store.snapshot()
            store.saveBookProgress("A", 49)
            assertFalse(store.matchesSnapshot(before))
            assertEquals(mapOf("A" to 49, "B" to 8), LibraryPreferenceStore(context, name).loadProgress())
            store.saveProgress(mapOf("B" to 9))
            assertEquals(mapOf("B" to 9), store.loadProgress())
            store.saveOnlineProgress(mapOf(10L to 4))
            store.saveOnlinePage(10, 50)
            store.saveOnlinePage(11, 0)
            assertEquals(mapOf(10L to 50, 11L to 0), LibraryPreferenceStore(context, name).loadOnlineProgress())
            store.saveOnlineProgress(emptyMap())
            assertTrue(store.loadOnlineProgress().isEmpty())
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun legacyHistoryMigratesAndUpdatesOnlyChangedRecordsWithoutResurrectingDeletes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-fixture-${System.nanoTime()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val old = RecentHistoryEntry("book:A", "A", 1, HistorySource.DEVICE, bookId = "A", page = 3)
        val other = old.copy(key = "book:B", bookId = "B", title = "B")
        val store = RecentHistoryStore(context, name)
        try {
            preferences.edit().putString("entries", encodeRecentHistory(listOf(old, other))).apply()
            assertEquals(2, store.load().size)
            val latest = old.copy(page = 49, viewedAt = 2)
            store.save(listOf(latest, other))
            val untouched = preferences.getString("entry:book:B", null)
            store.save(listOf(latest.copy(page = 50), other))
            assertEquals(untouched, preferences.getString("entry:book:B", null))
            assertEquals(50, RecentHistoryStore(context, name).load().first().page)
            store.save(listOf(other))
            assertEquals(listOf(other), RecentHistoryStore(context, name).load())
            store.save(emptyList())
            assertTrue(RecentHistoryStore(context, name).load().isEmpty())
            assertFalse(preferences.contains("entries"))
        } finally { context.deleteSharedPreferences(name) }
    }
}
