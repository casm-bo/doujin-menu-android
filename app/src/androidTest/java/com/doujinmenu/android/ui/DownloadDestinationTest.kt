package com.doujinmenu.android.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.data.DownloadRequestStore
import com.doujinmenu.android.data.DownloadTarget
import com.doujinmenu.android.model.*
import com.doujinmenu.android.security.BrowserPreferenceStore
import org.junit.Assert.*
import org.junit.Test

class DownloadDestinationTest {
    @Test fun deviceChoiceNeverQueuesPcAndOfflinePcChoiceIsDurable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val preferences = BrowserPreferenceStore(context)
        val previous = preferences.loadDownloadDestination()
        val location = preferences.loadDownloadLocation()
        val suppressed = preferences.isDesktopDownloadAlertSuppressed()
        val requests = DownloadRequestStore.get(context)
        val before = requests.requests.value
        val store = ViewModelStore()
        val fixture = GallerySummary(id = 9_900_001, title = "Download fixture", artists = emptyList(), series = emptyList(), galleryType = null,
            tags = emptyList(), thumbnailUrl = null, pageCount = 1, language = null)
        lateinit var model: MainViewModel
        try {
            preferences.saveDownloadDestination(DownloadDestination.ASK)
            preferences.saveDownloadLocation(null)
            preferences.suppressDesktopDownloadAlert(false)
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
                model.downloadGallery(fixture)
                assertEquals(fixture, model.uiState.pendingDownloadGallery)
                assertEquals(before, requests.requests.value)
                model.chooseDownloadDestination(DownloadDestination.DEVICE)
                assertTrue(model.uiState.downloadFolderRequired)
                assertEquals(before, requests.requests.value)
                model.cancelDownloadPrompt()
                model.setDownloadDestination(DownloadDestination.DESKTOP)
                assertTrue(model.uiState.desktopDownloadConnectionPrompt)
                model.dismissDesktopDownloadConnectionPrompt(true)
                model.downloadGallery(fixture)
            }
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (requests.requests.value.none { it.galleryId == fixture.id } && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50)
            val request = requests.requests.value.first { it.galleryId == fixture.id }
            assertEquals(DownloadTarget.DESKTOP, request.target)
            assertTrue(request.pendingForDesktop(model.uiState.selectedProfileId.orEmpty()))
            assertTrue(java.io.File(context.filesDir, "download-requests.json").readText().contains(request.id))
            instrumentation.runOnMainSync { assertFalse(model.uiState.desktopDownloadConnectionPrompt) }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            requests.update { before }
            preferences.saveDownloadDestination(previous); preferences.saveDownloadLocation(location); preferences.suppressDesktopDownloadAlert(suppressed)
        }
    }
}
