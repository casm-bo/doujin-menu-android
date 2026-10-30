package com.doujinzip.android.security

import com.doujinzip.android.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSaveQueueTest {
    @Test fun snapshotsAreSavedOffCallerThreadInOrderAndFlushIncludesBothWorkspaces() = runBlocking {
        val caller = Thread.currentThread().id
        val saved = mutableListOf<Pair<Boolean, String>>()
        val queue = WorkspaceSaveQueue { gallery, workspace ->
            assertNotEquals(caller, Thread.currentThread().id)
            saved.add(gallery to (workspace.activeTab.currentPage as BrowserPage.Search).query)
        }
        repeat(100) { index -> queue.submit(index % 2 == 0, BrowserWorkspace.initial(BrowserPage.Search(query = "$index"))) }
        queue.flush()
        assertEquals((0..99).map { (it % 2 == 0) to "$it" }, saved)
        queue.close()
    }
    @Test fun failuresAreReportedByFlushAndSuccessfulRetryRecovers() = runBlocking {
        var fail = true
        val queue = WorkspaceSaveQueue { _, _ -> if (fail) throw IllegalStateException("synthetic failure") }
        queue.submit(false, BrowserWorkspace.initial())
        assertTrue(runCatching { queue.flush() }.isFailure)
        fail = false
        queue.submit(false, BrowserWorkspace.initial())
        queue.flush(); queue.close()
    }
    @Test fun closeDrainsPendingSnapshots() = runBlocking {
        var count = 0
        val queue = WorkspaceSaveQueue { _, _ -> count++ }
        repeat(50) { queue.submit(false, BrowserWorkspace.initial()) }
        queue.close()
        assertEquals(50, count)
    }
}
