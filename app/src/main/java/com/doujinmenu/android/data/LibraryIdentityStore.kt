package com.doujinmenu.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.LibraryMetadata
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps local-library identities and parsed metadata inside the app instead of
 * writing UUID sidecars next to user-owned gallery files.
 */
class LibraryIdentityStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_IDENTITIES (
                source_key TEXT PRIMARY KEY NOT NULL,
                source_uri TEXT NOT NULL,
                sync_id TEXT NOT NULL,
                hitomi_id TEXT,
                title TEXT,
                metadata_json TEXT NOT NULL,
                modified_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX index_library_identities_sync_id ON $TABLE_IDENTITIES(sync_id)",
        )
        database.execSQL(
            "CREATE INDEX index_library_identities_hitomi_id ON $TABLE_IDENTITIES(hitomi_id)",
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun resolve(
        sourceKey: String,
        sourceUri: String,
        parsedInfo: ParsedInfoTxt,
        modifiedAt: Long,
    ): StoredLibraryIdentity {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val existing = findBySourceKey(database, sourceKey)
            val sameGallery = parsedInfo.metadata.hitomiId
                ?.let { findFirstByHitomiId(database, it) }
            val cachedInfo = existing?.info ?: sameGallery?.info ?: ParsedInfoTxt()
            val mergedInfo = mergeParsedInfo(parsedInfo, cachedInfo)
            val syncId = existing?.syncId
                ?: parsedInfo.uuid?.normalizedSyncId()
                ?: sameGallery?.syncId
                ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(COLUMN_SOURCE_KEY, sourceKey)
                put(COLUMN_SOURCE_URI, sourceUri)
                put(COLUMN_SYNC_ID, syncId)
                put(COLUMN_HITOMI_ID, mergedInfo.metadata.hitomiId)
                put(COLUMN_TITLE, mergedInfo.title)
                put(COLUMN_METADATA_JSON, mergedInfo.metadata.toJson().toString())
                put(COLUMN_MODIFIED_AT, modifiedAt)
                put(COLUMN_LAST_SEEN_AT, now)
            }
            database.insertWithOnConflict(
                TABLE_IDENTITIES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            database.setTransactionSuccessful()
            return StoredLibraryIdentity(syncId, mergedInfo)
        } finally {
            database.endTransaction()
        }
    }

    /**
     * The desktop database remains authoritative for an existing Hitomi book.
     * When a legacy sidecar is absent, adopt its sync ID using the immutable
     * gallery ID and persist that mapping for future offline scans.
     */
    fun reconcileWithDesktop(
        localBooks: List<LibraryBook>,
        desktopBooks: List<LibraryBook>,
    ): LibraryIdentityReconciliation {
        val desktopSyncIdsByHitomiId = desktopBooks.asSequence()
            .mapNotNull { book ->
                val hitomiId = book.metadata.hitomiId?.normalizedHitomiId() ?: return@mapNotNull null
                val syncId = book.syncId?.normalizedSyncId() ?: return@mapNotNull null
                hitomiId to syncId
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ids) -> ids.distinct().singleOrNull() }
            .filterValues { it != null }
            .mapValues { (_, syncId) -> requireNotNull(syncId) }

        if (desktopSyncIdsByHitomiId.isEmpty()) {
            return LibraryIdentityReconciliation(localBooks, emptyMap())
        }

        val aliases = linkedMapOf<String, String>()
        val reconciled = localBooks.map { book ->
            val hitomiId = book.metadata.hitomiId?.normalizedHitomiId() ?: return@map book
            val desktopSyncId = desktopSyncIdsByHitomiId[hitomiId] ?: return@map book
            if (book.syncId?.normalizedSyncId() == desktopSyncId) return@map book

            writableDatabase.update(
                TABLE_IDENTITIES,
                ContentValues().apply { put(COLUMN_SYNC_ID, desktopSyncId) },
                "$COLUMN_HITOMI_ID = ?",
                arrayOf(hitomiId),
            )
            val newId = stableFileId(desktopSyncId)
            aliases[book.id] = newId
            book.copy(id = newId, syncId = desktopSyncId)
        }
        return LibraryIdentityReconciliation(reconciled, aliases)
    }

    private fun findBySourceKey(database: SQLiteDatabase, sourceKey: String): StoredRow? =
        findFirst(database, "$COLUMN_SOURCE_KEY = ?", arrayOf(sourceKey))

    private fun findFirstByHitomiId(database: SQLiteDatabase, hitomiId: String): StoredRow? =
        findFirst(database, "$COLUMN_HITOMI_ID = ?", arrayOf(hitomiId.normalizedHitomiId()))

    private fun findFirst(
        database: SQLiteDatabase,
        selection: String,
        selectionArgs: Array<String>,
    ): StoredRow? = database.query(
        TABLE_IDENTITIES,
        arrayOf(COLUMN_SYNC_ID, COLUMN_TITLE, COLUMN_METADATA_JSON),
        selection,
        selectionArgs,
        null,
        null,
        "$COLUMN_LAST_SEEN_AT DESC",
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val metadata = runCatching {
            metadataFromJson(JSONObject(cursor.getString(2)))
        }.getOrDefault(LibraryMetadata())
        StoredRow(
            syncId = cursor.getString(0).normalizedSyncId(),
            info = ParsedInfoTxt(
                title = cursor.getString(1),
                metadata = metadata,
            ),
        )
    }

    private data class StoredRow(
        val syncId: String,
        val info: ParsedInfoTxt,
    )

    companion object {
        private const val DATABASE_NAME = "library_identity.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_IDENTITIES = "library_identities"
        private const val COLUMN_SOURCE_KEY = "source_key"
        private const val COLUMN_SOURCE_URI = "source_uri"
        private const val COLUMN_SYNC_ID = "sync_id"
        private const val COLUMN_HITOMI_ID = "hitomi_id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_METADATA_JSON = "metadata_json"
        private const val COLUMN_MODIFIED_AT = "modified_at"
        private const val COLUMN_LAST_SEEN_AT = "last_seen_at"

        internal fun stableFileId(syncId: String) = "file:${syncId.normalizedSyncId()}"
    }
}

data class StoredLibraryIdentity(
    val syncId: String,
    val info: ParsedInfoTxt,
)

data class LibraryIdentityReconciliation(
    val books: List<LibraryBook>,
    val idAliases: Map<String, String>,
)

internal fun mergeParsedInfo(preferred: ParsedInfoTxt, fallback: ParsedInfoTxt): ParsedInfoTxt =
    ParsedInfoTxt(
        title = preferred.title ?: fallback.title,
        uuid = preferred.uuid ?: fallback.uuid,
        metadata = LibraryMetadata(
            hitomiId = preferred.metadata.hitomiId ?: fallback.metadata.hitomiId,
            artists = preferred.metadata.artists.ifEmpty { fallback.metadata.artists },
            groups = preferred.metadata.groups.ifEmpty { fallback.metadata.groups },
            galleryType = preferred.metadata.galleryType ?: fallback.metadata.galleryType,
            series = preferred.metadata.series.ifEmpty { fallback.metadata.series },
            characters = preferred.metadata.characters.ifEmpty { fallback.metadata.characters },
            tags = preferred.metadata.tags.ifEmpty { fallback.metadata.tags },
            language = preferred.metadata.language ?: fallback.metadata.language,
        ),
    )

private fun LibraryMetadata.toJson() = JSONObject()
    .put("hitomiId", hitomiId)
    .put("artists", JSONArray(artists))
    .put("groups", JSONArray(groups))
    .put("galleryType", galleryType)
    .put("series", JSONArray(series))
    .put("characters", JSONArray(characters))
    .put("tags", JSONArray(tags))
    .put("language", language)

private fun metadataFromJson(json: JSONObject) = LibraryMetadata(
    hitomiId = json.optString("hitomiId").takeIf(String::isNotBlank),
    artists = json.stringList("artists"),
    groups = json.stringList("groups"),
    galleryType = json.optString("galleryType").takeIf(String::isNotBlank),
    series = json.stringList("series"),
    characters = json.stringList("characters"),
    tags = json.stringList("tags"),
    language = json.optString("language").takeIf(String::isNotBlank),
)

private fun JSONObject.stringList(key: String): List<String> = buildList {
    val values = optJSONArray(key) ?: return@buildList
    repeat(values.length()) { index ->
        values.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun String.normalizedSyncId() = trim().lowercase()
private fun String.normalizedHitomiId() = trim()
