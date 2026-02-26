package app.gamenative.ui.screen.library.components

import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.component.AnimatedWavyBackground
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.icons.Amazon
import app.gamenative.ui.icons.CustomGame
import app.gamenative.ui.icons.Steam
import app.gamenative.utils.CustomGameScanner
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

import android.hardware.input.InputManager
import android.content.Context
import android.view.InputDevice
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.winlator.inputcontrols.ExternalController

import app.gamenative.service.SteamService
import app.gamenative.service.epic.EpicAuthManager
import app.gamenative.service.gog.GOGAuthManager
import app.gamenative.service.amazon.AmazonAuthManager
import kotlin.math.sign

private enum class FrontendTab(val label: String) {
    LIBRARY("Library"),
    STEAM("Steam"),
    EPIC("Epic"),
    GOG("GOG"),
    AMAZON("Amazon"),
    CUSTOM("Custom"),
}

private enum class ControllerType {
    NONE, XBOX, PLAYSTATION, GENERIC
}

private fun checkAuthStatus(context: Context, tab: FrontendTab): Boolean {
    return when (tab) {
        FrontendTab.STEAM -> SteamService.isLoggedIn
        FrontendTab.EPIC -> EpicAuthManager.hasStoredCredentials(context)
        FrontendTab.GOG -> GOGAuthManager.hasStoredCredentials(context)
        FrontendTab.AMAZON -> AmazonAuthManager.hasStoredCredentials(context)
        else -> true
    }
}

@Composable
private fun rememberFrontendArtUrl(item: LibraryItem, isHero: Boolean = false): String {
    val context = LocalContext.current
    return remember(item.appId, isHero) {
        when (item.gameSource) {
            GameSource.STEAM -> {
                if (isHero) "https://cdn.cloudflare.steamstatic.com/steam/apps/${item.gameId}/library_hero.jpg"
                else "https://shared.steamstatic.com/store_item_assets/steam/apps/${item.gameId}/library_600x900.jpg"
            }
            GameSource.CUSTOM_GAME -> {
                val gameFolderPath = CustomGameScanner.getFolderPathFromAppId(item.appId)
                val type = if (isHero) "hero" else "grid_capsule"
                val sgdbImage = gameFolderPath?.let { path ->
                    val folder = File(path)
                    folder.listFiles()?.firstOrNull { file ->
                        file.name.startsWith("steamgriddb_$type") &&
                            (file.name.endsWith(".png", ignoreCase = true) ||
                                file.name.endsWith(".jpg", ignoreCase = true) ||
                                file.name.endsWith(".webp", ignoreCase = true))
                    }?.let { Uri.fromFile(it).toString() }
                }
                
                if (sgdbImage != null) sgdbImage 
                else {
                    val path = CustomGameScanner.findIconFileForCustomGame(context, item.appId)
                    if (!path.isNullOrEmpty()) {
                        val file = File(path)
                        val ts = if (file.exists()) file.lastModified() else 0L
                        "file://$path?t=$ts"
                    } else {
                        item.clientIconUrl
                    }
                }
            }
            else -> item.clientIconUrl.ifEmpty { item.iconHash }
        }
    }
}

/**
 * Fallback for when the main artwork is empty or fails to load.
 * Tries the game's icon URL (same as list view uses), then falls back to a letter.
 */
@Composable
private fun FrontendFallbackIcon(
    item: LibraryItem,
    bgColor: Color = Color(0xFF2A2A3E),
    letterSize: androidx.compose.ui.unit.TextUnit = 18.sp,
) {
    val iconUrl = item.clientIconUrl
    if (iconUrl.isNotEmpty()) {
        CoilImage(
            modifier = Modifier.fillMaxSize().background(bgColor),
            imageModel = { iconUrl },
            imageOptions = ImageOptions(contentScale = ContentScale.Fit),
            failure = {
                Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
                    Text(item.name.take(1), fontSize = letterSize, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    } else {
        Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            Text(item.name.take(1), fontSize = letterSize, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControllerBadge(
    button: String,
    controllerType: ControllerType,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (controllerType) {
        ControllerType.PLAYSTATION -> {
            when (button) {
                "A" -> "X" to Color(0xFF5865F2)
                "B" -> "O" to Color(0xFFED4245)
                "X" -> "□" to Color(0xFFEB459E)
                "Y" -> "△" to Color(0xFF3BA559)
                else -> button to Color.White.copy(alpha = 0.8f)
            }
        }
        else -> {
            when (button) {
                "A" -> "A" to Color(0xFF3BA559)
                "B" -> "B" to Color(0xFFED4245)
                "X" -> "X" to Color(0xFF5865F2)
                "Y" -> "Y" to Color(0xFFFEE75C)
                else -> button to Color.White.copy(alpha = 0.8f)
            }
        }
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatSpeed(bytesPerSec: Double): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var speed = bytesPerSec
    var unitIdx = 0
    while (speed >= 1024 && unitIdx < units.size - 1) {
        speed /= 1024
        unitIdx++
    }
    return String.format("%.1f %s", speed, units[unitIdx])
}

@Composable
private fun FrontendDownloadDialog(
    item: LibraryItem,
    controllerType: ControllerType,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableStateOf("0 B/s") }
    var isInstalled by remember { mutableStateOf(false) }
    var focusedButton by remember { mutableIntStateOf(1) } // 0=Play, 1=OK

    // Monitor progress and install status
    LaunchedEffect(item.appId) {
        val gameId = item.gameId
        while (true) {
            val info = when (item.gameSource) {
                app.gamenative.data.GameSource.STEAM -> app.gamenative.service.SteamService.getAppDownloadInfo(gameId)
                app.gamenative.data.GameSource.EPIC -> app.gamenative.service.epic.EpicService.getDownloadInfo(gameId)
                app.gamenative.data.GameSource.GOG -> app.gamenative.service.gog.GOGService.getDownloadInfo(gameId.toString())
                else -> null
            }

            progress = info?.getProgress() ?: 0f
            speed = formatSpeed(info?.getRecentSpeedBytesPerSec() ?: 0.0)

            isInstalled = when (item.gameSource) {
                app.gamenative.data.GameSource.STEAM -> app.gamenative.service.SteamService.isAppInstalled(gameId)
                app.gamenative.data.GameSource.EPIC -> app.gamenative.service.epic.EpicService.getEpicGameOf(gameId)?.isInstalled ?: false
                app.gamenative.data.GameSource.GOG -> app.gamenative.service.gog.GOGService.isGameInstalled(gameId.toString())
                else -> false
            }

            if (isInstalled && progress >= 1f) {
                progress = 1f
                speed = "0 B/s"
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // Controller input
    DisposableEffect(Unit) {
        val keyListener: (AndroidEvent.KeyEvent) -> Boolean = { event ->
            if (event.event.action == KeyEvent.ACTION_DOWN) {
                when (event.event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                        focusedButton = 0; true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusedButton = 1; true
                    }
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        if (focusedButton == 0) {
                            if (isInstalled) onPlay()
                        } else onDismiss()
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_B -> { onDismiss(); true }
                    else -> false
                }
            } else false
        }
        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(keyListener)
        onDispose {
            PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(keyListener)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A2E),
            modifier = Modifier.width(320.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Text(speed, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
                
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlay,
                        enabled = isInstalled,
                        modifier = Modifier.weight(1f),
                        border = if (focusedButton == 0) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    ) {
                        Text("Play")
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = if (focusedButton == 1) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Text("OK", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FrontendInstallDialog(
    item: LibraryItem,
    controllerType: ControllerType,
    onInstall: () -> Unit,
    onCustomPath: () -> Unit,
    onDismiss: () -> Unit,
) {
    var focusedButton by remember { mutableIntStateOf(0) } // 0=Install, 1=Custom Path

    // Controller input for dialog
    DisposableEffect(Unit) {
        val keyListener: (AndroidEvent.KeyEvent) -> Boolean = { event ->
            if (event.event.action == KeyEvent.ACTION_DOWN) {
                when (event.event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                        focusedButton = 0; true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusedButton = 1; true
                    }
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        if (focusedButton == 0) onInstall() else onCustomPath()
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_B -> { onDismiss(); true }
                    else -> false
                }
            } else false
        }
        val motionListener: (AndroidEvent.MotionEvent) -> Boolean = { motionEvent ->
            val event = motionEvent.event
            if (event is android.view.MotionEvent) {
                val axisX = event.getAxisValue(android.view.MotionEvent.AXIS_X)
                val hatX = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
                val x = if (abs(hatX) > 0.1f) hatX else axisX
                
                if (x < -0.5f) { focusedButton = 0; true }
                else if (x > 0.5f) { focusedButton = 1; true }
                else false
            } else false
        }
        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(keyListener)
        PluviaApp.events.on<AndroidEvent.MotionEvent, Boolean>(motionListener)
        onDispose {
            PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(keyListener)
            PluviaApp.events.off<AndroidEvent.MotionEvent, Boolean>(motionListener)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A2E),
            tonalElevation = 8.dp,
            modifier = Modifier.width(340.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This game is not installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val installBorder = if (focusedButton == 0) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    val customBorder = if (focusedButton == 1) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

                    Button(
                        onClick = onInstall,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = installBorder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Install", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onCustomPath,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = customBorder ?: BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Custom Path", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (controllerType != ControllerType.NONE) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ControllerBadge("A", controllerType)
                            Text("Select", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ControllerBadge("B", controllerType)
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFrontendPane(
    state: LibraryState,
    onNavigate: (String) -> Unit,
    onClickPlay: (String, Boolean) -> Unit,
    onAddCustomGame: () -> Unit,
    onViewChanged: (PaneType) -> Unit,
    onModalBottomSheet: (Boolean) -> Unit,
    onNavigateRoute: (String) -> Unit,
    onEdit: (LibraryItem) -> Unit,
    onSearchQuery: (String) -> Unit,
    onFocusChanged: (LibraryItem?) -> Unit = {},
    onRefresh: () -> Unit = {},
    isAnyDialogOpen: Boolean = false,
) {
    val context = LocalContext.current
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    val tabs = FrontendTab.entries
    val coroutineScope = rememberCoroutineScope()
    var isSearchingLocally by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var connectedControllerType by remember { mutableStateOf(ControllerType.NONE) }
    var installDialogItem by remember { mutableStateOf<LibraryItem?>(null) }
    var downloadingDialogItem by remember { mutableStateOf<LibraryItem?>(null) }
    var storefrontHeaderOffsetY by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()

    val handleInstallClick: (LibraryItem) -> Unit = { item ->
        scope.launch(Dispatchers.IO) {
            val gameId = item.gameId
            when (item.gameSource) {
                app.gamenative.data.GameSource.STEAM -> {
                    val path = app.gamenative.service.SteamService.getAppDirPath(gameId)
                    if (app.gamenative.ui.components.requestPermissionsForPath(context, path, null)) {
                        app.gamenative.service.SteamService.downloadApp(gameId)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Storage permission required", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                app.gamenative.data.GameSource.EPIC -> {
                    val path = app.gamenative.service.epic.EpicService.getInstallPath(gameId) ?: app.gamenative.service.epic.EpicConstants.getGameInstallPath(context, item.name)
                    if (app.gamenative.ui.components.requestPermissionsForPath(context, path, null)) {
                        app.gamenative.service.epic.EpicService.downloadGame(context, gameId, emptyList(), path)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Storage permission required", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                app.gamenative.data.GameSource.GOG -> {
                    val installPath = app.gamenative.service.gog.GOGService.getInstallPath(gameId.toString()) 
                        ?: app.gamenative.service.gog.GOGConstants.getGameInstallPath(item.name)
                    
                    if (app.gamenative.ui.components.requestPermissionsForPath(context, installPath, null)) {
                        app.gamenative.service.gog.GOGService.downloadGame(context, gameId.toString(), installPath)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Storage permission required", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                else -> {}
            }
            withContext(Dispatchers.Main) {
                downloadingDialogItem = item
            }
        }
    }

    val frontendFolderPicker = app.gamenative.ui.component.picker.rememberDownloadFolderPicker(
        onPathSelected = { path ->
            val item = installDialogItem ?: return@rememberDownloadFolderPicker
            when (item.gameSource) {
                app.gamenative.data.GameSource.STEAM -> {
                    app.gamenative.service.SteamService.setCustomInstallPath(item.gameId, path)
                }
                app.gamenative.data.GameSource.EPIC -> {
                    app.gamenative.service.epic.EpicService.setCustomInstallPath(context, item.gameId, path)
                }
                app.gamenative.data.GameSource.GOG -> {
                    app.gamenative.service.gog.GOGService.setCustomInstallPath(context, item.gameId.toString(), path)
                }
                else -> {}
            }
            Toast.makeText(context, "Installation path set", Toast.LENGTH_SHORT).show()
        }
    )

    // Hoisted grid state for storefront tabs — accessible from controller input handlers
    val gridState = rememberLazyGridState()
    var focusedGridIndex by remember { mutableIntStateOf(0) }
    var leftStickX by remember { mutableFloatStateOf(0f) }
    var leftStickY by remember { mutableFloatStateOf(0f) }
    var rightStickX by remember { mutableFloatStateOf(0f) }
    var rightStickY by remember { mutableFloatStateOf(0f) }
    var hatX by remember { mutableFloatStateOf(0f) }
    var hatY by remember { mutableFloatStateOf(0f) }

    var isHeaderFocused by remember { mutableStateOf(false) }
    var headerFocusIndex by remember { mutableIntStateOf(0) } // 0=Search, 1=Settings, 2=GameMenu
    var rightTriggerPressed by remember { mutableStateOf(false) }

    val currentLeftStickX by rememberUpdatedState(leftStickX)
    val currentLeftStickY by rememberUpdatedState(leftStickY)
    val currentRightStickX by rememberUpdatedState(rightStickX)
    val currentRightStickY by rememberUpdatedState(rightStickY)
    val currentHatX by rememberUpdatedState(hatX)
    val currentHatY by rememberUpdatedState(hatY)

    // Helper: handle game click — if uninstalled on a storefront tab, show install dialog
    val handleGameClick: (LibraryItem) -> Unit = { item ->
        val isLibraryTab = tabs[selectedTabIdx] == FrontendTab.LIBRARY
        if (!isLibraryTab && !item.isInstalled) {
            installDialogItem = item
        } else {
            onClickPlay(item.appId, false)
        }
    }

    // Detect connected controller
    LaunchedEffect(Unit) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val checkControllers = {
            val deviceIds = inputManager.inputDeviceIds
            var foundType = ControllerType.NONE
            for (id in deviceIds) {
                val device = inputManager.getInputDevice(id)
                if (device != null && (device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || 
                    device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)) {
                    val controller = ExternalController.getController(id)
                    if (controller != null) {
                        foundType = if (controller.isPlayStationController) ControllerType.PLAYSTATION
                        else if (controller.isXboxController) ControllerType.XBOX
                        else ControllerType.GENERIC
                        break
                    }
                }
            }
            connectedControllerType = foundType
        }

        checkControllers()
        
        // Simple polling for connection changes
        while(true) {
            delay(2000)
            checkControllers()
        }
    }

    val tabItems: List<LibraryItem> = remember(
        selectedTabIdx, state.appInfoList, state.searchQuery,
        state.steamItems, state.gogItems, state.epicItems, state.amazonItems, state.customItems
    ) {
        if (state.searchQuery.isNotEmpty()) {
            // When searching, combine all full source lists and filter
            (state.steamItems + state.gogItems + state.epicItems + state.amazonItems + state.customItems)
                .filter { it.name.contains(state.searchQuery, ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
        } else {
            when (tabs[selectedTabIdx]) {
                FrontendTab.LIBRARY -> state.appInfoList
                    .filter { it.isInstalled }
                    .sortedByDescending { it.lastPlayed }
                FrontendTab.STEAM -> state.steamItems
                FrontendTab.EPIC -> state.epicItems
                FrontendTab.GOG -> state.gogItems
                FrontendTab.AMAZON -> state.amazonItems
                FrontendTab.CUSTOM -> state.customItems
            }
        }
    }

    val isCustomTab = tabs[selectedTabIdx] == FrontendTab.CUSTOM
    val isLibraryOrCustom = tabs[selectedTabIdx] == FrontendTab.LIBRARY || isCustomTab
    val pagerCount = if (isCustomTab) tabItems.size + 1 else tabItems.size
    val pagerState = rememberPagerState(pageCount = { pagerCount })

    val focusedItem: LibraryItem? = remember(pagerState.currentPage, focusedGridIndex, tabItems, isLibraryOrCustom) {
        if (isLibraryOrCustom) {
            if (pagerState.currentPage < tabItems.size) tabItems[pagerState.currentPage] else null
        } else {
            if (focusedGridIndex in tabItems.indices) tabItems[focusedGridIndex] else null
        }
    }

    // Sync focus changes to parent
    LaunchedEffect(focusedItem) {
        onFocusChanged(focusedItem)
    }

    // Stable Right Joystick Continuous Scroll
    LaunchedEffect(isLibraryOrCustom) {
        while (true) {
            val rx = currentRightStickX
            val ry = currentRightStickY
            
            if (isLibraryOrCustom) {
                // Carousel: right stick X = fast page browsing
                if (abs(rx) > 0.25f) {
                    // Quadratic curve for speed
                    val rxAbs = abs(rx)
                    // 50% faster than before (max speed 15 -> 22)
                    val speed = (rxAbs * rxAbs * 18f + 4f).roundToInt().coerceIn(4, 22)
                    val delayMs = (1000L / speed)
                    val target = if (rx > 0) {
                        (pagerState.currentPage + 1).coerceAtMost(pagerCount - 1)
                    } else {
                        (pagerState.currentPage - 1).coerceAtLeast(0)
                    }
                    if (target != pagerState.currentPage) {
                        pagerState.scrollToPage(target)
                    }
                    delay(delayMs)
                } else {
                    delay(32)
                }
            } else {
                // Grid tabs: right stick Y = smooth pixel scroll
                if (abs(ry) > 0.1f) {
                    // Cubic curve for ultra-smooth start
                    val scaledY = sign(ry) * (abs(ry) * abs(ry) * abs(ry))
                    // 50% faster than before (50f -> 75f)
                    val speed = scaledY * 75f
                    gridState.scroll { scrollBy(speed) }
                    delay(10)
                } else {
                    // Snap focusedGridIndex to first visible item when released (only if not moving)
                    val firstVisible = gridState.firstVisibleItemIndex
                    if (firstVisible in tabItems.indices && abs(ry) < 0.05f) {
                        // Don't auto-snap if we're already within visible range
                        val visibleItems = gridState.layoutInfo.visibleItemsInfo
                        if (visibleItems.none { it.index == focusedGridIndex }) {
                            focusedGridIndex = firstVisible
                        }
                    }
                    delay(32)
                }
            }
        }
    }

    // Stable Left Joystick Selection Navigation (Variable/Progressive Speed)
    LaunchedEffect(selectedTabIdx, isSearchingLocally) {
        var nextMoveTime = 0L
        var lastAppliedDir = -1 // 0=U, 1=D, 2=L, 3=R

        while (true) {
            if (isSearchingLocally) {
                delay(100)
                continue
            }

            // Combine Stick and Hat for navigation
            val lxStick = currentLeftStickX
            val lyStick = currentLeftStickY
            val hx = currentHatX
            val hy = currentHatY
            
            val lx = if (abs(hx) > 0.1f) hx else lxStick
            val ly = if (abs(hy) > 0.1f) hy else lyStick
            
            val absX = abs(lx)
            val absY = abs(ly)
            val maxAbs = maxOf(absX, absY)

            if (maxAbs > 0.2f) {
                val currentTime = System.currentTimeMillis()
                
                // Determine major axis direction
                val currentDir = if (absX > absY) {
                    if (lx > 0) 3 else 2 // Right, Left
                } else {
                    if (ly > 0) 1 else 0 // Down, Up
                }

                // Move if: 
                // 1. Time for repeat has passed
                // 2. Direction changed (instant switch)
                // 3. We just started moving (nextMoveTime was 0)
                if (currentTime >= nextMoveTime || currentDir != lastAppliedDir) {
                    if (absX > absY) {
                        // Horizontal movement
                        if (absX > 0.25f) {
                            if (isHeaderFocused) {
                                val maxIdx = if (focusedItem != null) 2 else 1
                                if (lx > 0) headerFocusIndex = (headerFocusIndex + 1) % (maxIdx + 1)
                                else headerFocusIndex = (headerFocusIndex - 1 + (maxIdx + 1)) % (maxIdx + 1)
                            } else {
                                if (isLibraryOrCustom) {
                                    if (lx > 0) {
                                        if (pagerState.currentPage < pagerCount - 1) {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    } else {
                                        if (pagerState.currentPage > 0) {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                } else {
                                    val newIdx = if (lx > 0) (focusedGridIndex + 1).coerceAtMost(tabItems.size - 1)
                                                 else (focusedGridIndex - 1).coerceAtLeast(0)
                                    if (newIdx != focusedGridIndex) {
                                        focusedGridIndex = newIdx
                                        gridState.animateScrollToItem(newIdx)
                                    }
                                }
                            }
                        }
                    } else {
                        // Vertical movement
                        if (absY > 0.25f) {
                            if (ly < 0) { // UP
                                if (!isHeaderFocused) {
                                    if (isLibraryOrCustom) {
                                        isHeaderFocused = true
                                        headerFocusIndex = 0
                                    } else {
                                        val newIdx = focusedGridIndex - 4
                                        if (newIdx >= 0) {
                                            focusedGridIndex = newIdx
                                            gridState.animateScrollToItem(newIdx)
                                        } else {
                                            isHeaderFocused = true
                                            headerFocusIndex = 0
                                        }
                                    }
                                }
                            } else if (ly > 0) { // DOWN
                                if (isHeaderFocused) {
                                    isHeaderFocused = false
                                } else if (!isLibraryOrCustom) {
                                    val newIdx = (focusedGridIndex + 4).coerceAtMost(tabItems.size - 1)
                                    if (newIdx != focusedGridIndex) {
                                        focusedGridIndex = newIdx
                                        gridState.animateScrollToItem(newIdx)
                                    }
                                }
                            }
                        }
                    }

                    // Update tracking for next iteration
                    lastAppliedDir = currentDir
                    val normalized = (maxAbs - 0.2f) / 0.8f
                    val speedCurve = normalized * normalized // Quadratic
                    // Range: 1000ms (slow) to 200ms (fast) - 50% faster than previous 1500-300
                    val currentDelay = (1000f - (speedCurve * 800f)).toLong().coerceIn(200L, 1000L)
                    nextMoveTime = currentTime + currentDelay
                }
            } else {
                nextMoveTime = 0L // Reset for instant response on next push
                lastAppliedDir = -1
            }
            delay(16) // High frequency polling for perfect responsiveness
        }
    }

    // Controller Input Handling
    DisposableEffect(focusedItem, selectedTabIdx, pagerCount, isSearchingLocally, isLibraryOrCustom, isHeaderFocused, headerFocusIndex, isAnyDialogOpen, installDialogItem) {
        val keyListener: (AndroidEvent.KeyEvent) -> Boolean = { event ->
            if (isAnyDialogOpen || installDialogItem != null) false
            else if (event.event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (isSearchingLocally && (event.event.keyCode == KeyEvent.KEYCODE_BACK || event.event.keyCode == KeyEvent.KEYCODE_BUTTON_B)) {
                    isSearchingLocally = false
                    focusManager.clearFocus()
                    true
                } else if (!isSearchingLocally) {
                    when (event.event.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            selectedTabIdx = (selectedTabIdx - 1 + tabs.size) % tabs.size
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            selectedTabIdx = (selectedTabIdx + 1) % tabs.size
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            // Only handle if not already handled by axis in LaunchedEffect
                            if (abs(currentHatX) < 0.1f) {
                                if (isHeaderFocused) {
                                    val maxIdx = if (focusedItem != null) 2 else 1
                                    headerFocusIndex = (headerFocusIndex - 1 + (maxIdx + 1)) % (maxIdx + 1)
                                    true
                                } else if (isLibraryOrCustom) {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                        true
                                    } else false
                                } else {
                                    val newIdx = (focusedGridIndex - 1).coerceAtLeast(0)
                                    if (newIdx != focusedGridIndex) {
                                        focusedGridIndex = newIdx
                                        coroutineScope.launch { gridState.animateScrollToItem(newIdx) }
                                        true
                                    } else false
                                }
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (abs(currentHatX) < 0.1f) {
                                if (isHeaderFocused) {
                                    val maxIdx = if (focusedItem != null) 2 else 1
                                    headerFocusIndex = (headerFocusIndex + 1) % (maxIdx + 1)
                                    true
                                } else if (isLibraryOrCustom) {
                                    if (pagerState.currentPage < pagerCount - 1) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        true
                                    } else false
                                } else {
                                    val newIdx = (focusedGridIndex + 1).coerceAtMost(tabItems.size - 1)
                                    if (newIdx != focusedGridIndex) {
                                        focusedGridIndex = newIdx
                                        coroutineScope.launch { gridState.animateScrollToItem(newIdx) }
                                        true
                                    } else false
                                }
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (abs(currentHatY) < 0.1f) {
                                if (!isHeaderFocused) {
                                    if (isLibraryOrCustom) {
                                        isHeaderFocused = true
                                        headerFocusIndex = 0
                                        true
                                    } else {
                                        val newIdx = focusedGridIndex - 4
                                        if (newIdx >= 0) {
                                            focusedGridIndex = newIdx
                                            coroutineScope.launch { gridState.animateScrollToItem(newIdx) }
                                            true
                                        } else {
                                            isHeaderFocused = true
                                            headerFocusIndex = 0
                                            true
                                        }
                                    }
                                } else false
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (abs(currentHatY) < 0.1f) {
                                if (isHeaderFocused) {
                                    isHeaderFocused = false
                                    true
                                } else if (!isLibraryOrCustom) {
                                    val newIdx = (focusedGridIndex + 4).coerceAtMost(tabItems.size - 1)
                                    if (newIdx != focusedGridIndex) {
                                        focusedGridIndex = newIdx
                                        coroutineScope.launch { gridState.animateScrollToItem(newIdx) }
                                        true
                                    } else false
                                } else false
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X -> {
                            if (isHeaderFocused) {
                                when (headerFocusIndex) {
                                    0 -> { // Search
                                        isSearchingLocally = true
                                        coroutineScope.launch {
                                            delay(100)
                                            searchFocusRequester.requestFocus()
                                            // Make sure the keyboard appears
                                            try {
                                                android.app.Instrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                                            } catch (e: Exception) {}
                                        }
                                    }
                                    1 -> { // Settings
                                        onNavigateRoute("settings")
                                    }
                                    2 -> { // GameMenu
                                        focusedItem?.let { onEdit(it) }
                                    }
                                }
                                true
                            } else {
                                val isAuth = checkAuthStatus(context, tabs[selectedTabIdx])
                                if (!isAuth) {
                                    if (event.event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                                        onNavigateRoute("login")
                                        true
                                    } else false
                                } else {
                                    if (event.event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                                        focusedItem?.let { handleGameClick(it) }
                                        true
                                    } else {
                                        focusedItem?.let { onEdit(it) }
                                        true
                                    }
                                }
                            }
                        }
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            focusedItem?.let { onEdit(it) }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            onViewChanged(PaneType.LIST)
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            if (!rightTriggerPressed) {
                                onRefresh()
                                rightTriggerPressed = true // Prevents double fire if axis also reports
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            } else false
        }

        val motionListener: (AndroidEvent.MotionEvent) -> Boolean = { motionEvent ->
            if (isAnyDialogOpen || installDialogItem != null) false
            else if (!isSearchingLocally) {
                val event = motionEvent.event
                if (event is android.view.MotionEvent) {
                    // Left Stick
                    leftStickX = event.getAxisValue(android.view.MotionEvent.AXIS_X)
                    leftStickY = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
                    
                    // Right Stick (with fallbacks for generic controllers)
                    val rz = event.getAxisValue(android.view.MotionEvent.AXIS_RZ)
                    val z = event.getAxisValue(android.view.MotionEvent.AXIS_Z)
                    val rx = event.getAxisValue(android.view.MotionEvent.AXIS_RX)
                    val ry = event.getAxisValue(android.view.MotionEvent.AXIS_RY)
                    
                    // Priority for Right Stick: RZ/Z (Standard) -> RY/RX (Fallback)
                    rightStickX = if (abs(z) > 0.01f) z else rx
                    rightStickY = if (abs(rz) > 0.01f) rz else ry
                    
                    // Hat axes (D-Pad)
                    hatX = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
                    hatY = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)

                    // Refresh Trigger detection (R2 axis)
                    val rTrigger = event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER)
                    val gas = event.getAxisValue(android.view.MotionEvent.AXIS_GAS)
                    val triggerVal = maxOf(rTrigger, gas)
                    
                    if (triggerVal > 0.7f) {
                        if (!rightTriggerPressed) {
                            rightTriggerPressed = true
                            onRefresh()
                        }
                    } else if (triggerVal < 0.2f) {
                        rightTriggerPressed = false
                    }
                    
                    true
                } else false
            } else false
        }


        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(keyListener)
        PluviaApp.events.on<AndroidEvent.MotionEvent, Boolean>(motionListener)

        onDispose {
            PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(keyListener)
            PluviaApp.events.off<AndroidEvent.MotionEvent, Boolean>(motionListener)
        }
    }

    LaunchedEffect(selectedTabIdx) {
        pagerState.scrollToPage(0)
        focusedGridIndex = 0
        rightStickX = 0f
        rightStickY = 0f
        isHeaderFocused = false
        storefrontHeaderOffsetY = 0f // Reset header visibility on tab switch
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        AnimatedWavyBackground(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        if (focusedItem != null) {
            val artUrl = rememberFrontendArtUrl(focusedItem, isHero = true)
            if (artUrl.isNotEmpty()) {
                val bgModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.fillMaxSize().blur(40.dp)
                } else Modifier.fillMaxSize()
                
                CoilImage(
                    modifier = bgModifier,
                    imageModel = { artUrl },
                    imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // HEADER — slides up/down on storefront tabs when scrolling
        val isLibraryTabHeader = tabs[selectedTabIdx] == FrontendTab.LIBRARY
        val isStorefrontTab = !isLibraryTabHeader && !isCustomTab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .graphicsLayer {
                    if (isStorefrontTab) {
                        translationY = storefrontHeaderOffsetY
                        alpha = ((storefrontHeaderOffsetY + 200f) / 200f).coerceIn(0f, 1f)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top Left Source Icon
            Box(modifier = Modifier.width(110.dp)) {
                focusedItem?.let { item ->
                    val iconModifier = Modifier.size(32.dp).alpha(0.8f)
                    when (item.gameSource) {
                        GameSource.STEAM -> Icon(imageVector = Icons.Filled.Steam, contentDescription = "Steam", tint = Color.White, modifier = iconModifier)
                        GameSource.EPIC -> Icon(painterResource(R.drawable.ic_epic), "Epic", tint = Color.White, modifier = iconModifier)
                        GameSource.GOG -> Icon(painterResource(R.drawable.ic_gog), "GOG", tint = Color.White, modifier = iconModifier)
                        GameSource.AMAZON -> Icon(imageVector = Icons.Filled.Amazon, contentDescription = "Amazon", tint = Color.White, modifier = iconModifier)
                        GameSource.CUSTOM_GAME -> Icon(imageVector = Icons.Filled.CustomGame, contentDescription = "Custom", tint = Color.White, modifier = iconModifier)
                    }
                }
            }

            // Centered Box for Tabs + Search
            Box(modifier = Modifier.weight(1f).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Search Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = isSearchingLocally,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(40.dp)
                                    .padding(end = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    BasicTextField(
                                        value = state.searchQuery,
                                        onValueChange = { onSearchQuery(it) },
                                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                        cursorBrush = SolidColor(Color.White),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(searchFocusRequester),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                        decorationBox = { innerTextField ->
                                            if (state.searchQuery.isEmpty()) {
                                                Text("Search games...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                                            }
                                            innerTextField()
                                        }
                                    )
                                    IconButton(
                                        onClick = { 
                                            isSearchingLocally = false
                                            onSearchQuery("")
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (!isSearchingLocally) {
                            val isSearchFocused = isHeaderFocused && headerFocusIndex == 0
                            IconButton(
                                onClick = { 
                                    isHeaderFocused = true
                                    headerFocusIndex = 0
                                    isSearchingLocally = true
                                    coroutineScope.launch {
                                        delay(100)
                                        searchFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .then(
                                        if (isSearchFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                            ) {
                                Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // TABS with floating badges
                    Box(contentAlignment = Alignment.TopCenter) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedTabIdx,
                            containerColor = Color.Transparent,
                            divider = {},
                            edgePadding = 0.dp,
                            indicator = { tabPositions ->
                                if (selectedTabIdx < tabPositions.size) {
                                    TabRowDefaults.PrimaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIdx]),
                                        color = MaterialTheme.colorScheme.primary,
                                        height = 3.dp
                                    )
                                }
                            }
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                Tab(
                                    selected = selectedTabIdx == index,
                                    onClick = { selectedTabIdx = index },
                                    text = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 16.sp,
                                            fontWeight = if (selectedTabIdx == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedTabIdx == index) Color.White else Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                )
                            }
                        }

                        if (connectedControllerType != ControllerType.NONE) {
                            // Float badges above the tabs
                            Box(modifier = Modifier.matchParentSize().padding(horizontal = 8.dp)) {
                                ControllerBadge(
                                    button = "L1",
                                    controllerType = connectedControllerType,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(y = (-18).dp)
                                )
                                ControllerBadge(
                                    button = "R1",
                                    controllerType = connectedControllerType,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(y = (-18).dp)
                                )
                            }
                        }
                    }
                    
                    // Spacer to offset the search button on the left for perfect tab centering
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Top Right Icons
            Column(
                modifier = Modifier.width(110.dp), 
                horizontalAlignment = Alignment.End
            ) {
                val isSettingsFocused = isHeaderFocused && headerFocusIndex == 1
                Surface(
                    onClick = { 
                        isHeaderFocused = true
                        headerFocusIndex = 1
                        onNavigateRoute("settings") 
                    },
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                        .then(
                            if (isSettingsFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                if (focusedItem != null) {
                    val isGameMenuFocused = isHeaderFocused && headerFocusIndex == 2
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        onClick = { 
                            isHeaderFocused = true
                            headerFocusIndex = 2
                            onEdit(focusedItem) 
                        },
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                            .then(
                                if (isGameMenuFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SettingsSuggest, "Game Menu", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        // MAIN CONTENT: Library tab uses HorizontalPager, Storefront tabs use 4-column grid
        val isLibraryTab = tabs[selectedTabIdx] == FrontendTab.LIBRARY
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp

        if (isLibraryTab || isCustomTab) {
            // Library / Custom: Horizontal pager carousel
            val pageWidth = 150.dp
            val horizontalPadding = (screenWidth - pageWidth) / 2

            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                modifier = Modifier.fillMaxWidth().height(220.dp).align(Alignment.Center),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                pageSpacing = 0.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val isFocused = page == pagerState.currentPage
                val scale by animateFloatAsState(
                    targetValue = if (isFocused) 1.15f else 0.85f,
                    animationSpec = tween(300),
                    label = "cardScale"
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCustomTab && page == tabItems.size) {
                        Box(
                            modifier = Modifier
                                .scale(scale)
                                .width(130.dp)
                                .height(150.dp)
                                .shadow(if (isFocused) 16.dp else 4.dp, RoundedCornerShape(16.dp), spotColor = Color.Black)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(16.dp))
                                .clickable { onAddCustomGame() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                                Text("Add Game", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else if (page < tabItems.size) {
                        val item = tabItems[page]
                        val artUrl = rememberFrontendArtUrl(item, isHero = false)

                        Box(
                            modifier = Modifier
                                .scale(scale)
                                .width(130.dp)
                                .height(150.dp)
                                .shadow(if (isFocused) 24.dp else 8.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { handleGameClick(item) }
                                .border(
                                    if (isFocused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                    else BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            if (artUrl.isNotEmpty()) {
                                CoilImage(
                                    modifier = Modifier.fillMaxSize(),
                                    imageModel = { artUrl },
                                    imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                                    failure = { FrontendFallbackIcon(item, bgColor = Color.DarkGray, letterSize = 24.sp) }
                                )
                            } else {
                                FrontendFallbackIcon(item, bgColor = Color.DarkGray, letterSize = 24.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    item.name,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
                } else {
                    // Storefront tabs (Steam, Epic, GOG, Amazon): 4-column vertical grid
                    // gridState is hoisted above for controller access
        
                    val isAuth = checkAuthStatus(context, tabs[selectedTabIdx])
        
                    if (!isAuth) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Not Signed In",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { onNavigateRoute("login") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.width(200.dp).height(50.dp)
                                ) {
                                    Text("Sign In", fontWeight = FontWeight.Bold)
                                }
                                
                                if (connectedControllerType != ControllerType.NONE) {
                                    Spacer(Modifier.height(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ControllerBadge("A", connectedControllerType)
                                        Text("to Sign In", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        // Track scroll direction for header auto-hide
                        var headerVisible by remember { mutableStateOf(true) }
                        var previousScrollOffset by remember { mutableIntStateOf(0) }
                        var previousFirstVisibleItem by remember { mutableIntStateOf(0) }
        
                        LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
                            val currentFirst = gridState.firstVisibleItemIndex
                            val currentOffset = gridState.firstVisibleItemScrollOffset
                            val scrollingDown = currentFirst > previousFirstVisibleItem ||
                                (currentFirst == previousFirstVisibleItem && currentOffset > previousScrollOffset + 10)
                            val scrollingUp = currentFirst < previousFirstVisibleItem ||
                                (currentFirst == previousFirstVisibleItem && currentOffset < previousScrollOffset - 10)
                            if (scrollingDown && headerVisible) headerVisible = false
                            if (scrollingUp && !headerVisible) headerVisible = true
                            if (currentFirst == 0 && currentOffset == 0) headerVisible = true
                            previousFirstVisibleItem = currentFirst
                            previousScrollOffset = currentOffset
                        }
        
                        // Animate header offset
                        val headerOffsetY by animateFloatAsState(
                            targetValue = if (headerVisible) 0f else -200f,
                            animationSpec = tween(250),
                            label = "headerOffset"
                        )
        
                        // Pull-to-refresh wrapping the storefront grid
                        val pullToRefreshState = rememberPullToRefreshState()
        
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = onRefresh,
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 80.dp, bottom = 24.dp)
                            ) {
                                items(count = tabItems.size, key = { tabItems[it].appId }) { index ->
                                    val item = tabItems[index]
                                    val isFocused = index == focusedGridIndex
                                    val artUrl = rememberFrontendArtUrl(item, isHero = false)
                                    val focusScale by animateFloatAsState(
                                        targetValue = if (isFocused) 1.04f else 1f,
                                        animationSpec = tween(200),
                                        label = "gridFocusScale"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1.25f)
                                            .scale(focusScale)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { handleGameClick(item) }
                                            .border(
                                                if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                                else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                                RoundedCornerShape(10.dp)
                                            )
                                    ) {
                                        if (artUrl.isNotEmpty()) {
                                            CoilImage(
                                                modifier = Modifier.fillMaxSize(),
                                                imageModel = { artUrl },
                                                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                                                failure = { FrontendFallbackIcon(item) }
                                            )
                                        } else {
                                            FrontendFallbackIcon(item)
                                        }
        
                                        // Gradient overlay with game name
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                item.name,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp
                                            )
                                        }
        
                                        // Install status indicator
                                        if (!item.isInstalled) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(7.dp)
                                                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
        
                        // Re-show header state for the HEADER Row above (we'll use graphicsLayer)
                        // Store headerOffsetY in a key that the header can read
                        SideEffect {
                            storefrontHeaderOffsetY = headerOffsetY
                        }
                    }
                }
        // FOOTER — only show on Library/Custom tabs (storefront tabs have no play time)
        if (focusedItem != null && (isLibraryTab || isCustomTab)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (connectedControllerType == ControllerType.NONE) {
                    Text(
                        focusedItem.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                val totalHours = focusedItem.playTime / 60
                val totalMinutes = focusedItem.playTime % 60
                val lastHours = focusedItem.lastSessionTime / 60
                val lastMinutes = focusedItem.lastSessionTime % 60
                
                val playtimeText = "${if (lastHours > 0) "${lastHours}h " else ""}${lastMinutes}m Last / " +
                                 "${if (totalHours > 0) "${totalHours}h " else ""}${totalMinutes}m Total Played Time"
                
                Text(
                    text = playtimeText,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (connectedControllerType != ControllerType.NONE) {
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ControllerBadge("A", connectedControllerType)
                            Text("Play", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ControllerBadge("Y", connectedControllerType)
                            Text("Game Menu", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ControllerBadge("B", connectedControllerType)
                            Text("Back to List", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }

        // Install dialog for uninstalled games on storefront tabs
        if (installDialogItem != null) {
            FrontendInstallDialog(
                item = installDialogItem!!,
                controllerType = connectedControllerType,
                onInstall = {
                    val item = installDialogItem!!
                    installDialogItem = null
                    handleInstallClick(item)
                },
                onCustomPath = {
                    frontendFolderPicker.launchPicker()
                },
                onDismiss = { installDialogItem = null }
            )
        }

        // Download progress dialog
        if (downloadingDialogItem != null) {
            FrontendDownloadDialog(
                item = downloadingDialogItem!!,
                controllerType = connectedControllerType,
                onPlay = {
                    val item = downloadingDialogItem!!
                    downloadingDialogItem = null
                    if (item.isInstalled) {
                        onClickPlay(item.appId, false)
                    } else {
                        handleInstallClick(item)
                    }
                },
                onDismiss = { downloadingDialogItem = null }
            )
        }
    }
}
