package com.doujinzip.android.data

import java.io.File
import java.util.Locale
import java.util.zip.ZipException

internal fun archiveCacheFile(directory: File, prefix: String, entryName: String): File {
    // Keep archive-controlled text out of the output path, including its extension.
    val extension = when (entryName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg" -> "jpg"
        "jpeg" -> "jpeg"
        "png" -> "png"
        "webp" -> "webp"
        "gif" -> "gif"
        "bmp" -> "bmp"
        "avif" -> "avif"
        else -> "jpg"
    }
    val parent = directory.canonicalFile
    val file = File(parent, "$prefix.$extension").canonicalFile
    if (file.parentFile != parent) throw ZipException("Archive cache path escapes destination")
    return file
}
