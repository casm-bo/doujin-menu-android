package com.doujinzip.android.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.data.ReaderImages
import com.doujinzip.android.model.ReaderImageSource
import com.doujinzip.android.model.ViewerReadingMode
import com.doujinzip.android.security.LibraryPreferenceStore
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderImageTransportTest {
    @Test fun headerProbeRequestsRangeAndDropsAuthorizationAcrossOrigins() = runBlocking<Unit> {
        val image = Bitmap.createBitmap(64, 256, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        image.recycle()
        val first = ServerSocket(0).apply { soTimeout = 5000 }
        val second = ServerSocket(0).apply { soTimeout = 5000 }
        val request = AtomicReference("")
        val origin = thread {
            first.use { server -> server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                socket.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${second.localPort}/image\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            } }
        }
        val target = thread {
            second.use { server -> server.accept().use { socket ->
                socket.soTimeout = 5000
                val lines = mutableListOf<String>()
                val input = socket.getInputStream().bufferedReader()
                while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; lines += line }
                request.set(lines.joinToString("\n"))
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes); flush()
                }
            } }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val size = ReaderImages(context).probe(ReaderImageSource("http://127.0.0.1:${first.localPort}/image", mapOf("Authorization" to "Bearer test-only")))!!
        origin.join(5000); target.join(5000)
        assertEquals(64, size.width); assertEquals(256, size.height)
        assertTrue(request.get().contains("Range: bytes=0-65535", true))
        assertFalse(request.get().contains("Authorization", true))
    }

    @Test fun manualModeOverridesAreSavedPerWorkAndCanBeReset() {
        val store = LibraryPreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val key = "isolated-mode-test"
        try {
            store.saveReaderMode(key, ViewerReadingMode.WEBTOON)
            assertEquals(ViewerReadingMode.WEBTOON, LibraryPreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext).loadReaderMode(key))
            assertNull(store.loadReaderMode("another-isolated-work"))
            store.saveReaderMode(key, ViewerReadingMode.AUTO)
            assertEquals(ViewerReadingMode.AUTO, store.loadReaderMode(key))
            store.saveReaderMode(key, null)
            assertNull(store.loadReaderMode(key))
        } finally { store.saveReaderMode(key, null) }
    }
}
