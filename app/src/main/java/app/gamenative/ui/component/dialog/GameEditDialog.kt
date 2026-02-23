package app.gamenative.ui.component.dialog

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.PluviaApp
import app.gamenative.R
import com.winlator.container.Container
import app.gamenative.data.LibraryItem
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.screen.library.appscreen.AmazonAppScreen
import app.gamenative.ui.screen.library.appscreen.CustomGameAppScreen
import app.gamenative.ui.screen.library.appscreen.EpicAppScreen
import app.gamenative.ui.screen.library.appscreen.GOGAppScreen
import app.gamenative.ui.screen.library.appscreen.SteamAppScreen
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.SaveManager
import com.winlator.container.ContainerData
import com.winlator.contentdialog.NavigationDialog
import com.winlator.inputcontrols.InputControlsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private enum class EditView {
    MENU,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameEditDialog(
    libraryItem: LibraryItem,
    onDismiss: () -> Unit,
    onClickPlay: (Boolean) -> Unit,
    onTestGraphics: () -> Unit,
) {
    val context = LocalContext.current
    var currentView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(EditView.MENU) }
    
    // Get the appropriate screen model based on game source
    val screenModel = remember(libraryItem.gameSource) {
        when (libraryItem.gameSource) {
            app.gamenative.data.GameSource.STEAM -> SteamAppScreen()
            app.gamenative.data.GameSource.CUSTOM_GAME -> CustomGameAppScreen()
            app.gamenative.data.GameSource.GOG -> GOGAppScreen()
            app.gamenative.data.GameSource.EPIC -> EpicAppScreen()
            app.gamenative.data.GameSource.AMAZON -> AmazonAppScreen()
        }
    }

    var containerData by remember { mutableStateOf(ContainerData()) }
    
    // Launchers for actions
    val exportFrontendLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val content = libraryItem.gameId.toString()
                        outputStream.write(content.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                    Toast.makeText(context, context.getString(R.string.base_app_exported), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.base_app_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            if (uri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val container = ContainerUtils.getContainer(context, libraryItem.appId)
                        val gameName = libraryItem.name
                        val success = SaveManager.exportSave(context, container, gameName, uri)
                        withContext(Dispatchers.Main) {
                            if (success) Toast.makeText(context, "Save exported successfully", Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, "Failed to export save", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    )

    val importSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val container = ContainerUtils.getContainer(context, libraryItem.appId)
                        val success = SaveManager.importSave(context, container, uri)
                        withContext(Dispatchers.Main) {
                            if (success) Toast.makeText(context, "Save imported successfully", Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, "Failed to import save", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    )

    // Event handlers for dialogs that might be triggered by menu options
    // Since we are inside a Dialog, showing another Dialog (via events) works fine (it stacks).
    // However, for "Settings" we want inline replacement.
    
    val menuOptions = screenModel.getOptionsMenu(
        context = context,
        libraryItem = libraryItem,
        onEditContainer = {
            // Load container data synchronously before switching to Settings view
            containerData = screenModel.loadContainerData(context, libraryItem)
            currentView = EditView.SETTINGS
        },
        onBack = onDismiss, // Not used in this context usually
        onClickPlay = onClickPlay,
        onTestGraphics = onTestGraphics,
        exportFrontendLauncher = exportFrontendLauncher,
        exportSaveLauncher = exportSaveLauncher,
        importSaveLauncher = importSaveLauncher
    )

    Dialog(
        onDismissRequest = {
            if (currentView == EditView.SETTINGS) {
                currentView = EditView.MENU
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        // Handle physical back button
        BackHandler {
            if (currentView == EditView.SETTINGS) {
                currentView = EditView.MENU
            } else {
                onDismiss()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            when (currentView) {
                EditView.MENU -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = libraryItem.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        
                        HorizontalDivider()
                        
                        // Menu Items List
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Filter options based on requirement
                            // User wants: "Settings, Controller, Saves, Container, Create Shortcut, Export for Frontend, Fetch Game Images, Get Support"
                            // And "Test Graphics" inside Container.
                            
                            // Map existing options to this order
                            val orderedTypes = listOf(
                                AppOptionMenuType.EditContainer, // Settings
                                AppOptionMenuType.Controller,
                                AppOptionMenuType.Saves,
                                AppOptionMenuType.Container,
                                AppOptionMenuType.CreateShortcut,
                                AppOptionMenuType.ExportFrontend,
                                AppOptionMenuType.FetchSteamGridDBImages,
                                AppOptionMenuType.GetSupport
                            )
                            
                            orderedTypes.forEach { type ->
                                val option = menuOptions.find { it.optionType == type }
                                if (option != null) {
                                    val label = if (type == AppOptionMenuType.EditContainer) "Settings" else type.text
                                    MenuButton(
                                        text = label,
                                        onClick = option.onClick
                                    )
                                }
                            }

                            // Add Uninstall button at the bottom if installed
                            if (screenModel.isInstalled(context, libraryItem)) {
                                MenuButton(
                                    text = stringResource(R.string.uninstall),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    onClick = {
                                        screenModel.onDeleteDownloadClick(context, libraryItem)
                                        // The uninstall logic in BaseAppScreen/SteamAppScreen shows a dialog
                                        // which is handled by AdditionalDialogs in AppScreen.
                                        // Since we are in GameEditDialog, we might need to close this dialog 
                                        // or let the uninstall dialog appear on top.
                                    }
                                )
                            }
                        }
                        
                        // Footer with Back Button at Bottom Left
                        HorizontalDivider()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Back")
                            }
                        }
                    }
                }
                EditView.SETTINGS -> {
                    // Inline Container Config
                    // We reuse ContainerConfigScreen but wrapped in a Column to provide a Back button
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ContainerConfigScreen handles its own TopBar and Dismiss
                        // We override onDismissRequest to go back to MENU
                        ContainerConfigScreen(
                            title = "${libraryItem.name} Config",
                            initialConfig = containerData,
                            onDismissRequest = { currentView = EditView.MENU },
                            onSave = { config ->
                                screenModel.saveContainerConfig(context, libraryItem, config)
                                currentView = EditView.MENU
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Render any additional dialogs from the screen model (e.g., Uninstall confirmation)
    screenModel.AdditionalDialogs(
        libraryItem = libraryItem,
        onDismiss = onDismiss,
        onEditContainer = {
            containerData = screenModel.loadContainerData(context, libraryItem)
            currentView = EditView.SETTINGS
        },
        onBack = onDismiss
    )
    
    // Listen for Container Options event to inject Test Graphics
    // This is a bit hacky: The Container option emits ShowContainerOptions.
    // BaseAppScreen handles this by showing ContainerOptionsDialog.
    // We are inside GameEditDialog. If we want to modify the Container Options Dialog,
    // we need to intercept this event or modify where it's handled.
    // Since BaseAppScreen logic runs in AppScreen, and we are NOT in AppScreen but in LibraryScreen context,
    // BaseAppScreen event listeners are NOT active here unless we activate them.
    // Wait, screenModel methods (getOptionsMenu) emit events. Who listens?
    // In LibraryAppScreen, DisposableEffect registers listeners.
    // Here in GameEditDialog, we need to register listeners too!
    
    var showContainerDialog by remember { mutableStateOf(false) }
    var showControllerDialog by remember { mutableStateOf(false) }
    var showSavesDialog by remember { mutableStateOf(false) }
    
    DisposableEffect(libraryItem.appId) {
        val showContainerListener: (AndroidEvent.ShowContainerOptions) -> Unit = {
            if (it.appId == libraryItem.appId) showContainerDialog = true
        }
        val showControllerListener: (AndroidEvent.ShowControllerOptions) -> Unit = {
            showControllerDialog = true
        }
        val showSavesListener: (AndroidEvent.ShowSavesOptions) -> Unit = {
            showSavesDialog = true
        }

        PluviaApp.events.on(showContainerListener)
        PluviaApp.events.on(showControllerListener)
        PluviaApp.events.on(showSavesListener)

        onDispose {
            PluviaApp.events.off(showContainerListener)
            PluviaApp.events.off(showControllerListener)
            PluviaApp.events.off(showSavesListener)
        }
    }
    
    if (showContainerDialog) {
        ContainerOptionsDialog(
            onDismiss = { showContainerDialog = false },
            onOpen = {
                showContainerDialog = false
                onClickPlay(true) // Run container
            },
            onReset = {
                showContainerDialog = false
                // Reset logic - access via screenModel? screenModel.resetContainerToDefaults is protected.
                // We might need to duplicate it or expose it.
                // For now, let's use ContainerUtils directly.
                val container = ContainerUtils.getOrCreateContainer(context, libraryItem.appId)
                val defaults = ContainerUtils.getDefaultContainerData().copy(drives = container.drives)
                ContainerUtils.applyToContainer(context, libraryItem.appId, defaults)
                Toast.makeText(context, "Container reset to defaults", Toast.LENGTH_SHORT).show()
            },
            onTestGraphics = onTestGraphics
        )
    }
    
    if (showControllerDialog) {
       ControllerOptionsDialog(
            onDismiss = { showControllerDialog = false },
            onEditOnScreen = {
                showControllerDialog = false
                val inputControlsManager = InputControlsManager(context)
                val container = ContainerUtils.getContainer(context, libraryItem.appId)
                val profileIdStr = container.getExtra("profileId", "0")
                var profileId = profileIdStr.toIntOrNull() ?: 0
                
                if (profileId == 0) {
                    val allProfiles = inputControlsManager.getProfiles(false)
                    val sourceProfile = inputControlsManager.getProfile(0)
                        ?: allProfiles.firstOrNull { it.id == 2 }
                        ?: allProfiles.firstOrNull()
                        
                    if (sourceProfile != null) {
                        try {
                            val newProfile = inputControlsManager.duplicateProfile(sourceProfile)
                            newProfile.setName("${libraryItem.name} - Controls")
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
                // Trigger profile selection dialog - need to add state for it if we support it here
                // For now simplified
                Toast.makeText(context, "Profile selection not fully implemented in this quick view", Toast.LENGTH_SHORT).show()
            },
            onImportICP = { /* ... */ },
            onExportICP = { /* ... */ },
            onControllerManager = {
                 showControllerDialog = false
                 com.winlator.contentdialog.ControllerAssignmentDialog.show(context, null)
            },
            onMotionControls = {
                 showControllerDialog = false
                 com.winlator.inputcontrols.MotionControls.getInstance(context).showContentDialog(context, null)
            },
            onEditPhysicalController = { /* ... */ }
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
                val gameName = libraryItem.name
                exportSaveLauncher.launch("${gameName}_${System.currentTimeMillis()}.zip")
            }
        )
    }
}

@Composable
fun MenuButton(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
