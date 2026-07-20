package com.doujinmenu.android.data

import com.doujinmenu.android.model.LibraryMetadata

data class ParsedInfoTxt(
    val title: String? = null,
    val uuid: String? = null,
    val metadata: LibraryMetadata = LibraryMetadata(),
)

object InfoTxtParser {
    fun parse(content: String): ParsedInfoTxt {
        val values = content.lineSequence().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator < 0) null else line.substring(0, separator).trim() to
                line.substring(separator + 1).trim()
        }.filter { it.second.isNotBlank() }.toMap()
        fun list(key: String) = values[key].orEmpty().split(',')
            .map { it.trim().replace(Regex("\\s+"), "_") }.filter(String::isNotBlank)
        val uuid = values.entries.firstOrNull { (key) ->
            key.equals("uuid", ignoreCase = true) || key == "고유 UUID"
        }?.value
        return ParsedInfoTxt(
            title = values["제목"],
            uuid = uuid,
            metadata = LibraryMetadata(
                hitomiId = values["갤러리 넘버"] ?: values["갤러리 번호"],
                artists = list("작가"),
                groups = list("그룹"),
                galleryType = values["타입"] ?: values["종류"],
                series = list("시리즈"),
                characters = list("캐릭터"),
                tags = list("태그"),
                language = values["언어"],
            ),
        )
    }
}
