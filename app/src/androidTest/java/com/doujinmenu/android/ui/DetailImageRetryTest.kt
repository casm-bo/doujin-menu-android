package com.doujinmenu.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinmenu.android.R
import com.doujinmenu.android.ui.theme.DoujinMenuTheme
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DetailImageRetryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failuresRetryAutomaticallyThenOfferManualRetry() {
        val server = ServerSocket(0)
        val requests = AtomicInteger()
        val executor = Executors.newSingleThreadExecutor()
        val image = ByteArrayOutputStream().apply {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.BLUE)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, this); bitmap.recycle()
            }
        }.toByteArray()
        executor.submit {
            runCatching { while (!server.isClosed) server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                val success = requests.incrementAndGet() > 4
                val body = if (success) image else byteArrayOf()
                socket.getOutputStream().use { output ->
                    output.write("HTTP/1.1 ${if (success) "200 OK" else "503 Service Unavailable"}\r\nContent-Type: image/png\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(body)
                }
            } }
        }
        try {
            val retry = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ui_try_again)
            compose.setContent { DoujinMenuTheme { DetailImage("http://127.0.0.1:${server.localPort}/preview.png", "Fixture", Modifier.size(160.dp)) } }
            compose.waitUntil(15_000) { requests.get() >= 4 && compose.onAllNodesWithContentDescription(retry).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(4, requests.get())
            compose.onNodeWithContentDescription(retry).performClick()
            compose.waitUntil(10_000) { requests.get() == 5 }
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(retry).fetchSemanticsNodes().isEmpty() }
            assertEquals(5, requests.get())
        } finally { server.close(); executor.shutdownNow() }
    }
}
