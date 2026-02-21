package app.gamenative.ui.component.dialog

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.winhandler.ProcessInfo
import com.winlator.winhandler.WinHandler
import com.winlator.winhandler.OnGetProcessInfoListener
import kotlinx.coroutines.delay
import app.gamenative.R
import androidx.compose.ui.res.stringResource

@Composable
fun TaskManagerDialog(
    winHandler: WinHandler,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var memoryInfo by remember { mutableStateOf<ActivityManager.MemoryInfo?>(null) }
    
    // Process listener
    val processListener = remember {
        val currentList = mutableListOf<ProcessInfo>()
        var expectedCount = 0
        OnGetProcessInfoListener { index, count, processInfo ->
            if (count == 0) {
                processes = emptyList()
                return@OnGetProcessInfoListener
            }
            if (index == 0) {
                currentList.clear()
                expectedCount = count
            }
            if (processInfo != null) {
                currentList.add(processInfo)
            }
            if (currentList.size >= expectedCount) {
                processes = currentList.toList().sortedByDescending { it.memoryUsage }
            }
        }
    }

    // Refresh loop
    LaunchedEffect(Unit) {
        winHandler.setOnGetProcessInfoListener(processListener)
        while (true) {
            winHandler.listProcesses()
            
            // Update Android Memory Info
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(mi)
            memoryInfo = mi
            
            delay(3000) // Refresh every 3 seconds
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.675f) // Width reduced by 25% (0.9 * 0.75)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp) // Reduced padding
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            text = stringResource(R.string.task_manager),
                            style = MaterialTheme.typography.titleMedium, // Reduced font size
                            fontWeight = FontWeight.Bold
                        )
                        memoryInfo?.let { mi ->
                            val usedMem = mi.totalMem - mi.availMem
                            val totalMem = mi.totalMem
                            val percent = (usedMem.toDouble() / totalMem.toDouble() * 100).toInt()
                            Text(
                                text = "System RAM: ${percent}% used",
                                fontSize = 10.sp, // Reduced font size
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(
                            modifier = Modifier.size(32.dp), // Reduced size
                            onClick = { winHandler.listProcesses() }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            modifier = Modifier.size(32.dp), // Reduced size
                            onClick = onDismiss
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp), // Reduced padding
                    verticalArrangement = Arrangement.spacedBy(6.dp) // Reduced spacing
                ) {
                    if (processes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No processes found", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(processes, key = { it.pid }) { process ->
                            val scope = rememberCoroutineScope()
                            ProcessItem(
                                process = process,
                                onKill = {
                                    winHandler.killProcess(process.name)
                                    scope.launch {
                                        delay(500)
                                        winHandler.listProcesses()
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Bottom bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(9.dp), // Reduced padding
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Processes: ${processes.size}",
                            style = MaterialTheme.typography.bodySmall // Reduced font size
                        )
                    }
                }
            }
        }
    }
    
    // Reset listener on dispose
    DisposableEffect(Unit) {
        onDispose {
            winHandler.setOnGetProcessInfoListener(null)
        }
    }
}

@Composable
fun ProcessItem(
    process: ProcessInfo,
    onKill: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(9.dp) // Reduced from 12.dp
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = process.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, // Reduced from 18.sp (bodyLarge)
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PID: ${process.pid}",
                        fontSize = 10.sp, // Reduced from 12.sp
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Mem: ${process.formattedMemoryUsage}",
                        fontSize = 10.sp, // Reduced from 12.sp
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (process.wow64Process) {
                        Text(
                            text = "32-bit",
                            fontSize = 10.sp, // Reduced from 12.sp
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            IconButton(
                modifier = Modifier.size(32.dp), // Reduced from default 48.dp
                onClick = onKill,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete, 
                    contentDescription = "Kill Process",
                    modifier = Modifier.size(18.dp) // Reduced from default 24.dp
                )
            }
        }
    }
}
