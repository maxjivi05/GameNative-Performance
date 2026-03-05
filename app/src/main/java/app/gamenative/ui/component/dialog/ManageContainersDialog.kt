package app.gamenative.ui.component.dialog

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.PrefManager
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.core.Callback
import com.winlator.core.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ManageContainersDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val containerManager = remember { ContainerManager(context) }
    var containers by remember { mutableStateOf(containerManager.containers.toList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Manage Containers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(containers, key = { it.id }) { container ->
                        ContainerItem(
                            container = container,
                            onDelete = {
                                containerManager.removeContainerAsync(container) {
                                    // Clean up PrefManager references
                                    val map = PrefManager.gameContainers.toMutableMap()
                                    val toRemove = map.filterValues { it == container.id }.keys
                                    toRemove.forEach { map.remove(it) }
                                    PrefManager.gameContainers = map
                                    
                                    // Refresh list
                                    val newManager = ContainerManager(context)
                                    containers = newManager.containers.toList()
                                }
                            }
                        )
                    }
                }
                
                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun ContainerItem(container: Container, onDelete: () -> Unit) {
    val context = LocalContext.current
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var showGames by remember { mutableStateOf(false) }

    LaunchedEffect(container) {
        withContext(Dispatchers.IO) {
            val totalSize = FileUtils.getSize(container.rootDir)
            sizeBytes = totalSize
        }
    }

    val usedByGames = remember(container.id) {
        PrefManager.gameContainers.filterValues { it == container.id }.keys.toList()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { showGames = !showGames },
        color = Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = container.id,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wine: ${container.wineVersion}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sizeBytes != null) {
                        Text(
                            text = Formatter.formatFileSize(context, sizeBytes!!),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }

                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Container",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Container?") },
                            text = { Text("Are you sure you want to completely remove this container and all its files? This action cannot be undone.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirm = false
                                        onDelete()
                                    }
                                ) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            if (showGames) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Used by Apps/Games:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (usedByGames.isEmpty()) {
                        Text(
                            text = "No games currently using this container.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else {
                        usedByGames.forEach { appId ->
                            Text(
                                text = "• $appId",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
