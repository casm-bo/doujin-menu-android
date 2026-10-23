package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class SeriesAssignmentsTest {
    @Test fun replacementCommitsOrderMembershipAndReindexesOnlyAffectedGroups() {
        val before = mapOf("a" to CustomSeriesAssignment("Target", 0), "b" to CustomSeriesAssignment("Target", 1),
            "c" to CustomSeriesAssignment("Other", 0), "d" to CustomSeriesAssignment("Other", 1), "e" to CustomSeriesAssignment("Unrelated", 5, 1))
        val result = replaceSeriesMembers(before, "Target", listOf("c", "b"), 100)
        assertNull(result["a"])
        assertEquals(CustomSeriesAssignment("Target", 0, 100), result["c"])
        assertEquals(CustomSeriesAssignment("Target", 1, 100), result["b"])
        assertEquals(CustomSeriesAssignment("Other", 0, 100), result["d"])
        assertEquals(before["e"], result["e"])
        assertEquals(5, before.size)
        assertEquals(setOf("d", "e"), replaceSeriesMembers(result, "Target", emptyList(), 101).keys)
        assertTrue(runCatching { replaceSeriesMembers(before, "Target", listOf("a", "a"), 1) }.isFailure)
    }
    @Test fun detailCombinesEveryEpisodesFacetsWithoutDuplicatingTagsOrMixingGroupNames() {
        fun book(id: String, tags: List<String>) = LibraryBook(id, id, locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 1,
            metadata = LibraryMetadata(tags = tags, series = listOf("Original IP"), artists = listOf("Artist")))
        val metadata = unionSeriesMetadata(listOf(book("a", listOf("shared", "a")), book("b", listOf("shared", "b"))))
        assertEquals(listOf("shared", "a", "b"), metadata.tags)
        assertEquals(listOf("Original IP"), metadata.series)
        assertEquals(listOf("Artist"), metadata.artists)
        assertNull(metadata.publishedDate)
    }
    @Test fun appendPreservesMembersOrderAndMovesOnlySelectedWorks() {
        val before = mapOf("a" to CustomSeriesAssignment("Target", 0), "b" to CustomSeriesAssignment("Target", 1),
            "c" to CustomSeriesAssignment("Other", 0), "d" to CustomSeriesAssignment("Other", 1))
        val result = appendSeriesMembers(before, linkedSetOf("a", "c", "new"), "Target", 100)
        assertEquals(listOf("a", "b", "c", "new"), result.filterValues { it.name == "Target" }.entries.sortedBy { it.value.order }.map { it.key })
        assertEquals(CustomSeriesAssignment("Other", 0, 100), result["d"])
        assertEquals(5, result.size)
        assertEquals(result, appendSeriesMembers(result, setOf("a", "c", "new"), "Target", 100))
    }
    @Test fun newSeriesDoesNotTouchUnrelatedGroups() {
        val before = mapOf("a" to CustomSeriesAssignment("Existing", 0, 1))
        val result = appendSeriesMembers(before, setOf("b"), "New", 2)
        assertEquals(before["a"], result["a"])
        assertEquals(CustomSeriesAssignment("New", 0, 2), result["b"])
    }
}
