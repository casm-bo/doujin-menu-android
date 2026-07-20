package com.doujinmenu.android.ui

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doujinmenu.android.data.LibraryScanner
import com.doujinmenu.android.data.LibraryArchiveExtractor
import com.doujinmenu.android.data.LibraryFileDeleter
import com.doujinmenu.android.data.LibraryIdentityStore
import com.doujinmenu.android.data.LibraryUploadPreparer
import com.doujinmenu.android.model.DesktopProfile
import com.doujinmenu.android.model.DownloadQueueItem
import com.doujinmenu.android.model.DownloadStatus
import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.CustomSeriesAssignment
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.model.LibraryReadFilter
import com.doujinmenu.android.model.LibrarySort
import com.doujinmenu.android.model.LibraryVisibilityFilter
import com.doujinmenu.android.model.SearchFavorite
import com.doujinmenu.android.model.StorageLocation
import com.doujinmenu.android.model.ViewerPreferences
import com.doujinmenu.android.network.CompanionClient
import com.doujinmenu.android.network.CompanionApiException
import com.doujinmenu.android.network.BookStateSyncUpdate
import com.doujinmenu.android.network.EndpointNormalizer
import com.doujinmenu.android.network.FilterSuggestion
import com.doujinmenu.android.network.HitomiSuggestionClient
import com.doujinmenu.android.network.SeriesSyncUpdate
import com.doujinmenu.android.network.SeriesSyncResult
import com.doujinmenu.android.security.BrowserPreferenceStore
import com.doujinmenu.android.security.LibraryPreferenceStore
import com.doujinmenu.android.security.SecureProfileStore
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class MainUiState(
    val host: String = "",
    val port: String = EndpointNormalizer.DEFAULT_PORT.toString(),
    val pairingCode: String = "",
    val deviceName: String = "",
    val profiles: List<DesktopProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val searchQuery: String = "",
    val favoriteName: String = "",
    val searchFavorites: List<SearchFavorite> = emptyList(),
    val preferredLanguages: Set<String> = emptySet(),
    val customLanguages: Set<String> = emptySet(),
    val filterSuggestions: List<FilterSuggestion> = emptyList(),
    val isLoadingFilterSuggestions: Boolean = false,
    val knownFilterTokens: Set<String> = emptySet(),
    val viewedGalleryIds: Set<Long> = emptySet(),
    val libraryLocations: List<StorageLocation> = emptyList(),
    val desktopLibraryLocations: List<StorageLocation> = emptyList(),
    val libraryBooks: List<LibraryBook> = emptyList(),
    val libraryHiddenIds: Set<String> = emptySet(),
    val activeLibraryBook: LibraryBook? = null,
    val libraryFavoriteIds: Set<String> = emptySet(),
    val libraryFavoriteSeriesNames: Set<String> = emptySet(),
    val libraryReadIds: Set<String> = emptySet(),
    val libraryProgress: Map<String, Int> = emptyMap(),
    val onlineReaderProgress: Map<Long, Int> = emptyMap(),
    val customSeriesByBookId: Map<String, CustomSeriesAssignment> = emptyMap(),
    val libraryQuery: String = "",
    val libraryFavoritesOnly: Boolean = false,
    val libraryReadFilter: LibraryReadFilter = LibraryReadFilter.ALL,
    val libraryVisibilityFilter: LibraryVisibilityFilter = LibraryVisibilityFilter.VISIBLE,
    val librarySort: LibrarySort = LibrarySort.TITLE_ASC,
    val selectedLibraryLocationUri: String? = null,
    val selectedLibraryLocationUris: Set<String>? = null,
    val librarySeriesMode: Boolean = false,
    val selectedLibrarySeries: String? = null,
    val isAutoCreatingSeries: Boolean = false,
    val isLibraryScanning: Boolean = false,
    val isLibrarySyncing: Boolean = false,
    val librarySyncPendingCount: Int = 0,
    val libraryLastSyncedAt: Long? = null,
    val librarySyncError: String? = null,
    val libraryScanError: String? = null,
    val isLibraryBookLoading: Boolean = false,
    val viewerPreferences: ViewerPreferences = ViewerPreferences(),
    val downloadLocation: StorageLocation? = null,
    val desktopDownloadPath: String? = null,
    val submittedSearchQuery: String = "",
    val submittedSearchQueries: List<String> = emptyList(),
    val galleries: List<GallerySummary> = emptyList(),
    val activeGallery: GallerySummary? = null,
    val galleryCache: Map<Long, GallerySummary> = emptyMap(),
    val currentPage: Int = 0,
    val hasNextPage: Boolean = false,
    val isLoadingPage: Boolean = false,
    val isRefreshing: Boolean = false,
    val readerGalleryId: Long? = null,
    val readerPages: List<String> = emptyList(),
    val isReaderLoading: Boolean = false,
    val readerError: String? = null,
    val downloadingGalleryIds: Set<Long> = emptySet(),
    val downloadQueue: List<DownloadQueueItem> = emptyList(),
    val isDownloadQueueLoading: Boolean = false,
    val downloadQueueError: String? = null,
    val isDownloadConnectionUnavailable: Boolean = false,
    val activeDownloadActionIds: Set<Long> = emptySet(),
    val downloadNotification: DownloadNotification? = null,
    val connectionNotification: String? = null,
    val desktopConnectionState: DesktopConnectionState = DesktopConnectionState.IDLE,
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

enum class DesktopConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

data class DownloadNotification(
    val galleryId: Long,
    val galleryTitle: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = CompanionClient()
    private val suggestionClient = HitomiSuggestionClient()
    private val profileStore = SecureProfileStore(application)
    private val browserPreferenceStore = BrowserPreferenceStore(application)
    private val libraryPreferenceStore = LibraryPreferenceStore(application)
    private val libraryIdentityStore = LibraryIdentityStore(application)
    private val libraryScanner = LibraryScanner(application, libraryIdentityStore)
    private val libraryArchiveExtractor = LibraryArchiveExtractor(application)
    private val libraryFileDeleter = LibraryFileDeleter(application)
    private val libraryUploadPreparer = LibraryUploadPreparer(application)
    private val suggestionCache = mutableMapOf<String, List<FilterSuggestion>>()
    private var suggestionJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private var downloadRefreshJob: Job? = null
    private var librarySyncDebounceJob: Job? = null
    private var importantLibraryRefreshJob: Job? = null
    private var reconnectLibraryRefreshJob: Job? = null
    private var activeBookPreparationJob: Job? = null
    private var hasEnteredForeground = false
    private var notifyOnDownloadReconnect = false
    private val observedCompletedDownloadIds = mutableSetOf<Long>()
    private val pendingHistoryBookIds = mutableSetOf<String>()
    private val seriesSyncMutex = Mutex()
    private val bookStateSyncMutex = Mutex()
    private val librarySyncFlushMutex = Mutex()

    var uiState by mutableStateOf(MainUiState())
        private set

    init {
        val profiles = profileStore.load()
        uiState = uiState.copy(
            profiles = profiles,
            selectedProfileId = profiles.firstOrNull()?.id,
            searchFavorites = browserPreferenceStore.loadFavorites(),
            preferredLanguages = browserPreferenceStore.loadPreferredLanguages(),
            customLanguages = browserPreferenceStore.loadCustomLanguages(),
            knownFilterTokens = browserPreferenceStore.loadKnownFilterTokens(),
            viewedGalleryIds = browserPreferenceStore.loadViewedGalleryIds(),
            libraryLocations = browserPreferenceStore.loadLibraryLocations(),
            libraryFavoriteIds = libraryPreferenceStore.loadFavoriteIds(),
            libraryHiddenIds = libraryPreferenceStore.loadHiddenIds(),
            libraryFavoriteSeriesNames = libraryPreferenceStore.loadFavoriteSeriesNames(),
            libraryReadIds = libraryPreferenceStore.loadReadIds(),
            libraryProgress = libraryPreferenceStore.loadProgress(),
            onlineReaderProgress = libraryPreferenceStore.loadOnlineProgress(),
            customSeriesByBookId = libraryPreferenceStore.loadCustomSeries(),
            librarySyncPendingCount = pendingLibrarySyncCount(),
            viewerPreferences = libraryPreferenceStore.loadViewerPreferences(),
            downloadLocation = browserPreferenceStore.loadDownloadLocation(),
        )
        refreshLibrary()
    }

    fun setHost(value: String) = update { copy(host = value) }
    fun setPort(value: String) = update { copy(port = value.filter(Char::isDigit).take(5)) }
    fun setPairingCode(value: String) = update {
        copy(pairingCode = value.filter(Char::isDigit).take(PAIRING_CODE_LENGTH))
    }
    fun setDeviceName(value: String) = update { copy(deviceName = value) }
    fun setSearchQuery(value: String) {
        update { copy(searchQuery = value) }
        refreshFilterSuggestions(value)
    }

    fun selectFilterSuggestion(suggestion: FilterSuggestion) {
        val query = uiState.searchQuery
        val tokenStart = query.indexOfLast { it.isWhitespace() }.let { if (it < 0) 0 else it + 1 }
        val negative = query.substring(tokenStart).startsWith("-")
        val replacement = (if (negative) "-" else "") + suggestion.token
        uiState = uiState.copy(
            searchQuery = query.substring(0, tokenStart) + replacement + " ",
            filterSuggestions = emptyList(),
            isLoadingFilterSuggestions = false,
        )
    }
    fun setFavoriteName(value: String) = update { copy(favoriteName = value) }

    fun saveSearchFavorite() {
        val query = uiState.searchQuery.trim()
        if (query.isEmpty()) {
            uiState = uiState.copy(message = "저장할 검색 조건을 입력하세요.", isError = true)
            return
        }
        val favorite = SearchFavorite(
            id = UUID.randomUUID().toString(),
            name = uiState.favoriteName.trim().ifBlank { query },
            query = query,
        )
        val favorites = uiState.searchFavorites + favorite
        browserPreferenceStore.saveFavorites(favorites)
        uiState = uiState.copy(
            favoriteName = "",
            searchFavorites = favorites,
            message = "검색 조건을 즐겨찾기에 저장했습니다.",
            isError = false,
        )
    }

    fun removeSearchFavorite(id: String) {
        val favorites = uiState.searchFavorites.filterNot { it.id == id }
        browserPreferenceStore.saveFavorites(favorites)
        uiState = uiState.copy(searchFavorites = favorites)
    }

    fun searchFavorite(favorite: SearchFavorite) {
        uiState = uiState.copy(searchQuery = favorite.query)
        search()
    }

    fun togglePreferredLanguage(language: String) {
        val normalized = language.trim().lowercase()
        if (normalized.isEmpty()) return
        val languages = uiState.preferredLanguages.toMutableSet().apply {
            if (!add(normalized)) remove(normalized)
        }
        browserPreferenceStore.savePreferredLanguages(languages)
        uiState = uiState.copy(preferredLanguages = languages)
    }

    fun addCustomLanguage(language: String) {
        val normalized = language.substringAfter(':').trim().lowercase()
            .replace(Regex("\\s+"), "_")
        if (normalized.isEmpty()) return
        val custom = uiState.customLanguages + normalized
        val selected = uiState.preferredLanguages + normalized
        browserPreferenceStore.saveCustomLanguages(custom)
        browserPreferenceStore.savePreferredLanguages(selected)
        uiState = uiState.copy(customLanguages = custom, preferredLanguages = selected)
    }

    fun removeCustomLanguage(language: String) {
        val normalized = language.trim().lowercase()
        val custom = uiState.customLanguages - normalized
        val selected = uiState.preferredLanguages - normalized
        browserPreferenceStore.saveCustomLanguages(custom)
        browserPreferenceStore.savePreferredLanguages(selected)
        uiState = uiState.copy(customLanguages = custom, preferredLanguages = selected)
    }

    fun searchFromLanguage(language: String) {
        val normalized = language.trim().lowercase().replace(Regex("\\s+"), "_")
        if (normalized.isEmpty() || normalized == "n/a") return
        val selected = setOf(normalized)
        browserPreferenceStore.savePreferredLanguages(selected)
        uiState = uiState.copy(
            preferredLanguages = selected,
            searchQuery = uiState.searchQuery
                .replace(Regex("(?i)(^|\\s)-?language:[^\\s]+"), " ")
                .trim().replace(Regex("\\s+"), " "),
        )
        search()
    }

    fun searchFromFacet(facet: String) {
        val normalizedFacet = normalizeFilterFacet(facet)
        uiState = uiState.copy(searchQuery = normalizedFacet)
        search()
    }

    fun testConnection() {
        viewModelScope.launch {
            runOperation {
                val baseUrl = EndpointNormalizer.normalize(uiState.host, uiState.port)
                val status = client.getStatus(baseUrl)
                notifyOnDownloadReconnect = false
                uiState = uiState.copy(
                    message = "연결 성공: ${status.service} API v${status.version}" +
                        if (status.pairingAvailable) " · 페어링 가능" else " · 페어링 코드 없음",
                    connectionNotification = "PC와 연결되었습니다.",
                    desktopConnectionState = DesktopConnectionState.CONNECTED,
                    isError = false,
                )
            }
        }
    }

    fun pair() {
        viewModelScope.launch {
            runOperation {
                val code = uiState.pairingCode
                require(code.length == PAIRING_CODE_LENGTH) { "6자리 페어링 코드를 입력하세요." }
                val profileName = uiState.deviceName.trim()
                require(profileName.isNotEmpty()) { "앱에서 표시할 연결 이름을 입력하세요." }
                val phoneDeviceName = Build.MODEL.orEmpty().ifBlank { "Android device" }
                val baseUrl = EndpointNormalizer.normalize(uiState.host, uiState.port)
                val status = client.getStatus(baseUrl)
                require(status.pairingAvailable) { "데스크톱에서 새 페어링 코드를 먼저 생성하세요." }

                val result = client.pair(baseUrl, code, phoneDeviceName)
                notifyOnDownloadReconnect = false
                val profile = DesktopProfile(
                    id = result.deviceId,
                    name = profileName,
                    baseUrl = baseUrl,
                    token = result.token,
                )
                val profiles = uiState.profiles.filterNot { it.id == profile.id } + profile
                profileStore.save(profiles)
                uiState = uiState.copy(
                    profiles = profiles,
                    selectedProfileId = profile.id,
                    pairingCode = "",
                    isDownloadConnectionUnavailable = false,
                    connectionNotification = "${profile.name}와 연결되었습니다.",
                    desktopConnectionState = DesktopConnectionState.CONNECTED,
                    message = "${profile.name} 페어링 완료 · 토큰을 Keystore로 보호해 저장했습니다.",
                    isError = false,
                )
                refreshLibrary()
            }
        }
    }

    fun selectProfile(id: String) {
        if (uiState.isLoadingPage) return
        val profile = uiState.profiles.firstOrNull { it.id == id } ?: return
        uiState = uiState.copy(
            selectedProfileId = id,
            message = "${profile.name} 선택됨",
            isError = false,
            galleries = emptyList(),
            currentPage = 0,
            hasNextPage = false,
            downloadQueue = emptyList(),
            downloadQueueError = null,
            isDownloadConnectionUnavailable = false,
            desktopConnectionState = DesktopConnectionState.CONNECTING,
        )
        refreshLibrary()
    }

    fun removeProfile(id: String) {
        if (uiState.isLoadingPage) return
        val profiles = uiState.profiles.filterNot { it.id == id }
        profileStore.save(profiles)
        uiState = uiState.copy(
            profiles = profiles,
            selectedProfileId = if (uiState.selectedProfileId == id) profiles.firstOrNull()?.id
                else uiState.selectedProfileId,
            galleries = emptyList(),
            currentPage = 0,
            hasNextPage = false,
            downloadQueue = emptyList(),
            downloadQueueError = null,
            isDownloadConnectionUnavailable = false,
            desktopConnectionState = if (profiles.isEmpty()) {
                DesktopConnectionState.IDLE
            } else {
                DesktopConnectionState.CONNECTING
            },
            message = "저장된 데스크톱을 삭제했습니다.",
            isError = false,
        )
    }

    fun search() {
        if (uiState.isLoadingPage) return
        viewModelScope.launch { loadPage(reset = true) }
    }

    fun selectGallery(galleryId: Long) {
        val viewed = rememberViewedGallery(galleryId)
        uiState.galleries.firstOrNull { it.id == galleryId }?.let { gallery ->
            uiState = uiState.copy(
                activeGallery = gallery,
                galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)),
                viewedGalleryIds = viewed,
            )
        } ?: run { uiState = uiState.copy(viewedGalleryIds = viewed) }
    }

    fun libraryBookIdForGallery(galleryId: Long): String? =
        preferredLibraryBookForGalleryId(
            uiState.libraryBooks.filterNot { it.id in uiState.libraryHiddenIds },
            galleryId,
        )?.id

    fun startConnectionMonitoring() {
        if (connectionMonitorJob?.isActive == true) return
        connectionMonitorJob = viewModelScope.launch {
            var monitoredProfileId: String? = null
            var wasConnected = false
            var lastSyncGeneration = 0L
            var hasSyncGenerationBaseline = false
            while (true) {
                val profile = selectedProfile()
                if (profile == null) {
                    monitoredProfileId = null
                    wasConnected = false
                    lastSyncGeneration = 0L
                    hasSyncGenerationBaseline = false
                    uiState = uiState.copy(desktopConnectionState = DesktopConnectionState.IDLE)
                    delay(CONNECTION_MONITOR_INTERVAL_MS)
                    continue
                }
                if (monitoredProfileId != profile.id) {
                    monitoredProfileId = profile.id
                    wasConnected = false
                    lastSyncGeneration = 0L
                    hasSyncGenerationBaseline = false
                    uiState = uiState.copy(desktopConnectionState = DesktopConnectionState.CONNECTING)
                }
                val status = runCatching { client.getStatus(profile.baseUrl) }.getOrNull()
                val connected = status != null
                if (connected && !wasConnected) {
                    val hasPendingSync =
                        libraryPreferenceStore.loadPendingSeriesSyncIds().isNotEmpty() ||
                            libraryPreferenceStore.loadPendingBookStateSyncIds().isNotEmpty()
                    if (
                        hasPendingSync || uiState.librarySyncError != null ||
                        uiState.libraryBooks.none(LibraryBook::isCloud)
                    ) {
                        requestReconnectLibraryRefresh()
                    }
                }
                if (status != null) {
                    when {
                        !hasSyncGenerationBaseline || status.syncGeneration < lastSyncGeneration -> {
                            lastSyncGeneration = status.syncGeneration
                            hasSyncGenerationBaseline = true
                        }
                        status.syncGeneration > lastSyncGeneration -> {
                            lastSyncGeneration = status.syncGeneration
                            requestImportantLibraryRefresh()
                        }
                    }
                }
                wasConnected = connected
                uiState = uiState.copy(
                    desktopConnectionState = if (connected) {
                        DesktopConnectionState.CONNECTED
                    } else {
                        DesktopConnectionState.DISCONNECTED
                    },
                )
                delay(CONNECTION_MONITOR_INTERVAL_MS)
            }
        }
    }

    fun stopConnectionMonitoring() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
    }

    fun loadNextPage() {
        if (uiState.isLoadingPage || !uiState.hasNextPage || uiState.currentPage < 1) return
        viewModelScope.launch { loadPage(reset = false) }
    }

    fun refresh() {
        if (uiState.isLoadingPage || uiState.isRefreshing) return
        viewModelScope.launch {
            uiState = uiState.copy(isRefreshing = true)
            try {
                loadPage(reset = true, preserveResults = true)
            } finally {
                uiState = uiState.copy(isRefreshing = false)
            }
        }
    }

    fun loadReader(galleryId: Long) {
        if (uiState.isReaderLoading) return
        if (uiState.readerGalleryId == galleryId && uiState.readerPages.isNotEmpty()) return
        val profile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
        if (profile == null) {
            uiState = uiState.copy(readerError = "연결된 데스크톱이 없습니다.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(
                readerGalleryId = galleryId,
                readerPages = emptyList(),
                isReaderLoading = true,
                readerError = null,
            )
            try {
                val pages = client.getGalleryPages(profile, galleryId)
                uiState = uiState.copy(readerPages = pages)
            } catch (error: Exception) {
                uiState = uiState.copy(
                    readerError = error.message ?: "페이지 목록을 불러오지 못했습니다.",
                )
            } finally {
                uiState = uiState.copy(isReaderLoading = false)
            }
        }
    }

    fun downloadGallery(gallery: GallerySummary) {
        if (gallery.id in uiState.downloadingGalleryIds) return
        val profile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
        if (profile == null) {
            uiState = uiState.copy(message = "연결된 데스크톱이 없습니다.", isError = true)
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(
                downloadingGalleryIds = uiState.downloadingGalleryIds + gallery.id,
                message = null,
                isError = false,
            )
            try {
                val item = client.requestDownload(profile, gallery.id)
                uiState = uiState.copy(
                    downloadQueue = sortDownloadQueueNewest(
                        (uiState.downloadQueue + item).distinctBy { it.id },
                    ),
                    downloadNotification = DownloadNotification(gallery.id, gallery.title),
                )
            } catch (error: Exception) {
                val unsupported = error.message?.contains("Not found", ignoreCase = true) == true
                uiState = uiState.copy(
                    message = if (unsupported) {
                        "현재 Companion Server에는 다운로드 API가 없습니다. 데스크톱 업데이트가 필요합니다."
                    } else {
                        error.message ?: "다운로드 요청에 실패했습니다."
                    },
                    isError = true,
                )
            } finally {
                uiState = uiState.copy(
                    downloadingGalleryIds = uiState.downloadingGalleryIds - gallery.id,
                )
            }
        }
    }

    fun dismissDownloadNotification() {
        uiState = uiState.copy(downloadNotification = null)
    }

    fun dismissConnectionNotification() {
        uiState = uiState.copy(connectionNotification = null)
    }

    fun dismissMessage() {
        uiState = uiState.copy(message = null, isError = false)
    }

    fun addLibraryLocation(uri: String, displayName: String) {
        val location = StorageLocation(uri, displayName)
        val locations = (uiState.libraryLocations + location).distinctBy { it.uri }
        browserPreferenceStore.saveLibraryLocations(locations)
        uiState = uiState.copy(libraryLocations = locations)
        refreshLibrary()
    }

    fun removeLibraryLocation(uri: String) {
        val locations = uiState.libraryLocations.filterNot { it.uri == uri }
        browserPreferenceStore.saveLibraryLocations(locations)
        uiState = uiState.copy(
            libraryLocations = locations,
            selectedLibraryLocationUri = uiState.selectedLibraryLocationUri
                ?.takeIf { selected -> locations.any { it.uri == selected } },
            selectedLibraryLocationUris = uiState.selectedLibraryLocationUris?.filterTo(linkedSetOf()) {
                selected -> locations.any { it.uri == selected } ||
                    uiState.desktopLibraryLocations.any { it.uri == selected }
            },
        )
        refreshLibrary()
    }

    fun refreshLibrary() {
        if (uiState.isLibraryScanning) return
        val scannedLocations = uiState.libraryLocations
        val profile = selectedProfile()
        viewModelScope.launch {
            uiState = uiState.copy(isLibraryScanning = true, libraryScanError = null)
            val result = libraryScanner.scan(scannedLocations)
            val cloudResult = profile?.let { runCatching { client.getLibraryBooks(it) } }
            val cloudBooks = when {
                profile == null -> emptyList()
                cloudResult?.isSuccess == true -> cloudResult.getOrThrow()
                else -> uiState.libraryBooks.filter(LibraryBook::isCloud)
            }
            val identityReconciliation = withContext(Dispatchers.IO) {
                libraryIdentityStore.reconcileWithDesktop(result.books, cloudBooks)
            }
            val localBooks = identityReconciliation.books
            val cloudError = cloudResult?.exceptionOrNull()?.let { error ->
                "데스크톱 라이브러리를 불러오지 못했습니다: ${error.message ?: "알 수 없는 오류"}"
            }
            val desktopDownloadPath = profile?.let {
                runCatching { client.getDesktopDownloadPath(it) }.getOrNull()
            }
            val legacyDesktopIdAliases = profile?.let { selected ->
                cloudBooks.mapNotNull { book ->
                    val remoteId = book.remoteBookId ?: return@mapNotNull null
                    val legacyId = "desktop:${selected.id}:$remoteId"
                    (legacyId to book.id).takeIf { legacyId != book.id }
                }.toMap()
            }.orEmpty()
            val legacyIdAliases = legacyDesktopIdAliases +
                identityReconciliation.idAliases +
                legacyLocalLibraryIdAliases(localBooks)
            var hiddenIds = remapLibraryIds(libraryPreferenceStore.loadHiddenIds(), legacyIdAliases)
            var favoriteIds = remapLibraryIds(libraryPreferenceStore.loadFavoriteIds(), legacyIdAliases)
            var readIds = remapLibraryIds(libraryPreferenceStore.loadReadIds(), legacyIdAliases)
            var progress = remapLibraryIdKeys(libraryPreferenceStore.loadProgress(), legacyIdAliases)
            var pendingBookStateIds = remapLibraryIds(
                libraryPreferenceStore.loadPendingBookStateSyncIds(),
                legacyIdAliases,
            )
            var pendingSeriesSyncIds = remapLibraryIds(
                libraryPreferenceStore.loadPendingSeriesSyncIds(),
                legacyIdAliases,
            )
            val localSeries = remapLibraryIdKeys(
                libraryPreferenceStore.loadCustomSeries(),
                legacyIdAliases,
            )
            val localSeriesRemovalTimes = remapLibraryIdKeys(
                libraryPreferenceStore.loadSeriesRemovalTimes(),
                legacyIdAliases,
            )
            val seriesMerge = mergeSyncedSeries(
                local = localSeries,
                removalTimes = localSeriesRemovalTimes,
                books = cloudBooks,
            )
            val rawBooks = localBooks + cloudBooks
            val knownSyncableIds = rawBooks.asSequence()
                .filter { it.syncId != null }
                .mapTo(hashSetOf(), LibraryBook::id)
            val librarySnapshotComplete = result.errors.isEmpty() &&
                (profile == null || cloudResult?.isSuccess == true)
            val knownBookIds = rawBooks.mapTo(hashSetOf(), LibraryBook::id)
            val customSeries = if (librarySnapshotComplete) {
                seriesMerge.assignments.filterKeys { it in knownBookIds }
            } else {
                seriesMerge.assignments
            }
            val seriesRemovalTimes = if (librarySnapshotComplete) {
                seriesMerge.removalTimes.filterKeys { it in knownBookIds }
            } else {
                seriesMerge.removalTimes
            }
            pendingSeriesSyncIds += seriesMerge.localWinnerIds.filter { it in knownSyncableIds }
            var customTitles = remapLibraryIdKeys(libraryPreferenceStore.loadCustomTitles(), legacyIdAliases)
            var favoriteSeriesNames = libraryPreferenceStore.loadFavoriteSeriesNames()
            if (librarySnapshotComplete) {
                pendingBookStateIds = pendingBookStateIds.filterTo(linkedSetOf()) {
                    it in knownSyncableIds
                }
            }
            pendingSeriesSyncIds = sanitizePendingSeriesSyncIds(
                pending = pendingSeriesSyncIds,
                knownSyncableIds = knownSyncableIds,
                librarySnapshotComplete = librarySnapshotComplete,
                assignments = customSeries,
                removalTimes = seriesRemovalTimes,
            )
            val remoteBySyncId = cloudBooks.mapNotNull { remote ->
                remote.syncId?.lowercase()?.let { it to remote }
            }.toMap()
            rawBooks.forEach { book ->
                if (book.id in pendingBookStateIds) return@forEach
                val remote = book.syncId?.lowercase()?.let(remoteBySyncId::get) ?: return@forEach
                hiddenIds = hiddenIds.withMembership(book.id, remote.syncedHidden)
                favoriteIds = favoriteIds.withMembership(book.id, remote.syncedFavorite)
                readIds = readIds.withMembership(book.id, remote.syncedRead)
                progress = progress + (book.id to remote.syncedProgress)
                customTitles = if (remote.syncedCustomTitle == null) customTitles - book.id
                else customTitles + (book.id to remote.syncedCustomTitle)
                remote.syncedSeries?.name?.let { seriesName ->
                    favoriteSeriesNames = favoriteSeriesNames.withMembership(
                        seriesName,
                        remote.syncedSeriesFavorite,
                    )
                }
            }
            if (
                legacyIdAliases.isNotEmpty() ||
                cloudBooks.any(LibraryBook::hasSyncedSeriesState) ||
                customSeries != localSeries ||
                seriesRemovalTimes != localSeriesRemovalTimes
            ) {
                libraryPreferenceStore.saveHiddenIds(hiddenIds)
                libraryPreferenceStore.saveFavoriteIds(favoriteIds)
                libraryPreferenceStore.saveReadIds(readIds)
                libraryPreferenceStore.saveProgress(progress)
                libraryPreferenceStore.saveCustomSeries(customSeries)
                libraryPreferenceStore.saveSeriesRemovalTimes(seriesRemovalTimes)
                libraryPreferenceStore.savePendingSeriesSyncIds(pendingSeriesSyncIds)
                libraryPreferenceStore.savePendingBookStateSyncIds(pendingBookStateIds)
                libraryPreferenceStore.saveCustomTitles(customTitles)
                libraryPreferenceStore.saveFavoriteSeriesNames(favoriteSeriesNames)
            }
            // Pending IDs can outlive deleted or migrated books. Persist the sanitized queues
            // even when no other library preference changed so an impossible item cannot stay
            // visible as "대기" forever.
            libraryPreferenceStore.savePendingSeriesSyncIds(pendingSeriesSyncIds)
            libraryPreferenceStore.savePendingBookStateSyncIds(pendingBookStateIds)
            val allBooks = (localBooks + cloudBooks).map { book ->
                    val customTitle = customTitles[book.id]
                    if (customTitle != null) book.copy(title = customTitle, originalTitle = customTitle)
                    else book.copy(title = preferredLocalizedTitle(book.originalTitle))
                }
            val previousActiveBook = uiState.activeLibraryBook
            val savedActiveBookId = previousActiveBook?.id ?: libraryPreferenceStore.loadActiveBookId()
            val refreshedActiveBook = savedActiveBookId?.let { savedId ->
                val activeId = legacyIdAliases[savedId] ?: savedId
                allBooks.firstOrNull { it.id == activeId }
                    ?.withPreservedReaderPages(previousActiveBook)
            }
            if (refreshedActiveBook != null && refreshedActiveBook.id != savedActiveBookId) {
                libraryPreferenceStore.saveActiveBookId(refreshedActiveBook.id)
            } else if (savedActiveBookId != null && refreshedActiveBook == null && librarySnapshotComplete) {
                libraryPreferenceStore.saveActiveBookId(null)
            }
            val readerReadyBooks = refreshedActiveBook?.let { active ->
                allBooks.map { if (it.id == active.id) active else it }
            } ?: allBooks
            uiState = uiState.copy(
                libraryBooks = readerReadyBooks,
                libraryHiddenIds = hiddenIds,
                libraryFavoriteIds = favoriteIds,
                libraryReadIds = readIds,
                libraryProgress = progress,
                customSeriesByBookId = customSeries,
                libraryFavoriteSeriesNames = favoriteSeriesNames,
                desktopLibraryLocations = cloudBooks.distinctBy(LibraryBook::locationUri).map { book ->
                    StorageLocation(book.locationUri, book.locationName, isCloud = true)
                },
                desktopDownloadPath = desktopDownloadPath,
                activeLibraryBook = refreshedActiveBook,
                isLibraryBookLoading = refreshedActiveBook?.hasResolvedReaderPages() == false,
                isLibraryScanning = false,
                libraryScanError = (result.errors + listOfNotNull(cloudError))
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("\n"),
                librarySyncPendingCount = pendingSeriesSyncIds.size + pendingBookStateIds.size,
                libraryLastSyncedAt = if (profile != null && cloudError == null) {
                    System.currentTimeMillis()
                } else uiState.libraryLastSyncedAt,
                librarySyncError = cloudError,
            )
            refreshedActiveBook
                ?.takeUnless(LibraryBook::hasResolvedReaderPages)
                ?.let(::prepareActiveLibraryBook)
            if (profile != null && (pendingSeriesSyncIds.isNotEmpty() || pendingBookStateIds.isNotEmpty())) {
                flushPendingLibrarySync(profile, readerReadyBooks)
            }
            if (uiState.isLibrarySyncing) {
                val remaining = pendingLibrarySyncCount()
                uiState = uiState.copy(
                    isLibrarySyncing = false,
                    librarySyncPendingCount = remaining,
                    libraryLastSyncedAt = if (remaining == 0) {
                        System.currentTimeMillis()
                    } else uiState.libraryLastSyncedAt,
                    message = if (remaining == 0) {
                        "라이브러리 동기화를 완료했습니다."
                    } else {
                        "동기화되지 않은 변경사항이 ${remaining}개 남아 있습니다."
                    },
                    isError = remaining > 0,
                )
            }
            if (uiState.libraryLocations != scannedLocations) refreshLibrary()
        }
    }

    fun setLibraryQuery(value: String) = update { copy(libraryQuery = value) }
    fun toggleLibraryFavoritesFilter() = update {
        copy(libraryFavoritesOnly = !libraryFavoritesOnly)
    }
    fun setLibraryReadFilter(value: LibraryReadFilter) = update { copy(libraryReadFilter = value) }
    fun setLibraryVisibilityFilter(value: LibraryVisibilityFilter) = update {
        copy(libraryVisibilityFilter = value)
    }
    fun setLibrarySort(value: LibrarySort) = update { copy(librarySort = value) }
    fun selectLibraryLocation(uri: String?) = update { copy(selectedLibraryLocationUri = uri) }
    fun toggleLibraryLocation(uri: String) = update {
        val allUris = (libraryLocations + desktopLibraryLocations).mapTo(linkedSetOf()) { it.uri }
        val current = selectedLibraryLocationUris ?: allUris
        val toggled = current.toMutableSet().apply {
            if (!add(uri)) remove(uri)
        }
        copy(
            selectedLibraryLocationUri = null,
            selectedLibraryLocationUris = toggled.takeUnless { it.containsAll(allUris) },
        )
    }
    fun setLibrarySeriesMode(enabled: Boolean) = update {
        copy(librarySeriesMode = enabled, selectedLibrarySeries = null)
    }
    fun selectLibrarySeries(name: String?) = update { copy(selectedLibrarySeries = name) }

    fun openLibraryBook(bookId: String) {
        val book = uiState.libraryBooks.firstOrNull { it.id == bookId } ?: return
        val readIds = uiState.libraryReadIds + bookId
        libraryPreferenceStore.saveReadIds(readIds)
        libraryPreferenceStore.saveActiveBookId(bookId)
        uiState = uiState.copy(
            activeLibraryBook = book,
            libraryReadIds = readIds,
            isLibraryBookLoading = !book.hasResolvedReaderPages(),
            libraryScanError = null,
        )
        enqueueBookStateSync(setOf(bookId), addHistory = true)
        prepareActiveLibraryBook(book)
    }

    private fun prepareActiveLibraryBook(book: LibraryBook) {
        if (book.hasResolvedReaderPages()) {
            if (uiState.activeLibraryBook?.id == book.id) {
                uiState = uiState.copy(isLibraryBookLoading = false)
            }
            return
        }
        val preparation: suspend () -> LibraryBook = if (book.isCloud) {
            val profile = selectedProfile()
            val remoteId = book.remoteBookId
            if (profile == null || remoteId == null) {
                if (uiState.activeLibraryBook?.id == book.id) {
                    uiState = uiState.copy(
                        isLibraryBookLoading = false,
                        libraryScanError = "데스크톱 연결을 복구한 뒤 다시 시도해 주세요.",
                    )
                }
                return
            }
            suspend {
                val urls = client.getLibraryBookPages(profile, remoteId)
                book.copy(
                    pages = urls.mapIndexed { index, url -> LibraryPage(url, "${index + 1}") },
                    coverUriOverride = urls.firstOrNull() ?: book.coverUri,
                )
            }
        } else {
            suspend { libraryArchiveExtractor.materialize(book) }
        }
        activeBookPreparationJob?.cancel()
        activeBookPreparationJob = viewModelScope.launch {
            runCatching { preparation() }
                .onSuccess { prepared ->
                    val activeMatches = uiState.activeLibraryBook?.id == prepared.id
                    uiState = uiState.copy(
                        activeLibraryBook = if (activeMatches) prepared else uiState.activeLibraryBook,
                        libraryBooks = uiState.libraryBooks.map {
                            if (it.id == prepared.id) prepared else it
                        },
                        isLibraryBookLoading = if (activeMatches) false else uiState.isLibraryBookLoading,
                        libraryScanError = if (activeMatches) null else uiState.libraryScanError,
                    )
                }
                .onFailure { error ->
                    if (uiState.activeLibraryBook?.id == book.id) {
                        uiState = uiState.copy(
                            isLibraryBookLoading = false,
                            libraryScanError = error.message ?: if (book.isCloud) {
                                "데스크톱 페이지를 불러올 수 없습니다."
                            } else {
                                "압축파일을 열 수 없습니다."
                            },
                        )
                    }
                }
        }
    }

    fun toggleLibraryFavorite(bookId: String) {
        val ids = uiState.libraryFavoriteIds.toMutableSet().apply {
            if (!add(bookId)) remove(bookId)
        }
        libraryPreferenceStore.saveFavoriteIds(ids)
        uiState = uiState.copy(libraryFavoriteIds = ids)
        enqueueBookStateSync(setOf(bookId))
    }

    fun toggleLibrarySeriesFavorite(seriesName: String) {
        val names = uiState.libraryFavoriteSeriesNames.toMutableSet().apply {
            if (!add(seriesName)) remove(seriesName)
        }
        libraryPreferenceStore.saveFavoriteSeriesNames(names)
        uiState = uiState.copy(libraryFavoriteSeriesNames = names)
        enqueueBookStateSync(
            uiState.customSeriesByBookId.filterValues { it.name == seriesName }.keys,
        )
    }

    fun toggleLibraryRead(bookId: String) {
        val ids = uiState.libraryReadIds.toMutableSet().apply {
            if (!add(bookId)) remove(bookId)
        }
        libraryPreferenceStore.saveReadIds(ids)
        uiState = uiState.copy(libraryReadIds = ids)
        enqueueBookStateSync(setOf(bookId))
    }

    fun addLibraryFavorites(bookIds: Set<String>) {
        val ids = uiState.libraryFavoriteIds + bookIds
        libraryPreferenceStore.saveFavoriteIds(ids)
        uiState = uiState.copy(libraryFavoriteIds = ids)
        enqueueBookStateSync(bookIds)
    }

    fun markLibraryBooksRead(bookIds: Set<String>) {
        val ids = uiState.libraryReadIds + bookIds
        libraryPreferenceStore.saveReadIds(ids)
        uiState = uiState.copy(libraryReadIds = ids)
        enqueueBookStateSync(bookIds)
    }

    fun markLibraryBooksUnread(bookIds: Set<String>) {
        val ids = uiState.libraryReadIds - bookIds
        libraryPreferenceStore.saveReadIds(ids)
        uiState = uiState.copy(libraryReadIds = ids)
        enqueueBookStateSync(bookIds)
    }

    fun assignLibrarySeries(bookIds: Set<String>, seriesName: String) {
        val name = seriesName.trim()
        if (name.isEmpty()) return
        if (!ensureValidSeriesName(name)) return
        val modifiedAt = System.currentTimeMillis()
        val affectedNames = bookIds.mapNotNullTo(linkedSetOf()) { id ->
            uiState.customSeriesByBookId[id]?.name?.takeIf { it != name }
        } + name
        var nextOrder = uiState.customSeriesByBookId.values
            .filter { it.name == name }
            .maxOfOrNull(CustomSeriesAssignment::order)?.plus(1) ?: 0
        val additions = buildMap {
            bookIds.forEach { bookId ->
                val existing = uiState.customSeriesByBookId[bookId]
                put(
                    bookId,
                    if (existing?.name == name) existing.copy(modifiedAt = modifiedAt)
                    else CustomSeriesAssignment(name, nextOrder++, modifiedAt),
                )
            }
        }
        val assignments = touchSeriesAssignments(
            reindexSeriesAssignments(uiState.customSeriesByBookId + additions, affectedNames),
            affectedNames,
            modifiedAt,
        )
        persistCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
    }

    fun autoCreateLibrarySeries() {
        if (uiState.isAutoCreatingSeries) return
        val candidates = uiState.libraryBooks.filter {
            it.id !in uiState.customSeriesByBookId && it.id !in uiState.libraryHiddenIds
        }
        if (candidates.size < 2) {
            uiState = uiState.copy(
                message = "자동 생성할 미분류 갤러리가 부족합니다.",
                isError = false,
            )
            return
        }
        uiState = uiState.copy(
            isAutoCreatingSeries = true,
            message = null,
            isError = false,
        )
        viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.Default) {
                    val parents = IntArray(candidates.size) { it }
                    fun root(index: Int): Int {
                        var value = index
                        while (parents[value] != value) {
                            parents[value] = parents[parents[value]]
                            value = parents[value]
                        }
                        return value
                    }
                    fun join(left: Int, right: Int) {
                        val leftRoot = root(left)
                        val rightRoot = root(right)
                        if (leftRoot != rightRoot) parents[rightRoot] = leftRoot
                    }
                    candidates.indices.forEach { left ->
                        val leftArtists = candidates[left].metadata.artists
                            .mapTo(linkedSetOf(), ::normalizeSeriesText)
                        if (leftArtists.isEmpty()) return@forEach
                        for (right in left + 1 until candidates.size) {
                            val rightArtists = candidates[right].metadata.artists
                                .mapTo(linkedSetOf(), ::normalizeSeriesText)
                            if (leftArtists.intersect(rightArtists).isEmpty()) continue
                            val leftStem = normalizeSeriesText(seriesComparisonStem(candidates[left].title))
                            val rightStem = normalizeSeriesText(seriesComparisonStem(candidates[right].title))
                            if (leftStem.length >= 2 && rightStem.length >= 2 &&
                                (leftStem == rightStem || diceSimilarity(leftStem, rightStem) >= 0.82f)
                            ) join(left, right)
                        }
                    }
                    candidates.indices.groupBy(::root).values
                        .map { indices -> indices.map(candidates::get) }
                        .filter { it.size >= 2 }
                }
                if (groups.isEmpty()) {
                    uiState = uiState.copy(
                        message = "같은 작가와 유사한 제목을 가진 갤러리를 찾지 못했습니다.",
                        isError = false,
                    )
                    return@launch
                }
                val assignments = uiState.customSeriesByBookId.toMutableMap()
                val modifiedAt = System.currentTimeMillis()
                val usedNames = assignments.values.mapTo(linkedSetOf(), CustomSeriesAssignment::name)
                groups.forEach { group ->
                    val baseName = seriesDisplayStem(group.first().title).ifBlank { group.first().title }
                    var name = baseName
                    var suffix = 2
                    while (name in usedNames) name = "$baseName ($suffix)".also { suffix++ }
                    usedNames += name
                    group.sortedWith(compareBy<LibraryBook> { trailingSeriesNumber(it.title) ?: Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .forEachIndexed { order, book ->
                            assignments[book.id] = CustomSeriesAssignment(name, order, modifiedAt)
                        }
                }
                val indexed = reindexSeriesAssignments(assignments)
                persistCustomSeries(indexed)
                uiState = uiState.copy(
                    customSeriesByBookId = indexed,
                    message = "${groups.size}개 시리즈를 자동 생성했습니다.",
                    isError = false,
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    message = "자동 생성에 실패했습니다: ${error.message ?: "알 수 없는 오류"}",
                    isError = true,
                )
            } finally {
                uiState = uiState.copy(isAutoCreatingSeries = false)
            }
        }
    }

    fun reorderLibrarySeriesBooks(orderedBookIds: List<String>) {
        val requestedIds = orderedBookIds.distinct()
        val seriesName = requestedIds.firstNotNullOfOrNull { id ->
            uiState.customSeriesByBookId[id]?.name
        } ?: return
        val knownIds = uiState.libraryBooks.mapTo(hashSetOf(), LibraryBook::id)
        val currentIds = uiState.customSeriesByBookId.entries.asSequence()
            .filter { (id, assignment) -> id in knownIds && assignment.name == seriesName }
            .sortedWith(compareBy({ it.value.order }, { it.key }))
            .map { it.key }
            .toList()
        val movableIds = requestedIds.filter { id ->
            id in currentIds && uiState.customSeriesByBookId[id]?.name == seriesName
        }
        if (movableIds.size < 2) return
        val movableSet = movableIds.toHashSet()
        val replacements = movableIds.iterator()
        val reorderedIds = currentIds.map { id ->
            if (id in movableSet) replacements.next() else id
        }
        if (reorderedIds == currentIds) return
        val assignments = uiState.customSeriesByBookId.toMutableMap()
        val modifiedAt = System.currentTimeMillis()
        reorderedIds.forEachIndexed { order, id ->
            assignments[id] = assignments.getValue(id).copy(
                order = order,
                modifiedAt = modifiedAt,
            )
        }
        persistCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
    }

    fun setLibraryBooksHidden(bookIds: Set<String>, hidden: Boolean) {
        val updated = if (hidden) uiState.libraryHiddenIds + bookIds else uiState.libraryHiddenIds - bookIds
        libraryPreferenceStore.saveHiddenIds(updated)
        if (hidden && uiState.activeLibraryBook?.id in bookIds) {
            libraryPreferenceStore.saveActiveBookId(null)
            activeBookPreparationJob?.cancel()
        }
        uiState = uiState.copy(
            libraryHiddenIds = updated,
            activeLibraryBook = uiState.activeLibraryBook?.takeUnless { hidden && it.id in bookIds },
            message = if (hidden) "선택한 갤러리를 숨겼습니다." else "선택한 갤러리의 숨김을 해제했습니다.",
            isError = false,
        )
        enqueueBookStateSync(bookIds)
    }

    fun deleteLibraryBooks(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        val profile = selectedProfile()
        val selectedBooks = uiState.libraryBooks.filter { it.id in bookIds }
        val remoteBooks = selectedBooks.filter { it.isCloud && it.remoteBookId != null }
        val localBooks = selectedBooks.filterNot(LibraryBook::isCloud)
        if (remoteBooks.isNotEmpty() && profile == null) {
            uiState = uiState.copy(
                message = "데스크톱 갤러리를 삭제하려면 PC에 연결해야 합니다.",
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            val deletedIds = linkedSetOf<String>()
            val failures = mutableListOf<String>()
            remoteBooks.forEach { book ->
                runCatching { client.deleteLibraryBook(profile!!, book.remoteBookId!!) }
                    .onSuccess { deletedIds += book.id }
                    .onFailure { failures += "${book.title}: ${it.message ?: "삭제 실패"}" }
            }
            localBooks.forEach { book ->
                runCatching { libraryFileDeleter.delete(book) }
                    .onSuccess { deletedIds += book.id }
                    .onFailure { failures += "${book.title}: ${it.message ?: "삭제 실패"}" }
            }
            if (deletedIds.isNotEmpty()) removeDeletedLibraryState(deletedIds)
            val details = buildList {
                if (remoteBooks.any { it.id in deletedIds }) {
                    add("데스크톱 갤러리를 휴지통으로 이동하고 DB에서 삭제했습니다.")
                }
                if (localBooks.any { it.id in deletedIds }) {
                    add("로컬 갤러리 파일을 영구 삭제했습니다.")
                }
                if (failures.isNotEmpty()) add("${failures.size}개 삭제에 실패했습니다.")
            }.joinToString(" ")
            uiState = uiState.copy(message = details, isError = failures.isNotEmpty())
        }
    }

    private fun removeDeletedLibraryState(bookIds: Set<String>) {
        if (uiState.activeLibraryBook?.id in bookIds) {
            libraryPreferenceStore.saveActiveBookId(null)
            activeBookPreparationJob?.cancel()
        }
        val hidden = uiState.libraryHiddenIds - bookIds
        libraryPreferenceStore.saveHiddenIds(hidden)
        val favorites = uiState.libraryFavoriteIds - bookIds
        libraryPreferenceStore.saveFavoriteIds(favorites)
        val read = uiState.libraryReadIds - bookIds
        libraryPreferenceStore.saveReadIds(read)
        val progress = uiState.libraryProgress - bookIds
        libraryPreferenceStore.saveProgress(progress)
        val series = uiState.customSeriesByBookId - bookIds
        libraryPreferenceStore.saveCustomSeries(series)
        libraryPreferenceStore.saveSeriesRemovalTimes(
            libraryPreferenceStore.loadSeriesRemovalTimes() - bookIds,
        )
        libraryPreferenceStore.savePendingSeriesSyncIds(
            libraryPreferenceStore.loadPendingSeriesSyncIds() - bookIds,
        )
        libraryPreferenceStore.savePendingBookStateSyncIds(
            libraryPreferenceStore.loadPendingBookStateSyncIds() - bookIds,
        )
        val titles = libraryPreferenceStore.loadCustomTitles() - bookIds
        libraryPreferenceStore.saveCustomTitles(titles)
        uiState = uiState.copy(
            libraryBooks = uiState.libraryBooks.filterNot { it.id in bookIds },
            libraryHiddenIds = hidden,
            libraryFavoriteIds = favorites,
            libraryReadIds = read,
            libraryProgress = progress,
            customSeriesByBookId = series,
            activeLibraryBook = uiState.activeLibraryBook?.takeUnless { it.id in bookIds },
        )
    }

    fun renameLibraryBook(bookId: String, title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty()) return
        val titles = libraryPreferenceStore.loadCustomTitles() + (bookId to normalized)
        libraryPreferenceStore.saveCustomTitles(titles)
        uiState = uiState.copy(
            libraryBooks = uiState.libraryBooks.map {
                if (it.id == bookId) it.copy(title = normalized, originalTitle = normalized) else it
            },
            activeLibraryBook = uiState.activeLibraryBook?.let {
                if (it.id == bookId) it.copy(title = normalized, originalTitle = normalized) else it
            },
        )
        enqueueBookStateSync(setOf(bookId))
    }

    fun removeLibraryBooksFromSeries(bookIds: Set<String>) {
        val affectedNames = bookIds.mapNotNullTo(linkedSetOf()) { uiState.customSeriesByBookId[it]?.name }
        val modifiedAt = System.currentTimeMillis()
        val assignments = touchSeriesAssignments(
            reindexSeriesAssignments(uiState.customSeriesByBookId - bookIds),
            affectedNames,
            modifiedAt,
        )
        persistCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
    }

    fun renameLibrarySeries(oldName: String, newName: String) {
        val normalized = newName.trim()
        if (normalized.isEmpty() || oldName == normalized) return
        if (!ensureValidSeriesName(normalized)) return
        val modifiedAt = System.currentTimeMillis()
        val renamed = uiState.customSeriesByBookId.mapValues { (_, assignment) ->
            if (assignment.name == oldName) {
                assignment.copy(name = normalized, modifiedAt = modifiedAt)
            } else assignment
        }
        val assignments = reindexSeriesAssignments(renamed)
        persistCustomSeries(assignments)
        val favorites = uiState.libraryFavoriteSeriesNames.toMutableSet().apply {
            if (remove(oldName)) add(normalized)
        }
        libraryPreferenceStore.saveFavoriteSeriesNames(favorites)
        uiState = uiState.copy(
            customSeriesByBookId = assignments,
            libraryFavoriteSeriesNames = favorites,
        )
    }

    fun mergeLibrarySeries(seriesNames: List<String>, mergedName: String) {
        val names = seriesNames.distinct()
        val name = mergedName.trim()
        if (names.size < 2 || name.isEmpty()) return
        if (!ensureValidSeriesName(name)) return
        val modifiedAt = System.currentTimeMillis()
        val selectedEntries = names.flatMap { sourceName ->
            uiState.customSeriesByBookId.entries
                .filter { it.value.name == sourceName }
                .sortedWith(compareBy({ it.value.order }, { it.key }))
        }
        if (selectedEntries.isEmpty()) return
        val assignments = uiState.customSeriesByBookId
            .filterValues { it.name !in names }
            .toMutableMap()
        var nextOrder = assignments.values.filter { it.name == name }
            .maxOfOrNull(CustomSeriesAssignment::order)?.plus(1) ?: 0
        selectedEntries.forEach { entry ->
            assignments[entry.key] = CustomSeriesAssignment(name, nextOrder++, modifiedAt)
        }
        val indexed = touchSeriesAssignments(
            reindexSeriesAssignments(assignments),
            setOf(name),
            modifiedAt,
        )
        persistCustomSeries(indexed)
        val keepFavorite = names.firstOrNull() in uiState.libraryFavoriteSeriesNames
        val favorites = uiState.libraryFavoriteSeriesNames.toMutableSet().apply {
            removeAll(names.toSet())
            if (keepFavorite) add(name)
        }
        libraryPreferenceStore.saveFavoriteSeriesNames(favorites)
        uiState = uiState.copy(
            customSeriesByBookId = indexed,
            libraryFavoriteSeriesNames = favorites,
        )
    }

    fun deleteLibrarySeries(names: Set<String>) {
        val assignments = reindexSeriesAssignments(
            uiState.customSeriesByBookId.filterValues { it.name !in names },
        )
        persistCustomSeries(assignments)
        val favorites = uiState.libraryFavoriteSeriesNames - names
        libraryPreferenceStore.saveFavoriteSeriesNames(favorites)
        uiState = uiState.copy(
            customSeriesByBookId = assignments,
            libraryFavoriteSeriesNames = favorites,
        )
    }

    fun nextLibrarySeriesBook(bookId: String): LibraryBook? {
        val assignment = uiState.customSeriesByBookId[bookId] ?: return null
        val nextId = uiState.customSeriesByBookId.entries
            .filter { it.value.name == assignment.name && it.value.order > assignment.order }
            .minByOrNull { it.value.order }?.key ?: return null
        return uiState.libraryBooks.firstOrNull { it.id == nextId }
    }

    fun previousLibrarySeriesBook(bookId: String): LibraryBook? {
        val assignment = uiState.customSeriesByBookId[bookId] ?: return null
        val previousId = uiState.customSeriesByBookId.entries
            .filter { it.value.name == assignment.name && it.value.order < assignment.order }
            .maxByOrNull { it.value.order }?.key ?: return null
        return uiState.libraryBooks.firstOrNull { it.id == previousId }
    }

    private fun reindexSeriesAssignments(
        assignments: Map<String, CustomSeriesAssignment>,
        names: Set<String>? = null,
    ): Map<String, CustomSeriesAssignment> = buildMap {
        if (names != null) {
            assignments.filterValues { it.name !in names }.forEach(::put)
        }
        assignments.entries.groupBy { it.value.name }.forEach { (name, entries) ->
            if (names != null && name !in names) return@forEach
            val modifiedAt = entries.maxOfOrNull { it.value.modifiedAt } ?: 0L
            entries.sortedWith(compareBy({ it.value.order }, { it.key })).forEachIndexed { index, entry ->
                put(entry.key, CustomSeriesAssignment(name, index, modifiedAt))
            }
        }
    }

    private fun touchSeriesAssignments(
        assignments: Map<String, CustomSeriesAssignment>,
        names: Set<String>,
        modifiedAt: Long,
    ): Map<String, CustomSeriesAssignment> = assignments.mapValues { (_, assignment) ->
        if (assignment.name in names) assignment.copy(modifiedAt = modifiedAt) else assignment
    }

    fun updateLibraryProgress(bookId: String, page: Int) {
        if (uiState.libraryProgress[bookId] == page) return
        val progress = uiState.libraryProgress + (bookId to page.coerceAtLeast(0))
        libraryPreferenceStore.saveProgress(progress)
        uiState = uiState.copy(libraryProgress = progress)
        enqueueBookStateSync(setOf(bookId))
    }

    private fun ensureValidSeriesName(name: String): Boolean {
        if (name.length <= MAX_SERIES_NAME_LENGTH) return true
        uiState = uiState.copy(
            message = "시리즈 이름은 ${MAX_SERIES_NAME_LENGTH}자 이하로 입력해 주세요.",
            isError = true,
        )
        return false
    }

    fun updateOnlineReaderProgress(galleryId: Long, page: Int) {
        if (uiState.onlineReaderProgress[galleryId] == page) return
        val progress = uiState.onlineReaderProgress + (galleryId to page.coerceAtLeast(0))
        libraryPreferenceStore.saveOnlineProgress(progress)
        uiState = uiState.copy(onlineReaderProgress = progress)
    }

    fun updateViewerPreferences(value: ViewerPreferences) {
        libraryPreferenceStore.saveViewerPreferences(value)
        uiState = uiState.copy(viewerPreferences = value)
    }

    fun setDownloadLocation(uri: String, displayName: String) {
        val location = StorageLocation(uri, displayName)
        browserPreferenceStore.saveDownloadLocation(location)
        uiState = uiState.copy(downloadLocation = location)
    }

    fun clearDownloadLocation() {
        browserPreferenceStore.saveDownloadLocation(null)
        uiState = uiState.copy(downloadLocation = null)
    }

    fun resetDownloadConnectionAttempt() {
        if (uiState.isDownloadConnectionUnavailable) {
            notifyOnDownloadReconnect = true
        }
        uiState = uiState.copy(isDownloadConnectionUnavailable = false)
    }

    fun refreshDownloads(silent: Boolean = false) {
        if (downloadRefreshJob?.isActive == true || uiState.isDownloadConnectionUnavailable) return
        val profile = selectedProfile() ?: run {
            if (!silent) {
                uiState = uiState.copy(downloadQueueError = "연결된 데스크톱이 없습니다.")
            }
            return
        }
        downloadRefreshJob = viewModelScope.launch {
            if (!silent) {
                uiState = uiState.copy(
                    isDownloadQueueLoading = true,
                    downloadQueueError = null,
                )
            }
            try {
                val downloads = sortDownloadQueueNewest(client.getDownloads(profile))
                val completedIds = downloads.asSequence()
                    .filter { it.status == DownloadStatus.COMPLETED }
                    .mapTo(hashSetOf(), DownloadQueueItem::id)
                val hasNewCompletion = completedIds.any { it !in observedCompletedDownloadIds }
                observedCompletedDownloadIds.clear()
                observedCompletedDownloadIds += completedIds
                uiState = uiState.copy(
                    downloadQueue = downloads,
                    downloadQueueError = null,
                    isDownloadConnectionUnavailable = false,
                    connectionNotification = if (notifyOnDownloadReconnect) {
                        "PC와 다시 연결되었습니다."
                    } else {
                        uiState.connectionNotification
                    },
                )
                notifyOnDownloadReconnect = false
                if (hasNewCompletion) requestImportantLibraryRefresh()
            } catch (error: Exception) {
                if (!silent) {
                    uiState = uiState.copy(
                        downloadQueueError = error.message ?: "다운로드 목록을 불러오지 못했습니다.",
                        isDownloadConnectionUnavailable = true,
                    )
                }
            } finally {
                if (!silent) uiState = uiState.copy(isDownloadQueueLoading = false)
            }
        }
    }

    fun pauseDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        client.pauseDownload(profile, queueId)
    }

    fun resumeDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        client.resumeDownload(profile, queueId)
    }

    fun retryDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        client.retryDownload(profile, queueId)
    }

    fun removeDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        client.removeDownload(profile, queueId)
    }

    fun clearCompletedDownloads() = runDownloadAction(null) { profile ->
        client.clearCompletedDownloads(profile)
    }

    private fun runDownloadAction(
        queueId: Long?,
        action: suspend (DesktopProfile) -> Unit,
    ) {
        if (queueId != null && queueId in uiState.activeDownloadActionIds) return
        val profile = selectedProfile() ?: run {
            uiState = uiState.copy(downloadQueueError = "연결된 데스크톱이 없습니다.")
            return
        }
        viewModelScope.launch {
            if (queueId != null) {
                uiState = uiState.copy(
                    activeDownloadActionIds = uiState.activeDownloadActionIds + queueId,
                )
            }
            try {
                action(profile)
                uiState = uiState.copy(
                    downloadQueue = sortDownloadQueueNewest(client.getDownloads(profile)),
                    downloadQueueError = null,
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    downloadQueueError = error.message ?: "다운로드 작업에 실패했습니다.",
                )
            } finally {
                if (queueId != null) {
                    uiState = uiState.copy(
                        activeDownloadActionIds = uiState.activeDownloadActionIds - queueId,
                    )
                }
            }
        }
    }

    private fun selectedProfile(): DesktopProfile? =
        uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }

    private suspend fun loadPage(reset: Boolean, preserveResults: Boolean = false) {
        val profile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
        if (profile == null) {
            uiState = uiState.copy(message = "먼저 데스크톱을 페어링하거나 선택하세요.", isError = true)
            return
        }
        val query = if (reset) uiState.searchQuery.trim() else uiState.submittedSearchQuery
        val searchQueries = if (reset) {
            queriesWithPreferredLanguages(query, uiState.preferredLanguages)
        } else {
            uiState.submittedSearchQueries.ifEmpty {
                queriesWithPreferredLanguages(query, uiState.preferredLanguages)
            }
        }
        if (searchQueries.isEmpty()) {
            uiState = uiState.copy(message = "검색어를 입력하세요.", isError = true)
            return
        }
        val page = if (reset) 1 else uiState.currentPage + 1
        uiState = uiState.copy(
            submittedSearchQuery = if (reset) query else uiState.submittedSearchQuery,
            submittedSearchQueries = if (reset) searchQueries else uiState.submittedSearchQueries,
            galleries = if (reset && !preserveResults) emptyList() else uiState.galleries,
            currentPage = if (reset) 0 else uiState.currentPage,
            hasNextPage = if (reset) false else uiState.hasNextPage,
            isLoadingPage = true,
            message = null,
            isError = false,
        )

        try {
            val searchResults = coroutineScope {
                searchQueries.map { resolvedQuery ->
                    async { client.search(profile, resolvedQuery, page = page) }
                }.awaitAll()
            }
            val galleryIds = searchResults.flatMap { it.galleryIds }.distinct()
            val summaries = fetchGallerySummaries(profile, galleryIds)
            val merged = if (reset) summaries else (uiState.galleries + summaries).distinctBy { it.id }
            val knownFilters = rememberFilterTokens(summaries)
            val failedCount = summaries.count { it.loadError != null }
            uiState = uiState.copy(
                galleries = merged,
                knownFilterTokens = knownFilters,
                currentPage = page,
                hasNextPage = searchResults.any { it.hasNextPage },
                message = if (failedCount > 0) {
                    "상세 정보를 불러오지 못한 갤러리가 ${failedCount}개 있습니다."
                } else {
                    null
                },
                isError = failedCount > 0,
            )
        } catch (error: Exception) {
            uiState = uiState.copy(
                message = error.message ?: "검색 페이지를 불러오지 못했습니다.",
                hasNextPage = false,
                isError = true,
            )
        } finally {
            uiState = uiState.copy(isLoadingPage = false)
        }
    }

    private suspend fun fetchGallerySummaries(
        profile: DesktopProfile,
        galleryIds: List<Long>,
    ): List<GallerySummary> = coroutineScope {
        val semaphore = Semaphore(GALLERY_DETAIL_CONCURRENCY)
        galleryIds.map { galleryId ->
            async {
                semaphore.withPermit {
                    runCatching { client.getGallery(profile, galleryId) }
                        .getOrElse { error ->
                            GallerySummary(
                                id = galleryId,
                                title = "Gallery #$galleryId",
                                artists = emptyList(),
                                series = emptyList(),
                                galleryType = null,
                                tags = emptyList(),
                                thumbnailUrl = null,
                                pageCount = 0,
                                language = null,
                                loadError = error.message ?: "상세 정보를 불러오지 못했습니다.",
                            )
                        }
                }
            }
        }.awaitAll()
    }

    private suspend fun runOperation(block: suspend () -> Unit) {
        uiState = uiState.copy(isBusy = true, message = null, isError = false)
        try {
            block()
        } catch (error: Exception) {
            uiState = uiState.copy(
                message = operationErrorMessage(error),
                isError = true,
            )
        } finally {
            uiState = uiState.copy(isBusy = false)
        }
    }

    private inline fun update(transform: MainUiState.() -> MainUiState) {
        uiState = uiState.transform()
    }

    private fun queriesWithPreferredLanguages(
        query: String,
        preferredLanguages: Set<String>,
    ): List<String> {
        val baseQuery = query
            .replace(Regex("(?i)(^|\\s)-?language:[^\\s]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (preferredLanguages.isEmpty()) return listOf(query).filter { it.isNotBlank() }
        return preferredLanguages.sorted().map { language ->
            listOf(baseQuery, "language:$language").filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    private fun normalizeFilterFacet(facet: String): String {
        val separator = facet.indexOf(':')
        if (separator < 0) return facet.trim().replace(Regex("\\s+"), "_")
        val prefix = facet.substring(0, separator).trim().lowercase()
        val value = facet.substring(separator + 1)
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), "_")
        return "$prefix:$value"
    }

    private fun refreshFilterSuggestions(query: String) {
        suggestionJob?.cancel()
        val rawToken = query.substringAfterLast(' ').removePrefix("-").trim()
        if (rawToken.isEmpty()) {
            uiState = uiState.copy(filterSuggestions = emptyList(), isLoadingFilterSuggestions = false)
            return
        }
        if (':' !in rawToken) {
            val partial = rawToken.lowercase()
            val typeHints = FILTER_TYPES
                .filter { it.startsWith(partial) }
                .map { FilterSuggestion("$it:", "$it 필터") }
            val local = localUntypedFilterSuggestions(partial)
            val cacheKey = "untyped:$partial"
            suggestionCache[cacheKey]?.let { cached ->
                uiState = uiState.copy(
                    filterSuggestions = mergeUntypedSuggestions(typeHints, local, cached, partial),
                    isLoadingFilterSuggestions = false,
                )
                return
            }

            uiState = uiState.copy(
                filterSuggestions = mergeUntypedSuggestions(typeHints, local, emptyList(), partial),
                isLoadingFilterSuggestions = true,
            )
            suggestionJob = viewModelScope.launch {
                delay(SUGGESTION_DEBOUNCE_MS)
                val remote = coroutineScope {
                    UNTYPED_SUGGESTION_TYPES.map { type ->
                        async {
                            runCatching { suggestionClient.getSuggestions(type, partial) }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll().flatten()
                }
                suggestionCache[cacheKey] = remote
                val knownFilters = rememberKnownFilterTokens(remote.mapTo(linkedSetOf()) { it.token })
                if (uiState.searchQuery.substringAfterLast(' ').removePrefix("-") == rawToken) {
                    uiState = uiState.copy(
                        filterSuggestions = mergeUntypedSuggestions(typeHints, local, remote, partial),
                        isLoadingFilterSuggestions = false,
                        knownFilterTokens = knownFilters,
                    )
                } else if (knownFilters != uiState.knownFilterTokens) {
                    uiState = uiState.copy(knownFilterTokens = knownFilters)
                }
            }
            return
        }
        val type = rawToken.substringBefore(':').lowercase()
        val partial = rawToken.substringAfter(':').lowercase()
        if (type !in FILTER_TYPES || partial.isEmpty()) {
            uiState = uiState.copy(filterSuggestions = emptyList(), isLoadingFilterSuggestions = false)
            return
        }

        val local = localFilterSuggestions(type, partial)
        val bucket = partial.first().let { if (it.isDigit()) "0-9" else it.toString() }
        val cacheKey = "$type:$bucket"
        suggestionCache[cacheKey]?.let { cached ->
            uiState = uiState.copy(
                filterSuggestions = mergeSuggestions(local, cached, partial),
                isLoadingFilterSuggestions = false,
            )
            return
        }

        uiState = uiState.copy(filterSuggestions = local, isLoadingFilterSuggestions = true)
        suggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            val remote = runCatching { suggestionClient.getSuggestions(type, partial) }
                .getOrDefault(emptyList())
            suggestionCache[cacheKey] = remote
            val knownFilters = rememberKnownFilterTokens(remote.mapTo(linkedSetOf()) { it.token })
            if (uiState.searchQuery.substringAfterLast(' ').removePrefix("-") == rawToken) {
                uiState = uiState.copy(
                    filterSuggestions = mergeSuggestions(local, remote, partial),
                    isLoadingFilterSuggestions = false,
                    knownFilterTokens = knownFilters,
                )
            } else if (knownFilters != uiState.knownFilterTokens) {
                uiState = uiState.copy(knownFilterTokens = knownFilters)
            }
        }
    }

    private fun localFilterSuggestions(type: String, partial: String): List<FilterSuggestion> {
        val favoriteTokens = uiState.searchFavorites.asSequence().flatMap { favorite ->
            favorite.query.split(Regex("\\s+")).asSequence()
        }
        return (uiState.knownFilterTokens.asSequence() + favoriteTokens)
            .map { it.removePrefix("-") }
            .filter { it.startsWith("$type:$partial") }
            .distinct()
            .take(MAX_FILTER_SUGGESTIONS)
            .map { FilterSuggestion(it, it.substringAfter(':').replace('_', ' ')) }
            .toList()
    }

    private fun localUntypedFilterSuggestions(partial: String): List<FilterSuggestion> {
        val favoriteTokens = uiState.searchFavorites.asSequence().flatMap { favorite ->
            favorite.query.split(Regex("\\s+")).asSequence()
        }
        return (uiState.knownFilterTokens.asSequence() + favoriteTokens)
            .map { it.removePrefix("-") }
            .filter { token -> token.substringAfter(':', "").startsWith(partial) }
            .distinct()
            .take(MAX_FILTER_SUGGESTIONS)
            .map { token ->
                FilterSuggestion(token, token.substringAfter(':').replace('_', ' '))
            }
            .toList()
    }

    private fun mergeUntypedSuggestions(
        typeHints: List<FilterSuggestion>,
        local: List<FilterSuggestion>,
        remote: List<FilterSuggestion>,
        partial: String,
    ): List<FilterSuggestion> = (typeHints + local + remote)
        .filter { suggestion ->
            suggestion.token.substringBefore(':').startsWith(partial) ||
                suggestion.token.substringAfter(':', "").startsWith(partial)
        }
        .distinctBy { it.token }
        .take(MAX_FILTER_SUGGESTIONS)

    private fun mergeSuggestions(
        local: List<FilterSuggestion>,
        remote: List<FilterSuggestion>,
        partial: String,
    ): List<FilterSuggestion> = (local + remote)
        .filter { it.token.substringAfter(':').startsWith(partial) }
        .distinctBy { it.token }
        .take(MAX_FILTER_SUGGESTIONS)

    private fun filterValue(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "_")

    private fun rememberFilterTokens(galleries: List<GallerySummary>): Set<String> {
        val discovered = galleries.asSequence().flatMap { gallery ->
            sequence {
                gallery.artists.forEach { yield("artist:${filterValue(it)}") }
                gallery.series.forEach { yield("series:${filterValue(it)}") }
                gallery.galleryType?.let { yield("type:${filterValue(it)}") }
                gallery.tags.forEach { yield("${it.type}:${filterValue(it.name)}") }
            }
        }.filter { it.substringAfter(':').isNotBlank() }.toSet()
        return rememberKnownFilterTokens(discovered)
    }

    private fun rememberKnownFilterTokens(discovered: Set<String>): Set<String> {
        if (discovered.isEmpty()) return uiState.knownFilterTokens
        val merged = (uiState.knownFilterTokens + discovered)
            .toList()
            .takeLast(MAX_KNOWN_FILTER_TOKENS)
            .toSet()
        if (merged != uiState.knownFilterTokens) {
            browserPreferenceStore.saveKnownFilterTokens(merged)
        }
        return merged
    }

    private fun rememberViewedGallery(galleryId: Long): Set<Long> {
        if (galleryId in uiState.viewedGalleryIds) return uiState.viewedGalleryIds
        val viewed = (uiState.viewedGalleryIds + galleryId)
            .toList()
            .takeLast(MAX_VIEWED_GALLERIES)
            .toSet()
        browserPreferenceStore.saveViewedGalleryIds(viewed)
        return viewed
    }

    private fun cacheGalleries(
        existing: Map<Long, GallerySummary>,
        incoming: List<GallerySummary>,
    ): Map<Long, GallerySummary> {
        val cache = LinkedHashMap(existing)
        incoming.forEach { gallery ->
            cache.remove(gallery.id)
            cache[gallery.id] = gallery
        }
        while (cache.size > MAX_CACHED_GALLERIES) {
            cache.remove(cache.keys.first())
        }
        return cache
    }

    private fun persistCustomSeries(assignments: Map<String, CustomSeriesAssignment>) {
        libraryPreferenceStore.saveCustomSeries(assignments)
        val changedIds = (uiState.customSeriesByBookId.keys + assignments.keys).filterTo(linkedSetOf()) {
            uiState.customSeriesByBookId[it] != assignments[it]
        }
        if (changedIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val removalTimes = libraryPreferenceStore.loadSeriesRemovalTimes().toMutableMap()
        changedIds.forEach { id ->
            if (id in assignments) removalTimes.remove(id) else removalTimes[id] = now
        }
        libraryPreferenceStore.saveSeriesRemovalTimes(removalTimes)
        val syncableIds = uiState.libraryBooks.asSequence()
            .filter { it.id in changedIds && it.syncId != null }
            .mapTo(linkedSetOf(), LibraryBook::id)
        if (syncableIds.isNotEmpty()) {
            libraryPreferenceStore.savePendingSeriesSyncIds(
                libraryPreferenceStore.loadPendingSeriesSyncIds() + syncableIds,
            )
        }
        scheduleLibrarySync()
    }

    private fun pendingLibrarySyncCount(): Int =
        libraryPreferenceStore.loadPendingSeriesSyncIds().size +
            libraryPreferenceStore.loadPendingBookStateSyncIds().size

    private fun enqueueBookStateSync(changedIds: Set<String>, addHistory: Boolean = false) {
        val syncableIds = uiState.libraryBooks.asSequence()
            .filter { it.id in changedIds && it.syncId != null }
            .mapTo(linkedSetOf(), LibraryBook::id)
        if (syncableIds.isEmpty()) return
        val pending = libraryPreferenceStore.loadPendingBookStateSyncIds() + syncableIds
        libraryPreferenceStore.savePendingBookStateSyncIds(pending)
        if (addHistory) pendingHistoryBookIds += syncableIds
        uiState = uiState.copy(librarySyncPendingCount = pendingLibrarySyncCount())
        scheduleLibrarySync()
    }

    fun onAppForegrounded() {
        startConnectionMonitoring()
        if (!hasEnteredForeground) {
            hasEnteredForeground = true
            scheduleLibrarySync(0L)
            return
        }
        if (uiState.isLibraryScanning) {
            scheduleLibrarySync(0L)
        } else {
            refreshLibrary()
        }
    }

    fun onAppBackgrounded() {
        stopConnectionMonitoring()
        reconnectLibraryRefreshJob?.cancel()
        librarySyncDebounceJob?.cancel()
        librarySyncDebounceJob = viewModelScope.launch { flushPendingLibrarySync() }
    }

    private fun requestImportantLibraryRefresh() {
        importantLibraryRefreshJob?.cancel()
        importantLibraryRefreshJob = viewModelScope.launch {
            while (uiState.isLibraryScanning || uiState.isLibrarySyncing) {
                delay(IMPORTANT_LIBRARY_REFRESH_RETRY_MS)
            }
            syncLibraryNow()
        }
    }

    private fun requestReconnectLibraryRefresh() {
        reconnectLibraryRefreshJob?.cancel()
        reconnectLibraryRefreshJob = viewModelScope.launch {
            while (uiState.isLibraryScanning) {
                delay(IMPORTANT_LIBRARY_REFRESH_RETRY_MS)
            }
            refreshLibrary()
        }
    }

    private fun scheduleLibrarySync(delayMs: Long = LIBRARY_SYNC_DEBOUNCE_MS) {
        uiState = uiState.copy(librarySyncPendingCount = pendingLibrarySyncCount())
        if (pendingLibrarySyncCount() == 0) return
        librarySyncDebounceJob?.cancel()
        librarySyncDebounceJob = viewModelScope.launch {
            delay(delayMs)
            flushPendingLibrarySync()
        }
    }

    private suspend fun flushPendingLibrarySync(
        profileOverride: DesktopProfile? = null,
        booksOverride: List<LibraryBook>? = null,
    ) = librarySyncFlushMutex.withLock {
        val profile = profileOverride ?: selectedProfile() ?: return@withLock
        if (uiState.isLibraryScanning && booksOverride == null) {
            scheduleLibrarySync(LIBRARY_SYNC_SCAN_RETRY_MS)
            return@withLock
        }
        val books = booksOverride ?: uiState.libraryBooks
        val pendingSeriesIds = libraryPreferenceStore.loadPendingSeriesSyncIds()
        val pendingBookIds = libraryPreferenceStore.loadPendingBookStateSyncIds()
        val uploadFailures = uploadMissingLocalBooks(profile, books, pendingSeriesIds + pendingBookIds)
        if (uploadFailures.isNotEmpty()) {
            uiState = uiState.copy(
                librarySyncError = uploadFailures.joinToString("\n"),
                message = "${uploadFailures.size}개 로컬 파일을 데스크톱으로 전송하지 못했습니다.",
                isError = true,
            )
        }
        if (pendingSeriesIds.isNotEmpty()) {
            syncSeriesUpdates(
                profile = profile,
                books = books,
                assignments = libraryPreferenceStore.loadCustomSeries(),
                removalTimes = libraryPreferenceStore.loadSeriesRemovalTimes(),
                ids = pendingSeriesIds,
                uploadLocalBooks = false,
            )
        }
        if (pendingBookIds.isNotEmpty()) {
            val historyIds = pendingBookIds.filterTo(linkedSetOf()) { it in pendingHistoryBookIds }
            if (historyIds.isNotEmpty()) {
                syncBookStateUpdates(
                    profile,
                    books,
                    historyIds,
                    addHistory = true,
                    uploadLocalBooks = false,
                )
                pendingHistoryBookIds -= historyIds
            }
            val remainingIds = libraryPreferenceStore.loadPendingBookStateSyncIds()
            if (remainingIds.isNotEmpty()) {
                syncBookStateUpdates(profile, books, remainingIds, uploadLocalBooks = false)
            }
        }
    }

    private suspend fun syncBookStateUpdates(
        profile: DesktopProfile,
        books: List<LibraryBook>,
        ids: Set<String>,
        addHistory: Boolean = false,
        uploadLocalBooks: Boolean = true,
    ) = bookStateSyncMutex.withLock {
        if (uploadLocalBooks) uploadMissingLocalBooks(profile, books, ids)
        val customTitles = libraryPreferenceStore.loadCustomTitles()
        val updates = books.asSequence()
            .filter { it.id in ids }
            .mapNotNull { book ->
                val syncId = book.syncId ?: return@mapNotNull null
                val seriesName = uiState.customSeriesByBookId[book.id]?.name
                BookStateSyncUpdate(
                    bookSyncId = syncId,
                    baseVersion = book.syncStateVersion,
                    currentPage = uiState.libraryProgress[book.id] ?: 0,
                    isFavorite = book.id in uiState.libraryFavoriteIds,
                    isRead = book.id in uiState.libraryReadIds,
                    isHidden = book.id in uiState.libraryHiddenIds,
                    customTitle = customTitles[book.id],
                    seriesFavorite = seriesName in uiState.libraryFavoriteSeriesNames,
                    historyEventId = if (addHistory) UUID.randomUUID().toString() else null,
                    historyViewedAt = if (addHistory) Instant.now().toString() else null,
                )
            }
            .distinctBy { it.bookSyncId.lowercase() }
            .toList()
        if (updates.isEmpty()) return@withLock

        var pending = updates
        var conflictCount = 0
        for (attempt in 0..BOOK_STATE_SYNC_MAX_RETRIES) {
            val result = runCatching { client.saveBookStates(profile, pending) }.getOrNull()
            if (result != null) {
                conflictCount += result.count { it.conflict }
                val completedMutationIds = result.asSequence()
                    .filter { it.status == "applied" || it.status == "duplicate" }
                    .mapTo(hashSetOf()) { it.mutationId }
                pending = pending.filterNot { it.mutationId in completedMutationIds }
                if (pending.isEmpty()) break
            }
            if (attempt < BOOK_STATE_SYNC_MAX_RETRIES) {
                delay(BOOK_STATE_SYNC_RETRY_BASE_DELAY_MS shl attempt)
            }
        }

        val failedSyncIds = pending.mapTo(hashSetOf()) { it.bookSyncId.lowercase() }
        val attemptedIds = books.asSequence()
            .filter { it.id in ids && it.syncId != null }
            .mapTo(linkedSetOf(), LibraryBook::id)
        val failedIds = books.asSequence()
            .filter { it.id in attemptedIds && it.syncId?.lowercase() in failedSyncIds }
            .mapTo(linkedSetOf(), LibraryBook::id)
        val savedPending = (libraryPreferenceStore.loadPendingBookStateSyncIds() - attemptedIds) + failedIds
        libraryPreferenceStore.savePendingBookStateSyncIds(savedPending)
        uiState = uiState.copy(
            librarySyncPendingCount = savedPending.size +
                libraryPreferenceStore.loadPendingSeriesSyncIds().size,
            librarySyncError = if (pending.isEmpty()) null
            else "책 상태 ${pending.size}개를 동기화하지 못했습니다.",
            message = when {
                pending.isNotEmpty() -> "책 상태 ${pending.size}개를 동기화하지 못했습니다."
                conflictCount > 0 -> "다른 기기의 변경 ${conflictCount}건을 확인하고 최신 상태로 저장했습니다."
                else -> uiState.message
            },
            isError = pending.isNotEmpty(),
        )
    }

    fun syncLibraryNow() {
        val profile = selectedProfile()
        if (profile == null || uiState.isLibrarySyncing) {
            if (profile == null) {
                uiState = uiState.copy(
                    librarySyncError = "먼저 데스크톱과 연결하세요.",
                    message = "먼저 데스크톱과 연결하세요.",
                    isError = true,
                )
            }
            return
        }
        viewModelScope.launch {
            librarySyncDebounceJob?.cancel()
            uiState = uiState.copy(isLibrarySyncing = true, librarySyncError = null)
            val queuedIds = libraryPreferenceStore.loadPendingSeriesSyncIds() +
                libraryPreferenceStore.loadPendingBookStateSyncIds()
            flushPendingLibrarySync(profile, uiState.libraryBooks)
            val remainingIds = uiState.libraryBooks.asSequence()
                .mapTo(linkedSetOf(), LibraryBook::id) - queuedIds
            val failures = uploadMissingLocalBooks(profile, uiState.libraryBooks, remainingIds)
            if (failures.isNotEmpty()) {
                uiState = uiState.copy(
                    isLibrarySyncing = false,
                    librarySyncError = failures.joinToString("\n"),
                    message = "${failures.size}개 파일을 데스크톱으로 전송하지 못했습니다.",
                    isError = true,
                )
            }
            refreshLibrary()
        }
    }

    private suspend fun syncSeriesUpdates(
        profile: DesktopProfile,
        books: List<LibraryBook>,
        assignments: Map<String, CustomSeriesAssignment>,
        removalTimes: Map<String, Long>,
        ids: Set<String>,
        uploadLocalBooks: Boolean = true,
    ) = seriesSyncMutex.withLock {
        val uploadFailures = if (uploadLocalBooks) {
            uploadMissingLocalBooks(profile, books, ids)
        } else {
            emptyList()
        }
        if (uploadFailures.isNotEmpty()) {
            uiState = uiState.copy(
                librarySyncError = uploadFailures.joinToString("\n"),
                message = "시리즈를 저장하기 전에 ${uploadFailures.size}개 로컬 파일을 전송하지 못했습니다.",
                isError = true,
            )
        }
        val updates = books.asSequence()
            .filter { it.id in ids }
            .mapNotNull { book ->
                val syncId = book.syncId ?: return@mapNotNull null
                val assignment = assignments[book.id]
                val modifiedAt = assignment?.modifiedAt ?: removalTimes[book.id] ?: return@mapNotNull null
                SeriesSyncUpdate(
                    bookSyncId = syncId,
                    assignment = assignment,
                    modifiedAt = modifiedAt,
                )
            }
            .groupBy { it.bookSyncId.lowercase() }
            .map { (_, candidates) -> candidates.maxBy(SeriesSyncUpdate::modifiedAt) }
            .toList()
        if (updates.isEmpty()) return@withLock

        val failed = mutableListOf<SeriesSyncUpdate>()
        val failureMessages = linkedSetOf<String>()
        updates.chunked(MAX_SERIES_SYNC_ASSIGNMENTS).forEach { batch ->
            val outcome = syncSeriesBatchResilient(profile, batch)
            failed += outcome.failed
            outcome.errorMessage?.let(failureMessages::add)
            settleSeriesPendingBatch(
                books = books,
                attempted = batch,
                failed = outcome.failed,
                remoteWinnerSyncIds = outcome.remoteWinnerSyncIds,
            )
        }
        if (failed.isNotEmpty()) {
            val details = failureMessages.joinToString("\n").takeIf(String::isNotBlank)
            uiState = uiState.copy(
                librarySyncError = details ?: "시리즈 ${failed.size}개 항목을 데스크톱에 저장하지 못했습니다.",
                message = buildString {
                    append("시리즈 ${failed.size}개 항목을 데스크톱에 저장하지 못했습니다.")
                    if (details != null) append(" ").append(details.lineSequence().first())
                },
                isError = true,
            )
        }
        uiState = uiState.copy(
            librarySyncPendingCount = pendingLibrarySyncCount(),
        )
    }

    private fun settleSeriesPendingBatch(
        books: List<LibraryBook>,
        attempted: List<SeriesSyncUpdate>,
        failed: List<SeriesSyncUpdate>,
        remoteWinnerSyncIds: Set<String>,
    ) {
        val attemptedBySyncId = attempted.associateBy { it.bookSyncId.lowercase() }
        val failedSyncIds = failed.mapTo(hashSetOf()) { it.bookSyncId.lowercase() }
        val currentAssignments = libraryPreferenceStore.loadCustomSeries()
        val currentRemovals = libraryPreferenceStore.loadSeriesRemovalTimes()
        val completedIds = books.asSequence().mapNotNull { book ->
            val syncId = book.syncId?.lowercase() ?: return@mapNotNull null
            val update = attemptedBySyncId[syncId] ?: return@mapNotNull null
            if (syncId in failedSyncIds) return@mapNotNull null
            val currentAssignment = currentAssignments[book.id]
            val stillSameLocalRevision = if (update.assignment == null) {
                currentAssignment == null && currentRemovals[book.id] == update.modifiedAt
            } else {
                currentAssignment?.modifiedAt == update.modifiedAt &&
                    currentAssignment.sameSeriesStateAs(update.assignment)
            }
            book.id.takeIf { stillSameLocalRevision || syncId in remoteWinnerSyncIds }
        }.toSet()
        libraryPreferenceStore.savePendingSeriesSyncIds(
            libraryPreferenceStore.loadPendingSeriesSyncIds() - completedIds,
        )
        uiState = uiState.copy(librarySyncPendingCount = pendingLibrarySyncCount())
    }

    private suspend fun uploadMissingLocalBooks(
        profile: DesktopProfile,
        books: List<LibraryBook>,
        ids: Set<String>? = null,
    ): List<String> {
        val remoteSyncIds = books.asSequence()
            .filter(LibraryBook::isCloud)
            .mapNotNull { it.syncId?.lowercase() }
            .toHashSet()
        val targets = books.asSequence()
            .filterNot(LibraryBook::isCloud)
            .filter { ids == null || it.id in ids }
            .filter { book -> book.syncId?.lowercase()?.let { it !in remoteSyncIds } == true }
            .distinctBy { it.syncId?.lowercase() }
            .toList()
        val failures = mutableListOf<String>()
        targets.forEach { book ->
            val prepared = runCatching { libraryUploadPreparer.prepare(book) }.getOrElse { error ->
                failures += "${book.title}: ${error.message ?: "업로드 준비 실패"}"
                return@forEach
            }
            var uploaded = false
            var lastError: Throwable? = null
            for (attempt in 0..FILE_UPLOAD_MAX_RETRIES) {
                val result = runCatching {
                    client.uploadLibraryArchive(
                        profile = profile,
                        archive = prepared.file,
                        fileName = prepared.fileName,
                        syncId = prepared.syncId,
                    )
                }
                if (result.isSuccess) {
                    uploaded = true
                    break
                }
                lastError = result.exceptionOrNull()
                if (attempt < FILE_UPLOAD_MAX_RETRIES) {
                    delay(FILE_UPLOAD_RETRY_BASE_DELAY_MS shl attempt)
                }
            }
            prepared.file.delete()
            if (!uploaded) {
                failures += "${book.title}: ${lastError?.message ?: "파일 업로드 실패"}"
            }
        }
        return failures
    }

    private data class SeriesBatchOutcome(
        val failed: List<SeriesSyncUpdate>,
        val remoteWinnerSyncIds: Set<String>,
        val errorMessage: String? = null,
        val rejectedByClientError: Boolean = false,
    )

    private suspend fun syncSeriesBatchResilient(
        profile: DesktopProfile,
        updates: List<SeriesSyncUpdate>,
    ): SeriesBatchOutcome {
        val outcome = syncSeriesBatchWithRetry(profile, updates)
        if (!outcome.rejectedByClientError || updates.size == 1) return outcome
        val midpoint = updates.size / 2
        val left = syncSeriesBatchResilient(profile, updates.subList(0, midpoint))
        val right = syncSeriesBatchResilient(profile, updates.subList(midpoint, updates.size))
        return SeriesBatchOutcome(
            failed = left.failed + right.failed,
            remoteWinnerSyncIds = left.remoteWinnerSyncIds + right.remoteWinnerSyncIds,
            errorMessage = listOfNotNull(left.errorMessage, right.errorMessage)
                .distinct()
                .joinToString("\n")
                .takeIf(String::isNotBlank),
            rejectedByClientError = left.rejectedByClientError || right.rejectedByClientError,
        )
    }

    private suspend fun syncSeriesBatchWithRetry(
        profile: DesktopProfile,
        updates: List<SeriesSyncUpdate>,
    ): SeriesBatchOutcome {
        var pending = updates
        val remoteWinnerSyncIds = linkedSetOf<String>()
        var lastError: Throwable? = null
        repeat(SERIES_SYNC_MAX_RETRIES + 1) { attempt ->
            val saveResult = runCatching { client.saveLibrarySeries(profile, pending) }
            lastError = saveResult.exceptionOrNull() ?: lastError
            saveResult.getOrNull()?.let { results ->
                remoteWinnerSyncIds += applySeriesSyncConflicts(results)
                val bySyncId = results.associateBy { it.bookSyncId.lowercase() }
                pending = pending.filter { update ->
                    when (bySyncId[update.bookSyncId.lowercase()]?.status) {
                        "applied", "already_applied", "conflict" -> false
                        else -> true
                    }
                }
                if (pending.isEmpty()) {
                    return SeriesBatchOutcome(emptyList(), remoteWinnerSyncIds)
                }
            }

            val apiError = saveResult.exceptionOrNull() as? CompanionApiException
            if (apiError?.statusCode?.let { it in 400..499 } == true) {
                return SeriesBatchOutcome(
                    failed = pending,
                    remoteWinnerSyncIds = remoteWinnerSyncIds,
                    errorMessage = apiError.message,
                    rejectedByClientError = true,
                )
            }

            val remoteBooks = if (saveResult.isFailure) {
                runCatching { client.getLibraryBooks(profile) }.getOrNull()
            } else null
            if (remoteBooks != null) {
                pending = pendingSeriesSyncUpdates(pending, remoteBooks)
                if (pending.isEmpty()) {
                    return SeriesBatchOutcome(emptyList(), remoteWinnerSyncIds)
                }
            }

            if (attempt < SERIES_SYNC_MAX_RETRIES) {
                delay(SERIES_SYNC_RETRY_BASE_DELAY_MS shl attempt)
            }
        }
        return SeriesBatchOutcome(
            failed = pending,
            remoteWinnerSyncIds = remoteWinnerSyncIds,
            errorMessage = lastError?.message,
        )
    }

    private fun applySeriesSyncConflicts(results: List<SeriesSyncResult>): Set<String> {
        val conflicts = results.filter { it.status == "conflict" }
        if (conflicts.isEmpty()) return emptySet()
        val assignments = uiState.customSeriesByBookId.toMutableMap()
        val removals = libraryPreferenceStore.loadSeriesRemovalTimes().toMutableMap()
        val booksBySyncId = uiState.libraryBooks.groupBy { it.syncId?.lowercase() }
        val acceptedSyncIds = linkedSetOf<String>()
        conflicts.forEach { result ->
            val syncId = result.bookSyncId.lowercase()
            val matchingBooks = booksBySyncId[syncId].orEmpty()
            val newestLocalRevision = matchingBooks.maxOfOrNull { book ->
                maxOf(assignments[book.id]?.modifiedAt ?: 0L, removals[book.id] ?: 0L)
            } ?: 0L
            if (newestLocalRevision > result.modifiedAt) return@forEach
            acceptedSyncIds += syncId
            matchingBooks.forEach { book ->
                if (result.name == null) {
                    assignments.remove(book.id)
                    removals[book.id] = result.modifiedAt
                } else {
                    assignments[book.id] = CustomSeriesAssignment(
                        name = result.name,
                        order = result.order.coerceAtLeast(0),
                        modifiedAt = result.modifiedAt,
                    )
                    removals.remove(book.id)
                }
            }
        }
        libraryPreferenceStore.saveCustomSeries(assignments)
        libraryPreferenceStore.saveSeriesRemovalTimes(removals)
        if (acceptedSyncIds.isNotEmpty()) {
            uiState = uiState.copy(
                customSeriesByBookId = assignments,
                message = "다른 기기에서 먼저 변경된 시리즈 ${acceptedSyncIds.size}개는 데스크톱 상태를 적용했습니다.",
                isError = false,
            )
        }
        return acceptedSyncIds
    }

    private companion object {
        const val PAIRING_CODE_LENGTH = 6
        const val GALLERY_DETAIL_CONCURRENCY = 4
        const val SUGGESTION_DEBOUNCE_MS = 200L
        const val CONNECTION_MONITOR_INTERVAL_MS = 2_000L
        const val MAX_FILTER_SUGGESTIONS = 12
        const val MAX_CACHED_GALLERIES = 200
        const val MAX_KNOWN_FILTER_TOKENS = 20_000
        const val MAX_VIEWED_GALLERIES = 50_000
        const val MAX_SERIES_SYNC_ASSIGNMENTS = 25
        const val MAX_SERIES_NAME_LENGTH = 500
        const val LIBRARY_SYNC_DEBOUNCE_MS = 15_000L
        const val LIBRARY_SYNC_SCAN_RETRY_MS = 1_000L
        const val IMPORTANT_LIBRARY_REFRESH_RETRY_MS = 250L
        const val SERIES_SYNC_MAX_RETRIES = 3
        const val SERIES_SYNC_RETRY_BASE_DELAY_MS = 500L
        const val FILE_UPLOAD_MAX_RETRIES = 3
        const val FILE_UPLOAD_RETRY_BASE_DELAY_MS = 750L
        const val BOOK_STATE_SYNC_MAX_RETRIES = 3
        const val BOOK_STATE_SYNC_RETRY_BASE_DELAY_MS = 500L
        val FILTER_TYPES = listOf(
            "artist", "group", "type", "language", "series", "character", "male", "female", "tag",
        )
        val UNTYPED_SUGGESTION_TYPES = listOf("tag", "language", "type")
    }
}

internal fun sortDownloadQueueNewest(items: List<DownloadQueueItem>): List<DownloadQueueItem> =
    items.sortedWith(
        compareByDescending<DownloadQueueItem> { it.addedAt }
            .thenByDescending(DownloadQueueItem::id),
    )

internal fun preferredLibraryBookForGalleryId(
    books: List<LibraryBook>,
    galleryId: Long,
): LibraryBook? = books.asSequence()
    .filter { it.metadata.hitomiId?.toLongOrNull() == galleryId }
    .sortedBy(LibraryBook::isCloud)
    .firstOrNull()

internal fun remapLibraryIds(
    ids: Set<String>,
    aliases: Map<String, String>,
): Set<String> = ids.mapTo(linkedSetOf()) { aliases[it] ?: it }

internal fun <T> remapLibraryIdKeys(
    values: Map<String, T>,
    aliases: Map<String, String>,
): Map<String, T> = buildMap {
    values.filterKeys { it !in aliases }.forEach(::put)
    values.filterKeys { it in aliases }.forEach { (id, value) ->
        putIfAbsent(aliases.getValue(id), value)
    }
}

internal fun legacyLocalLibraryIdAliases(
    books: List<LibraryBook>,
): Map<String, String> = books.asSequence()
    .filterNot(LibraryBook::isCloud)
    .filter { it.folderUri.isNotBlank() && it.folderUri != it.id }
    .associate { it.folderUri to it.id }

internal fun LibraryBook.hasResolvedReaderPages(): Boolean =
    pages.isNotEmpty() && pages.all { it.uri.isNotBlank() }

internal fun LibraryBook.withPreservedReaderPages(previous: LibraryBook?): LibraryBook {
    if (hasResolvedReaderPages() || previous?.hasResolvedReaderPages() != true) return this
    val sameSource = if (isCloud) {
        previous.isCloud && remoteBookId == previous.remoteBookId
    } else {
        !previous.isCloud && folderUri == previous.folderUri
    }
    val unchanged = sameSource &&
        pages.size == previous.pages.size &&
        modifiedAt == previous.modifiedAt
    if (!unchanged) return this
    return copy(
        pages = previous.pages,
        coverUriOverride = previous.coverUriOverride ?: coverUriOverride,
    )
}

internal fun pendingSeriesSyncUpdates(
    updates: List<SeriesSyncUpdate>,
    remoteBooks: List<LibraryBook>,
): List<SeriesSyncUpdate> {
    val remoteBySyncId = remoteBooks.mapNotNull { book ->
        book.syncId?.lowercase()?.let { it to book }
    }.toMap()
    return updates.filterNot { update ->
        val remote = remoteBySyncId[update.bookSyncId.lowercase()] ?: return@filterNot false
        remote.hasSyncedSeriesState &&
            remote.syncedSeriesModifiedAt >= update.modifiedAt &&
            remote.syncedSeries.sameSeriesStateAs(update.assignment)
    }
}

private fun CustomSeriesAssignment?.sameSeriesStateAs(
    expected: CustomSeriesAssignment?,
): Boolean = when {
    this == null || expected == null -> this == null && expected == null
    else -> name == expected.name && order == expected.order
}

private fun Set<String>.withMembership(value: String, included: Boolean): Set<String> =
    if (included) this + value else this - value

internal data class SeriesMergeResult(
    val assignments: Map<String, CustomSeriesAssignment>,
    val removalTimes: Map<String, Long>,
    val localWinnerIds: Set<String>,
)

internal fun sanitizePendingSeriesSyncIds(
    pending: Set<String>,
    knownSyncableIds: Set<String>,
    librarySnapshotComplete: Boolean,
    assignments: Map<String, CustomSeriesAssignment>,
    removalTimes: Map<String, Long>,
): Set<String> = pending.filterTo(linkedSetOf()) { id ->
    (!librarySnapshotComplete || id in knownSyncableIds) &&
        (id in assignments || id in removalTimes)
}

internal fun mergeSyncedSeries(
    local: Map<String, CustomSeriesAssignment>,
    removalTimes: Map<String, Long>,
    books: List<LibraryBook>,
): SeriesMergeResult {
    val merged = local.toMutableMap()
    val mergedRemovalTimes = removalTimes.toMutableMap()
    val localWinnerIds = linkedSetOf<String>()
    books.filter(LibraryBook::hasSyncedSeriesState).forEach { book ->
        val localModifiedAt = maxOf(
            merged[book.id]?.modifiedAt ?: 0L,
            mergedRemovalTimes[book.id] ?: 0L,
        )
        if (localModifiedAt > book.syncedSeriesModifiedAt) {
            localWinnerIds += book.id
        } else {
            merged.remove(book.id)
            mergedRemovalTimes.remove(book.id)
            book.syncedSeries?.let { merged[book.id] = it }
                ?: run { mergedRemovalTimes[book.id] = book.syncedSeriesModifiedAt }
        }
    }
    return SeriesMergeResult(merged, mergedRemovalTimes, localWinnerIds)
}

private fun operationErrorMessage(error: Exception): String {
    val causes = generateSequence(error as Throwable?) { it.cause }.toList()
    return when {
        causes.any { it is SocketTimeoutException } ->
            "연결 시간이 초과되었습니다. 휴대폰의 VPN을 끄거나 로컬 네트워크 접근을 허용하고, PC와 같은 네트워크인지 확인해 주세요."
        causes.any { it is ConnectException || it is NoRouteToHostException } ->
            "PC에 연결할 수 없습니다. 휴대폰의 VPN과 네트워크 설정을 확인하고, PC와 같은 네트워크에 연결되어 있는지 확인해 주세요."
        causes.any { it is UnknownHostException } ->
            "PC 주소를 찾을 수 없습니다. 입력한 IP/호스트와 네트워크 설정을 확인해 주세요."
        else -> error.message ?: "알 수 없는 오류가 발생했습니다."
    }
}

private fun seriesDisplayStem(title: String): String = title
    .replace(Regex("^\\s*\\[[^]]+]\\s*"), "")
    .replace(
        Regex(
            "(?i)\\s*(?:[-_ ]*(?:vol(?:ume)?|ch(?:apter)?|episode|ep|part|권|화|장)\\.?\\s*)?#?" +
                "[0-9０-９]+(?:\\.[0-9]+)?\\s*$",
        ),
        "",
    )
    .trim(' ', '-', '_', '.', ':')

private fun seriesComparisonStem(title: String): String = seriesDisplayStem(title)
    .replace(Regex("(?i)\\b(?:vol(?:ume)?|ch(?:apter)?|episode|ep|part)\\.?\\b"), " ")
    .replace(Regex("(?:전|중|후|상|하)편|완결|특별편|외전"), " ")
    .replace(Regex("[0-9０-９]+"), " ")
    .trim()

private fun normalizeSeriesText(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun trailingSeriesNumber(value: String): Int? = Regex("([0-9]+)\\D*$")
    .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun diceSimilarity(left: String, right: String): Float {
    if (left == right) return 1f
    if (left.length < 2 || right.length < 2) return 0f
    val leftPairs = left.windowed(2).groupingBy { it }.eachCount().toMutableMap()
    var overlap = 0
    right.windowed(2).forEach { pair ->
        val count = leftPairs[pair] ?: 0
        if (count > 0) {
            overlap++
            leftPairs[pair] = count - 1
        }
    }
    return (2f * overlap) / (left.length - 1 + right.length - 1)
}
