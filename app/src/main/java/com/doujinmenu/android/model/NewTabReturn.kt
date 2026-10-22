package com.doujinmenu.android.model

const val NEW_TAB_RETURN_WINDOW_MS = 30_000L

data class NewTabReturn(
    val tabId: String,
    val isGallery: Boolean,
    val originTabId: String,
    val originIsGallery: Boolean,
    val openedAtMillis: Long? = null,
) {
    fun isAvailable(nowMillis: Long): Boolean = openedAtMillis?.let {
        nowMillis - it in 0 until NEW_TAB_RETURN_WINDOW_MS
    } == true
}

fun BrowserWorkspace.closeTabReturningTo(
    tabId: String,
    originTabId: String,
    fallback: BrowserPage = BrowserPage.Search(),
): BrowserWorkspace = closeTab(tabId, fallback).let { closed ->
    if (closed.tabs.any { it.id == originTabId }) closed.selectTab(originTabId) else closed
}
