package app.gamenative.ui.component.dialog

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf(context.filesDir.parentFile?.absolutePath ?: "") }
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    var clipboardFiles by remember { mutableStateOf(setOf<File>()) }
    var isCutOperation by remember { mutableStateOf(false) }

    val files = remember(currentPath) {
        val dir = File(currentPath)
        val list = dir.listFiles()?.toList() ?: emptyList()
        list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Internal Explorer", fontSize = 18.sp) },
                        navigationIcon = {
                            IconButton(onClick = {
                                val parent = File(currentPath).parentFile
                                if (parent != null && parent.absolutePath.startsWith(context.filesDir.parentFile?.absolutePath ?: "/data/data")) {
                                    currentPath = parent.absolutePath
                                    selectedFiles = emptySet()
                                }
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (selectedFiles.isNotEmpty()) {
                                IconButton(onClick = {
                                    clipboardFiles = selectedFiles.toSet()
                                    isCutOperation = false
                                    selectedFiles = emptySet()
                                }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                                IconButton(onClick = {
                                    clipboardFiles = selectedFiles.toSet()
                                    isCutOperation = true
                                    selectedFiles = emptySet()
                                }) { Icon(Icons.Default.ContentCut, contentDescription = "Cut") }
                                IconButton(onClick = {
                                    selectedFiles.forEach { it.deleteRecursively() }
                                    selectedFiles = emptySet()
                                    currentPath = currentPath 
                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                            if (clipboardFiles.isNotEmpty()) {
                                IconButton(onClick = {
                                    performPaste(context, clipboardFiles, currentPath, isCutOperation)
                                    if (isCutOperation) clipboardFiles = emptySet()
                                    currentPath = currentPath 
                                }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") }
                            }
                            IconButton(onClick = {
                                selectedFiles = if (selectedFiles.size == files.size) emptySet() else files.toSet()
                            }) { Icon(Icons.Default.SelectAll, contentDescription = "Select All") }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                    Text(text = currentPath, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(files) { file ->
                            val isSelected = selectedFiles.contains(file)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedFiles.isNotEmpty()) {
                                                selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                                            } else if (file.isDirectory) {
                                                currentPath = file.absolutePath
                                            }
                                        },
                                        onLongClick = {
                                            selectedFiles = selectedFiles + file
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                    selectedFiles = if (checked) selectedFiles + file else selectedFiles - file
                                })
                                Icon(
                                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (file.isDirectory) Color(0xFFF4B400) else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = file.name,
                                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun performPaste(context: Context, sources: Set<File>, destDir: String, isCut: Boolean) {
    for (source in sources) {
        val dest = File(destDir, source.name)
        try {
            if (source.isDirectory) source.copyRecursively(dest, overwrite = true)
            else source.copyTo(dest, overwrite = true)
            if (isCut) source.deleteRecursively()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
