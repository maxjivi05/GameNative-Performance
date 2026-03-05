package app.gamenative.ui.component.dialog

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import com.winlator.contentdialog.NavigationDialog

@Composable
fun GameNavigationMenu(
    onDismiss: () -> Unit,
    areControlsVisible: Boolean,
    isGamePaused: Boolean,
    areJoysticksVisible: Boolean,
    onAction: (Int) -> Unit
) {
    var currentMenu by remember { mutableStateOf("main") }
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .padding(24.dp)
                    .clickable(enabled = false) {}, // Prevent clicks from dismissing when clicking inside
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val title = when (currentMenu) {
                        "main" -> stringResource(R.string.touchpad_help_main_menu_title)
                        "touch" -> stringResource(R.string.touch_controller)
                        "display" -> stringResource(R.string.container_config_tab_graphics)
                        "tools" -> stringResource(R.string.tools)
                        "controller" -> stringResource(R.string.controller)
                        else -> stringResource(R.string.touchpad_help_main_menu_title)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentMenu != "main") {
                            IconButton(
                                onClick = {
                                    currentMenu = "main"
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.button_back),
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    AnimatedContent(
                        targetState = currentMenu,
                        transitionSpec = {
                            if (targetState == "main") {
                                slideInHorizontally { -it } + fadeIn() togetherWith
                                        slideOutHorizontally { it } + fadeOut()
                            } else {
                                slideInHorizontally { it } + fadeIn() togetherWith
                                        slideOutHorizontally { -it } + fadeOut()
                            }
                        },
                        label = "MenuTransition"
                    ) { targetMenu ->
                        when (targetMenu) {
                            "main" -> MainMenuGrid(
                                isGamePaused = isGamePaused,
                                onAction = { action ->
                                    when (action) {
                                        -1 -> currentMenu = "touch"
                                        -2 -> currentMenu = "display"
                                        -3 -> currentMenu = "tools"
                                        -4 -> currentMenu = "controller"
                                        else -> {
                                            onAction(action)
                                            onDismiss()
                                        }
                                    }
                                },
                                focusRequester = focusRequester
                            )
                            "touch" -> TouchMenuGrid(
                                areControlsVisible = areControlsVisible,
                                areJoysticksVisible = areJoysticksVisible,
                                onAction = { action ->
                                    onAction(action)
                                    onDismiss()
                                },
                                focusRequester = focusRequester
                            )
                            "display" -> DisplayMenuGrid(
                                onAction = { action ->
                                    onAction(action)
                                    onDismiss()
                                },
                                focusRequester = focusRequester
                            )
                            "tools" -> ToolsMenuGrid(
                                onAction = { action ->
                                    onAction(action)
                                    onDismiss()
                                },
                                focusRequester = focusRequester
                            )
                            "controller" -> ControllerMenuGrid(
                                onAction = { action ->
                                    onAction(action)
                                    onDismiss()
                                },
                                focusRequester = focusRequester
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(currentMenu) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun MainMenuGrid(
    isGamePaused: Boolean,
    onAction: (Int) -> Unit,
    focusRequester: FocusRequester
) {
    val items = listOf(
        NavigationItem(
            id = NavigationDialog.ACTION_PAUSE_GAME,
            title = if (isGamePaused) stringResource(R.string.resume_game) else stringResource(R.string.pause_game),
            icon = if (isGamePaused) Icons.Default.PlayArrow else Icons.Default.Pause
        ),
        NavigationItem(
            id = -1, // Touch Controller sub-menu
            title = stringResource(R.string.touch_controller),
            icon = Icons.Default.TouchApp
        ),
        NavigationItem(
            id = -2, // Display sub-menu
            title = stringResource(R.string.container_config_tab_graphics),
            icon = Icons.Default.Monitor
        ),
        NavigationItem(
            id = -3, // Tools sub-menu
            title = stringResource(R.string.tools),
            icon = Icons.Default.Construction
        ),
        NavigationItem(
            id = -4, // Controller sub-menu
            title = stringResource(R.string.controller),
            icon = Icons.Default.SportsEsports
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_EXIT_GAME,
            title = stringResource(R.string.exit_game),
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            isDestructive = true
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            NavigationMenuItem(
                item = item,
                onClick = { onAction(item.id) },
                modifier = if (items.indexOf(item) == 0) Modifier.focusRequester(focusRequester) else Modifier
            )
        }
    }
}

@Composable
private fun TouchMenuGrid(
    areControlsVisible: Boolean,
    areJoysticksVisible: Boolean,
    onAction: (Int) -> Unit,
    focusRequester: FocusRequester
) {
    val items = listOf(
        NavigationItem(
            id = NavigationDialog.ACTION_INPUT_CONTROLS,
            title = if (areControlsVisible) stringResource(R.string.hide_controls) else stringResource(R.string.show_controls),
            icon = Icons.Default.Visibility
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_SHOW_JOYSTICKS,
            title = if (areJoysticksVisible) stringResource(R.string.hide_joysticks) else stringResource(R.string.show_joysticks),
            icon = Icons.Default.Gamepad
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_EDIT_CONTROLS,
            title = stringResource(R.string.edit_controls),
            icon = Icons.Default.Edit
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_TOUCH_TRANSPARENCY,
            title = stringResource(R.string.touch_transparency),
            icon = Icons.Default.Opacity
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            NavigationMenuItem(
                item = item,
                onClick = { onAction(item.id) },
                modifier = if (items.indexOf(item) == 0) Modifier.focusRequester(focusRequester) else Modifier
            )
        }
    }
}

@Composable
private fun DisplayMenuGrid(
    onAction: (Int) -> Unit,
    focusRequester: FocusRequester
) {
    val items = listOf(
        NavigationItem(
            id = NavigationDialog.ACTION_STRETCH_TO_FULLSCREEN,
            title = stringResource(R.string.stretch_to_fullscreen),
            icon = Icons.Default.AspectRatio
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_NATIVE_RENDERING,
            title = stringResource(R.string.native_rendering),
            icon = Icons.Default.ScreenshotMonitor
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_SCREEN_EFFECT,
            title = stringResource(R.string.screen_effect),
            icon = Icons.Default.AutoFixHigh
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_HUD,
            title = stringResource(R.string.hud),
            icon = Icons.Default.QueryStats
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            NavigationMenuItem(
                item = item,
                onClick = { onAction(item.id) },
                modifier = if (items.indexOf(item) == 0) Modifier.focusRequester(focusRequester) else Modifier
            )
        }
    }
}

@Composable
private fun ToolsMenuGrid(
    onAction: (Int) -> Unit,
    focusRequester: FocusRequester
) {
    val items = listOf(
        NavigationItem(
            id = NavigationDialog.ACTION_TASK_MANAGER,
            title = stringResource(R.string.task_manager),
            icon = Icons.Default.Dns
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_KEYBOARD,
            title = stringResource(R.string.keyboard),
            icon = Icons.Default.Keyboard
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            NavigationMenuItem(
                item = item,
                onClick = { onAction(item.id) },
                modifier = if (items.indexOf(item) == 0) Modifier.focusRequester(focusRequester) else Modifier
            )
        }
    }
}

@Composable
private fun ControllerMenuGrid(
    onAction: (Int) -> Unit,
    focusRequester: FocusRequester
) {
    val items = listOf(
        NavigationItem(
            id = NavigationDialog.ACTION_CONTROLLER_MANAGER,
            title = stringResource(R.string.controller_manager),
            icon = Icons.Default.SettingsInputComponent
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_EDIT_PHYSICAL_CONTROLLER,
            title = stringResource(R.string.edit_physical_controller),
            icon = Icons.Default.Tune
        ),
        NavigationItem(
            id = NavigationDialog.ACTION_MOTION_CONTROLS,
            title = stringResource(R.string.motion_controls),
            icon = Icons.Default.ScreenRotation
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            NavigationMenuItem(
                item = item,
                onClick = { onAction(item.id) },
                modifier = if (items.indexOf(item) == 0) Modifier.focusRequester(focusRequester) else Modifier
            )
        }
    }
}

@Composable
private fun NavigationMenuItem(
    item: NavigationItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(14.dp)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            modifier = Modifier.size(36.dp),
            tint = if (item.isDestructive) MaterialTheme.colorScheme.error
            else if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            color = if (item.isDestructive) MaterialTheme.colorScheme.error
            else if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class NavigationItem(
    val id: Int,
    val title: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false
)
