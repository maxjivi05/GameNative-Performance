package app.gamenative.ui.screen.library.appscreen

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.data.AmazonGame
import app.gamenative.data.LibraryItem
import app.gamenative.service.amazon.AmazonConstants
import app.gamenative.service.amazon.AmazonService
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.utils.ContainerUtils
import com.winlator.container.ContainerData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Amazon-specific implementation of [BaseAppScreen].
 *
 * Amazon games are currently library-only (no download/install through GameNative).
 * This screen displays game metadata read from the Room database ([AmazonGame]).
 */
class AmazonAppScreen : BaseAppScreen() {

    companion object {
        private const val TAG = "AmazonAppScreen"

        // Shared state for uninstall dialog — list of appIds that should show the dialog
        private val uninstallDialogAppIds = mutableStateListOf<String>()

        fun showUninstallDialog(appId: String) {
            Timber.tag(TAG).d("showUninstallDialog: appId=$appId")
            if (!uninstallDialogAppIds.contains(appId)) {
                uninstallDialogAppIds.add(appId)
            }
        }

        fun hideUninstallDialog(appId: String) {
            Timber.tag(TAG).d("hideUninstallDialog: appId=$appId")
            uninstallDialogAppIds.remove(appId)
        }

        fun shouldShowUninstallDialog(appId: String): Boolean =
            uninstallDialogAppIds.contains(appId)

        /** Extract the raw Amazon product ID from a library item's appId. */
        fun productIdOf(libraryItem: LibraryItem): String =
            libraryItem.appId.removePrefix("AMAZON_")

        /** Format a byte count as a user-friendly size string. */
        private fun formatBytes(bytes: Long): String {
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
                bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
                bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
                else -> "$bytes B"
            }
        }

        /**
         * Parse an Amazon release-date string into a Unix timestamp in seconds.
         *
         * Amazon may return dates in several formats:
         *  - Full ISO-8601:  "2022-08-18T17:50:00Z"
         *  - Short ISO-8601 date: "2022-08-18"
         *  - Year only:      "2022"
         */
        fun parseReleaseDate(dateStr: String): Long {
            if (dateStr.isBlank()) return 0L
            return try {
                when {
                    // Full ISO-8601 with timezone (e.g. "2022-08-18T17:50:00Z" or +offset)
                    dateStr.contains('T') -> {
                        ZonedDateTime.parse(dateStr).toInstant().epochSecond
                    }
                    // yyyy-MM-dd
                    dateStr.length == 10 && dateStr[4] == '-' -> {
                        LocalDate.parse(dateStr).atStartOfDay().toInstant(
                            java.time.ZoneOffset.UTC
                        ).epochSecond
                    }
                    // Four-digit year only
                    dateStr.length == 4 -> {
                        LocalDate.of(dateStr.toInt(), 1, 1).atStartOfDay().toInstant(
                            java.time.ZoneOffset.UTC
                        ).epochSecond
                    }
                    else -> 0L
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to parse Amazon release date: $dateStr")
                0L
            }
        }
    }

    // ── BaseAppScreen contract ─────────────────────────────────────────────

    @Composable
    override fun getGameDisplayInfo(
        context: Context,
        libraryItem: LibraryItem,
    ): GameDisplayInfo {
        val productId = productIdOf(libraryItem)
        Timber.tag(TAG).d(
            "getGameDisplayInfo: productId=$productId name=${libraryItem.name} " +
                "gameId=${productId.hashCode()} libraryItem.gameId=${libraryItem.gameId}"
        )

        var game by remember(productId) { mutableStateOf<AmazonGame?>(null) }

        // Refresh key — incremented when install status changes so we re-fetch from DB.
        // This ensures size/installPath/etc. are up-to-date after download completes.
        var refreshKey by remember(productId) { mutableStateOf(0) }

        androidx.compose.runtime.DisposableEffect(productId) {
            val listener: (app.gamenative.events.AndroidEvent.LibraryInstallStatusChanged) -> Unit = { event ->
                if (event.appId == productId.hashCode()) {
                    Timber.tag(TAG).d("[REFRESH] Install status changed for $productId — refreshing game info")
                    refreshKey++
                }
            }
            app.gamenative.PluviaApp.events.on<app.gamenative.events.AndroidEvent.LibraryInstallStatusChanged, Unit>(listener)
            onDispose {
                app.gamenative.PluviaApp.events.off<app.gamenative.events.AndroidEvent.LibraryInstallStatusChanged, Unit>(listener)
            }
        }

        LaunchedEffect(productId, refreshKey) {
            game = AmazonService.getAmazonGameOf(productId)
            Timber.tag(TAG).d(
                "Loaded game: title=${game?.title}, developer=${game?.developer}, " +
                    "releaseDate=${game?.releaseDate}, artUrl=${game?.artUrl?.take(60)}, " +
                    "heroUrl=${game?.heroUrl?.take(60)}, downloadSize=${game?.downloadSize}, " +
                    "installSize=${game?.installSize}, isInstalled=${game?.isInstalled}"
            )
            // Proactively fetch size from manifest if not yet cached
            val g = game
            if (g != null && (g.downloadSize <= 0L) && !g.isInstalled) {
                val size = AmazonService.fetchDownloadSize(productId)
                if (size != null && size > 0L) {
                    // Re-read from DB to pick up the cached size
                    game = AmazonService.getAmazonGameOf(productId)
                }
            }
        }

        val g = game

        // Artwork — use heroUrl for the backdrop, artUrl/iconHash for the icon
        val heroImageUrl = g?.heroUrl?.takeIf { it.isNotEmpty() }
            ?: g?.artUrl?.takeIf { it.isNotEmpty() }   // fall back to art if no hero
            ?: libraryItem.iconHash.takeIf { it.isNotEmpty() }

        val iconUrl = g?.artUrl?.takeIf { it.isNotEmpty() }
            ?: libraryItem.iconHash.takeIf { it.isNotEmpty() }

        // Metadata
        val developer = g?.developer?.takeIf { it.isNotEmpty() }
            ?: g?.publisher?.takeIf { it.isNotEmpty() }
            ?: ""

        val releaseDateTs = g?.releaseDate?.let { parseReleaseDate(it) } ?: 0L

        val sizeFromStore = if ((g?.downloadSize ?: 0L) > 0L) {
            formatBytes(g!!.downloadSize)
        } else {
            null
        }

        // Playtime info
        val lastPlayedFormatted = remember(g?.lastPlayed) {
            val lp = g?.lastPlayed ?: 0L
            if (lp > 0L) {
                java.text.SimpleDateFormat("MMM d, yyyy — h:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date(lp))
            } else {
                null // never played
            }
        }

        val playtimeFormatted = remember(g?.playTimeMinutes) {
            val totalSeconds = g?.playTimeMinutes ?: 0L  // column name is legacy, stores seconds
            if (totalSeconds > 0L) {
                val totalMinutes = totalSeconds / 60.0
                when {
                    totalMinutes < 1.0 -> "< 1 min"
                    totalMinutes < 60.0 -> "${totalMinutes.toInt()} min"
                    else -> {
                        val hours = totalMinutes / 60.0
                        if (hours % 1.0 == 0.0) {
                            "${hours.toInt()} hrs"
                        } else {
                            String.format(java.util.Locale.getDefault(), "%.1f hrs", hours)
                        }
                    }
                }
            } else {
                null
            }
        }

        return GameDisplayInfo(
            name = g?.title ?: libraryItem.name,
            iconUrl = iconUrl,
            heroImageUrl = heroImageUrl,
            gameId = productId.hashCode(), // Stable Int from Amazon UUID — matches AmazonService event IDs
            appId = libraryItem.appId,
            releaseDate = releaseDateTs,
            developer = developer,
            installLocation = if (g?.isInstalled == true && g.installPath.isNotEmpty()) {
                g.installPath
            } else {
                null
            },
            sizeOnDisk = if ((g?.installSize ?: 0L) > 0L) formatBytes(g!!.installSize) else null,
            sizeFromStore = sizeFromStore,
            lastPlayedText = lastPlayedFormatted,
            playtimeText = playtimeFormatted,
        )
    }

override fun isInstalled(context: Context, libraryItem: LibraryItem): Boolean =
        AmazonService.isGameInstalled(productIdOf(libraryItem))

    override fun isValidToDownload(context: Context, libraryItem: LibraryItem): Boolean =
        !isInstalled(context, libraryItem) &&
            AmazonService.getDownloadInfo(productIdOf(libraryItem)) == null

    override fun isDownloading(context: Context, libraryItem: LibraryItem): Boolean =
        AmazonService.getDownloadInfo(productIdOf(libraryItem)) != null

    override fun getDownloadProgress(context: Context, libraryItem: LibraryItem): Float =
        AmazonService.getDownloadInfo(productIdOf(libraryItem))?.getProgress() ?: 0f

    override fun hasPartialDownload(context: Context, libraryItem: LibraryItem): Boolean = false

    override fun onDownloadInstallClick(
        context: Context,
        libraryItem: LibraryItem,
        onClickPlay: (Boolean) -> Unit,
    ) {
        val productId = productIdOf(libraryItem)
        val installed = isInstalled(context, libraryItem)
        val downloading = isDownloading(context, libraryItem)

        Timber.tag(TAG).d("onDownloadInstallClick: productId=$productId, installed=$installed, downloading=$downloading")

        if (downloading) {
            Timber.tag(TAG).i("Download already in progress for $productId — ignoring click")
            return
        }

        if (installed) {
            Timber.tag(TAG).i("Game already installed, launching: $productId")
            onClickPlay(false)
            return
        }

        // Show install confirmation dialog with size info
        Timber.tag(TAG).i("Showing install confirmation dialog for: ${libraryItem.appId}")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val game = AmazonService.getAmazonGameOf(productId)

                // Try to get download size: use cached value first, otherwise fetch from manifest
                var sizeBytes = game?.downloadSize ?: 0L
                if (sizeBytes <= 0L) {
                    Timber.tag(TAG).d("Download size not cached, fetching manifest for $productId…")
                    sizeBytes = AmazonService.fetchDownloadSize(productId) ?: 0L
                }

                val downloadSize = if (sizeBytes > 0L) {
                    app.gamenative.utils.StorageUtils.formatBinarySize(sizeBytes)
                } else {
                    "Unknown"
                }
                val installDir = AmazonConstants.getGameInstallPath(context, game?.title ?: libraryItem.name)
                val availableSpace = app.gamenative.utils.StorageUtils.formatBinarySize(
                    app.gamenative.utils.StorageUtils.getAvailableSpace(installDir)
                )

                val message = context.getString(
                    R.string.amazon_install_confirmation_message,
                    downloadSize,
                    availableSpace
                )
                val state = app.gamenative.ui.component.dialog.state.MessageDialogState(
                    visible = true,
                    type = app.gamenative.ui.enums.DialogType.INSTALL_APP,
                    title = context.getString(R.string.amazon_install_game_title),
                    message = message,
                    confirmBtnText = context.getString(R.string.download),
                    dismissBtnText = context.getString(R.string.cancel)
                )
                BaseAppScreen.showInstallDialog(libraryItem.appId, state)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to show install dialog for: ${libraryItem.appId}")
            }
        }
    }

    private fun performDownload(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        CoroutineScope(Dispatchers.IO).launch {
            val game = AmazonService.getAmazonGameOf(productId) ?: run {
                Timber.tag(TAG).w("performDownload: game not found for $productId")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Game not found — try syncing library", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val installPath = AmazonConstants.getGameInstallPath(context, game.title)
            Timber.tag(TAG).i("Downloading '${game.title}' → $installPath")

            val result = AmazonService.downloadGame(context, productId, installPath)
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                Timber.tag(TAG).e("downloadGame failed: $msg")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to start download: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onPauseResumeClick(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        if (AmazonService.getDownloadInfo(productId) != null) {
            Timber.tag(TAG).i("Cancelling download for $productId")
            AmazonService.cancelDownload(productId)
        } else {
            // Resume — re-start the download
            onDownloadInstallClick(context, libraryItem) {}
        }
    }

    override fun onDeleteDownloadClick(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        Timber.tag(TAG).i("onDeleteDownloadClick: productId=$productId")

        if (isDownloading(context, libraryItem)) {
            // Cancel active download
            Timber.tag(TAG).i("Cancelling active download for $productId")
            AmazonService.cancelDownload(productId)
            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
        } else if (isInstalled(context, libraryItem)) {
            // Show uninstall confirmation dialog (debounces multi-tap)
            Timber.tag(TAG).i("Showing uninstall dialog for: ${libraryItem.appId}")
            showUninstallDialog(libraryItem.appId)
        }
    }

    private fun performUninstall(context: Context, libraryItem: LibraryItem) {
        val productId = productIdOf(libraryItem)
        Timber.tag(TAG).i("performUninstall: deleting game $productId")
        CoroutineScope(Dispatchers.IO).launch {
            val result = AmazonService.deleteGame(context, productId)
            if (result.isSuccess) {
                Timber.tag(TAG).i("Uninstall succeeded for $productId")
            } else {
                Timber.tag(TAG).e("Uninstall failed for $productId: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    override fun onUpdateClick(context: Context, libraryItem: LibraryItem) {
        Timber.tag(TAG).i("onUpdateClick: re-downloading ${productIdOf(libraryItem)}")
        performDownload(context, libraryItem)
    }

    override suspend fun isUpdatePendingSuspend(context: Context, libraryItem: LibraryItem): Boolean {
        val productId = productIdOf(libraryItem)
        return AmazonService.isUpdatePending(productId)
    }

    override fun getInstallPath(context: Context, libraryItem: LibraryItem): String? {
        return AmazonService.getInstallPath(productIdOf(libraryItem))
    }

    override fun observeGameState(
        context: Context,
        libraryItem: LibraryItem,
        onStateChanged: () -> Unit,
        onProgressChanged: (Float) -> Unit,
        onHasPartialDownloadChanged: ((Boolean) -> Unit)?,
    ): (() -> Unit)? {
        val gameId = libraryItem.gameId
        val productId = productIdOf(libraryItem)
        Timber.tag(TAG).d("[OBSERVE] Setting up observeGameState for productId=$productId, gameId=$gameId")

        val disposables = mutableListOf<() -> Unit>()
        var currentProgressListener: ((Float) -> Unit)? = null

        // If download is already in progress, attach listener immediately
        val existingDownloadInfo = AmazonService.getDownloadInfo(productId)
        if (existingDownloadInfo != null && (existingDownloadInfo.getProgress() ?: 0f) < 1f) {
            Timber.tag(TAG).d("[OBSERVE] Download already in progress for $productId, attaching progress listener")
            val progressListener: (Float) -> Unit = { progress ->
                onProgressChanged(progress)
            }
            existingDownloadInfo.addProgressListener(progressListener)
            currentProgressListener = progressListener
            disposables += {
                currentProgressListener?.let { listener ->
                    existingDownloadInfo.removeProgressListener(listener)
                    currentProgressListener = null
                }
            }
            existingDownloadInfo.getProgress()?.let { onProgressChanged(it) }
        }

        // Listen for download status changes (events use productId.hashCode() as appId)
        val downloadStatusListener: (app.gamenative.events.AndroidEvent.DownloadStatusChanged) -> Unit = { event ->
            if (event.appId == gameId) {
                Timber.tag(TAG).d("[OBSERVE] DownloadStatusChanged for $productId, isDownloading=${event.isDownloading}")
                if (event.isDownloading) {
                    val downloadInfo = AmazonService.getDownloadInfo(productId)
                    if (downloadInfo != null) {
                        currentProgressListener?.let { downloadInfo.removeProgressListener(it) }
                        val progressListener: (Float) -> Unit = { progress ->
                            Timber.tag(TAG).v("[OBSERVE] Progress for $productId: $progress")
                            onProgressChanged(progress)
                        }
                        downloadInfo.addProgressListener(progressListener)
                        currentProgressListener = progressListener
                        disposables += {
                            currentProgressListener?.let { listener ->
                                downloadInfo.removeProgressListener(listener)
                                currentProgressListener = null
                            }
                        }
                    }
                } else {
                    currentProgressListener?.let { listener ->
                        AmazonService.getDownloadInfo(productId)?.removeProgressListener(listener)
                        currentProgressListener = null
                    }
                    onHasPartialDownloadChanged?.invoke(false)
                }
                onStateChanged()
            }
        }
        app.gamenative.PluviaApp.events.on<app.gamenative.events.AndroidEvent.DownloadStatusChanged, Unit>(downloadStatusListener)
        disposables += { app.gamenative.PluviaApp.events.off<app.gamenative.events.AndroidEvent.DownloadStatusChanged, Unit>(downloadStatusListener) }

        // Listen for install status changes
        val installListener: (app.gamenative.events.AndroidEvent.LibraryInstallStatusChanged) -> Unit = { event ->
            if (event.appId == gameId) {
                Timber.tag(TAG).d("[OBSERVE] Install status changed for $productId")
                onStateChanged()
            }
        }
        app.gamenative.PluviaApp.events.on<app.gamenative.events.AndroidEvent.LibraryInstallStatusChanged, Unit>(installListener)
        disposables += { app.gamenative.PluviaApp.events.off<app.gamenative.events.AndroidEvent.LibraryInstallStatusChanged, Unit>(installListener) }

        return { disposables.forEach { it() } }
    }

    override fun getExportFileExtension(): String = ".amazon"

    @Composable
    override fun getSourceSpecificMenuOptions(
        context: Context,
        libraryItem: LibraryItem,
        onEditContainer: () -> Unit,
        onBack: () -> Unit,
        onClickPlay: (Boolean) -> Unit,
        isInstalled: Boolean,
    ): List<AppMenuOption> {
        val options = mutableListOf<AppMenuOption>()
        if (isInstalled) {
            options.add(getVerifyFilesOption(context, libraryItem))
        }
        return options
    }

    @Composable
    private fun getVerifyFilesOption(
        context: Context,
        libraryItem: LibraryItem,
    ): AppMenuOption {
        var showDialog by remember { mutableStateOf(false) }
        var verifyResult by remember { mutableStateOf<String?>(null) }
        var isVerifying by remember { mutableStateOf(false) }

        // Confirmation dialog before verifying
        if (showDialog && !isVerifying && verifyResult == null) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.amazon_verify_files_title)) },
                text = { Text(stringResource(R.string.amazon_verify_files_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isVerifying = true
                            val productId = productIdOf(libraryItem)
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = AmazonService.verifyGame(context, productId)
                                withContext(Dispatchers.Main) {
                                    isVerifying = false
                                    verifyResult = if (result.isSuccess) {
                                        val v = result.getOrNull()!!
                                        if (v.isValid) {
                                            context.getString(
                                                R.string.amazon_verify_success,
                                                v.verifiedOk,
                                                v.totalFiles,
                                            )
                                        } else {
                                            context.getString(
                                                R.string.amazon_verify_failed_detail,
                                                v.verifiedOk,
                                                v.totalFiles,
                                                v.missingFiles,
                                                v.sizeMismatch,
                                                v.hashMismatch,
                                            )
                                        }
                                    } else {
                                        context.getString(
                                            R.string.amazon_verify_error,
                                            result.exceptionOrNull()?.message ?: "Unknown error",
                                        )
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.amazon_verify_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Progress dialog while verifying
        if (isVerifying) {
            AlertDialog(
                onDismissRequest = { /* non-dismissable while verifying */ },
                title = { Text(stringResource(R.string.amazon_verify_files_title)) },
                text = { Text(stringResource(R.string.amazon_verify_in_progress)) },
                confirmButton = {},
            )
        }

        // Result dialog
        if (verifyResult != null) {
            AlertDialog(
                onDismissRequest = {
                    verifyResult = null
                    showDialog = false
                },
                title = { Text(stringResource(R.string.amazon_verify_files_title)) },
                text = { Text(verifyResult!!) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            verifyResult = null
                            showDialog = false
                        },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
            )
        }

        return AppMenuOption(
            optionType = AppOptionMenuType.VerifyFiles,
            onClick = { showDialog = true },
        )
    }

    @Composable
    override fun AdditionalDialogs(
        libraryItem: LibraryItem,
        onDismiss: () -> Unit,
        onEditContainer: () -> Unit,
        onBack: () -> Unit,
    ) {
        val context = LocalContext.current
        val appId = libraryItem.appId

        // ── Install confirmation dialog (from BaseAppScreen shared state) ──
        var installDialogState by remember(appId) {
            mutableStateOf(BaseAppScreen.getInstallDialogState(appId) ?: app.gamenative.ui.component.dialog.state.MessageDialogState(false))
        }
        LaunchedEffect(appId) {
            snapshotFlow { BaseAppScreen.getInstallDialogState(appId) }
                .collect { state ->
                    installDialogState = state ?: app.gamenative.ui.component.dialog.state.MessageDialogState(false)
                }
        }

        if (installDialogState.visible) {
            val onDismissRequest: (() -> Unit) = {
                BaseAppScreen.hideInstallDialog(appId)
            }
            val onConfirmClick: (() -> Unit)? = when (installDialogState.type) {
                app.gamenative.ui.enums.DialogType.INSTALL_APP -> {
                    {
                        BaseAppScreen.hideInstallDialog(appId)
                        performDownload(context, libraryItem)
                    }
                }
                else -> null
            }
            app.gamenative.ui.component.dialog.MessageDialog(
                visible = installDialogState.visible,
                onDismissRequest = onDismissRequest,
                onConfirmClick = onConfirmClick,
                onDismissClick = onDismissRequest,
                confirmBtnText = installDialogState.confirmBtnText,
                dismissBtnText = installDialogState.dismissBtnText,
                title = installDialogState.title,
                message = installDialogState.message,
            )
        }

        // ── Uninstall confirmation dialog ──
        var showUninstallDialog by remember { mutableStateOf(shouldShowUninstallDialog(libraryItem.appId)) }
        LaunchedEffect(libraryItem.appId) {
            snapshotFlow { shouldShowUninstallDialog(libraryItem.appId) }
                .collect { shouldShow ->
                    showUninstallDialog = shouldShow
                }
        }

        if (showUninstallDialog) {
            AlertDialog(
                onDismissRequest = {
                    hideUninstallDialog(libraryItem.appId)
                },
                title = { Text(stringResource(R.string.amazon_uninstall_game_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.amazon_uninstall_confirmation_message,
                            libraryItem.name,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            hideUninstallDialog(libraryItem.appId)
                            performUninstall(context, libraryItem)
                        },
                    ) {
                        Text(stringResource(R.string.uninstall))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            hideUninstallDialog(libraryItem.appId)
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    override fun loadContainerData(context: Context, libraryItem: LibraryItem): ContainerData {
        val container = ContainerUtils.getOrCreateContainer(context, libraryItem.appId)
        return ContainerUtils.toContainerData(container)
    }

    override fun saveContainerConfig(
        context: Context,
        libraryItem: LibraryItem,
        config: ContainerData,
    ) {
        ContainerUtils.applyToContainer(context, libraryItem.appId, config)
    }

    override fun supportsContainerConfig(): Boolean = true
}
