package app.gamenative.ui.screen.library.components

import android.net.Uri
import android.os.Build
import android.view.KeyEvent
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
import kotlinx.coroutines.launch
import java.io.File

private enum class FrontendTab(val label: String) {
    LIBRARY("Library"),
    STEAM("Steam"),
    EPIC("Epic"),
    GOG("GOG"),
    AMAZON("Amazon"),
    CUSTOM("Custom"),
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
) {
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    val tabs = FrontendTab.entries
    val coroutineScope = rememberCoroutineScope()
    var isSearchingLocally by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val tabItems: List<LibraryItem> = remember(selectedTabIdx, state.appInfoList, state.searchQuery) {
        if (state.searchQuery.isNotEmpty()) {
            // When searching, show all matches regardless of source/installed status
            state.appInfoList
        } else {
            when (tabs[selectedTabIdx]) {
                FrontendTab.LIBRARY -> state.appInfoList
                    .filter { it.isInstalled }
                    .sortedByDescending { it.lastPlayed }
                FrontendTab.STEAM -> state.appInfoList
                    .filter { it.gameSource == GameSource.STEAM }
                    .sortedBy { it.name.lowercase() }
                FrontendTab.EPIC -> state.appInfoList
                    .filter { it.gameSource == GameSource.EPIC }
                    .sortedBy { it.name.lowercase() }
                FrontendTab.GOG -> state.appInfoList
                    .filter { it.gameSource == GameSource.GOG }
                    .sortedBy { it.name.lowercase() }
                FrontendTab.AMAZON -> state.appInfoList
                    .filter { it.gameSource == GameSource.AMAZON }
                    .sortedBy { it.name.lowercase() }
                FrontendTab.CUSTOM -> state.appInfoList
                    .filter { it.gameSource == GameSource.CUSTOM_GAME }
                    .sortedBy { it.name.lowercase() }
            }
        }
    }

    val isCustomTab = tabs[selectedTabIdx] == FrontendTab.CUSTOM
    val pagerCount = if (isCustomTab) tabItems.size + 1 else tabItems.size
    val pagerState = rememberPagerState(pageCount = { pagerCount })

    val focusedItem: LibraryItem? = remember(pagerState.currentPage, tabItems) {
        if (pagerState.currentPage < tabItems.size) tabItems[pagerState.currentPage] else null
    }

    // Controller Input Handling
    DisposableEffect(focusedItem, selectedTabIdx, pagerCount, isSearchingLocally) {
        val keyListener: (AndroidEvent.KeyEvent) -> Boolean = { event ->
            if (event.event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (isSearchingLocally && event.event.keyCode == KeyEvent.KEYCODE_BACK) {
                    isSearchingLocally = false
                    onSearchQuery("")
                    true
                } else if (!isSearchingLocally) {
                    when (event.event.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L2 -> {
                            selectedTabIdx = (selectedTabIdx - 1 + tabs.size) % tabs.size
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            selectedTabIdx = (selectedTabIdx + 1) % tabs.size
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (pagerState.currentPage < pagerCount - 1) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_A -> { // X (PS) / A (Xbox)
                            focusedItem?.let { onClickPlay(it.appId, false) }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_Y -> { // Triangle (PS) / Y (Xbox)
                            onNavigateRoute("settings")
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_B -> { // Circle (PS) / B (Xbox)
                            onViewChanged(PaneType.LIST)
                            true
                        }
                        else -> false
                    }
                } else false
            } else false
        }

        val motionListener: (AndroidEvent.MotionEvent) -> Boolean = { motionEvent ->
            if (!isSearchingLocally) {
                val event = motionEvent.event
                if (event is android.view.MotionEvent) {
                    val axisX = event.getAxisValue(android.view.MotionEvent.AXIS_X)
                    if (axisX < -0.5f) {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            true
                        } else false
                    } else if (axisX > 0.5f) {
                        if (pagerState.currentPage < pagerCount - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            true
                        } else false
                    } else false
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

        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .align(Alignment.TopCenter)
                .zIndex(2f),
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
                            IconButton(
                                onClick = { 
                                    isSearchingLocally = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(100)
                                        searchFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // TABS
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
                    
                    // Spacer to offset the search button on the left for perfect tab centering
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Top Right Icons
            Column(
                modifier = Modifier.width(110.dp), 
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    onClick = { onNavigateRoute("settings") },
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                if (focusedItem != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        onClick = { onEdit(focusedItem) },
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SettingsSuggest, "Game Menu", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        // MAIN CONTENT: Pager
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
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
                            .clickable { onClickPlay(item.appId, false) }
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
                                failure = {
                                    Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                        Text(item.name.take(1), fontSize = 24.sp, color = Color.White)
                                    }
                                }
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                Text(item.name.take(1), fontSize = 24.sp, color = Color.White)
                            }
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

        // FOOTER
        if (focusedItem != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    focusedItem.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
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
            }
        }
    }
}
