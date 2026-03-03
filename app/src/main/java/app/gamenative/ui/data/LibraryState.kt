package app.gamenative.ui.data

import app.gamenative.PrefManager
import app.gamenative.data.GameCompatibilityStatus
import app.gamenative.data.LibraryItem
import app.gamenative.data.SteamApp
import app.gamenative.ui.enums.AppFilter
import java.util.EnumSet

data class DownloadItemState(
    val appId: String,
    val name: String,
    val artUrl: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: Long,
    val isPaused: Boolean,
    val isCompleted: Boolean,
    val progress: Float,
    val isQueued: Boolean = false,
    val retryCount: Int = 0,
    val hasError: Boolean = false,
    val errorMessage: String = ""
)

data class LibraryState(
    val appInfoSortType: EnumSet<AppFilter> = PrefManager.libraryFilter,
    val appInfoList: List<LibraryItem> = emptyList(),
    val isRefreshing: Boolean = false,

    // Human readable, not 0-indexed
    val totalAppsInFilter: Int = 0,
    val currentPaginationPage: Int = 1,
    val lastPaginationPage: Int = 1,

    val modalBottomSheet: Boolean = false,

    val isSearching: Boolean = false,
    val searchQuery: String = "",

    // App Source filters (Steam / Custom Games / GOG / Epic / Amazon)
    val showSteamInLibrary: Boolean = PrefManager.showSteamInLibrary,
    val showCustomGamesInLibrary: Boolean = PrefManager.showCustomGamesInLibrary,
    val showGOGInLibrary: Boolean = PrefManager.showGOGInLibrary,
    val showEpicInLibrary: Boolean = PrefManager.showEpicInLibrary,
    val showAmazonInLibrary: Boolean = PrefManager.showAmazonInLibrary,

    // Loading state for skeleton loaders
    val isLoading: Boolean = false,

    // Refresh counter that increments when custom game images are fetched
    // Used to trigger UI recomposition to show newly downloaded images
    val imageRefreshCounter: Long = 0,

    // Compatibility status map: game name -> compatibility status
    val compatibilityMap: Map<String, GameCompatibilityStatus> = emptyMap(),

    // Live installed count computed from the full combined list (before pagination)
    val totalInstalledCount: Int = 0,

    // Full per-source lists (unpaginated) for storefront tabs
    val storeItems: List<LibraryItem> = emptyList(),
    val activeDownloads: List<DownloadItemState> = emptyList(),
    val steamItems: List<LibraryItem> = emptyList(),
    val steamApps: List<SteamApp> = emptyList(),
    val gogItems: List<LibraryItem> = emptyList(),
    val epicItems: List<LibraryItem> = emptyList(),
    val amazonItems: List<LibraryItem> = emptyList(),
    val customItems: List<LibraryItem> = emptyList(),

    // Current library layout
    val libraryLayout: app.gamenative.ui.enums.PaneType = app.gamenative.PrefManager.libraryLayout,

    // Reactive max concurrent downloads
    val maxConcurrentDownloads: Int = PrefManager.maxConcurrentDownloads,

    // Download progress map: appId -> progress (0f..1f) for overlay in Library tab
    val downloadProgressMap: Map<String, Float> = emptyMap(),

    // AIO Store toggle: true = single "Store" tab, false = individual store tabs
    val aioStoreEnabled: Boolean = PrefManager.aioStoreEnabled,
)
