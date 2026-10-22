package com.doujinmenu.android.network

import coil3.intercept.Interceptor
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class HitomiThumbnailInterceptor(
    private val loadContext: suspend () -> HitomiImageContext = { HitomiClient().getImageContext() },
) : Interceptor {
    private val mutex = Mutex()
    private var cached: Pair<Long, Result<HitomiImageContext>>? = null

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data.toString()
        val thumbnail = hitomiThumbnailParts(url) ?: return chain.proceed()
        val context = mutex.withLock {
            (cached?.takeIf {
                System.nanoTime() - it.first < if (it.second.isSuccess) 300_000_000_000L else 15_000_000_000L
            }?.second ?: runCatching { loadContext() }
                .onFailure { if (it is CancellationException) throw it }
                .also { cached = System.nanoTime() to it }).getOrThrow()
        }
        val headers = NetworkHeaders.Builder()
        chain.request.httpHeaders.asMap().forEach { (name, values) ->
            if (!name.equals("Authorization", ignoreCase = true)) values.forEach { headers.add(name, it) }
        }
        if (chain.request.httpHeaders.asMap().keys.none { it.equals("Referer", ignoreCase = true) }) {
            headers["Referer"] = "https://hitomi.la/"
        }
        // Resolve at load time so already saved notifications and search caches also follow CDN changes.
        val request = chain.request.newBuilder()
            .data(context.thumbnailUrl(thumbnail.second, thumbnail.first))
            .httpHeaders(headers.build())
            .build()
        return chain.withRequest(request).proceed()
    }
}

internal fun hitomiThumbnailParts(url: String): Pair<String, String>? {
    val match = thumbnailPattern.matchEntire(url) ?: return null
    val (directory, last, pair, hash) = match.destructured
    if (!hash.endsWith(pair + last, ignoreCase = true)) return null
    return directory to hash
}

private val thumbnailPattern = Regex("https://[ab]?tn\\.(?:gold-usergeneratedcontent\\.net|hitomi\\.la)/(webp(?:big|small)tn)/([a-fA-F0-9])/([a-fA-F0-9]{2})/([a-fA-F0-9]{64})\\.webp")
