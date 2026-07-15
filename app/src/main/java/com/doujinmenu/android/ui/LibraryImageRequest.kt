package com.doujinmenu.android.ui

import android.content.Context
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

internal fun libraryImageRequest(context: Context, uri: String, token: String?): Any {
    if (token.isNullOrBlank() || !uri.startsWith("http")) return uri
    return ImageRequest.Builder(context)
        .data(uri)
        .httpHeaders(
            NetworkHeaders.Builder()
                .set("Authorization", "Bearer $token")
                .build(),
        )
        .build()
}
