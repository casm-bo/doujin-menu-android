package com.doujinmenu.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import com.doujinmenu.android.model.*
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class ReaderImages(private val context: Context) {
    private val directory = File(context.cacheDir, "reader-images")
    private fun cached(source: ReaderImageSource): File {
        val identity = source.uri + source.headers.toSortedMap().entries.joinToString()
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.image")
    }

    suspend fun probe(source: ReaderImageSource): ImageDimensions? = withContext(Dispatchers.IO) {
        val file = cached(source)
        if (file.isFile) return@withContext dimensions(file)
        // A header probe never downloads or decodes the complete image.
        val bytes = open(source, headerOnly = true).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (output.size() < 64 * 1024) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer, 0, minOf(buffer.size, 64 * 1024 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        bounds(options)
    }

    suspend fun file(source: ReaderImageSource): File = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create reader cache" }
            val target = cached(source)
            if (target.isFile) { target.setLastModified(System.currentTimeMillis()); return@withLock target }
            val temporary = File(directory, "${target.name}.part")
            try {
                open(source).use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > 256L * 1024 * 1024) throw IOException("Image exceeds reader cache limit")
                        output.write(buffer, 0, read)
                    }
                } }
                if (!temporary.renameTo(target)) throw IOException("Cannot save reader image")
                var total = directory.listFiles { file -> file.extension == "image" }.orEmpty().sumOf { it.length() }
                directory.listFiles { file -> file.extension == "image" && file != target }.orEmpty().sortedBy { it.lastModified() }.forEach {
                    val length = it.length()
                    if (total > 512L * 1024 * 1024 && it.delete()) total -= length
                }
                target
            } finally { temporary.delete() }
        }
    }

    fun dimensions(file: File): ImageDimensions? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return bounds(options)
    }

    @Suppress("DEPRECATION")
    fun tile(file: File, tile: WebtoonTile, displayWidth: Int): Bitmap {
        val decoder = requireNotNull(BitmapRegionDecoder.newInstance(file.absolutePath, false))
        try {
            var sample = 1
            val targetWidth = displayWidth.coerceIn(1, 1600)
            while (decoder.width / sample > targetWidth || tile.sourceHeight / sample > 2048) sample *= 2
            return requireNotNull(decoder.decodeRegion(Rect(0, tile.top, decoder.width, tile.bottom), BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }))
        } finally { decoder.recycle() }
    }

    private fun bounds(options: BitmapFactory.Options): ImageDimensions? =
        if (options.outWidth > 0 && options.outHeight > 0) ImageDimensions(options.outWidth, options.outHeight) else null

    private fun open(source: ReaderImageSource, headerOnly: Boolean = false): InputStream {
        val uri = Uri.parse(source.uri)
        if (uri.scheme !in listOf("http", "https")) return when (uri.scheme) {
            "file" -> File(requireNotNull(uri.path)).inputStream()
            null -> File(source.uri).inputStream()
            else -> context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open image")
        }
        val original = URL(source.uri)
        var current = original
        repeat(6) {
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000; connection.readTimeout = 10_000
            source.headers.forEach { (name, value) ->
                val sameOrigin = original.protocol == current.protocol && original.host == current.host && original.port == current.port
                if (!name.equals("Authorization", true) || sameOrigin) connection.setRequestProperty(name, value)
            }
            if (headerOnly) connection.setRequestProperty("Range", "bytes=0-65535")
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val next = URL(current, connection.getHeaderField("Location") ?: throw IOException("Missing redirect"))
                    require(next.protocol in listOf("http", "https") && !(current.protocol == "https" && next.protocol == "http"))
                    current = next; connection.disconnect()
                } else {
                    if (status !in listOf(200, 206)) throw IOException("Image HTTP $status")
                    return object : FilterInputStream(connection.inputStream) {
                        override fun close() { try { super.close() } finally { connection.disconnect() } }
                    }
                }
            } catch (error: Exception) { connection.disconnect(); throw error }
        }
        throw IOException("Too many image redirects")
    }

    private companion object { val downloadMutex = Mutex() }
}
