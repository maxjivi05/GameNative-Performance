package app.gamenative.service

import android.content.Context
import app.gamenative.PrefManager
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

data class QueuedDownload(
    val appId: String,
    val name: String,
    val gameSource: GameSource,
    val gameId: Int,
    val installPath: String,
    val dlcIds: List<Int> = emptyList(),
    var retryCount: Int = 0,
    var status: QueueStatus = QueueStatus.QUEUED
)

enum class QueueStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED }

object DownloadQueueManager {
    private val queue = ConcurrentLinkedQueue<QueuedDownload>()
    private val activeDownloads = CopyOnWriteArrayList<String>() // appIds currently downloading
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun enqueue(context: Context, item: LibraryItem, installPath: String, dlcIds: List<Int> = emptyList()) {
        // Don't enqueue if already in queue or actively downloading
        if (isQueued(item.appId) || isActivelyDownloading(item.appId)) {
            Timber.tag("DownloadQueue").d("${item.name} already queued or downloading, skipping")
            return
        }

        val queued = QueuedDownload(
            appId = item.appId,
            name = item.name,
            gameSource = item.gameSource,
            gameId = item.gameId,
            installPath = installPath,
            dlcIds = dlcIds
        )
        queue.add(queued)
        Timber.tag("DownloadQueue").i("Enqueued: ${item.name} (${item.appId})")
        processQueue(context)
    }

    fun processQueue(context: Context) {
        val maxConcurrent = PrefManager.maxConcurrentDownloads

        // Clean up activeDownloads: remove entries that are no longer actively downloading
        cleanupActiveDownloads()

        val activeCount = activeDownloads.size
        val slotsAvailable = maxConcurrent - activeCount

        if (slotsAvailable <= 0) {
            Timber.tag("DownloadQueue").d("No slots available ($activeCount/$maxConcurrent active)")
            return
        }

        var started = 0
        while (started < slotsAvailable) {
            val next = queue.poll() ?: break
            if (next.status == QueueStatus.FAILED) continue // skip permanently failed

            next.status = QueueStatus.DOWNLOADING
            activeDownloads.add(next.appId)
            startDownload(context, next)
            started++
            Timber.tag("DownloadQueue").i("Started download: ${next.name} (slot ${activeCount + started}/$maxConcurrent)")
        }
    }

    fun detectFailedDownloads(context: Context) {
        val allDownloads = DownloadService.getAllDownloads()
        for ((fullId, info) in allDownloads) {
            if (info.hasError() && activeDownloads.contains(fullId)) {
                activeDownloads.remove(fullId)
                Timber.tag("DownloadQueue").w("Download failed: $fullId - ${info.getErrorMessage()}")

                // Auto-retry if under limit
                if (info.getRetryCount() < 3) {
                    val canRetry = info.incrementRetry()
                    if (canRetry) {
                        Timber.tag("DownloadQueue").i("Auto-retrying $fullId (attempt ${info.getRetryCount()}/3)")
                        // Re-enqueue for retry
                        val retryItem = QueuedDownload(
                            appId = fullId,
                            name = fullId, // name will be resolved from UI
                            gameSource = detectGameSource(fullId),
                            gameId = extractGameId(fullId),
                            installPath = "", // will use existing path
                            retryCount = info.getRetryCount(),
                            status = QueueStatus.QUEUED
                        )
                        queue.add(retryItem)
                        processQueue(context)
                    }
                }
            }
        }
    }

    fun getQueuedItems(): List<QueuedDownload> = queue.toList()

    fun removeFromQueue(appId: String) {
        queue.removeAll { it.appId == appId }
        activeDownloads.remove(appId)
        Timber.tag("DownloadQueue").d("Removed from queue: $appId")
    }

    fun isQueued(appId: String): Boolean = queue.any { it.appId == appId }

    fun isActivelyDownloading(appId: String): Boolean = activeDownloads.contains(appId)

    fun getMaxConcurrent(): Int = PrefManager.maxConcurrentDownloads

    fun setMaxConcurrent(value: Int) {
        PrefManager.maxConcurrentDownloads = value
    }

    private fun cleanupActiveDownloads() {
        val allDownloads = DownloadService.getAllDownloads()
        val downloadingIds = allDownloads
            .filter { (_, info) -> info.isActive() && info.getProgress() < 1f }
            .map { it.first }
            .toSet()

        // Remove entries that are no longer active in any service
        activeDownloads.removeAll { it !in downloadingIds }
    }

    private fun startDownload(context: Context, item: QueuedDownload) {
        try {
            when (item.gameSource) {
                GameSource.STEAM -> {
                    SteamService.downloadApp(item.gameId)
                }
                GameSource.EPIC -> {
                    val path = if (item.installPath.isNotEmpty()) item.installPath
                    else app.gamenative.service.epic.EpicService.getInstallPath(item.gameId)
                        ?: app.gamenative.service.epic.EpicConstants.getGameInstallPath(context, item.name)
                    app.gamenative.service.epic.EpicService.downloadGame(context, item.gameId, item.dlcIds, path)
                }
                GameSource.GOG -> {
                    val path = if (item.installPath.isNotEmpty()) item.installPath
                    else app.gamenative.service.gog.GOGService.getInstallPath(item.gameId.toString())
                        ?: app.gamenative.service.gog.GOGConstants.getGameInstallPath(item.name)
                    app.gamenative.service.gog.GOGService.downloadGame(context, item.gameId.toString(), path)
                }
                GameSource.AMAZON -> {
                    val bareId = item.appId.removePrefix("AMAZON_")
                    val path = if (item.installPath.isNotEmpty()) item.installPath
                    else app.gamenative.service.amazon.AmazonService.getInstallPath(bareId)
                        ?: app.gamenative.service.amazon.AmazonConstants.getGameInstallPath(context, item.name)
                    scope.launch {
                        app.gamenative.service.amazon.AmazonService.downloadGame(context, bareId, path)
                    }
                }
                GameSource.CUSTOM_GAME -> {
                    Timber.tag("DownloadQueue").w("Custom games don't support downloads")
                }
            }
        } catch (e: Exception) {
            Timber.tag("DownloadQueue").e(e, "Failed to start download for ${item.name}")
            activeDownloads.remove(item.appId)
        }
    }

    private fun detectGameSource(appId: String): GameSource = when {
        appId.startsWith("STEAM_") -> GameSource.STEAM
        appId.startsWith("EPIC_") -> GameSource.EPIC
        appId.startsWith("GOG_") -> GameSource.GOG
        appId.startsWith("AMAZON_") -> GameSource.AMAZON
        appId.startsWith("CUSTOM_") -> GameSource.CUSTOM_GAME
        else -> GameSource.STEAM
    }

    private fun extractGameId(appId: String): Int {
        val idPart = appId.substringAfter("_")
        return idPart.toIntOrNull() ?: idPart.hashCode()
    }
}
