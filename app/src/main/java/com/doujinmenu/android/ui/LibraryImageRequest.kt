package com.doujinmenu.android.ui

import android.content.Context
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade

internal fun libraryImageRequest(context: Context, uri: String, token: String?): Any {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .crossfade(true)
    if (!token.isNullOrBlank() && uri.startsWith("http")) {
        request.httpHeaders(
            NetworkHeaders.Builder()
                .set("Authorization", "Bearer $token")
                .build(),
        )
    }
    return request.build()
}
