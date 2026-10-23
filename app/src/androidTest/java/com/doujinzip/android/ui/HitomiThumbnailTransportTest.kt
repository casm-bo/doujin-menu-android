package com.doujinzip.android.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import com.doujinzip.android.network.HitomiImageContext
import com.doujinzip.android.network.HitomiThumbnailInterceptor
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HitomiThumbnailTransportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val hash = "0".repeat(61) + "123"
    private val savedUrl = "https://tn.gold-usergeneratedcontent.net/webpbigtn/3/12/$hash.webp"

    @Test fun cachedNotificationAndSearchRequestsUseSharedRoutingAndHeaders() = runBlocking<Unit> {
        var contextLoads = 0
        val interceptor = HitomiThumbnailInterceptor {
            contextLoads++
            HitomiImageContext(setOf(786), 0, "12345/")
        }
        val loader = syntheticLoader(interceptor)
        try {
            val notice = loader.execute(ImageRequest.Builder(context).data(savedUrl).build())
            assertTrue(notice is SuccessResult)
            assertEquals(savedUrl.replace("//tn.", "//btn."), notice.request.data)
            assertEquals(listOf("https://hitomi.la/"), notice.request.httpHeaders.getAll("Referer"))
            val search = loader.execute(hitomiImageRequest(context, savedUrl, 42))
            assertTrue(search is SuccessResult)
            assertEquals(notice.request.data, search.request.data)
            assertEquals(listOf("https://hitomi.la/reader/42.html"), search.request.httpHeaders.getAll("Referer"))
            assertEquals(1, contextLoads)
        } finally { loader.shutdown() }
    }

    @Test fun companionCredentialsStayOnTheirOriginalOrigin() = runBlocking<Unit> {
        val loader = syntheticLoader(HitomiThumbnailInterceptor { HitomiImageContext(setOf(786), 0, "12345/") })
        try {
            val headers = NetworkHeaders.Builder().set("Authorization", "Bearer synthetic-fixture").build()
            val companionUrl = "https://example.invalid/cover"
            val companion = loader.execute(ImageRequest.Builder(context).data(companionUrl).httpHeaders(headers).build())
            assertEquals(companionUrl, companion.request.data)
            assertEquals(headers.asMap(), companion.request.httpHeaders.asMap())
            val thumbnail = loader.execute(ImageRequest.Builder(context).data(savedUrl).httpHeaders(headers).build())
            assertFalse(thumbnail.request.httpHeaders.asMap().keys.any { it.equals("Authorization", true) })
        } finally { loader.shutdown() }
    }

    @Test fun applicationInstallsThumbnailInterceptor() {
        assertTrue(context.applicationContext is SingletonImageLoader.Factory)
        assertTrue(SingletonImageLoader.get(context).components.interceptors.any { it is HitomiThumbnailInterceptor })
    }

    @Test fun offlineRequestsDoNotFetchRoutingForEveryCard() = runBlocking<Unit> {
        var contextLoads = 0
        val loader = syntheticLoader(HitomiThumbnailInterceptor {
            contextLoads++
            throw IOException("Synthetic offline response")
        })
        try {
            repeat(3) {
                assertTrue(loader.execute(ImageRequest.Builder(context).data(savedUrl).build()) is ErrorResult)
            }
            assertEquals(1, contextLoads)
        } finally { loader.shutdown() }
    }

    private fun syntheticLoader(interceptor: Interceptor): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(interceptor)
            add(Interceptor { chain ->
                SuccessResult(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImage(), chain.request)
            })
        }
        .build()
}
