package app.gamenative.ui.screen.library.components

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face4
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.GameCompatibilityStatus
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.service.DownloadService
import app.gamenative.service.SteamService
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGService
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.icons.Amazon
import app.gamenative.ui.icons.Steam
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.ListItemImage
import app.gamenative.utils.CustomGameScanner
import java.io.File
import timber.log.Timber

@Composable
internal fun AppItem(
    modifier: Modifier = Modifier,
    appInfo: LibraryItem,
    onClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    paneType: PaneType = PaneType.LIST,
    onFocus: () -> Unit = {},
    isRefreshing: Boolean = false,
    imageRefreshCounter: Long = 0L,
    compatibilityStatus: GameCompatibilityStatus? = null,
) {
    val context = LocalContext.current
    var hideText by remember { mutableStateOf(true) }
    var alpha by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(paneType) {
        hideText = true
        alpha = 1f
    }

    LaunchedEffect(imageRefreshCounter) {
        if (paneType != PaneType.LIST) {
            hideText = true
            alpha = 1f
        }
    }

    var isFocused by remember { mutableStateOf(false) }

    val border = if (isFocused) {
        androidx.compose.foundation.BorderStroke(
            width = 3.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            ),
        )
    } else {
        androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (isFocused) {
                    onFocus()
                }
            }
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        border = border,
    ) {
        var isInstalled by remember(appInfo.appId, appInfo.gameSource) { mutableStateOf(false) }
        val isSteam = appInfo.gameSource == GameSource.STEAM
        val downloadInfo = remember(appInfo.appId) { if (isSteam) SteamService.getAppDownloadInfo(appInfo.gameId) else null }
        var downloadProgress by remember(downloadInfo) { mutableFloatStateOf(downloadInfo?.getProgress() ?: 0f) }
        val isDownloading = downloadInfo != null && downloadProgress < 1f

        LaunchedEffect(appInfo.appId, appInfo.gameSource, isRefreshing) {
            isInstalled = when (appInfo.gameSource) {
                GameSource.STEAM -> SteamService.isAppInstalled(appInfo.gameId)
                GameSource.GOG -> GOGService.isGameInstalled(appInfo.gameId.toString())
                GameSource.EPIC -> EpicService.isGameInstalled(appInfo.gameId)
                GameSource.CUSTOM_GAME -> true
                GameSource.AMAZON -> app.gamenative.service.amazon.AmazonService.isGameInstalled(appInfo.appId.removePrefix("AMAZON_"))
                else -> false
            }
        }

        DisposableEffect(downloadInfo) {
            val onDownloadProgress: (Float) -> Unit = { progress -> downloadProgress = progress }
            downloadInfo?.addProgressListener(onDownloadProgress)
            onDispose { downloadInfo?.removeProgressListener(onDownloadProgress) }
        }

        val hPadding = if (paneType == PaneType.LIST) 16.dp else 0.dp
        val topPadding = if (paneType == PaneType.LIST) 8.dp else 0.dp
        val bottomPadding = if (paneType == PaneType.LIST) 2.dp else 0.dp
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = hPadding, end = hPadding, top = topPadding, bottom = bottomPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                ) {
                    if (paneType == PaneType.LIST) {
                        val iconUrl = remember(appInfo.appId, imageRefreshCounter) {
                            if (appInfo.gameSource == GameSource.CUSTOM_GAME) {
                                val path = CustomGameScanner.findIconFileForCustomGame(context, appInfo.appId)
                                if (!path.isNullOrEmpty()) {
                                    val file = File(path)
                                    val ts = if (file.exists()) file.lastModified() else 0L
                                    "file://$path?t=$ts"
                                } else {
                                    appInfo.clientIconUrl
                                }
                            } else {
                                appInfo.clientIconUrl
                            }
                        }
                        ListItemImage(
                            modifier = Modifier.size(56.dp),
                            imageModifier = Modifier.clip(RoundedCornerShape(10.dp)),
                            image = { iconUrl },
                        )
                    } else {
                        val aspectRatio = if (paneType == PaneType.GRID_CAPSULE) 2 / 3f else 460 / 215f

                        fun findSteamGridDBImage(imageType: String): String? {
                            if (appInfo.gameSource == GameSource.CUSTOM_GAME) {
                                val gameFolderPath = CustomGameScanner.getFolderPathFromAppId(appInfo.appId)
                                gameFolderPath?.let { path ->
                                    val folder = java.io.File(path)
                                    val imageFile = folder.listFiles()?.firstOrNull { file ->
                                        file.name.startsWith("steamgriddb_$imageType") &&
                                            (file.name.endsWith(".png", ignoreCase = true) ||
                                                file.name.endsWith(".jpg", ignoreCase = true) ||
                                                file.name.endsWith(".webp", ignoreCase = true))
                                    }
                                    return imageFile?.let { android.net.Uri.fromFile(it).toString() }
                                }
                            }
                            return null
                        }

                        val imageUrl = remember(appInfo.appId, paneType, imageRefreshCounter) {
                            when (appInfo.gameSource) {
                                GameSource.CUSTOM_GAME -> {
                                    when (paneType) {
                                        PaneType.GRID_CAPSULE -> {
                                            findSteamGridDBImage("grid_capsule")
                                                ?: CustomGameScanner.findIconFileForCustomGame(context, appInfo.appId)?.let {
                                                    val file = File(it)
                                                    val ts = if (file.exists()) file.lastModified() else 0L
                                                    "file://$it?t=$ts"
                                                }
                                                ?: "https://shared.steamstatic.com/store_item_assets/steam/apps/${appInfo.gameId}/library_600x900.jpg"
                                        }
                                        PaneType.GRID_HERO -> {
                                            findSteamGridDBImage("grid_hero")
                                                ?: CustomGameScanner.findIconFileForCustomGame(context, appInfo.appId)?.let {
                                                    val file = File(it)
                                                    val ts = if (file.exists()) file.lastModified() else 0L
                                                    "file://$it?t=$ts"
                                                }
                                                ?: "https://shared.steamstatic.com/store_item_assets/steam/apps/${appInfo.gameId}/header.jpg"
                                        }
                                        else -> {
                                            val gameFolderPath = CustomGameScanner.getFolderPathFromAppId(appInfo.appId)
                                            val heroUrl = gameFolderPath?.let { path ->
                                                val folder = java.io.File(path)
                                                val heroFile = folder.listFiles()?.firstOrNull { file ->
                                                    val fileName = file.name
                                                    fileName.startsWith("steamgriddb_hero") &&
                                                        !fileName.contains("grid") &&
                                                        (fileName.endsWith(".png", ignoreCase = true) ||
                                                            fileName.endsWith(".jpg", ignoreCase = true) ||
                                                            fileName.endsWith(".webp", ignoreCase = true))
                                                }
                                                heroFile?.let { android.net.Uri.fromFile(it).toString() }
                                            }
                                            heroUrl
                                                ?: CustomGameScanner.findIconFileForCustomGame(context, appInfo.appId)?.let {
                                                    val file = File(it)
                                                    val ts = if (file.exists()) file.lastModified() else 0L
                                                    "file://$it?t=$ts"
                                                }
                                                ?: "https://shared.steamstatic.com/store_item_assets/steam/apps/${appInfo.gameId}/header.jpg"
                                        }
                                    }
                                }
                                GameSource.GOG -> appInfo.iconHash
                                GameSource.EPIC -> appInfo.iconHash
                                GameSource.AMAZON -> appInfo.iconHash
                                GameSource.STEAM -> {
                                    if (paneType == PaneType.GRID_CAPSULE) {
                                        "https://shared.steamstatic.com/store_item_assets/steam/apps/${appInfo.gameId}/library_600x900.jpg"
                                    } else {
                                        "https://shared.steamstatic.com/store_item_assets/steam/apps/${appInfo.gameId}/header.jpg"
                                    }
                                }
                            }
                        }

                        LaunchedEffect(imageUrl) {
                            if (paneType != PaneType.LIST) {
                                hideText = true
                                alpha = 1f
                            }
                        }

                        Box {
                            ListItemImage(
                                modifier = Modifier.aspectRatio(aspectRatio),
                                imageModifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .alpha(alpha),
                                image = { imageUrl },
                                onFailure = {
                                    hideText = false
                                    alpha = 0.1f
                                },
                            )

                            compatibilityStatus?.let { status ->
                                val (text, color) = when (status) {
                                    GameCompatibilityStatus.COMPATIBLE -> stringResource(R.string.library_compatible) to Color.Green
                                    GameCompatibilityStatus.GPU_COMPATIBLE -> stringResource(R.string.library_compatible) to Color.Green
                                    GameCompatibilityStatus.UNKNOWN -> stringResource(R.string.library_compatibility_unknown) to Color.Gray
                                    GameCompatibilityStatus.NOT_COMPATIBLE -> stringResource(R.string.library_not_compatible) to Color.Red
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                                    ) {
                                        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        GameSourceIcon(appInfo.gameSource)
                                    }
                                }
                            }
                        }

                        if (!hideText) {
                            GameInfoBlock(
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                appInfo = appInfo,
                                isRefreshing = isRefreshing,
                                compatibilityStatus = compatibilityStatus,
                            )
                        } else {
                            // We use isInstalled calculated above
                            LaunchedEffect(isRefreshing) {
                                if (!isRefreshing) {
                                    isInstalled = when (appInfo.gameSource) {
                                        GameSource.STEAM -> SteamService.isAppInstalled(appInfo.gameId)
                                        GameSource.GOG -> GOGService.isGameInstalled(appInfo.gameId.toString())
                                        GameSource.EPIC -> EpicService.isGameInstalled(appInfo.gameId)
                                        GameSource.CUSTOM_GAME -> true
                                        GameSource.AMAZON -> app.gamenative.service.amazon.AmazonService.isGameInstalled(appInfo.appId.removePrefix("AMAZON_"))
                                        else -> false
                                    }
                                }
                            }

                            val hasIcons = isInstalled || appInfo.isShared
                            val iconWidth = when {
                                isInstalled && appInfo.isShared -> 44.dp
                                hasIcons -> 22.dp
                                else -> 0.dp
                            }

                            Box(
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = appInfo.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(end = iconWidth),
                                )

                                if (hasIcons) {
                                    Row(
                                        modifier = Modifier.align(alignment = Alignment.CenterEnd),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isInstalled) {
                                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.library_installed), tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        if (appInfo.isShared) {
                                            Icon(Icons.Filled.Face4, contentDescription = stringResource(R.string.library_family_shared), tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (paneType == PaneType.LIST) {
                    GameInfoBlock(
                        modifier = Modifier.weight(1f),
                        appInfo = appInfo,
                        isRefreshing = isRefreshing,
                        compatibilityStatus = compatibilityStatus,
                        isListView = true,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Button(
                            onClick = onPlayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp).width(100.dp),
                        ) {
                            val playText = if (isInstalled) stringResource(R.string.run_app) else stringResource(R.string.install)
                            Text(text = playText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        val editEnabled = isInstalled || isDownloading
                        Button(
                            onClick = onEditClick,
                            enabled = editEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp).width(100.dp),
                        ) {
                            Text(text = stringResource(R.string.edit), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            if (paneType == PaneType.LIST && isInstalled) {
                // Formatting for accurate last/total playtime
                val totalHours = appInfo.playTime / 60
                val totalMinutes = appInfo.playTime % 60
                val totalString = "${totalHours.toString().padStart(2, '0')}:${totalMinutes.toString().padStart(2, '0')}"
                
                val lastHours = appInfo.lastSessionTime / 60
                val lastMinutes = appInfo.lastSessionTime % 60
                val lastString = "${lastHours.toString().padStart(2, '0')}:${lastMinutes.toString().padStart(2, '0')}"

                Text(
                    text = "( $lastString Last / $totalString Total )",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 0.dp)
                )
            }
        }
    }
}

@Composable
internal fun GameInfoBlock(
    modifier: Modifier,
    appInfo: LibraryItem,
    isRefreshing: Boolean = false,
    compatibilityStatus: GameCompatibilityStatus? = null,
    isListView: Boolean = false,
) {
    val isSteam = appInfo.gameSource == GameSource.STEAM
    val downloadInfo = remember(appInfo.appId) { if (isSteam) SteamService.getAppDownloadInfo(appInfo.gameId) else null }
    var downloadProgress by remember(downloadInfo) { mutableFloatStateOf(downloadInfo?.getProgress() ?: 0f) }
    val isDownloading = downloadInfo != null && downloadProgress < 1f
    var isInstalledSteam by remember(appInfo.appId) { mutableStateOf(if (isSteam) SteamService.isAppInstalled(appInfo.gameId) else false) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && isSteam) {
            isInstalledSteam = SteamService.isAppInstalled(appInfo.gameId)
        }
    }

    LaunchedEffect(appInfo.appId, downloadInfo, isRefreshing) {
        if (downloadInfo != null) {
            downloadProgress = downloadInfo.getProgress() ?: 0f
        }
    }

    DisposableEffect(downloadInfo) {
        val onDownloadProgress: (Float) -> Unit = { progress -> downloadProgress = progress }
        downloadInfo?.addProgressListener(onDownloadProgress)
        onDispose { downloadInfo?.removeProgressListener(onDownloadProgress) }
    }

    var appSizeOnDisk by remember { mutableStateOf("") }

    LaunchedEffect(isSteam, isInstalledSteam) {
        if (isSteam && isInstalledSteam) {
            appSizeOnDisk = "..."
            DownloadService.getSizeOnDiskDisplay(appInfo.gameId) { appSizeOnDisk = it }
        }
    }

    Column(modifier = modifier) {
        val displayName = if (isListView && appInfo.name.length > 9) appInfo.name.take(9) + "..." else appInfo.name
        val textStyle = if (isListView) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

        Text(text = displayName, style = textStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = if (isListView) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        
        Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val (statusText, statusColor) = when (appInfo.gameSource) {
                GameSource.STEAM -> {
                    val text = when {
                        isDownloading -> stringResource(R.string.library_installing)
                        isInstalledSteam -> stringResource(R.string.library_installed)
                        else -> stringResource(R.string.library_not_installed)
                    }
                    val color = when {
                        isDownloading || isInstalledSteam -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                    text to color
                }
                GameSource.GOG, GameSource.EPIC -> {
                    val isInstalled = when (appInfo.gameSource) {
                        GameSource.GOG -> GOGService.isGameInstalled(appInfo.gameId.toString())
                        GameSource.EPIC -> EpicService.isGameInstalled(appInfo.gameId)
                        else -> false
                    }
                    val text = if (isInstalled) stringResource(R.string.library_installed) else stringResource(R.string.library_not_installed)
                    val color = if (isInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    text to color
                }
                GameSource.CUSTOM_GAME -> stringResource(R.string.library_status_ready) to MaterialTheme.colorScheme.tertiary
                else -> stringResource(R.string.library_not_installed) to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).background(color = statusColor, shape = CircleShape))
                Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                if (isDownloading) {
                    Text(text = "${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = statusColor)
                }
            }

            if (isSteam && isInstalledSteam) {
                Text(text = "$appSizeOnDisk", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (appInfo.isShared) {
                Text(text = stringResource(R.string.library_family_shared), style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.tertiary)
            }

            compatibilityStatus?.let { status ->
                val (text, color) = when (status) {
                    GameCompatibilityStatus.COMPATIBLE -> stringResource(R.string.library_compatible) to Color.Green
                    GameCompatibilityStatus.GPU_COMPATIBLE -> stringResource(R.string.library_compatible) to Color.Green
                    GameCompatibilityStatus.UNKNOWN -> stringResource(R.string.library_compatibility_unknown) to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    GameCompatibilityStatus.NOT_COMPATIBLE -> stringResource(R.string.library_not_compatible) to Color.Red
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = color)
                    GameSourceIcon(appInfo.gameSource)
                }
            }
        }
    }
}

@Composable
fun GameSourceIcon(gameSource: GameSource, modifier: Modifier = Modifier, iconSize: Int = 12) {
    when (gameSource) {
        GameSource.STEAM -> Icon(imageVector = Icons.Filled.Steam, contentDescription = "Steam", modifier = modifier.size(iconSize.dp).alpha(0.7f))
        GameSource.CUSTOM_GAME -> Icon(imageVector = Icons.Filled.Folder, contentDescription = "Custom Game", modifier = modifier.size(iconSize.dp).alpha(0.7f))
        GameSource.GOG -> Icon(painter = painterResource(R.drawable.ic_gog), contentDescription = "Gog", modifier = modifier.size(iconSize.dp).alpha(0.7f))
        GameSource.EPIC -> Icon(painter = painterResource(R.drawable.ic_epic), contentDescription = "Epic", modifier = modifier.size(iconSize.dp).alpha(0.7f))
        GameSource.AMAZON -> Icon(imageVector = Icons.Filled.Amazon, contentDescription = "Amazon", modifier = modifier.size(iconSize.dp).alpha(0.7f))
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview_AppItem() {
    PrefManager.init(LocalContext.current)
    PluviaTheme {
        Surface {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(
                    items = List(5) { idx ->
                        val item = fakeAppInfo(idx)
                        LibraryItem(idx, "${GameSource.STEAM.name}_${item.id}", item.name, item.iconHash, idx % 2 == 0, GameSource.STEAM)
                    },
                    itemContent = {
                        val status = when (it.index % 4) {
                            0 -> GameCompatibilityStatus.COMPATIBLE
                            1 -> GameCompatibilityStatus.GPU_COMPATIBLE
                            2 -> GameCompatibilityStatus.NOT_COMPATIBLE
                            else -> GameCompatibilityStatus.UNKNOWN
                        }
                        AppItem(appInfo = it, onClick = {}, compatibilityStatus = status)
                    },
                )
            }
        }
    }
}

@Preview(device = "spec:width=1920px,height=1080px,dpi=440")
@Composable
private fun Preview_AppItemGrid() {
    PrefManager.init(LocalContext.current)
    PluviaTheme {
        Surface {
            Column {
                val appInfoList = List(4) { idx ->
                    val item = fakeAppInfo(idx)
                    LibraryItem(idx, "${GameSource.STEAM.name}_${item.id}", item.name, item.iconHash, idx % 2 == 0, GameSource.CUSTOM_GAME)
                }
                LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 72.dp)) {
                    items(items = appInfoList, key = { it.index }) { item ->
                        val status = when (item.index % 4) {
                            0 -> GameCompatibilityStatus.COMPATIBLE
                            1 -> GameCompatibilityStatus.GPU_COMPATIBLE
                            2 -> GameCompatibilityStatus.NOT_COMPATIBLE
                            else -> GameCompatibilityStatus.UNKNOWN
                        }
                        AppItem(appInfo = item, onClick = { }, paneType = PaneType.GRID_HERO, compatibilityStatus = status)
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 72.dp)) {
                    items(items = appInfoList, key = { it.index }) { item ->
                        val status = when (item.index % 4) {
                            0 -> GameCompatibilityStatus.COMPATIBLE
                            1 -> GameCompatibilityStatus.GPU_COMPATIBLE
                            2 -> GameCompatibilityStatus.NOT_COMPATIBLE
                            else -> GameCompatibilityStatus.UNKNOWN
                        }
                        AppItem(appInfo = item, onClick = { }, paneType = PaneType.GRID_CAPSULE, compatibilityStatus = status)
                    }
                }
            }
        }
    }
}
