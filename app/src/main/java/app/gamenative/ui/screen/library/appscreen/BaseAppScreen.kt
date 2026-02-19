package app.gamenative.ui.screen.library.appscreen

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.component.dialog.ContainerConfigDialog
import app.gamenative.ui.component.dialog.ControllerOptionsDialog
import app.gamenative.ui.component.dialog.SavesOptionsDialog
import app.gamenative.ui.component.dialog.PhysicalControllerConfigSection
import app.gamenative.ui.component.dialog.ProfileSelectionDialog
import com.winlator.contentdialog.NavigationDialog
import com.winlator.inputcontrols.InputControlsManager
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.GameMetadataManager
import app.gamenative.utils.SteamGridDB
import app.gamenative.utils.createPinnedShortcut
import com.winlator.container.ContainerData
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.gamenative.utils.SaveManager

/**
 * Abstract base class for AppScreen implementations.
 * This defines the contract that all game source-specific screens must implement.
 */
abstract class BaseAppScreen {
    // Shared state for install dialog - map of appId (String) to MessageDialogState
    companion object {
        private val installDialogStates = mutableStateMapOf<String, app.gamenative.ui.component.dialog.state.MessageDialogState>()

        fun showInstallDialog(appId: String, state: app.gamenative.ui.component.dialog.state.MessageDialogState) {
            installDialogStates[appId] = state
        }

        fun hideInstallDialog(appId: String) {
            installDialogStates.remove(appId)
        }

        fun getInstallDialogState(appId: String): app.gamenative.ui.component.dialog.state.MessageDialogState? {
            return installDialogStates[appId]
        }
    }

    /**
     * Get the game display information for rendering the UI.
     * This is called to get all the data needed for the common UI layout.
     */
    @Composable
    abstract fun getGameDisplayInfo(
        context: Context,
        libraryItem: LibraryItem,
    ): GameDisplayInfo

    /**
     * Check if the game is installed
     */
    abstract fun isInstalled(context: Context, libraryItem: LibraryItem): Boolean

    /**
     * Check if the game can be downloaded/installed
     */
    abstract fun isValidToDownload(context: Context, libraryItem: LibraryItem): Boolean

    /**
     * Check if the game is currently downloading
     */
    abstract fun isDownloading(context: Context, libraryItem: LibraryItem): Boolean

    /**
     * Get the current download progress (0.0 to 1.0)
     */
    abstract fun getDownloadProgress(context: Context, libraryItem: LibraryItem): Float

    /**
     * Check if there's a partial/incomplete download that can be resumed
     * Default implementation checks if progress is > 0 and < 1, but can be overridden
     * for more accurate detection (e.g., checking for marker files)
     */
    open fun hasPartialDownload(context: Context, libraryItem: LibraryItem): Boolean {
        val progress = getDownloadProgress(context, libraryItem)
        return progress > 0f && progress < 1f
    }

    /**
     * Check if an update is pending (synchronous version, returns false by default)
     * Override isUpdatePendingSuspend for async checks
     */
    open fun isUpdatePending(context: Context, libraryItem: LibraryItem): Boolean {
        return false
    }

    /**
     * Check if an update is pending (suspend version for async checks)
     * Override this if you need to call suspend functions
     */
    open suspend fun isUpdatePendingSuspend(context: Context, libraryItem: LibraryItem): Boolean {
        return isUpdatePending(context, libraryItem)
    }

    /**
     * Handle the play/install button click
     */
    abstract fun onDownloadInstallClick(context: Context, libraryItem: LibraryItem, onClickPlay: (Boolean) -> Unit)

    /**
     * Handle pause/resume download click
     */
    abstract fun onPauseResumeClick(context: Context, libraryItem: LibraryItem)

    /**
     * Handle delete download click
     */
    abstract fun onDeleteDownloadClick(context: Context, libraryItem: LibraryItem)

    /**
     * Handle custom path selection click
     */
    open fun onCustomPathClick(context: Context, libraryItem: LibraryItem) {
        // Default: no-op, can be overridden by sources that support custom paths
    }

    /**
     * Handle update click
     */
    abstract fun onUpdateClick(context: Context, libraryItem: LibraryItem)

    /**
     * Get the game name for shortcuts and dialogs
     */
    @Composable
    protected fun getGameName(context: Context, libraryItem: LibraryItem): String {
        // Use display info to get the name
        return getGameDisplayInfo(context, libraryItem).name
    }

    protected fun getGameSource(libraryItem: LibraryItem): GameSource {
        return libraryItem.gameSource
    }

    /**
     * Get the game ID for shortcuts depending on app type
     */
    protected fun getGameId(libraryItem: LibraryItem): Int {
        return libraryItem.gameId
    }

    /**
     * Get the icon URL for shortcuts (can be null)
     */
    @Composable
    protected fun getIconUrl(context: Context, libraryItem: LibraryItem): String? {
        return getGameDisplayInfo(context, libraryItem).iconUrl
    }

    /**
     * Get the file extension for exported frontend files (e.g., ".steam", ".game")
     * Must be overridden by subclasses to provide source-specific extension
     */
    abstract fun getExportFileExtension(): String

    /**
     * Get the game install path (non-composable version).
     * Returns the path to the game's installation directory, or null if not installed.
     * Must be implemented by subclasses to provide source-specific path resolution.
     */
    protected abstract fun getInstallPath(context: Context, libraryItem: LibraryItem): String?

    /**
     * Get Edit Container menu option.
     */
    @Composable
    protected open fun getEditContainerOption(
        context: Context,
        libraryItem: LibraryItem,
        onEditContainer: () -> Unit,
    ): AppMenuOption {
        return AppMenuOption(
            optionType = AppOptionMenuType.EditContainer,
            onClick = onEditContainer,
        )
    }

    @Composable
    protected open fun getRunContainerOption(
        context: Context,
        libraryItem: LibraryItem,
        onClickPlay: (Boolean) -> Unit,
    ): AppMenuOption? {
        return AppMenuOption(
            AppOptionMenuType.RunContainer,
            onClick = {
                onRunContainerClick(context, libraryItem, onClickPlay)
            },
        )
    }

    @Composable
    protected open fun getTestGraphicsOption(
        context: Context,
        libraryItem: LibraryItem,
        onTestGraphics: () -> Unit,
    ): AppMenuOption? {
        return AppMenuOption(
            AppOptionMenuType.TestGraphics,
            onClick = {
                onTestGraphicsClick(context, libraryItem, onTestGraphics)
            },
        )
    }

    @Composable
    protected abstract fun getResetContainerOption(
        context: Context,
        libraryItem: LibraryItem,
    ): AppMenuOption?

    @Composable
    protected open fun getExportContainerOption(
        context: Context,
        libraryItem: LibraryItem,
        exportFrontendLauncher: ActivityResultLauncher<String>,
    ): AppMenuOption? {
        val gameId = getGameId(libraryItem)
        val gameName = getGameName(context, libraryItem)
        val extension = getExportFileExtension()
        return AppMenuOption(
            optionType = AppOptionMenuType.ExportFrontend,
            onClick = {
                val suggested = "${gameName}$extension"
                exportFrontendLauncher.launch(suggested)
            },
        )
    }

    /**
     * Get Create Shortcut menu option. Subclasses can override to customize behavior.
     */
    @Composable
    protected open fun getCreateShortcutOption(
        context: Context,
        libraryItem: LibraryItem,
    ): AppMenuOption? {
        val gameSource = getGameSource(libraryItem)
        val gameId = getGameId(libraryItem)
        val gameName = getGameName(context, libraryItem)
        val iconUrl = getIconUrl(context, libraryItem)

        return AppMenuOption(
            optionType = AppOptionMenuType.CreateShortcut,
            onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        createPinnedShortcut(
                            context = context,
                            gameId = gameId,
                            label = gameName,
                            gameSource = gameSource,
                            iconUrl = iconUrl,
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.base_app_shortcut_created),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.base_app_shortcut_failed,
                                    e.message ?: "",
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
        )
    }

    /**
     * Get source-specific menu options. Subclasses can override to add custom options.
     */
    @Composable
    protected open fun getSourceSpecificMenuOptions(
        context: Context,
        libraryItem: LibraryItem,
        onEditContainer: () -> Unit,
        onBack: () -> Unit,
        onClickPlay: (Boolean) -> Unit,
        isInstalled: Boolean,
    ): List<AppMenuOption> {
        return emptyList()
    }

    @Composable
    private fun getSubmitFeedbackOption(context: Context, libraryItem: LibraryItem): AppMenuOption {
        return AppMenuOption(
            optionType = AppOptionMenuType.SubmitFeedback,
            onClick = {
                PluviaApp.events.emit(AndroidEvent.ShowGameFeedback(libraryItem.appId))
            },
        )
    }

    @Composable
    private fun getFetchImagesOption(context: Context, libraryItem: LibraryItem): AppMenuOption {
        return AppMenuOption(
            optionType = AppOptionMenuType.FetchSteamGridDBImages,
            onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val gameName = libraryItem.name
                        val gameFolderPath = getGameFolderPathForImageFetch(context, libraryItem)

                        if (gameFolderPath != null) {
                            val folder = File(gameFolderPath)
                            val appId = libraryItem.gameId
                            GameMetadataManager.update(
                                folder = folder,
                                appId = appId,
                                steamgriddbFetched = false,
                            )

                            SteamGridDB.fetchGameImages(gameName, gameFolderPath)
                            PluviaApp.events.emit(AndroidEvent.CustomGameImagesFetched(libraryItem.appId))
                            onAfterFetchImages(context, libraryItem, gameFolderPath)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.base_app_images_fetched),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.base_app_game_folder_not_found),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.base_app_images_fetch_failed,
                                    e.message ?: "",
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
        )
    }

    @Composable
    private fun getGetSupportOption(context: Context): AppMenuOption {
        return AppMenuOption(
            optionType = AppOptionMenuType.GetSupport,
            onClick = {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    ("https://discord.gg/2hKv4VfZfE").toUri(),
                )
                context.startActivity(browserIntent)
            },
        )
    }

    protected open fun onRunContainerClick(
        context: Context,
        libraryItem: LibraryItem,
        onClickPlay: (Boolean) -> Unit,
    ) {
        onClickPlay(true)
    }

    protected open fun onTestGraphicsClick(
        context: Context,
        libraryItem: LibraryItem,
        onTestGraphics: () -> Unit,
    ) {
        onTestGraphics()
    }

    /**
     * Get the game folder path for image fetching.
     * Override this in subclasses to provide source-specific path resolution.
     * Default implementation uses getInstallPath() if the game is installed.
     */
    protected open fun getGameFolderPathForImageFetch(context: Context, libraryItem: LibraryItem): String? {
        // Check if installed and get path
        if (isInstalled(context, libraryItem)) {
            return getInstallPath(context, libraryItem)
        }
        return null
    }

    /**
     * Hook called after images are fetched. Override in subclasses for post-processing
     * (e.g., icon extraction for Custom Games).
     */
    protected open fun onAfterFetchImages(context: Context, libraryItem: LibraryItem, gameFolderPath: String) {
        // Default: no post-processing
    }

    /**
     * Reset container to default settings while preserving drive mappings.
     * This is common behavior for all game sources.
     */
    protected fun resetContainerToDefaults(context: Context, libraryItem: LibraryItem) {
        val container = ContainerUtils.getOrCreateContainer(context, libraryItem.appId)
        val defaults = ContainerUtils.getDefaultContainerData().copy(drives = container.drives)

        ContainerUtils.applyToContainer(context, libraryItem.appId, defaults)

        Toast.makeText(context, "Container reset to defaults", Toast.LENGTH_SHORT).show()
    }

    /**
     * Common reset confirmation dialog for all game sources.
     */
    @Composable
    protected fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(context.getString(R.string.base_app_reset_container_title)) },
            text = {
                Text(context.getString(R.string.steam_reset_container_message))
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = context.getString(R.string.base_app_reset_container_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(context.getString(R.string.cancel))
                }
            },
        )
    }

    /**
     * Get the options menu items specific to this game source
     */
    @Composable
    fun getOptionsMenu(
        context: Context,
        libraryItem: LibraryItem,
        onEditContainer: () -> Unit,
        onBack: () -> Unit,
        onClickPlay: (Boolean) -> Unit,
        onTestGraphics: () -> Unit,
        exportFrontendLauncher: ActivityResultLauncher<String>,
        exportSaveLauncher: ActivityResultLauncher<String>,
        importSaveLauncher: ActivityResultLauncher<Array<String>>,
    ): List<AppMenuOption> {
        val isInstalled = isInstalled(context, libraryItem)
        val menuOptions = mutableListOf<AppMenuOption>()

        // Always available: Edit Container
        menuOptions.add(getEditContainerOption(context, libraryItem, onEditContainer))

        if (isInstalled) {
            // Options only available when game is installed
            
            menuOptions.add(AppMenuOption(AppOptionMenuType.Controller, onClick = {
                PluviaApp.events.emit(AndroidEvent.ShowControllerOptions)
            }))

            menuOptions.add(AppMenuOption(AppOptionMenuType.Saves, onClick = {
                PluviaApp.events.emit(AndroidEvent.ShowSavesOptions)
            }))

            getRunContainerOption(context, libraryItem, onClickPlay)?.let { menuOptions.add(it) }
            getTestGraphicsOption(context, libraryItem, onTestGraphics)?.let { menuOptions.add(it) }
            getResetContainerOption(context, libraryItem)?.let { menuOptions.add(it) }
            getCreateShortcutOption(context, libraryItem)?.let { menuOptions.add(it) }
            getExportContainerOption(context, libraryItem, exportFrontendLauncher)?.let { menuOptions.add(it) }
        }

        // Always available options
        menuOptions.add(getSubmitFeedbackOption(context, libraryItem))
        menuOptions.add(getFetchImagesOption(context, libraryItem))
        menuOptions.add(getGetSupportOption(context))

        // Add any source-specific options
        menuOptions.addAll(getSourceSpecificMenuOptions(context, libraryItem, onEditContainer, onBack, onClickPlay, isInstalled))

        return menuOptions
    }

    /**
     * Load container data for editing
     */
    abstract fun loadContainerData(context: Context, libraryItem: LibraryItem): ContainerData

    /**
     * Save container configuration
     */
    abstract fun saveContainerConfig(context: Context, libraryItem: LibraryItem, config: ContainerData)

    /**
     * Get the main content composable for this screen.
     * This uses the common UI layout from AppScreenContent.
     */
    @Composable
    fun Content(
        libraryItem: LibraryItem,
        onClickPlay: (Boolean) -> Unit,
        onTestGraphics: () -> Unit,
        onBack: () -> Unit,
    ) {
        val context = LocalContext.current
        val displayInfo = getGameDisplayInfo(context, libraryItem)

        // Use composable state for values that change over time
        var isInstalledState by remember(libraryItem.appId) {
            mutableStateOf(isInstalled(context, libraryItem))
        }
        var isValidToDownloadState by remember(libraryItem.appId) {
            mutableStateOf(isValidToDownload(context, libraryItem))
        }
        var isDownloadingState by remember(libraryItem.appId) {
            mutableStateOf(isDownloading(context, libraryItem))
        }
        var downloadProgressState by remember(libraryItem.appId) {
            mutableFloatStateOf(getDownloadProgress(context, libraryItem))
        }
        var isUpdatePendingState by remember(libraryItem.appId) {
            mutableStateOf(false) // Initialize to false, will be updated in LaunchedEffect
        }

        // Calculate hasPartialDownload state
        var hasPartialDownloadState by remember(libraryItem.appId) {
            mutableStateOf(hasPartialDownload(context, libraryItem))
        }

        val uiScope = rememberCoroutineScope()

        suspend fun performStateRefresh(includeUpdatePending: Boolean) {
            isInstalledState = isInstalled(context, libraryItem)
            isValidToDownloadState = isValidToDownload(context, libraryItem)
            val currentlyDownloading = isDownloading(context, libraryItem)
            isDownloadingState = currentlyDownloading
            downloadProgressState = getDownloadProgress(context, libraryItem)
            hasPartialDownloadState = hasPartialDownload(context, libraryItem)
            if (includeUpdatePending) {
                isUpdatePendingState = isUpdatePendingSuspend(context, libraryItem)
            }
        }

        fun requestStateRefresh(includeUpdatePending: Boolean) {
            uiScope.launch {
                performStateRefresh(includeUpdatePending)
            }
        }

        LaunchedEffect(libraryItem.appId) {
            performStateRefresh(true)
        }

        var showConfigDialog by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        var showControllerDialog by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        var showSavesDialog by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        var showProfileSelectionDialog by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        var showNavigationDialog by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        var initialNavigationAction by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableIntStateOf(0)
        }

        var showPhysicalControllerDialog by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        var profilesListKey by remember { mutableIntStateOf(0) }
        var activeProfile by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<com.winlator.inputcontrols.ControlsProfile?>(null)
        }

        var containerData by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(ContainerData())
        }

        val onEditContainer: () -> Unit = {
            containerData = loadContainerData(context, libraryItem)
            showConfigDialog = true
        }

        val inputControlsManager = remember { InputControlsManager(context) }

        // Import ICP Launcher
        val importICPLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                if (uri != null) {
                    val profile = inputControlsManager.importProfile(uri)
                    if (profile != null) {
                        val container = ContainerUtils.getContainer(context, libraryItem.appId)
                        container.putExtra("profileId", profile.id.toString())
                        container.saveData()
                        profilesListKey++
                        Toast.makeText(context, "Profile ${profile.getName()} imported and selected", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import profile", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        // Export ICP Launcher
        val exportICPLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
            onResult = { uri ->
                if (uri != null) {
                    val container = ContainerUtils.getContainer(context, libraryItem.appId)
                    val profileIdStr = container.getExtra("profileId", "0")
                    val profileId = profileIdStr.toIntOrNull() ?: 0
                    val profile = inputControlsManager.getProfile(profileId)
                    if (profile != null) {
                        if (inputControlsManager.exportProfile(profile, uri)) {
                            Toast.makeText(context, "Profile exported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to export profile", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No profile associated with this container", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        DisposableEffect(libraryItem.appId) {
            val showControllerListener: (AndroidEvent.ShowControllerOptions) -> Unit = {
                showControllerDialog = true
            }
            val showSavesListener: (AndroidEvent.ShowSavesOptions) -> Unit = {
                showSavesDialog = true
            }

            PluviaApp.events.on(showControllerListener)
            PluviaApp.events.on(showSavesListener)

            onDispose {
                PluviaApp.events.off(showControllerListener)
                PluviaApp.events.off(showSavesListener)
            }
        }

        // Export for Frontend launcher
        val exportFrontendLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
            onResult = { uri ->
                if (uri != null) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            val content = getGameId(libraryItem).toString()
                            outputStream.write(content.toByteArray(Charsets.UTF_8))
                            outputStream.flush()
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.base_app_exported),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.base_app_export_failed,
                                e.message ?: "",
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.base_app_export_cancelled),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )

        // Export Save Launcher
        val exportSaveLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
            onResult = { uri ->
                if (uri != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val container = ContainerUtils.getContainer(context, libraryItem.appId)
                            val gameName = displayInfo.name
                            val success = SaveManager.exportSave(context, container, gameName, uri)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(context, "Save exported successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to export save or no saves found", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error exporting save: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )

        // Import Save Launcher
        val importSaveLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                if (uri != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val container = ContainerUtils.getContainer(context, libraryItem.appId)
                            val success = SaveManager.importSave(context, container, uri)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(context, "Save imported successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to import save", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error importing save: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )

        val optionsMenu = getOptionsMenu(context, libraryItem, onEditContainer, onBack, onClickPlay, onTestGraphics, exportFrontendLauncher, exportSaveLauncher, importSaveLauncher)

        // Get download info based on game source for progress tracking
        val downloadInfo = when (libraryItem.gameSource) {
            app.gamenative.data.GameSource.STEAM -> app.gamenative.service.SteamService.getAppDownloadInfo(displayInfo.gameId)
            app.gamenative.data.GameSource.EPIC -> app.gamenative.service.epic.EpicService.getDownloadInfo(displayInfo.gameId)
            app.gamenative.data.GameSource.GOG -> app.gamenative.service.gog.GOGService.getDownloadInfo(displayInfo.gameId.toString())
            app.gamenative.data.GameSource.CUSTOM_GAME -> null // Custom games don't support downloads yet
        }

        DisposableEffect(libraryItem.appId) {
            val dispose = observeGameState(
                context = context,
                libraryItem = libraryItem,
                onStateChanged = { requestStateRefresh(true) },
                onProgressChanged = { progress ->
                    uiScope.launch {
                        downloadProgressState = progress
                    }
                },
                onHasPartialDownloadChanged = { hasPartial ->
                    hasPartialDownloadState = hasPartial
                },
            )
            onDispose {
                dispose?.invoke()
            }
        }

        // Render the common UI
        app.gamenative.ui.screen.library.AppScreenContent(
            displayInfo = displayInfo,
            isInstalled = isInstalledState,
            isValidToDownload = isValidToDownloadState,
            isDownloading = isDownloadingState,
            downloadProgress = downloadProgressState,
            hasPartialDownload = hasPartialDownloadState,
            isUpdatePending = isUpdatePendingState,
            downloadInfo = downloadInfo,
            onDownloadInstallClick = {
                onDownloadInstallClick(context, libraryItem, onClickPlay)
                uiScope.launch {
                    delay(100)
                    performStateRefresh(true)
                }
            },
            onPauseResumeClick = {
                onPauseResumeClick(context, libraryItem)
                uiScope.launch {
                    delay(100)
                    performStateRefresh(false)
                }
            },
            onDeleteDownloadClick = {
                onDeleteDownloadClick(context, libraryItem)
            },
            onUpdateClick = {
                onUpdateClick(context, libraryItem)
                uiScope.launch {
                    performStateRefresh(true)
                }
            },
            onCustomPathClick = {
                onCustomPathClick(context, libraryItem)
            },
            onBack = onBack,
            optionsMenu = optionsMenu.toTypedArray(),
        )

        // Show container config dialog if needed
        if (showConfigDialog) {
            ContainerConfigDialog(
                title = "${displayInfo.name} Config",
                initialConfig = containerData,
                onDismissRequest = { showConfigDialog = false },
                onSave = {
                    saveContainerConfig(context, libraryItem, it)
                    showConfigDialog = false
                },
            )
        }

        if (showControllerDialog) {
            ControllerOptionsDialog(
                onDismiss = { showControllerDialog = false },
                onEditOnScreen = {
                    showControllerDialog = false
                    val container = ContainerUtils.getContainer(context, libraryItem.appId)
                    val profileIdStr = container.getExtra("profileId", "0")
                    var profileId = profileIdStr.toIntOrNull() ?: 0
                    
                    if (profileId == 0) {
                        // Create a game-specific profile if it doesn't exist
                        val allProfiles = inputControlsManager.getProfiles(false)
                        val sourceProfile = inputControlsManager.getProfile(0)
                            ?: allProfiles.firstOrNull { it.id == 2 }
                            ?: allProfiles.firstOrNull()
                            
                        if (sourceProfile != null) {
                            try {
                                val newProfile = inputControlsManager.duplicateProfile(sourceProfile)
                                newProfile.setName("${displayInfo.name} - Controls")
                                newProfile.save()
                                profileId = newProfile.id
                                container.putExtra("profileId", profileId.toString())
                                container.saveData()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to auto-create profile for editor")
                            }
                        }
                    }
                    
                    if (profileId != 0) {
                        PluviaApp.events.emit(AndroidEvent.OpenControlsEditor(profileId))
                    } else {
                        Toast.makeText(context, "Could not open editor: No profile found", Toast.LENGTH_SHORT).show()
                    }
                },
                onSelectICP = {
                    showControllerDialog = false
                    showProfileSelectionDialog = true
                },
                onImportICP = {
                    showControllerDialog = false
                    importICPLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onExportICP = {
                    showControllerDialog = false
                    val gameName = displayInfo.name
                    exportICPLauncher.launch("${gameName}.icp")
                },
                onControllerManager = {
                    showControllerDialog = false
                    initialNavigationAction = NavigationDialog.ACTION_CONTROLLER_MANAGER
                    showNavigationDialog = true
                },
                onMotionControls = {
                    showControllerDialog = false
                    initialNavigationAction = NavigationDialog.ACTION_MOTION_CONTROLS
                    showNavigationDialog = true
                },
                onEditPhysicalController = {
                    showControllerDialog = false
                    initialNavigationAction = NavigationDialog.ACTION_EDIT_PHYSICAL_CONTROLLER
                    showNavigationDialog = true
                }
            )
        }

        if (showSavesDialog) {
            SavesOptionsDialog(
                onDismiss = { showSavesDialog = false },
                onImport = {
                    showSavesDialog = false
                    importSaveLauncher.launch(arrayOf("application/zip"))
                },
                onExport = {
                    showSavesDialog = false
                    val gameName = displayInfo.name
                    exportSaveLauncher.launch("${gameName}_${System.currentTimeMillis()}.zip")
                }
            )
        }

        if (showNavigationDialog) {
            // This is a bit tricky as NavigationDialog is a legacy View-based dialog.
            // We can show it using a side effect.
            SideEffect {
                val dialog = NavigationDialog(context) { itemId ->
                    // Handle item selection if needed, but here we just wanted to launch
                    // specific parts of it. Actually, NavigationDialog launches its own
                    // sub-dialogs for these actions.
                }
                // We want to immediately trigger the action and dismiss the main nav dialog
                // but NavigationDialog doesn't easily support that without showing it.
                // Alternative: call the manager/dialogs directly.
                when (initialNavigationAction) {
                    NavigationDialog.ACTION_CONTROLLER_MANAGER -> 
                        com.winlator.contentdialog.ControllerAssignmentDialog.show(context, null)
                    NavigationDialog.ACTION_MOTION_CONTROLS -> 
                        com.winlator.inputcontrols.MotionControls.getInstance(context).showContentDialog(context, null)
                    NavigationDialog.ACTION_EDIT_PHYSICAL_CONTROLLER -> {
                        // Handled by showPhysicalControllerDialog
                    }
                }
                showNavigationDialog = false
            }
        }

        if (showPhysicalControllerDialog && activeProfile != null) {
            PhysicalControllerConfigSection(
                profile = activeProfile!!,
                onDismiss = { showPhysicalControllerDialog = false },
                onSave = { showPhysicalControllerDialog = false }
            )
        }

        if (showProfileSelectionDialog) {
            val container = ContainerUtils.getContainer(context, libraryItem.appId)
            val selectedId = container.getExtra("profileId", "0").toIntOrNull() ?: 0
            
            val profilesList = remember(profilesListKey) { inputControlsManager.getProfiles(false) }

            ProfileSelectionDialog(
                profiles = profilesList,
                selectedProfileId = selectedId,
                onDismiss = { showProfileSelectionDialog = false },
                onProfileSelected = { profile ->
                    container.putExtra("profileId", profile.id.toString())
                    container.saveData()
                    showProfileSelectionDialog = false
                    Toast.makeText(context, "Profile ${profile.name} selected", Toast.LENGTH_SHORT).show()
                },
                onDeleteProfile = { profile ->
                    inputControlsManager.removeProfile(profile)
                    if (selectedId == profile.id) {
                        container.putExtra("profileId", "0")
                        container.saveData()
                    }
                    profilesListKey++
                    Toast.makeText(context, "Profile deleted", Toast.LENGTH_SHORT).show()
                },
                onRenameProfile = { profile, newName ->
                    inputControlsManager.renameProfile(profile, newName)
                    profilesListKey++
                    Toast.makeText(context, "Profile renamed", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Render any additional dialogs
        AdditionalDialogs(libraryItem, onDismiss = {}, onEditContainer = onEditContainer, onBack = onBack)
    }

    /**
     * Check if container configuration editing is supported
     */
    abstract fun supportsContainerConfig(): Boolean

    /**
     * Observe download/install state changes for this app.
     * Return a lambda that will be invoked to clean up observers.
     */
    protected open fun observeGameState(
        context: Context,
        libraryItem: LibraryItem,
        onStateChanged: () -> Unit,
        onProgressChanged: (Float) -> Unit,
        onHasPartialDownloadChanged: ((Boolean) -> Unit)? = null,
    ): (() -> Unit)? {
        return null
    }

    /**
     * Get additional dialogs to show (e.g., loading, message dialogs).
     * Override this to add source-specific dialogs.
     */
    @Composable
    open fun AdditionalDialogs(
        libraryItem: LibraryItem,
        onDismiss: () -> Unit,
        onEditContainer: () -> Unit,
        onBack: () -> Unit,
    ) {
        // Default: no additional dialogs
    }
}
