package com.doujinmenu.android.data

import org.junit.Assert.*
import org.junit.Test

class DownloadRequestTest {
    @Test fun `queue round trip preserves target and desktop without starting work`() {
        val request = DownloadRequest(galleryId = 42, title = "제목", profileId = "pc-one")
        val restored = decodeDownloadRequests(encodeDownloadRequests(listOf(request))).single().recover()
        assertEquals(request, restored)
        assertEquals(RequestStatus.WAITING, restored.status)
        assertEquals(DownloadTarget.DESKTOP, restored.target)
        assertFalse(restored.isBusy)
    }

    @Test fun `duplicate click does not add another pending request`() {
        val request = DownloadRequest(galleryId = 42, title = "Example")
        assertEquals(listOf(request), addDownloadRequest(listOf(request), request.copy(id = "other")))
        assertEquals(2, addDownloadRequest(listOf(request.copy(status = RequestStatus.SENT)), request).size)
    }

    @Test fun `interrupted local work never becomes desktop work or starts itself`() {
        for (status in listOf(RequestStatus.RUNNING, RequestStatus.WAITING)) {
            val recovered = DownloadRequest(galleryId = 42, title = "Example", target = DownloadTarget.LOCAL,
                status = status, downloadedFiles = 3, totalFiles = 5).recover()
            assertEquals(RequestStatus.PAUSED, recovered.status)
            assertEquals(DownloadTarget.LOCAL, recovered.target)
            assertEquals(3, recovered.downloadedFiles)
        }
    }
}
