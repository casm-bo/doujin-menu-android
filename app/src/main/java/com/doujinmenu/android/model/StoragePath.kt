package com.doujinmenu.android.model

/** Presentation only: SAF permissions and file operations always use the original URI. */
internal fun readableStoragePath(raw: String, displayName: String = ""): String {
    val decoded = runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw).replace('\\', '/')
    val path = when {
        decoded.startsWith("content://") -> {
            val document = decoded.substringAfter("/tree/", "").substringBefore("/document/").substringBefore('?')
            when {
                document.startsWith("primary:") -> document.substringAfter(':')
                ':' in document -> document.replace(':', '/')
                document.equals("downloads", true) -> "Downloads"
                else -> displayName
            }
        }
        decoded.contains("://") -> displayName
        else -> decoded.replace(Regex("(?i)^[a-z]:/Users/[^/]+/"), "")
            .replace(Regex("^/(?:home|Users)/[^/]+/"), "")
            .removePrefix("/storage/emulated/0/").replace(Regex("(?i)^[a-z]:/"), "").removePrefix("~/")
    }
    return "~/" + path.trim('/').ifBlank { displayName.trim('/') }
}
