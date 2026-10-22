package com.doujinmenu.android

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.doujinmenu.android.network.HitomiThumbnailInterceptor

class DoujinMenuApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(HitomiThumbnailInterceptor()) }
        .build()
}
