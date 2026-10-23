package com.doujinzip.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.doujinzip.android.model.ReaderImageSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Editable covers are user data; clearing thumbnail/reader caches must not remove them. */
class PresentationCovers(private val context: Context) {
    private val directory = File(context.filesDir, "presentation-covers")
    fun file(hash: String): File {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        return File(directory, "$hash.image")
    }
    fun contains(hash: String): Boolean = file(hash).isFile
    fun bytes(hash: String): ByteArray {
        val source = file(hash)
        require(source.length() in 1..MAX_BYTES)
        return source.readBytes()
    }

    suspend fun import(source: ReaderImageSource): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(source.uri)
        var temporary: File? = null
        try {
            val input = when (uri.scheme) {
                "http", "https" -> ReaderImages(context).file(source)
                "file" -> File(requireNotNull(uri.path))
                null -> File(source.uri)
                else -> File.createTempFile("cover-import-", ".image", context.cacheDir).also { target ->
                    temporary = target
                    val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open selected cover")
                    stream.use { incoming -> target.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = incoming.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 256L * 1024 * 1024) throw IOException("Selected image is too large")
                            output.write(buffer, 0, count)
                        }
                    } }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a supported image" }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 2048) sample *= 2
            val decoded = requireNotNull(BitmapFactory.decodeFile(input.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }))
            try {
                val oriented = orient(decoded, input)
                try {
                val scale = minOf(512f / oriented.width, 1024f / oriented.height, 1f)
                val resized = Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true)
                try {
                    val output = ByteArrayOutputStream()
                    @Suppress("DEPRECATION") val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    check(resized.compress(format, 85, output))
                    val bytes = output.toByteArray()
                    val hash = digest(bytes)
                    put(hash, bytes)
                    hash
                } finally { if (resized !== oriented) resized.recycle() }
                } finally { if (oriented !== decoded) oriented.recycle() }
            } finally { decoded.recycle() }
        } finally { temporary?.delete() }
    }

    fun put(hash: String, bytes: ByteArray) {
        val target = file(hash)
        require(bytes.size.toLong() in 1..MAX_BYTES && digest(bytes) == hash) { "Cover content does not match its hash" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..4096 && bounds.outWidth.toLong() * bounds.outHeight <= 4_194_304)
        check(directory.isDirectory || directory.mkdirs())
        if (target.isFile) return
        val temporary = File.createTempFile("cover-", ".part", directory)
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            check(temporary.renameTo(target) || target.isFile) { "Cannot save selected cover" }
        } finally { temporary.delete() }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun orient(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching { ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    private companion object { const val MAX_BYTES = 5L * 1024 * 1024 }
}
