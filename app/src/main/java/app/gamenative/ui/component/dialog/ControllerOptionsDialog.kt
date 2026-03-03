package app.gamenative.ui.component.dialog

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R

@Composable
fun ControllerOptionsDialog(
    onDismiss: () -> Unit,
    onEditOnScreen: () -> Unit,
    onSelectICP: () -> Unit,
    onImportICP: () -> Unit,
    onExportICP: () -> Unit,
    onControllerManager: () -> Unit,
    onMotionControls: () -> Unit,
    onEditPhysicalController: () -> Unit
) {
    val items = listOf(
        ControllerOption(stringResource(R.string.edit_controls), Icons.Default.Edit, onEditOnScreen),
        ControllerOption("Select ICP", Icons.AutoMirrored.Filled.List, onSelectICP),
        ControllerOption("Import ICP", Icons.Default.FileDownload, onImportICP),
        ControllerOption("Export ICP", Icons.Default.FileUpload, onExportICP),
        ControllerOption(stringResource(R.string.controller_manager), Icons.Default.Gamepad, onControllerManager),
        ControllerOption(stringResource(R.string.motion_controls), Icons.Default.ScreenRotation, onMotionControls),
        ControllerOption(stringResource(R.string.edit_physical_controller), Icons.Default.SettingsInputComponent, onEditPhysicalController)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp) // Slightly wider for 3 columns
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_back),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Controller",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Options Grid - 3 columns
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        ControllerOptionItem(item)
                    }
                }

                // Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.close), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerOptionItem(item: ControllerOption) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 2.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp) // Shrunk from 100.dp
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp), // Shrunk from 32.dp
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer 
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), // Slightly smaller font
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer 
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class ControllerOption(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
