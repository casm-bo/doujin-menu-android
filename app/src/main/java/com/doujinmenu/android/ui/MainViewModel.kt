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
import com.doujinmenu.android.model.DesktopProfile
import com.doujinmenu.android.model.DownloadQueueItem
import com.doujinmenu.android.model.GallerySummary
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.CustomSeriesAssignment
import com.doujinmenu.android.model.LibraryPage
import com.doujinmenu.android.model.LibraryReadFilter
import com.doujinmenu.android.model.LibrarySort
import com.doujinmenu.android.model.SearchFavorite
import com.doujinmenu.android.model.StorageLocation
import com.doujinmenu.android.model.ViewerPreferences
import com.doujinmenu.android.network.CompanionClient
import com.doujinmenu.android.network.EndpointNormalizer
import com.doujinmenu.android.network.FilterSuggestion
import com.doujinmenu.android.network.HitomiSuggestionClient
import com.doujinmenu.android.security.BrowserPreferenceStore
import com.doujinmenu.android.security.LibraryPreferenceStore
import com.doujinmenu.android.security.SecureProfileStore
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class MainUiState(
    val host: String = "",
    val port: String = EndpointNormalizer.DEFAULT_PORT.toString(),
    val pairingCode: String = "",
    val deviceName: String = Build.MODEL.orEmpty().ifBlank { "Android device" },
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
    val activeLibraryBook: LibraryBook? = null,
    val libraryFavoriteIds: Set<String> = emptySet(),
    val libraryReadIds: Set<String> = emptySet(),
    val libraryProgress: Map<String, Int> = emptyMap(),
    val customSeriesByBookId: Map<String, CustomSeriesAssignment> = emptyMap(),
    val libraryQuery: String = "",
    val libraryFavoritesOnly: Boolean = false,
    val libraryReadFilter: LibraryReadFilter = LibraryReadFilter.ALL,
    val librarySort: LibrarySort = LibrarySort.TITLE_ASC,
    val selectedLibraryLocationUri: String? = null,
    val selectedLibraryLocationUris: Set<String>? = null,
    val librarySeriesMode: Boolean = false,
    val selectedLibrarySeries: String? = null,
    val isLibraryScanning: Boolean = false,
    val libraryScanError: String? = null,
    val isLibraryBookLoading: Boolean = false,
    val viewerPreferences: ViewerPreferences = ViewerPreferences(),
    val downloadLocation: StorageLocation? = null,
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
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

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
    private val libraryScanner = LibraryScanner(application)
    private val libraryArchiveExtractor = LibraryArchiveExtractor(application)
    private val suggestionCache = mutableMapOf<String, List<FilterSuggestion>>()
    private var suggestionJob: Job? = null
    private var notifyOnDownloadReconnect = false

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
            libraryReadIds = libraryPreferenceStore.loadReadIds(),
            libraryProgress = libraryPreferenceStore.loadProgress(),
            customSeriesByBookId = libraryPreferenceStore.loadCustomSeries(),
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
                val deviceName = uiState.deviceName.trim()
                require(deviceName.isNotEmpty()) { "장치 이름을 입력하세요." }
                val baseUrl = EndpointNormalizer.normalize(uiState.host, uiState.port)
                val status = client.getStatus(baseUrl)
                require(status.pairingAvailable) { "데스크톱에서 새 페어링 코드를 먼저 생성하세요." }

                val result = client.pair(baseUrl, code, deviceName)
                notifyOnDownloadReconnect = false
                val profile = DesktopProfile(
                    id = result.deviceId,
                    name = result.deviceName,
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
                    downloadQueue = (uiState.downloadQueue + item).distinctBy { it.id },
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
            val cloudBooks = profile?.let {
                runCatching { client.getLibraryBooks(it) }.getOrDefault(emptyList())
            }.orEmpty()
            val hiddenIds = libraryPreferenceStore.loadHiddenIds()
            val customTitles = libraryPreferenceStore.loadCustomTitles()
            val allBooks = (result.books + cloudBooks).filterNot { it.id in hiddenIds }
                .map { book -> customTitles[book.id]?.let { book.copy(title = it) } ?: book }
            uiState = uiState.copy(
                libraryBooks = allBooks,
                desktopLibraryLocations = cloudBooks.distinctBy(LibraryBook::locationUri).map { book ->
                    StorageLocation(book.locationUri, book.locationName, isCloud = true)
                },
                activeLibraryBook = uiState.activeLibraryBook?.let { active ->
                    allBooks.firstOrNull { it.id == active.id }
                },
                isLibraryScanning = false,
                libraryScanError = result.errors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            )
            if (uiState.libraryLocations != scannedLocations) refreshLibrary()
        }
    }

    fun setLibraryQuery(value: String) = update { copy(libraryQuery = value) }
    fun toggleLibraryFavoritesFilter() = update {
        copy(libraryFavoritesOnly = !libraryFavoritesOnly)
    }
    fun setLibraryReadFilter(value: LibraryReadFilter) = update { copy(libraryReadFilter = value) }
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
        uiState = uiState.copy(
            activeLibraryBook = book,
            libraryReadIds = readIds,
            isLibraryBookLoading = book.isCloud || book.pages.any { it.uri.isBlank() },
            libraryScanError = null,
        )
        if (book.isCloud) {
            val profile = selectedProfile() ?: return
            val remoteId = book.remoteBookId ?: return
            viewModelScope.launch {
                runCatching { client.getLibraryBookPages(profile, remoteId) }
                    .onSuccess { urls ->
                        val prepared = book.copy(
                            pages = urls.mapIndexed { index, url -> LibraryPage(url, "${index + 1}") },
                            coverUriOverride = urls.firstOrNull() ?: book.coverUri,
                        )
                        uiState = uiState.copy(
                            activeLibraryBook = prepared,
                            libraryBooks = uiState.libraryBooks.map {
                                if (it.id == prepared.id) prepared else it
                            },
                            isLibraryBookLoading = false,
                        )
                    }
                    .onFailure { error ->
                        uiState = uiState.copy(
                            isLibraryBookLoading = false,
                            libraryScanError = error.message ?: "데스크톱 페이지를 불러올 수 없습니다.",
                        )
                    }
            }
        } else if (book.pages.any { it.uri.isBlank() }) {
            viewModelScope.launch {
                runCatching { libraryArchiveExtractor.materialize(book) }
                    .onSuccess { prepared ->
                        uiState = uiState.copy(
                            activeLibraryBook = prepared,
                            libraryBooks = uiState.libraryBooks.map {
                                if (it.id == prepared.id) prepared else it
                            },
                            isLibraryBookLoading = false,
                        )
                    }
                    .onFailure { error ->
                        uiState = uiState.copy(
                            isLibraryBookLoading = false,
                            libraryScanError = error.message ?: "압축파일을 열 수 없습니다.",
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
    }

    fun toggleLibraryRead(bookId: String) {
        val ids = uiState.libraryReadIds.toMutableSet().apply {
            if (!add(bookId)) remove(bookId)
        }
        libraryPreferenceStore.saveReadIds(ids)
        uiState = uiState.copy(libraryReadIds = ids)
    }

    fun addLibraryFavorites(bookIds: Set<String>) {
        val ids = uiState.libraryFavoriteIds + bookIds
        libraryPreferenceStore.saveFavoriteIds(ids)
        uiState = uiState.copy(libraryFavoriteIds = ids)
    }

    fun markLibraryBooksRead(bookIds: Set<String>) {
        val ids = uiState.libraryReadIds + bookIds
        libraryPreferenceStore.saveReadIds(ids)
        uiState = uiState.copy(libraryReadIds = ids)
    }

    fun markLibraryBooksUnread(bookIds: Set<String>) {
        val ids = uiState.libraryReadIds - bookIds
        libraryPreferenceStore.saveReadIds(ids)
        uiState = uiState.copy(libraryReadIds = ids)
    }

    fun assignLibrarySeries(bookIds: Set<String>, seriesName: String) {
        val name = seriesName.trim()
        if (name.isEmpty()) return
        var nextOrder = uiState.customSeriesByBookId.values
            .filter { it.name == name }
            .maxOfOrNull(CustomSeriesAssignment::order)?.plus(1) ?: 0
        val additions = buildMap {
            bookIds.forEach { bookId ->
                val existing = uiState.customSeriesByBookId[bookId]
                put(
                    bookId,
                    if (existing?.name == name) existing else CustomSeriesAssignment(name, nextOrder++),
                )
            }
        }
        val assignments = reindexSeriesAssignments(uiState.customSeriesByBookId + additions)
        libraryPreferenceStore.saveCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
    }

    fun moveLibrarySeriesBook(bookId: String, offset: Int) {
        val assignment = uiState.customSeriesByBookId[bookId] ?: return
        val orderedIds = uiState.customSeriesByBookId
            .filterValues { it.name == assignment.name }
            .entries.sortedWith(compareBy({ it.value.order }, { it.key }))
            .map { it.key }
            .toMutableList()
        val from = orderedIds.indexOf(bookId)
        val to = (from + offset).coerceIn(0, orderedIds.lastIndex)
        if (from < 0 || from == to) return
        val moved = orderedIds.removeAt(from)
        orderedIds.add(to, moved)
        val assignments = uiState.customSeriesByBookId.toMutableMap()
        orderedIds.forEachIndexed { order, id ->
            assignments[id] = assignments.getValue(id).copy(order = order)
        }
        libraryPreferenceStore.saveCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
    }

    fun hideLibraryBooks(bookIds: Set<String>) {
        val hidden = libraryPreferenceStore.loadHiddenIds() + bookIds
        libraryPreferenceStore.saveHiddenIds(hidden)
        uiState = uiState.copy(
            libraryBooks = uiState.libraryBooks.filterNot { it.id in bookIds },
            activeLibraryBook = uiState.activeLibraryBook?.takeUnless { it.id in bookIds },
        )
    }

    fun renameLibraryBook(bookId: String, title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty()) return
        val titles = libraryPreferenceStore.loadCustomTitles() + (bookId to normalized)
        libraryPreferenceStore.saveCustomTitles(titles)
        uiState = uiState.copy(
            libraryBooks = uiState.libraryBooks.map { if (it.id == bookId) it.copy(title = normalized) else it },
            activeLibraryBook = uiState.activeLibraryBook?.let {
                if (it.id == bookId) it.copy(title = normalized) else it
            },
        )
    }

    fun removeLibraryBooksFromSeries(bookIds: Set<String>) {
        val assignments = reindexSeriesAssignments(uiState.customSeriesByBookId - bookIds)
        libraryPreferenceStore.saveCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
    }

    fun renameLibrarySeries(oldName: String, newName: String) {
        val normalized = newName.trim()
        if (normalized.isEmpty() || oldName == normalized) return
        val renamed = uiState.customSeriesByBookId.mapValues { (_, assignment) ->
            if (assignment.name == oldName) assignment.copy(name = normalized) else assignment
        }
        val assignments = reindexSeriesAssignments(renamed)
        libraryPreferenceStore.saveCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
    }

    fun deleteLibrarySeries(names: Set<String>) {
        val assignments = reindexSeriesAssignments(
            uiState.customSeriesByBookId.filterValues { it.name !in names },
        )
        libraryPreferenceStore.saveCustomSeries(assignments)
        uiState = uiState.copy(customSeriesByBookId = assignments)
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
    ): Map<String, CustomSeriesAssignment> = buildMap {
        assignments.entries.groupBy { it.value.name }.forEach { (name, entries) ->
            entries.sortedWith(compareBy({ it.value.order }, { it.key })).forEachIndexed { index, entry ->
                put(entry.key, CustomSeriesAssignment(name, index))
            }
        }
    }

    fun updateLibraryProgress(bookId: String, page: Int) {
        if (uiState.libraryProgress[bookId] == page) return
        val progress = uiState.libraryProgress + (bookId to page.coerceAtLeast(0))
        libraryPreferenceStore.saveProgress(progress)
        uiState = uiState.copy(libraryProgress = progress)
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
        if (uiState.isDownloadQueueLoading || uiState.isDownloadConnectionUnavailable) return
        val profile = selectedProfile() ?: run {
            if (!silent) {
                uiState = uiState.copy(downloadQueueError = "연결된 데스크톱이 없습니다.")
            }
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(
                isDownloadQueueLoading = true,
                downloadQueueError = if (silent) uiState.downloadQueueError else null,
            )
            try {
                val downloads = client.getDownloads(profile)
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
            } catch (error: Exception) {
                uiState = uiState.copy(
                    downloadQueueError = null,
                    isDownloadConnectionUnavailable = true,
                )
            } finally {
                uiState = uiState.copy(isDownloadQueueLoading = false)
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
                    downloadQueue = client.getDownloads(profile),
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
                message = error.message ?: "알 수 없는 오류가 발생했습니다.",
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

    private companion object {
        const val PAIRING_CODE_LENGTH = 6
        const val GALLERY_DETAIL_CONCURRENCY = 4
        const val SUGGESTION_DEBOUNCE_MS = 200L
        const val MAX_FILTER_SUGGESTIONS = 12
        const val MAX_CACHED_GALLERIES = 200
        const val MAX_KNOWN_FILTER_TOKENS = 20_000
        const val MAX_VIEWED_GALLERIES = 50_000
        val FILTER_TYPES = listOf(
            "artist", "group", "type", "language", "series", "character", "male", "female", "tag",
        )
        val UNTYPED_SUGGESTION_TYPES = listOf("tag", "language", "type")
    }
}
