package com.doujinmenu.android.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private enum class MainDestination(
    val route: String,
    val label: String,
    val symbol: String,
) {
    Browser("browser", "브라우저", "⌕"),
    Library("library", "갤러리", "▦"),
    Downloads("downloads", "다운로드", "↓"),
    Settings("settings", "설정", "⚙"),
}

@Composable
fun DoujinMenuApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "main",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("main") {
                MainShell(
                    viewModel = viewModel,
                    onGalleryClick = {
                        viewModel.selectGallery(it)
                        navController.navigate("gallery/$it")
                    },
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

        viewModel.uiState.downloadNotification?.let { notification ->
            DownloadNotificationBanner(
                notification = notification,
                onDismiss = viewModel::dismissDownloadNotification,
                onOpen = {
                    viewModel.dismissDownloadNotification()
                    navController.navigate("gallery/${notification.galleryId}") {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    viewModel: MainViewModel,
    onGalleryClick: (Long) -> Unit,
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val selected = MainDestination.entries.firstOrNull { it.route == currentRoute }
        ?: MainDestination.Browser
    val navigate: (MainDestination) -> Unit = { destination ->
        if (destination != selected) {
            navController.navigate(destination.route) {
                popUpTo(MainDestination.Browser.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        MainTabHost(
            navController = navController,
            viewModel = viewModel,
            contentPadding = padding,
            onGalleryClick = onGalleryClick,
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
                            icon = { Text(destination.symbol) },
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
                                icon = { Text(destination.symbol) },
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
private fun MainTabHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onGalleryClick: (Long) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = MainDestination.Browser.route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(MainDestination.Browser.route) {
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
                onSelectSuggestion = viewModel::selectFilterSuggestion,
                onRefresh = viewModel::refresh,
                onLoadNextPage = viewModel::loadNextPage,
                onGalleryClick = onGalleryClick,
            )
        }
        composable(MainDestination.Library.route) {
            PlaceholderScreen(
                title = "갤러리",
                description = "SMB 라이브러리와 즐겨찾기가 이 화면에 추가됩니다.",
                padding = contentPadding,
            )
        }
        composable(MainDestination.Downloads.route) {
            PlaceholderScreen(
                title = "다운로드",
                description = "데스크톱 다운로드 큐가 이 화면에 추가됩니다.",
                padding = contentPadding,
            )
        }
        composable(MainDestination.Settings.route) {
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
            )
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
