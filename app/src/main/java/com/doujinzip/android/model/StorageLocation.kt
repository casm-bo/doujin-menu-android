package com.doujinzip.android.model

data class StorageLocation(
    val uri: String,
    val displayName: String,
    val isCloud: Boolean = false,
)
