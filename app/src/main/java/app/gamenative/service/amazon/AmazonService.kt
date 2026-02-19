package app.gamenative.service.amazon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.PluviaApp
import app.gamenative.data.AmazonCredentials
import app.gamenative.data.AmazonGame
import app.gamenative.data.DownloadInfo
import app.gamenative.enums.Marker
import app.gamenative.events.AndroidEvent
import app.gamenative.service.NotificationHelper
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.MarkerUtils
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Amazon Games foreground service.
 *
 * Responsibilities:
 *  - Library sync via [AmazonManager] (calls Amazon distribution API directly, no nile binary)
 *  - Game download/install via [AmazonDownloadManager]
 */
@AndroidEntryPoint
class AmazonService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var amazonManager: AmazonManager

    @Inject
    lateinit var amazonDownloadManager: AmazonDownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Active downloads keyed by Amazon product ID (e.g. "amzn1.adg.product.XXXX")
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()

    // In-memory cache for install status + path — avoids runBlocking for fast UI lookups
    private val installInfoCache = ConcurrentHashMap<String, CachedInstallInfo>()

    private data class CachedInstallInfo(
        val isInstalled: Boolean,
        val installPath: String,
    )

    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.AMAZON_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.AMAZON_MANUAL_SYNC"
        private const val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L // 15 minutes
        private var instance: AmazonService? = null

        // Sync tracking variables
        private var lastSyncTimestamp: Long = 0L
        private var hasPerformedInitialSync: Boolean = false
        private var syncInProgress: Boolean = false
        private var backgroundSyncJob: Job? = null

        // Game session tracking for playtime
        private var gameSessionStartMs: Long = 0L
        private var activeGameProductId: String? = null

        private fun setSyncInProgress(inProgress: Boolean) {
            syncInProgress = inProgress
        }

        fun isSyncInProgress(): Boolean = syncInProgress

        /**
         * Returns true if the service has ongoing work (sync or downloads).
         * Used to guard against premature service stop.
         */
        fun hasActiveOperations(): Boolean {
            return syncInProgress || backgroundSyncJob?.isActive == true || hasActiveDownload()
        }

        val isRunning: Boolean
            get() = instance != null

        fun start(context: Context) {
            if (isRunning) {
                Timber.d("[Amazon] Service already running")
                return
            }

            val intent = Intent(context, AmazonService::class.java)

            // First-time start: always sync without throttle
            if (!hasPerformedInitialSync) {
                Timber.i("[Amazon] First-time start — starting service with initial sync")
                intent.action = ACTION_SYNC_LIBRARY
                context.startForegroundService(intent)
                return
            }

            // Subsequent starts: check throttle for sync
            val now = System.currentTimeMillis()
            val timeSinceLastSync = now - lastSyncTimestamp

            if (timeSinceLastSync >= SYNC_THROTTLE_MILLIS) {
                Timber.i("[Amazon] Starting service with automatic sync (throttle passed)")
                intent.action = ACTION_SYNC_LIBRARY
            } else {
                val remainingMinutes = (SYNC_THROTTLE_MILLIS - timeSinceLastSync) / 1000 / 60
                Timber.i("[Amazon] Starting service without sync — throttled (${remainingMinutes}min remaining)")
            }
            context.startForegroundService(intent)
        }

        fun stop() {
            instance?.stopSelf()
        }

        fun getInstance(): AmazonService? = instance

        fun hasStoredCredentials(context: Context): Boolean =
            AmazonAuthManager.hasStoredCredentials(context)

        /**
         * Authenticate with Amazon Games using PKCE authorization code.
         * Called from the Settings UI after the WebView captures the code.
         */
        suspend fun authenticateWithCode(
            context: Context,
            authCode: String,
        ): Result<AmazonCredentials> = AmazonAuthManager.authenticateWithCode(context, authCode)

        /**
         * Trigger a manual library sync, bypassing the throttle.
         */
        fun triggerLibrarySync(context: Context) {
            Timber.i("[Amazon] Manual sync requested — bypassing throttle")
            if (isRunning) {
                val intent = Intent(context, AmazonService::class.java)
                intent.action = ACTION_MANUAL_SYNC
                context.startForegroundService(intent)
            }
        }

        // ── Install queries ───────────────────────────────────────────────────

        /**
         * Fetch the total download size for a game by retrieving its manifest.
         * Also caches the result in the DB for future display.
         *
         * @return Size in bytes, or null on failure.
         */
        suspend fun fetchDownloadSize(productId: String): Long? {
            val svc = instance ?: return null
            val game = svc.amazonManager.getGameById(productId) ?: return null
            if (game.entitlementId.isBlank()) return null

            val token = svc.amazonManager.getBearerToken() ?: return null
            val size = AmazonApiClient.fetchDownloadSize(game.entitlementId, token) ?: return null

            // Cache in DB so we don't have to re-fetch next time
            svc.amazonManager.updateDownloadSize(productId, size)
            return size
        }

        /**
         * Returns true if the Amazon game with [productId] is marked as installed.
         * Reads from the in-memory cache — no DB query, no thread blocking.
         */
        fun isGameInstalled(productId: String): Boolean {
            return instance?.installInfoCache?.get(productId)?.isInstalled ?: false
        }

        // ── Playtime tracking ─────────────────────────────────────────────────

        /**
         * Mark the start of a game session for [productId].
         * Called from preLaunchApp when an Amazon game is about to launch.
         */
        fun startGameSession(productId: String) {
            activeGameProductId = productId
            gameSessionStartMs = System.currentTimeMillis()
            Timber.tag("Amazon").i("Game session started for $productId")
        }

        /**
         * Record the elapsed session playtime into the database.
         * Called from exitSteamApp when an Amazon game exits the XServer.
         *
         * Adds the session duration (in minutes) to the existing cumulative total
         * and updates the lastPlayed timestamp.
         */
        suspend fun recordSessionPlaytime() {
            val productId = activeGameProductId
            val startMs = gameSessionStartMs
            Timber.tag("Amazon").i("recordSessionPlaytime: productId=$productId, startMs=$startMs")

            if (productId == null || startMs <= 0L) {
                Timber.tag("Amazon").w("recordSessionPlaytime: no active session to record")
                return
            }

            val now = System.currentTimeMillis()
            val elapsedSeconds = ((now - startMs) / 1_000L).coerceAtLeast(1)

            // Reset tracking vars immediately to avoid double-counting
            activeGameProductId = null
            gameSessionStartMs = 0L

            val svc = instance ?: return
            val game = svc.amazonManager.getGameById(productId)
            val previousSeconds = game?.playTimeMinutes ?: 0L  // column name is legacy, stores seconds
            val totalSeconds = previousSeconds + elapsedSeconds

            svc.amazonManager.updatePlaytime(productId, now, totalSeconds)
            Timber.tag("Amazon").i(
                "Session ended for $productId: +${elapsedSeconds}s → total ${totalSeconds}s (${totalSeconds / 60}min)"
            )
        }

        /**
         * Returns the [AmazonGame] for the given product ID, or null if not found / service not up.
         */
        suspend fun getAmazonGameOf(productId: String): AmazonGame? {
            return withContext(Dispatchers.IO) {
                instance?.amazonManager?.getGameById(productId)
            }
        }

        /**
         * Returns the on-disk install path for [productId], or null if not installed.
         * Reads from the in-memory cache — no DB query, no thread blocking.
         */
        fun getInstallPath(productId: String): String? {
            val info = instance?.installInfoCache?.get(productId) ?: return null
            return if (info.isInstalled && info.installPath.isNotEmpty()) info.installPath else null
        }

        /** Deprecated name kept for call-site compatibility — delegates to [getInstallPath]. */
        fun getInstalledGamePath(gameId: String): String? = getInstallPath(gameId)

        /**
         * Checks whether an installed Amazon game has a newer version available.
         * Compares the stored [AmazonGame.versionId] against the live version from Amazon.
         *
         * @return `true` if an update is available, `false` otherwise (including API failures).
         */
        suspend fun isUpdatePending(productId: String): Boolean {
            val svc = instance ?: return false
            val game = svc.amazonManager.getGameById(productId) ?: return false
            if (!game.isInstalled || game.versionId.isEmpty()) return false
            val token = svc.amazonManager.getBearerToken() ?: return false
            return AmazonApiClient.isUpdateAvailable(productId, game.versionId, token) ?: false
        }

        // ── Download management ───────────────────────────────────────────────

        /** Returns the active [DownloadInfo] for [productId], or null if not downloading. */
        fun getDownloadInfo(productId: String): DownloadInfo? =
            getInstance()?.activeDownloads?.get(productId)

        /** Returns true if there is at least one active download. */
        fun hasActiveDownload(): Boolean =
            getInstance()?.activeDownloads?.isNotEmpty() == true

        /**
         * Begin downloading [productId] to [installPath].
         *
         * @return A [DownloadInfo] the UI can observe for progress/status updates.
         */
        suspend fun downloadGame(
            context: Context,
            productId: String,
            installPath: String,
        ): Result<DownloadInfo> {
            val instance = getInstance()
                ?: return Result.failure(Exception("Amazon service is not running"))

            // Already downloading?
            instance.activeDownloads[productId]?.let { existing ->
                Timber.tag("Amazon").w("Download already in progress for $productId")
                return Result.success(existing)
            }

            val game = withContext(Dispatchers.IO) {
                instance.amazonManager.getGameById(productId)
            } ?: return Result.failure(Exception("Game not found: $productId"))

            val downloadInfo = DownloadInfo(
                jobCount = 1,
                gameId = productId.hashCode(),
                downloadingAppIds = CopyOnWriteArrayList(),
            )
            downloadInfo.setActive(true)
            instance.activeDownloads[productId] = downloadInfo

            PluviaApp.events.emitJava(
                AndroidEvent.DownloadStatusChanged(productId.hashCode(), true)
            )

            val job = instance.serviceScope.launch {
                try {
                    val result = instance.amazonDownloadManager.downloadGame(
                        context = context,
                        game = game,
                        installPath = installPath,
                        downloadInfo = downloadInfo,
                    )

                    if (result.isSuccess) {
                        Timber.tag("Amazon").i("Download succeeded for $productId")
                        // Update install info cache
                        instance.installInfoCache[productId] = CachedInstallInfo(
                            isInstalled = true,
                            installPath = installPath,
                        )
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "Download completed: ${game.title}",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        PluviaApp.events.emitJava(
                            AndroidEvent.LibraryInstallStatusChanged(productId.hashCode())
                        )
                    } else {
                        val error = result.exceptionOrNull()
                        Timber.tag("Amazon").e(error, "Download failed for $productId")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "Download failed: ${error?.message ?: "Unknown error"}",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("Amazon").e(e, "Download exception for $productId")
                    downloadInfo.setActive(false)
                } finally {
                    instance.activeDownloads.remove(productId)
                    PluviaApp.events.emitJava(
                        AndroidEvent.DownloadStatusChanged(productId.hashCode(), false)
                    )
                }
            }

            downloadInfo.setDownloadJob(job)
            return Result.success(downloadInfo)
        }

        /**
         * Cancel an in-progress download for [productId].
         * @return true if a download was found and cancelled.
         */
        fun cancelDownload(productId: String): Boolean {
            val instance = getInstance() ?: return false
            val downloadInfo = instance.activeDownloads[productId] ?: run {
                Timber.tag("Amazon").w("No active download for $productId")
                return false
            }
            Timber.tag("Amazon").i("Cancelling download for $productId")
            downloadInfo.cancel()
            instance.activeDownloads.remove(productId)
            return true
        }

        /**
         * Delete installed files for [productId] and mark it as uninstalled in the DB.
         *
         * Uses the cached manifest (if available) to delete only the files that were
         * originally installed, then removes empty directories bottom-up. Falls back to
         * [File.deleteRecursively] when no manifest is cached (mirrors Nile's
         * `utils/uninstall.py` behaviour).
         */
        suspend fun deleteGame(context: Context, productId: String): Result<Unit> {
            val instance = getInstance()
                ?: return Result.failure(Exception("Amazon service is not running"))

            return withContext(Dispatchers.IO) {
                try {
                    val game = instance.amazonManager.getGameById(productId)
                        ?: return@withContext Result.failure(Exception("Game not found: $productId"))

                    val path = game.installPath
                    if (path.isNotEmpty() && File(path).exists()) {
                        val installDir = File(path)
                        val manifestFile = File(context.filesDir, "manifests/amazon/$productId.proto")

                        if (manifestFile.exists()) {
                            // ── Manifest-based uninstall ─────────────────────────
                            Timber.tag("Amazon").i("Manifest-based uninstall for $productId")
                            try {
                                val manifest = AmazonManifest.parse(manifestFile.readBytes())
                                var deletedFiles = 0
                                var failedFiles = 0

                                for (mf in manifest.allFiles) {
                                    val file = File(installDir, mf.unixPath)
                                    if (file.exists()) {
                                        if (file.delete()) {
                                            deletedFiles++
                                        } else {
                                            failedFiles++
                                            Timber.tag("Amazon").w("Failed to delete: ${file.absolutePath}")
                                        }
                                    }
                                }

                                // Walk directories bottom-up and remove empty ones
                                val dirs = mutableSetOf<File>()
                                for (mf in manifest.allFiles) {
                                    var parent = File(installDir, mf.unixPath).parentFile
                                    while (parent != null && parent != installDir && parent.startsWith(installDir)) {
                                        dirs.add(parent)
                                        parent = parent.parentFile
                                    }
                                }
                                // Sort deepest-first so child dirs are removed before parents
                                for (dir in dirs.sortedByDescending { it.absolutePath.length }) {
                                    if (dir.exists() && dir.isDirectory && (dir.listFiles()?.isEmpty() == true)) {
                                        dir.delete()
                                    }
                                }

                                // Remove the install dir itself if it's now empty
                                if (installDir.exists() && installDir.isDirectory &&
                                    (installDir.listFiles()?.isEmpty() == true)
                                ) {
                                    installDir.delete()
                                }

                                Timber.tag("Amazon").i(
                                    "Manifest-based uninstall complete: $deletedFiles deleted, $failedFiles failed"
                                )
                            } catch (e: Exception) {
                                Timber.tag("Amazon").w(e, "Manifest parse failed — falling back to recursive delete")
                                installDir.deleteRecursively()
                            }
                        } else {
                            // ── Fallback: recursive delete ───────────────────────
                            Timber.tag("Amazon").i("No cached manifest — recursive delete: $path")
                            installDir.deleteRecursively()
                        }

                        MarkerUtils.removeMarker(path, Marker.DOWNLOAD_COMPLETE_MARKER)
                        MarkerUtils.removeMarker(path, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    }

                    instance.amazonManager.markUninstalled(productId)

                    // Update install info cache
                    instance.installInfoCache[productId] = CachedInstallInfo(
                        isInstalled = false,
                        installPath = "",
                    )

                    // Delete cached manifest
                    try {
                        val manifestFile = File(context.filesDir, "manifests/amazon/$productId.proto")
                        if (manifestFile.exists()) {
                            manifestFile.delete()
                            Timber.tag("Amazon").d("Deleted cached manifest for $productId")
                        }
                    } catch (e: Exception) {
                        Timber.tag("Amazon").w(e, "Failed to delete cached manifest (non-fatal)")
                    }

                    withContext(Dispatchers.Main) {
                        ContainerUtils.deleteContainer(context, "AMAZON_$productId")
                    }

                    PluviaApp.events.emitJava(
                        AndroidEvent.LibraryInstallStatusChanged(productId.hashCode())
                    )

                    Timber.tag("Amazon").i("Game uninstalled: $productId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.tag("Amazon").e(e, "Failed to uninstall $productId")
                    Result.failure(e)
                }
            }
        }

        // ── Game verification ─────────────────────────────────────────────────

        /**
         * Result of verifying installed game files against the cached manifest.
         */
        data class VerificationResult(
            val totalFiles: Int,
            val verifiedOk: Int,
            val missingFiles: Int,
            val sizeMismatch: Int,
            val hashMismatch: Int,
            val failedFiles: List<String>,
        ) {
            val isValid: Boolean get() = failedFiles.isEmpty()
        }

        /**
         * Verify installed files for [productId] against the cached manifest.
         *
         * Checks:
         *  1. File existence
         *  2. File size matches manifest
         *  3. SHA-256 hash matches manifest (algorithm 0)
         *
         * Mirrors Nile's `nile verify {id}` command.
         *
         * @return [VerificationResult] with details, or a failure if manifest is missing.
         */
        suspend fun verifyGame(context: Context, productId: String): Result<VerificationResult> {
            val instance = getInstance()
                ?: return Result.failure(Exception("Amazon service is not running"))

            return withContext(Dispatchers.IO) {
                try {
                    val game = instance.amazonManager.getGameById(productId)
                        ?: return@withContext Result.failure(Exception("Game not found: $productId"))

                    if (!game.isInstalled || game.installPath.isEmpty()) {
                        return@withContext Result.failure(Exception("Game is not installed"))
                    }

                    val installDir = File(game.installPath)
                    if (!installDir.exists()) {
                        return@withContext Result.failure(Exception("Install directory not found: ${game.installPath}"))
                    }

                    val manifestFile = File(context.filesDir, "manifests/amazon/$productId.proto")
                    if (!manifestFile.exists()) {
                        return@withContext Result.failure(Exception("No cached manifest — reinstall to enable verification"))
                    }

                    val manifest = AmazonManifest.parse(manifestFile.readBytes())
                    val files = manifest.allFiles

                    Timber.tag("Amazon").i("Verifying ${files.size} files for $productId at ${game.installPath}")

                    var verifiedOk = 0
                    var missingFiles = 0
                    var sizeMismatch = 0
                    var hashMismatch = 0
                    val failedFiles = mutableListOf<String>()

                    for (mf in files) {
                        val file = File(installDir, mf.unixPath)

                        if (!file.exists()) {
                            missingFiles++
                            failedFiles.add(mf.unixPath)
                            Timber.tag("Amazon").d("Verify MISSING: ${mf.unixPath}")
                            continue
                        }

                        if (file.length() != mf.size) {
                            sizeMismatch++
                            failedFiles.add(mf.unixPath)
                            Timber.tag("Amazon").d(
                                "Verify SIZE MISMATCH: ${mf.unixPath} (expected=${mf.size}, actual=${file.length()})"
                            )
                            continue
                        }

                        // SHA-256 check (algorithm 0) — skip if hash not available
                        if (mf.hashAlgorithm == 0 && mf.hashBytes.isNotEmpty()) {
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            file.inputStream().buffered().use { input ->
                                val buf = ByteArray(8192)
                                var read: Int
                                while (input.read(buf).also { read = it } != -1) {
                                    digest.update(buf, 0, read)
                                }
                            }
                            val computed = digest.digest()
                            if (!computed.contentEquals(mf.hashBytes)) {
                                hashMismatch++
                                failedFiles.add(mf.unixPath)
                                Timber.tag("Amazon").d("Verify HASH MISMATCH: ${mf.unixPath}")
                                continue
                            }
                        }

                        verifiedOk++
                    }

                    val result = VerificationResult(
                        totalFiles = files.size,
                        verifiedOk = verifiedOk,
                        missingFiles = missingFiles,
                        sizeMismatch = sizeMismatch,
                        hashMismatch = hashMismatch,
                        failedFiles = failedFiles,
                    )

                    if (result.isValid) {
                        Timber.tag("Amazon").i("Verification PASSED: ${result.verifiedOk}/${result.totalFiles} files OK")
                    } else {
                        Timber.tag("Amazon").w(
                            "Verification FAILED: ${result.verifiedOk}/${result.totalFiles} OK, " +
                                "${result.missingFiles} missing, ${result.sizeMismatch} size mismatch, " +
                                "${result.hashMismatch} hash mismatch"
                        )
                    }

                    Result.success(result)
                } catch (e: Exception) {
                    Timber.tag("Amazon").e(e, "Verification failed for $productId")
                    Result.failure(e)
                }
            }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = {
        // Record playtime synchronously before stopping — must complete before
        // onDestroy() cancels serviceScope, so we use runBlocking on IO.
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            recordSessionPlaytime()
        }
        stop()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)
        Timber.i("[Amazon] Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.createForegroundNotification("Amazon Games Running")
        startForeground(1, notification)

        val shouldSync = when (intent?.action) {
            ACTION_MANUAL_SYNC -> {
                Timber.i("[Amazon] Manual sync requested — bypassing throttle")
                true
            }
            ACTION_SYNC_LIBRARY -> {
                Timber.i("[Amazon] Automatic sync requested")
                true
            }
            null -> {
                // Service restarted by Android (START_STICKY)
                val timeSinceLastSync = System.currentTimeMillis() - lastSyncTimestamp
                val shouldResync = !hasPerformedInitialSync || timeSinceLastSync >= SYNC_THROTTLE_MILLIS
                if (shouldResync) {
                    Timber.i("[Amazon] Service restarted by Android — performing sync (initial=$hasPerformedInitialSync, elapsed=${timeSinceLastSync}ms)")
                } else {
                    Timber.d("[Amazon] Service restarted by Android — skipping sync (throttled)")
                }
                shouldResync
            }
            else -> {
                Timber.d("[Amazon] Service started without sync action")
                false
            }
        }

        if (shouldSync) {
            backgroundSyncJob = serviceScope.launch { syncLibrary() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)
        backgroundSyncJob?.cancel()
        setSyncInProgress(false)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel()
        instance = null
        super.onDestroy()
        Timber.i("[Amazon] Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Instance helpers (for callers that hold a direct reference) ───────────

    /** Instance-method accessor for callers using [getInstance]?. */
    fun getInstalledGamePath(gameId: String): String? = getInstallPath(gameId)

    private suspend fun syncLibrary() {
        setSyncInProgress(true)
        try {
            amazonManager.refreshLibrary()
            refreshInstallInfoCache()
            lastSyncTimestamp = System.currentTimeMillis()
            hasPerformedInitialSync = true
            Timber.i("[Amazon] Sync complete — next auto-sync in 15 minutes")
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Library sync failed")
        } finally {
            setSyncInProgress(false)
        }
    }

    /**
     * Populate the in-memory install info cache from the DB.
     * Called after library sync so that [isGameInstalled] and [getInstallPath]
     * can answer without blocking.
     */
    private suspend fun refreshInstallInfoCache() {
        val games = withContext(Dispatchers.IO) {
            amazonManager.getAllGames()
        }
        for (game in games) {
            installInfoCache[game.id] = CachedInstallInfo(
                isInstalled = game.isInstalled,
                installPath = game.installPath,
            )
        }
        Timber.d("[Amazon] Install info cache refreshed: ${games.size} entries")
    }
}
