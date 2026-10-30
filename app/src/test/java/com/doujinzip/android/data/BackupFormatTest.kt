package com.doujinzip.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupFormatTest {
    @Test fun settingsKeepTypesAndDeviceCredentialsAreExcluded() {
        val values = mapOf("width" to 1f, "page" to 3, "time" to 9L, "enabled" to true, "names" to setOf("synthetic"), "text" to "fixture")
        assertEquals(values, decodeBackupPreferences(encodeBackupPreferences(values)))
        assertTrue(backupPreferenceAllowed("appearance", "normal_width"))
        assertTrue(backupPreferenceAllowed("library_preferences", "search_grid_columns"))
        assertTrue(backupPreferenceAllowed("recent_history", "entry:fixture"))
        assertTrue(backupPreferenceAllowed("updates", "skipped_update"))
        assertFalse(backupPreferenceAllowed("updates", "last_update_check"))
        assertFalse(backupPreferenceAllowed("secure_companion_profiles", "ciphertext"))
        assertFalse(backupPreferenceAllowed("app_lock", "credential"))
        assertFalse(backupPreferenceAllowed("browser_preferences", "library_locations"))
        assertFalse(backupPreferenceAllowed("library_preferences", "book_state_sync_cursors"))
    }
    @Test fun zipRoundTripAndUnsupportedVersionOrPathAreRejected() {
        val bytes = "synthetic cover".toByteArray()
        val archive = BackupArchive(JSONObject().put("format", "doujin-local-backup").put("version", 1), mapOf(BackupFormat.digest(bytes) to bytes))
        val out = ByteArrayOutputStream(); BackupFormat.write(archive, out)
        assertEquals(BackupFormat.digest(out.toByteArray()), out.toByteArray().inputStream().use(BackupFormat::streamDigest))
        val read = BackupFormat.read(out.toByteArray().inputStream())
        assertArrayEquals(bytes, read.covers.values.single())
        archive.data.put("version", 2)
        val newer = ByteArrayOutputStream(); BackupFormat.write(archive, newer)
        assertTrue(runCatching { BackupFormat.read(newer.toByteArray().inputStream()) }.isFailure)
        val bad = ByteArrayOutputStream()
        ZipOutputStream(bad).use { it.putNextEntry(ZipEntry("../escape")); it.write(bytes); it.closeEntry() }
        assertTrue(runCatching { BackupFormat.read(bad.toByteArray().inputStream()) }.isFailure)
    }
    @Test fun damagedContentIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("backup.json")); it.write("{}".toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry("checksums.json")); it.write("{\"backup.json\":\"wrong\"}".toByteArray()); it.closeEntry()
        }
        assertTrue(runCatching { BackupFormat.read(out.toByteArray().inputStream()) }.isFailure)
    }
    @Test fun oversizedDecompressedContentIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            val block = ByteArray(8192)
            repeat(BACKUP_LIMIT / block.size + 1) { zip.write(block) }
            zip.closeEntry()
        }
        assertTrue(runCatching { BackupFormat.read(out.toByteArray().inputStream()) }.isFailure)
    }
}
