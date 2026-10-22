package com.doujinzip.android.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.data.toJson
import com.doujinzip.android.model.*
import com.doujinzip.android.security.BrowserPreferenceStore
import com.doujinzip.android.security.SecureProfileStore
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileSwitchTest {
    @Test fun delayedPreviousPcCannotReplaceTheSelectedPcAndRemovalReloadsScope() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val server = ServerSocket(0)
        val workers = Executors.newCachedThreadPool()
        val requestedA = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val answeredA = CountDownLatch(1)
        val nonce = System.nanoTime().toString()
        val galleryId = "9800001"
        val profiles = listOf("A", "B").map { DesktopProfile("fixture-$nonce-$it", it, "http://127.0.0.1:${server.localPort}/$it", "token") }
        val profileStore = SecureProfileStore(context)
        val previous = profileStore.load()
        val browser = BrowserPreferenceStore(context)
        val workspace = browser.loadBrowserWorkspace()
        val store = ViewModelStore()
        workers.submit { runCatching { while (!server.isClosed) {
            val socket = server.accept()
            workers.submit { runCatching { socket.use {
                val input = it.getInputStream().bufferedReader()
                val path = input.readLine().substringAfter(' ').substringBefore(' ')
                while (!input.readLine().isNullOrEmpty()) { }
                val pc = if (path.startsWith("/A/")) "A" else "B"
                val data: Any = when {
                    path.contains("/library/presentations") -> JSONObject().put("values", JSONArray().put(
                        GalleryPresentation("hitomi:$galleryId", "Edited $pc", LibraryMetadata(hitomiId = galleryId), version = 1, revision = 1).toJson()
                    )).put("cursor", 1).put("hasMore", false)
                    path.contains("/library/books") -> {
                        if (pc == "A") { requestedA.countDown(); check(releaseA.await(10, TimeUnit.SECONDS)) }
                        JSONArray().put(JSONObject().put("id", 1).put("title", "Raw $pc").put("hitomiId", galleryId).put("pageCount", 1))
                    }
                    else -> JSONObject().put("path", "C:/Downloads/$pc")
                }
                val bytes = JSONObject().put("success", true).put("data", data).toString().toByteArray()
                runCatching { it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes) }
                if (pc == "A" && path.contains("/library/books")) answeredA.countDown()
            } } }
        } } }
        lateinit var model: MainViewModel
        fun waitFor(predicate: () -> Boolean) {
            val deadline = android.os.SystemClock.uptimeMillis() + 10_000
            do {
                var ready = false
                instrumentation.runOnMainSync { ready = predicate() }
                if (ready) return
                android.os.SystemClock.sleep(50)
            } while (android.os.SystemClock.uptimeMillis() < deadline)
            fail("Timed out waiting for selected PC library")
        }
        try {
            profileStore.save(profiles); browser.saveBrowserWorkspace(BrowserWorkspace.initial())
            instrumentation.runOnMainSync { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java] }
            assertTrue(requestedA.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { model.selectProfile(profiles[1].id) }
            waitFor { model.uiState.libraryBooks.singleOrNull()?.title == "Edited B" }
            releaseA.countDown()
            assertTrue(answeredA.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("Edited B", model.uiState.libraryBooks.single().title)
                assertEquals("Edited B", model.uiState.presentations.getValue("hitomi:$galleryId").value.title)
                model.removeProfile(profiles[1].id)
            }
            waitFor { model.uiState.libraryBooks.singleOrNull()?.title == "Edited A" }
            instrumentation.runOnMainSync {
                model.removeProfile(profiles[0].id)
                assertNull(model.uiState.selectedProfileId)
                assertTrue(model.uiState.libraryBooks.isEmpty())
                assertFalse("Removed PC metadata must not remain active", "hitomi:$galleryId" in model.uiState.presentations)
            }
        } finally {
            releaseA.countDown(); instrumentation.runOnMainSync { store.clear() }
            profileStore.save(previous); browser.saveBrowserWorkspace(workspace)
            server.close(); workers.shutdownNow()
        }
    }
}
