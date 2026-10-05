package com.doujinmenu.android.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.doujinmenu.android.model.BrowserPage
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlin.math.cos
import kotlin.math.sin

private enum class MainDestination(
    val route: String,
    val label: String,
    val symbol: String,
) {
    Browser("browser", "검색", "⌕"),
    Library("library", "갤러리", "▦"),
    Downloads("downloads", "다운로드", "↓"),
    Settings("settings", "설정", "⚙"),
}

@Composable
fun DoujinMenuApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var backgroundTabNotice by remember { mutableStateOf<Job?>(null) }
    val openGalleryInNewTab: (Long) -> Unit = { galleryId ->
        viewModel.openGalleryBackgroundTab(galleryId)?.let { result ->
            backgroundTabNotice?.cancel()
            backgroundTabNotice = snackbarScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val action = snackbarHostState.showSnackbar(
                    message = when {
                        result.tabId == null -> "새 탭을 열지 못했습니다."
                        result.alreadyOpen -> "이미 열린 탭이 있습니다."
                        else -> "새 탭에 열었습니다."
                    },
                    actionLabel = result.tabId?.let { "이동" },
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (action == SnackbarResult.ActionPerformed &&
                    viewModel.uiState.browserWorkspace.tabs.any { it.id == result.tabId }) {
                    viewModel.tabPreviews.captureNow()
                    viewModel.selectBrowserTab(requireNotNull(result.tabId))
                }
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastMainDestination by rememberSaveable { mutableStateOf(MainDestination.Browser.route) }
    var searchOpenedFromGallery by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val appMessage = viewModel.uiState.message
    val appMessageIsError = viewModel.uiState.isError
    LaunchedEffect(appMessage) {
        if (appMessage != null) {
            snackbarHostState.showSnackbar(
                message = appMessage,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            viewModel.dismissMessage()
        }
    }

    val browserNavigationRevision = viewModel.uiState.browserNavigationRevision
    LaunchedEffect(browserNavigationRevision) {
        when (val page = viewModel.activeBrowserPage()) {
            is BrowserPage.LibraryHome -> {
                lastMainDestination = MainDestination.Library.route
                navController.popBackStack("main", inclusive = false)
            }
            is BrowserPage.Search -> {
                if (searchOpenedFromGallery) {
                    when {
                        navController.currentDestination?.route == SEARCH_FROM_GALLERY_ROUTE -> Unit
                        navController.popBackStack(SEARCH_FROM_GALLERY_ROUTE, inclusive = false) -> Unit
                        else -> navController.navigate(SEARCH_FROM_GALLERY_ROUTE) { launchSingleTop = true }
                    }
                } else {
                    lastMainDestination = MainDestination.Browser.route
                    navController.popBackStack("main", inclusive = false)
                }
            }
            is BrowserPage.OnlineGallery -> navController.navigate("gallery/${page.galleryId}") {
                popUpTo("main")
                launchSingleTop = true
            }
            is BrowserPage.LibraryBook -> navController.navigate("library-detail/${Uri.encode(page.bookId)}") {
                popUpTo("main")
                launchSingleTop = true
            }
            is BrowserPage.OnlineReader -> navController.navigate(
                "reader/${page.galleryId}?startPage=${page.startPage}",
            ) {
                popUpTo("main")
                launchSingleTop = true
            }
            is BrowserPage.LibraryReader -> navController.navigate(
                "library-reader/${Uri.encode(page.bookId)}?startPage=${page.startPage}",
            ) {
                popUpTo("main")
                launchSingleTop = true
            }
        }
    }

    val searchBack: () -> Unit = {
        if (!viewModel.goBackInBrowserTab()) navController.popBackStack()
    }
    val galleryBack: () -> Unit = {
        if (!viewModel.goBackInGalleryTab()) navController.popBackStack()
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val previewIsGallery = when {
        currentRoute == SEARCH_FROM_GALLERY_ROUTE -> false
        currentRoute?.startsWith("library-") == true -> true
        currentRoute?.startsWith("gallery/") == true || currentRoute?.startsWith("reader/") == true -> false
        currentRoute == "main" && lastMainDestination == MainDestination.Library.route -> true
        currentRoute == "main" && lastMainDestination == MainDestination.Browser.route -> false
        else -> null
    }
    val previewWorkspace = if (previewIsGallery == true) viewModel.uiState.galleryWorkspace else viewModel.uiState.browserWorkspace
    val previewKey = previewIsGallery?.let {
        BrowserTabPreviewKey(it, previewWorkspace.activeTabId, previewWorkspace.activeTab.currentPage.key)
    }
    BrowserTabPreviewHost(viewModel.tabPreviews, previewKey) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("main") {
                MainShell(
                    viewModel = viewModel,
                    initialDestinationRoute = lastMainDestination,
                    onDestinationChanged = { lastMainDestination = it },
                    onGalleryClick = {
                        val libraryBookId = viewModel.libraryBookIdForGallery(it)
                        if (libraryBookId != null) {
                            viewModel.openLibraryBookTab(libraryBookId)
                        } else {
                            viewModel.selectGallery(it)
                        }
                    },
                    onGalleryLongClick = openGalleryInNewTab,
                    onLibraryBookClick = viewModel::openLibraryBookTab,
                )
            }
            composable(SEARCH_FROM_GALLERY_ROUTE) {
                MainShell(
                    viewModel = viewModel,
                    initialDestinationRoute = MainDestination.Browser.route,
                    onDestinationChanged = {},
                    onGalleryClick = {
                        val libraryBookId = viewModel.libraryBookIdForGallery(it)
                        if (libraryBookId != null) viewModel.openLibraryBookTab(libraryBookId)
                        else viewModel.selectGallery(it)
                    },
                    onGalleryLongClick = openGalleryInNewTab,
                    onLibraryBookClick = viewModel::openLibraryBookTab,
                    onBackToOrigin = {
                        searchOpenedFromGallery = false
                        navController.popBackStack()
                    },
                )
            }
            composable("library-detail/{bookId}") { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                BackHandler(onBack = galleryBack)
                val currentBook = viewModel.uiState.libraryBooks.firstOrNull { it.id == bookId }
                LaunchedEffect(bookId, currentBook?.id) {
                    if (currentBook != null) viewModel.openLibraryBook(bookId)
                }
                val nextBook = currentBook?.let { viewModel.nextLibrarySeriesBook(it.id) }
                val previousBook = currentBook?.let { viewModel.previousLibrarySeriesBook(it.id) }
                val isSeriesBook = currentBook?.id in viewModel.uiState.customSeriesByBookId
                LibraryDetailScreen(
                    book = currentBook,
                    previousBook = previousBook,
                    nextBook = nextBook,
                    isSeriesBook = isSeriesBook,
                    progress = currentBook?.let { viewModel.uiState.libraryProgress[it.id] } ?: 0,
                    isLoading = viewModel.uiState.isLibraryBookLoading,
                    error = viewModel.uiState.libraryScanError,
                    onBack = galleryBack,
                    onOpenReader = { page -> viewModel.openLibraryReaderTab(bookId, page) },
                    onOpenPreviousBook = {
                        previousBook?.let { viewModel.openLibraryBookTab(it.id) }
                    },
                    onOpenNextBook = {
                        nextBook?.let { viewModel.openLibraryBookTab(it.id) }
                    },
                    onOpenSeriesList = {
                        currentBook?.id?.let { id ->
                            viewModel.uiState.customSeriesByBookId[id]?.name?.let { series ->
                                viewModel.setLibrarySeriesMode(true)
                                viewModel.selectLibrarySeries(series)
                                lastMainDestination = MainDestination.Library.route
                                navController.popBackStack("main", inclusive = false)
                            }
                        }
                    },
                    onSearchFacet = { facet ->
                        searchOpenedFromGallery = true
                        viewModel.searchFromFacet(facet)
                    },
                    onSearchLanguage = { language ->
                        searchOpenedFromGallery = true
                        viewModel.searchFromLanguage(language)
                    },
                    tabBar = { AppGalleryTabStrip(viewModel, onBack = galleryBack) },
                )
            }
            composable(
                route = "library-reader/{bookId}?startPage={startPage}",
                arguments = listOf(navArgument("startPage") {
                    type = NavType.IntType
                    defaultValue = -1
                }),
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                BackHandler(onBack = galleryBack)
                val book = viewModel.uiState.libraryBooks.firstOrNull { it.id == bookId }
                LaunchedEffect(bookId, book?.id) {
                    if (book != null) viewModel.openLibraryBook(bookId)
                }
                val nextBook = book?.let { viewModel.nextLibrarySeriesBook(it.id) }
                val requestedPage = entry.arguments?.getInt("startPage") ?: -1
                LocalReaderScreen(
                    book = book,
                    initialPage = if (requestedPage >= 0) requestedPage
                        else book?.let { viewModel.uiState.libraryProgress[it.id] } ?: 0,
                    isLoading = viewModel.uiState.isLibraryBookLoading,
                    error = viewModel.uiState.libraryScanError,
                    favorite = book?.id in viewModel.uiState.libraryFavoriteIds,
                    preferences = viewModel.uiState.viewerPreferences,
                    onBack = galleryBack,
                    onToggleFavorite = { book?.let { viewModel.toggleLibraryFavorite(it.id) } },
                    onProgress = { page -> book?.let { viewModel.updateLibraryProgress(it.id, page) } },
                    nextBookTitle = nextBook?.title,
                    onOpenNextBook = {
                        nextBook?.let {
                            viewModel.openNextLibraryReaderTab(it.id)
                        }
                    },
                    onPreferencesChange = viewModel::updateViewerPreferences,
                )
            }
            composable("gallery/{galleryId}") { entry ->
                val galleryId = entry.arguments?.getString("galleryId")?.toLongOrNull()
                    ?: return@composable
                LaunchedEffect(galleryId) { viewModel.ensureGalleryLoaded(galleryId) }
                BackHandler(onBack = searchBack)
                GalleryDetailScreen(
                    gallery = viewModel.uiState.galleryCache[galleryId]
                        ?: viewModel.uiState.galleries.firstOrNull { it.id == galleryId },
                    state = viewModel.uiState,
                    onLoadPreview = viewModel::loadReader,
                    onBack = searchBack,
                    onOpenReader = { page -> viewModel.openOnlineReaderTab(galleryId, page) },
                    onSearchFacet = { facet ->
                        viewModel.searchFromFacet(facet)
                    },
                    onSearchLanguage = { language ->
                        viewModel.searchFromLanguage(language)
                    },
                    onDownload = viewModel::downloadGallery,
                    tabBar = { AppSearchTabStrip(viewModel, onBack = searchBack) },
                )
            }
            composable(
                route = "reader/{galleryId}?startPage={startPage}",
                arguments = listOf(
                    navArgument("startPage") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                ),
            ) { entry ->
                val galleryId = entry.arguments?.getString("galleryId")?.toLongOrNull()
                    ?: return@composable
                BackHandler(onBack = searchBack)
                val requestedPage = entry.arguments?.getInt("startPage") ?: -1
                ReaderScreen(
                    galleryId = galleryId,
                    initialPage = if (requestedPage >= 0) requestedPage
                        else viewModel.uiState.onlineReaderProgress[galleryId] ?: 0,
                    state = viewModel.uiState,
                    preferences = viewModel.uiState.viewerPreferences,
                    onLoad = viewModel::loadReader,
                    onBack = searchBack,
                    onProgress = { page -> viewModel.updateOnlineReaderProgress(galleryId, page) },
                    onPreferencesChange = viewModel::updateViewerPreferences,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = if (appMessageIsError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.inverseSurface,
                contentColor = if (appMessageIsError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }

    }

    viewModel.uiState.tabOverviewIsGallery?.let { isGallery ->
        BrowserTabsOverview(
            workspace = if (isGallery) viewModel.uiState.galleryWorkspace else viewModel.uiState.browserWorkspace,
            isGallery = isGallery,
            previews = viewModel.tabPreviews,
            onSelect = { id ->
                snackbarScope.launch {
                    viewModel.tabPreviews.captureNow()
                    viewModel.dismissTabOverview()
                    if (isGallery) viewModel.selectGalleryTab(id) else viewModel.selectBrowserTab(id)
                }
            },
            onCloseTab = { id -> if (isGallery) viewModel.closeGalleryTab(id) else viewModel.closeBrowserTab(id) },
            onUpdate = { viewModel.updateTabWorkspace(it, isGallery) },
            onDismiss = viewModel::dismissTabOverview,
        )
    }

    viewModel.uiState.availableUpdate?.let { release ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissAvailableUpdate(skipVersion = false) },
            title = { Text("새 버전 ${release.versionName}") },
            text = {
                Text(
                    release.notes.ifBlank { "새 버전을 사용할 수 있습니다." }.take(600),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                        viewModel.dismissAvailableUpdate(skipVersion = false)
                    },
                ) { Text("업데이트") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.dismissAvailableUpdate(skipVersion = true) }) {
                        Text("이 버전 건너뛰기")
                    }
                    TextButton(onClick = { viewModel.dismissAvailableUpdate(skipVersion = false) }) {
                        Text("나중에")
                    }
                }
            },
        )
    }
}

@Composable
private fun AppSearchTabStrip(viewModel: MainViewModel, onBack: (() -> Unit)? = null) {
    BrowserTabStrip(
        workspace = viewModel.uiState.browserWorkspace,
        onNew = viewModel::newBrowserTab,
        onShowOverview = { viewModel.showTabOverview(isGallery = false) },
        previews = viewModel.tabPreviews,
        onBack = onBack,
    )
}

@Composable
private fun AppGalleryTabStrip(viewModel: MainViewModel, onBack: (() -> Unit)? = null, showSync: Boolean = false) {
    BrowserTabStrip(
        workspace = viewModel.uiState.galleryWorkspace,
        onNew = viewModel::newGalleryTab,
        onShowOverview = { viewModel.showTabOverview(isGallery = true) },
        previews = viewModel.tabPreviews,
        onBack = onBack,
        extraActions = {
            if (showSync) IconButton(onClick = viewModel::syncLibraryNow,
                enabled = !viewModel.uiState.isLibrarySyncing && viewModel.uiState.selectedProfileId != null,
                modifier = Modifier.semantics { contentDescription = "라이브러리 동기화, 대기 ${viewModel.uiState.librarySyncPendingCount}개" }) {
                if (viewModel.uiState.isLibrarySyncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    val pending = viewModel.uiState.librarySyncPendingCount
                    Text(if (pending > 0) "↻ $pending" else "↻", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    viewModel: MainViewModel,
    initialDestinationRoute: String,
    onDestinationChanged: (String) -> Unit,
    onGalleryClick: (Long) -> Unit,
    onGalleryLongClick: (Long) -> Unit,
    onLibraryBookClick: (String) -> Unit,
    onBackToOrigin: (() -> Unit)? = null,
) {
    var selectedRoute by rememberSaveable(initialDestinationRoute) { mutableStateOf(initialDestinationRoute) }
    val selected = MainDestination.entries.firstOrNull { it.route == selectedRoute }
        ?: MainDestination.Browser
    LaunchedEffect(selectedRoute) {
        onDestinationChanged(selectedRoute)
    }
    val navigate: (MainDestination) -> Unit = { destination ->
        if (destination != selected) {
            selectedRoute = destination.route
        }
    }
    BackHandler(
        enabled = onBackToOrigin != null || when (selected) {
                MainDestination.Browser -> viewModel.canGoBackInBrowserTab()
                MainDestination.Library -> viewModel.canGoBackInGalleryTab()
                else -> false
            },
        onBack = {
            when {
                onBackToOrigin != null -> onBackToOrigin()
                selected == MainDestination.Browser -> viewModel.goBackInBrowserTab()
                else -> viewModel.goBackInGalleryTab()
            }
        },
    )

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        MainTabContent(
            destination = selected,
            viewModel = viewModel,
            contentPadding = padding,
            onGalleryClick = onGalleryClick,
            onGalleryLongClick = onGalleryLongClick,
            onLibraryBookClick = onLibraryBookClick,
            onNavigate = navigate,
        )
    }
    val topBar: @Composable () -> Unit = {
        Column {
            if (selected != MainDestination.Browser && selected != MainDestination.Library) {
                TopAppBar(title = { Text(selected.label) })
            }
            when (selected) {
                MainDestination.Browser -> AppSearchTabStrip(viewModel, onBack = onBackToOrigin
                    ?: if (viewModel.canGoBackInBrowserTab()) ({ viewModel.goBackInBrowserTab(); Unit }) else null)
                MainDestination.Library -> AppGalleryTabStrip(viewModel, showSync = true,
                    onBack = if (viewModel.canGoBackInGalleryTab()) ({ viewModel.goBackInGalleryTab(); Unit }) else null)
                else -> Unit
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 700.dp) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    MainDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == selected,
                            onClick = { navigate(destination) },
                            icon = { MainDestinationIcon(destination) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = topBar,
                ) { padding -> content(padding) }
            }
        } else {
            Scaffold(
                topBar = topBar,
                bottomBar = {
                    NavigationBar {
                        MainDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == selected,
                                onClick = { navigate(destination) },
                                icon = { MainDestinationIcon(destination) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                },
            ) { padding -> content(padding) }
        }
    }
}

private const val SEARCH_FROM_GALLERY_ROUTE = "search-from-gallery"

@Composable
private fun MainDestinationIcon(destination: MainDestination) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        when (destination) {
            MainDestination.Browser -> {
                drawCircle(
                    color,
                    radius = size.width * 0.28f,
                    center = Offset(size.width * 0.43f, size.height * 0.42f),
                    style = Stroke(width = size.width * 0.12f),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.63f, size.height * 0.63f),
                    Offset(size.width * 0.88f, size.height * 0.88f),
                    strokeWidth = size.width * 0.13f,
                )
            }
            MainDestination.Library -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.06f, size.height * 0.22f),
                    size = Size(size.width * 0.88f, size.height * 0.7f),
                    cornerRadius = CornerRadius(size.width * 0.16f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.14f, size.height * 0.08f),
                    size = Size(size.width * 0.42f, size.height * 0.3f),
                    cornerRadius = CornerRadius(size.width * 0.1f),
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.42f),
                    topLeft = Offset(size.width * 0.31f, size.height * 0.58f),
                    size = Size(size.width * 0.38f, size.height * 0.09f),
                    cornerRadius = CornerRadius(size.width * 0.04f),
                )
            }
            MainDestination.Downloads -> {
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.1f), Offset(size.width * 0.5f, size.height * 0.62f), size.width * 0.12f)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.28f, size.height * 0.4f), size.width * 0.12f)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.72f, size.height * 0.4f), size.width * 0.12f)
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.72f),
                    size = Size(size.width * 0.76f, size.height * 0.17f),
                    cornerRadius = CornerRadius(size.width * 0.06f),
                )
            }
            MainDestination.Settings -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(color, size.width * 0.25f, center, style = Stroke(width = size.width * 0.12f))
                drawCircle(color, size.width * 0.08f, center)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4.0
                    val start = size.width * 0.34f
                    val end = size.width * 0.46f
                    drawLine(
                        color,
                        Offset(center.x + cos(angle).toFloat() * start, center.y + sin(angle).toFloat() * start),
                        Offset(center.x + cos(angle).toFloat() * end, center.y + sin(angle).toFloat() * end),
                        strokeWidth = size.width * 0.14f,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabContent(
    destination: MainDestination,
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onGalleryClick: (Long) -> Unit,
    onGalleryLongClick: (Long) -> Unit,
    onLibraryBookClick: (String) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    var connectionSettingsRequested by rememberSaveable { mutableStateOf(false) }
    val openConnectionSettings: () -> Unit = {
        connectionSettingsRequested = true
        onNavigate(MainDestination.Settings)
    }
    AnimatedContent(
        targetState = destination,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            val enter = slideInHorizontally(
                animationSpec = tween(200, easing = LinearOutSlowInEasing),
                initialOffsetX = { width -> direction * (width / 10) },
            ) + fadeIn(tween(200))
            (enter togetherWith fadeOut(tween(200))).using(SizeTransform(clip = true))
        },
        label = "mainFragmentTransition",
    ) { current ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (current) {
        MainDestination.Browser -> {
            val search = viewModel.uiState.browserWorkspace.activeTab.currentPage as? BrowserPage.Search
            BrowserScreen(
                state = viewModel.uiState,
                contentPadding = contentPadding,
                onQueryChange = viewModel::setSearchQuery,
                onFavoriteNameChange = viewModel::setFavoriteName,
                onSearch = viewModel::search,
                onSaveFavorite = viewModel::saveSearchFavorite,
                onRemoveFavorite = viewModel::removeSearchFavorite,
                onFavoriteSearch = viewModel::searchFavorite,
                onToggleLanguage = viewModel::togglePreferredLanguage,
                onAddCustomLanguage = viewModel::addCustomLanguage,
                onRemoveCustomLanguage = viewModel::removeCustomLanguage,
                onSelectSuggestion = viewModel::selectFilterSuggestion,
                onRefresh = viewModel::refresh,
                onLoadNextPage = viewModel::loadNextPage,
                onGalleryClick = onGalleryClick,
                onGalleryLongClick = onGalleryLongClick,
                onSearchFacet = viewModel::searchFromFacet,
                onToggleLibraryFavorite = viewModel::toggleLibraryFavorite,
                onConnect = openConnectionSettings,
                pageKey = search?.key.orEmpty(),
                initialScrollAnchorKey = search?.scrollAnchorKey,
                initialScrollIndex = search?.scrollIndex ?: 0,
                initialScrollOffset = search?.scrollOffset ?: 0,
                onScrollChange = viewModel::updateBrowserScroll,
            )
        }
        MainDestination.Library -> {
            val libraryHome = viewModel.uiState.galleryWorkspace.activeTab.currentPage as? BrowserPage.LibraryHome
            LibraryScreen(
                state = viewModel.uiState,
                contentPadding = contentPadding,
                onQueryChange = viewModel::setLibraryQuery,
                onToggleFavoritesFilter = viewModel::toggleLibraryFavoritesFilter,
                onReadFilterChange = viewModel::setLibraryReadFilter,
                onVisibilityFilterChange = viewModel::setLibraryVisibilityFilter,
                onSortChange = viewModel::setLibrarySort,
                onLocationChange = viewModel::toggleLibraryLocation,
                onRefresh = viewModel::refreshLibrary,
                onOpenBook = onLibraryBookClick,
                onToggleFavorite = viewModel::toggleLibraryFavorite,
                onToggleSeriesFavorite = viewModel::toggleLibrarySeriesFavorite,
                onToggleRead = viewModel::toggleLibraryRead,
                onAddFavorites = viewModel::addLibraryFavorites,
                onMarkRead = viewModel::markLibraryBooksRead,
                onMarkUnread = viewModel::markLibraryBooksUnread,
                onAssignSeries = viewModel::assignLibrarySeries,
                onReorderSeriesBooks = viewModel::reorderLibrarySeriesBooks,
                onSetBooksHidden = viewModel::setLibraryBooksHidden,
                onDeleteBooks = viewModel::deleteLibraryBooks,
                onRenameBook = viewModel::renameLibraryBook,
                onRemoveBooksFromSeries = viewModel::removeLibraryBooksFromSeries,
                onRenameSeries = viewModel::renameLibrarySeries,
                onMergeSeries = viewModel::mergeLibrarySeries,
                onDeleteSeries = viewModel::deleteLibrarySeries,
                onAutoCreateSeries = viewModel::autoCreateLibrarySeries,
                onSeriesModeChange = viewModel::setLibrarySeriesMode,
                onSelectedSeriesChange = viewModel::selectLibrarySeries,
                onBackToSearch = {
                    onNavigate(MainDestination.Browser)
                },
                pageKey = libraryHome?.key.orEmpty(),
                initialScrollAnchorKey = libraryHome?.scrollAnchorKey,
                initialScrollIndex = libraryHome?.scrollIndex ?: 0,
                initialScrollOffset = libraryHome?.scrollOffset ?: 0,
                onScrollChange = viewModel::updateGalleryScroll,
            )
        }
        MainDestination.Downloads -> {
            DownloadsScreen(
                state = viewModel.uiState,
                contentPadding = contentPadding,
                onRefresh = viewModel::refreshDownloads,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onRetry = viewModel::retryDownload,
                onRemove = viewModel::removeDownload,
                onClearCompleted = viewModel::clearCompletedDownloads,
                onConnect = openConnectionSettings,
                onLeave = viewModel::resetDownloadConnectionAttempt,
                onRemoveRequest = viewModel::removeDownloadRequest,
                onLocalDownload = viewModel::startLocalDownload,
                onPauseLocal = viewModel::pauseLocalDownload,
                onSyncRequests = viewModel::syncDownloadRequests,
                onSetDownloadLocation = viewModel::setDownloadLocation,
            )
        }
        MainDestination.Settings -> {
            SettingsScreen(
                state = viewModel.uiState,
                contentPadding = contentPadding,
                onHostChange = viewModel::setHost,
                onPortChange = viewModel::setPort,
                onDeviceNameChange = viewModel::setDeviceName,
                onCodeChange = viewModel::setPairingCode,
                onTestConnection = viewModel::testConnection,
                onPair = viewModel::pair,
                onSelectProfile = viewModel::selectProfile,
                onRemoveProfile = viewModel::removeProfile,
                onAddLibraryLocation = viewModel::addLibraryLocation,
                onRemoveLibraryLocation = viewModel::removeLibraryLocation,
                onSetDownloadLocation = viewModel::setDownloadLocation,
                onClearDownloadLocation = viewModel::clearDownloadLocation,
                onViewerPreferencesChange = viewModel::updateViewerPreferences,
                onThemeModeChange = viewModel::setThemeMode,
                onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                openConnectionRequested = connectionSettingsRequested,
                onConnectionRequestHandled = { connectionSettingsRequested = false },
            )
        }
        }
        }
    }
}
