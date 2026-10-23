package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class StoragePathTest {
    @Test fun pathsAreReadableWithoutExposingSafAuthoritiesOrAbsoluteHomePaths() {
        assertEquals("~/Download/Books", readableStoragePath("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FBooks", "Books"))
        assertEquals("~/ABCD-1234/Comics", readableStoragePath("content://com.android.externalstorage.documents/tree/ABCD-1234%3AComics"))
        assertEquals("~/Downloads/Books", readableStoragePath("C:\\Users\\fixture-user\\Downloads\\Books"))
        assertEquals("~/Downloads", readableStoragePath("/storage/emulated/0/Downloads"))
        assertEquals("~/Cloud books", readableStoragePath("companion://private-server/library/42", "Cloud books"))
        assertEquals("~/A+B", readableStoragePath("content://com.android.externalstorage.documents/tree/primary%3AA+B"))
    }
}
