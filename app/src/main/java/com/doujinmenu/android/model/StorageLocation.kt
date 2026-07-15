package com.doujinmenu.android.model

data class StorageLocation(
    val uri: String,
    val displayName: String,
    val isCloud: Boolean = false,
)
