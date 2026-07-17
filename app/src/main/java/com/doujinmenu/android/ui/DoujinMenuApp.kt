package com.doujinmenu.android.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    var lastMainDestination by rememberSaveable { mutableStateOf(MainDestination.Browser.route) }

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
                            viewModel.openLibraryBook(libraryBookId)
                            navController.navigate("library-detail")
                        } else {
                            viewModel.selectGallery(it)
                            navController.navigate("gallery/$it")
                        }
                    },
                    onLibraryBookClick = { bookId ->
                        viewModel.openLibraryBook(bookId)
                        navController.navigate("library-detail")
                    },
                )
            }
            composable("library-detail") {
                val currentBook = viewModel.uiState.activeLibraryBook
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
                    onBack = navController::popBackStack,
                    onOpenReader = { page -> navController.navigate("library-reader?startPage=$page") },
                    onOpenPreviousBook = {
                        previousBook?.let { viewModel.openLibraryBook(it.id) }
                    },
                    onOpenNextBook = {
                        nextBook?.let { viewModel.openLibraryBook(it.id) }
                    },
                    onOpenSeriesList = {
                        currentBook?.id?.let { id ->
                            viewModel.uiState.customSeriesByBookId[id]?.name?.let { series ->
                                viewModel.setLibrarySeriesMode(true)
                                viewModel.selectLibrarySeries(series)
                                navController.popBackStack()
                            }
                        }
                    },
                    onSearchFacet = { facet ->
                        viewModel.searchFromFacet(facet)
                        lastMainDestination = MainDestination.Browser.route
                        navController.navigate("main")
                    },
                    onSearchLanguage = { language ->
                        viewModel.searchFromLanguage(language)
                        lastMainDestination = MainDestination.Browser.route
                        navController.navigate("main")
                    },
                )
            }
            composable(
                route = "library-reader?startPage={startPage}",
                arguments = listOf(navArgument("startPage") {
                    type = NavType.IntType
                    defaultValue = -1
                }),
            ) { entry ->
                val book = viewModel.uiState.activeLibraryBook
                val nextBook = book?.let { viewModel.nextLibrarySeriesBook(it.id) }
                val requestedPage = entry.arguments?.getInt("startPage") ?: -1
                LocalReaderScreen(
                    book = book,
                    initialPage = if (requestedPage >= 0) requestedPage
                        else book?.let { viewModel.uiState.libraryProgress[it.id] } ?: 0,
                    favorite = book?.id in viewModel.uiState.libraryFavoriteIds,
                    preferences = viewModel.uiState.viewerPreferences,
                    onBack = navController::popBackStack,
                    onToggleFavorite = { book?.let { viewModel.toggleLibraryFavorite(it.id) } },
                    onProgress = { page -> book?.let { viewModel.updateLibraryProgress(it.id, page) } },
                    nextBookTitle = nextBook?.title,
                    onOpenNextBook = {
                        nextBook?.let {
                            viewModel.openLibraryBook(it.id)
                            navController.popBackStack()
                            navController.navigate("library-reader?startPage=0")
                        }
                    },
                    onPreferencesChange = viewModel::updateViewerPreferences,
                )
            }
            composable("gallery/{galleryId}") { entry ->
                val galleryId = entry.arguments?.getString("galleryId")?.toLongOrNull()
                    ?: return@composable
                GalleryDetailScreen(
                    gallery = viewModel.uiState.galleryCache[galleryId]
                        ?: viewModel.uiState.activeGallery?.takeIf { it.id == galleryId }
                        ?: viewModel.uiState.galleries.firstOrNull { it.id == galleryId },
                    state = viewModel.uiState,
                    onLoadPreview = viewModel::loadReader,
                    onBack = navController::popBackStack,
                    onOpenReader = { page ->
                        navController.navigate("reader/$galleryId?startPage=$page")
                    },
                    onSearchFacet = { facet ->
                        viewModel.searchFromFacet(facet)
                        lastMainDestination = MainDestination.Browser.route
                        navController.navigate("main")
                    },
                    onSearchLanguage = { language ->
                        viewModel.searchFromLanguage(language)
                        lastMainDestination = MainDestination.Browser.route
                        navController.navigate("main")
                    },
                    onDownload = viewModel::downloadGallery,
                )
            }
            composable(
                route = "reader/{galleryId}?startPage={startPage}",
                arguments = listOf(
                    navArgument("startPage") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) { entry ->
                val galleryId = entry.arguments?.getString("galleryId")?.toLongOrNull()
                    ?: return@composable
                val startPage = entry.arguments?.getInt("startPage") ?: 0
                ReaderScreen(
                    galleryId = galleryId,
                    initialPage = startPage,
                    state = viewModel.uiState,
                    onLoad = viewModel::loadReader,
                    onBack = navController::popBackStack,
                )
            }
        }

        val appMessage = viewModel.uiState.message
        if (appMessage != null) {
            if (viewModel.uiState.isError) {
                AppErrorBanner(appMessage, viewModel::dismissMessage)
            } else {
                AppSuccessBanner(
                    message = appMessage,
                    onDismiss = viewModel::dismissMessage,
                    title = "알림",
                )
            }
        } else if (viewModel.uiState.connectionNotification != null) {
            AppSuccessBanner(
                message = viewModel.uiState.connectionNotification.orEmpty(),
                onDismiss = viewModel::dismissConnectionNotification,
                title = "연결됨",
            )
        } else viewModel.uiState.downloadNotification?.let { notification ->
            DownloadNotificationBanner(
                notification = notification,
                onDismiss = viewModel::dismissDownloadNotification,
                onOpen = viewModel::dismissDownloadNotification,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    viewModel: MainViewModel,
    initialDestinationRoute: String,
    onDestinationChanged: (String) -> Unit,
    onGalleryClick: (Long) -> Unit,
    onLibraryBookClick: (String) -> Unit,
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

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        MainTabContent(
            destination = selected,
            viewModel = viewModel,
            contentPadding = padding,
            onGalleryClick = onGalleryClick,
            onLibraryBookClick = onLibraryBookClick,
            onNavigate = navigate,
        )
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
                    topBar = { TopAppBar(title = { Text(selected.label) }) },
                ) { padding -> content(padding) }
            }
        } else {
            Scaffold(
                topBar = { TopAppBar(title = { Text(selected.label) }) },
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
                onToggleLibraryFavorite = viewModel::toggleLibraryFavorite,
                onConnect = openConnectionSettings,
            )
        }
        MainDestination.Library -> {
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
                onMoveSeriesBook = viewModel::moveLibrarySeriesBook,
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
                openConnectionRequested = connectionSettingsRequested,
                onConnectionRequestHandled = { connectionSettingsRequested = false },
                onStartConnectionMonitoring = viewModel::startConnectionMonitoring,
                onStopConnectionMonitoring = viewModel::stopConnectionMonitoring,
            )
        }
        }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                description,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
