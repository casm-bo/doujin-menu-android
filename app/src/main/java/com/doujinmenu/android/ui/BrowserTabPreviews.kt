package com.doujinmenu.android.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.doujinmenu.android.model.BrowserTab
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BrowserTabPreviewKey(val isGallery: Boolean, val tabId: String, val pageKey: String)

class BrowserTabPreviewCache(context: Context) {
    private val directory = File(context.cacheDir, "tab-previews")
    private val captureMutex = Mutex()
    internal var capture: (suspend () -> Unit)? = null
    private var revision by mutableIntStateOf(0)

    suspend fun captureNow() = captureMutex.withLock {
        try {
            capture?.invoke()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A disposable preview must never prevent tab navigation.
        }
    }

    fun preview(isGallery: Boolean, tab: BrowserTab): File? {
        revision // Refresh visible cards after a new capture.
        return file(BrowserTabPreviewKey(isGallery, tab.id, tab.currentPage.key)).takeIf { it.isFile }
    }

    internal suspend fun save(key: BrowserTabPreviewKey, source: Bitmap) {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val width = source.width.coerceAtMost(360)
            val height = (source.height.toLong() * width / source.width).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(source, width, height, true)
            try {
                val destination = file(key)
                val temporary = File(directory, "${destination.name}.tmp")
                try {
                    temporary.outputStream().use { output ->
                        check(scaled.compress(Bitmap.CompressFormat.JPEG, 75, output))
                    }
                    check(temporary.renameTo(destination))
                    trimTabPreviewFiles(directory, 32L * 1024 * 1024)
                } finally {
                    temporary.delete()
                }
            } finally {
                if (scaled !== source) scaled.recycle()
                source.recycle()
            }
        }
        revision++
    }

    private fun file(key: BrowserTabPreviewKey): File {
        val input = "${key.isGallery}:${key.tabId}:${key.pageKey}".toByteArray(Charsets.UTF_8)
        val name = MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
        return File(directory, "$name.jpg")
    }
}

internal fun trimTabPreviewFiles(directory: File, maxBytes: Long) {
    val files = directory.listFiles()?.filter { it.isFile && it.extension == "jpg" }
        ?.sortedBy { it.lastModified() }.orEmpty()
    var bytes = files.sumOf { it.length() }
    for (file in files) {
        if (bytes <= maxBytes) break
        val size = file.length()
        if (file.delete()) bytes -= size
    }
}

@Composable
fun BrowserTabPreviewHost(
    cache: BrowserTabPreviewCache,
    previewKey: BrowserTabPreviewKey?,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(cache, previewKey, layer) {
        val capture: suspend () -> Unit = {
            if (previewKey != null && layer.size.width > 0 && layer.size.height > 0) {
                cache.save(previewKey, layer.toImageBitmap().asAndroidBitmap())
            }
        }
        cache.capture = capture
        onDispose { if (cache.capture === capture) cache.capture = null }
    }
    Box(Modifier.fillMaxSize().drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }) { content() }
}
