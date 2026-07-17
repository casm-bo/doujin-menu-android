package com.doujinmenu.android.ui

import com.doujinmenu.android.model.CustomSeriesAssignment
import com.doujinmenu.android.model.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStableIdMigrationTest {
    @Test
    fun migratesLegacyDesktopIdsToStableSyncIds() {
        val legacyId = "desktop:profile:42"
        val stableId = "desktop:profile:550e8400-e29b-41d4-a716-446655440000"
        val aliases = mapOf(legacyId to stableId)
        val assignment = CustomSeriesAssignment("시리즈", 3)

        assertEquals(setOf(stableId), remapLibraryIds(setOf(legacyId), aliases))
        assertEquals(mapOf(stableId to assignment), remapLibraryIdKeys(mapOf(legacyId to assignment), aliases))
    }

    @Test
    fun stableValueWinsWhenBothLegacyAndStableKeysExist() {
        val legacyId = "desktop:profile:42"
        val stableId = "desktop:profile:uuid"

        assertEquals(
            mapOf(stableId to "new"),
            remapLibraryIdKeys(
                mapOf(legacyId to "old", stableId to "new"),
                mapOf(legacyId to stableId),
            ),
        )
    }

    @Test
    fun desktopSeriesRestoresMissingMobileAssignment() {
        val assignment = CustomSeriesAssignment("Desktop Series", 2, modifiedAt = 200)
        val book = LibraryBook(
            id = "desktop:profile:uuid",
            title = "book",
            locationUri = "desktop",
            locationName = "desktop",
            folderUri = "desktop",
            pages = emptyList(),
            modifiedAt = 0,
            isCloud = true,
            syncId = "uuid",
            syncedSeries = assignment,
            syncedSeriesModifiedAt = 200,
            hasSyncedSeriesState = true,
        )

        val result = mergeSyncedSeries(emptyMap(), emptyMap(), listOf(book))
        assertEquals(mapOf(book.id to assignment), result.assignments)
        assertEquals(emptySet<String>(), result.localWinnerIds)
    }

    @Test
    fun newestMobileSeriesWinsAndIsQueuedForUpload() {
        val mobile = CustomSeriesAssignment("Mobile Series", 1, modifiedAt = 300)
        val desktop = CustomSeriesAssignment("Desktop Series", 2, modifiedAt = 200)
        val book = LibraryBook(
            id = "desktop:profile:uuid",
            title = "book",
            locationUri = "desktop",
            locationName = "desktop",
            folderUri = "desktop",
            pages = emptyList(),
            modifiedAt = 0,
            isCloud = true,
            syncId = "uuid",
            syncedSeries = desktop,
            syncedSeriesModifiedAt = 200,
            hasSyncedSeriesState = true,
        )

        val result = mergeSyncedSeries(mapOf(book.id to mobile), emptyMap(), listOf(book))
        assertEquals(mapOf(book.id to mobile), result.assignments)
        assertEquals(setOf(book.id), result.localWinnerIds)
    }
}
