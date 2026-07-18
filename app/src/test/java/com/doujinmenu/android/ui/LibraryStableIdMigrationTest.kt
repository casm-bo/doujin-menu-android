package com.doujinmenu.android.ui

import com.doujinmenu.android.model.CustomSeriesAssignment
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.network.SeriesSyncUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import com.doujinmenu.android.network.toJsonObject

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
    fun migratesLegacyLocalUriToStableFileId() {
        val legacyUri = "content://library/document/book-1"
        val stableId = "file:550e8400-e29b-41d4-a716-446655440000"
        val book = LibraryBook(
            id = stableId,
            title = "book",
            locationUri = "content://library/tree/root",
            locationName = "library",
            folderUri = legacyUri,
            pages = listOf(LibraryPage("content://library/document/page-1", "1.jpg")),
            modifiedAt = 0,
        )

        val aliases = legacyLocalLibraryIdAliases(listOf(book))

        assertEquals(mapOf(legacyUri to stableId), aliases)
        assertEquals(setOf(stableId), remapLibraryIds(setOf(legacyUri), aliases))
        assertEquals(mapOf(stableId to "시리즈"), remapLibraryIdKeys(mapOf(legacyUri to "시리즈"), aliases))
    }

    @Test
    fun ignoresLocalBooksThatStillUseTheirUriAsId() {
        val legacyUri = "content://library/document/book-1"
        val book = LibraryBook(
            id = legacyUri,
            title = "book",
            locationUri = "content://library/tree/root",
            locationName = "library",
            folderUri = legacyUri,
            pages = emptyList(),
            modifiedAt = 0,
        )

        assertEquals(emptyMap<String, String>(), legacyLocalLibraryIdAliases(listOf(book)))
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

    @Test
    fun serverVerificationKeepsOnlyUpdatesThatWereNotApplied() {
        val applied = SeriesSyncUpdate("applied", CustomSeriesAssignment("Series", 1, 200), 200)
        val missing = SeriesSyncUpdate("missing", CustomSeriesAssignment("Other", 0, 300), 300)
        val remote = LibraryBook(
            id = "desktop:profile:applied",
            title = "book",
            locationUri = "desktop",
            locationName = "desktop",
            folderUri = "desktop",
            pages = emptyList(),
            modifiedAt = 0,
            isCloud = true,
            syncId = "APPLIED",
            syncedSeries = CustomSeriesAssignment("Series", 1, 200),
            syncedSeriesModifiedAt = 200,
            hasSyncedSeriesState = true,
        )

        assertEquals(listOf(missing), pendingSeriesSyncUpdates(listOf(applied, missing), listOf(remote)))
    }

    @Test
    fun newerDifferentServerStateIsNotMistakenForSuccessfulUpload() {
        val update = SeriesSyncUpdate("uuid", CustomSeriesAssignment("Mobile", 0, 200), 200)
        val remote = LibraryBook(
            id = "desktop:profile:uuid",
            title = "book",
            locationUri = "desktop",
            locationName = "desktop",
            folderUri = "desktop",
            pages = emptyList(),
            modifiedAt = 0,
            isCloud = true,
            syncId = "uuid",
            syncedSeries = CustomSeriesAssignment("Desktop", 0, 300),
            syncedSeriesModifiedAt = 300,
            hasSyncedSeriesState = true,
        )

        assertEquals(listOf(update), pendingSeriesSyncUpdates(listOf(update), listOf(remote)))
    }

    @Test
    fun verifiedRemovalDoesNotNeedAnotherUpload() {
        val removal = SeriesSyncUpdate("uuid", null, 400)
        val remote = LibraryBook(
            id = "desktop:profile:uuid",
            title = "book",
            locationUri = "desktop",
            locationName = "desktop",
            folderUri = "desktop",
            pages = emptyList(),
            modifiedAt = 0,
            isCloud = true,
            syncId = "uuid",
            syncedSeries = null,
            syncedSeriesModifiedAt = 400,
            hasSyncedSeriesState = true,
        )

        assertEquals(emptyList<SeriesSyncUpdate>(), pendingSeriesSyncUpdates(listOf(removal), listOf(remote)))
    }

    @Test
    fun stalePendingSeriesIdsWithoutStateAreDiscarded() {
        assertEquals(
            emptySet<String>(),
            sanitizePendingSeriesSyncIds(
                pending = setOf("stale-id"),
                knownSyncableIds = setOf("stale-id"),
                librarySnapshotComplete = true,
                assignments = emptyMap(),
                removalTimes = emptyMap(),
            ),
        )
    }

    @Test
    fun pendingRemovalSurvivesAnIncompleteLibrarySnapshot() {
        assertEquals(
            setOf("temporarily-missing"),
            sanitizePendingSeriesSyncIds(
                pending = setOf("temporarily-missing"),
                knownSyncableIds = emptySet(),
                librarySnapshotComplete = false,
                assignments = emptyMap(),
                removalTimes = mapOf("temporarily-missing" to 500L),
            ),
        )
    }

    @Test
    fun seriesSyncDoesNotUseTheSharedBookStateVersionByDefault() {
        val payload = SeriesSyncUpdate(
            bookSyncId = "uuid",
            assignment = CustomSeriesAssignment("Series", 0, 1_000L),
            modifiedAt = 1_000L,
        ).toJsonObject()

        assertFalse(payload.has("baseVersion"))
    }
}
