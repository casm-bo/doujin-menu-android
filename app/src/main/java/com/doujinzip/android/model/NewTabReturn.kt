package com.doujinzip.android.model

data class NewTabReturn(
    val tabId: String,
    val isGallery: Boolean,
    val originTabId: String,
    val originIsGallery: Boolean,
) {
    fun isAtRoot(workspace: BrowserWorkspace, gallery: Boolean): Boolean =
        isGallery == gallery && workspace.activeTabId == tabId && !workspace.activeTab.canGoBack
}

fun BrowserWorkspace.closeTabReturningTo(
    tabId: String,
    originTabId: String,
    fallback: BrowserPage = BrowserPage.Search(),
): BrowserWorkspace = closeTab(tabId, fallback).let { closed ->
    if (closed.tabs.any { it.id == originTabId }) closed.selectTab(originTabId) else closed
}
