package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class SeriesAssignmentsTest {
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
