package com.doujinzip.android.ui

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.BrowserPage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarTitleTest {
    @Test fun actualToolbarFormatterUsesSubmittedQueryAndLocalizedFallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("en", "ko", "ja", "zh")) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
            assertEquals("tag:full_color", BrowserPage.Search(query = "draft", submittedQuery = "tag:full_color", currentPage = 1).localizedToolbarTitle(resources))
            assertEquals("language:korean", BrowserPage.Search(query = "draft", submittedQueries = listOf("language:korean"), currentPage = 1).localizedToolbarTitle(resources))
            assertEquals("korean", BrowserPage.Search(preferredLanguages = setOf("korean")).localizedToolbarTitle(resources))
            assertEquals(resources.getString(R.string.ui_new_search), BrowserPage.Search().localizedToolbarTitle(resources))
            assertEquals(resources.getString(R.string.ui_gallery), BrowserPage.LibraryHome().localizedToolbarTitle(resources))
        }
    }
}
