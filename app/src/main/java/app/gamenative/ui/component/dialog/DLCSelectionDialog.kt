package app.gamenative.ui.component.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.gamenative.utils.StorageUtils

data class DLCInfo(
    val id: String,
    val name: String,
    val downloadSize: Long,
    val installSize: Long,
    val isOwned: Boolean,
    val isInstalled: Boolean,
    val initialSelected: Boolean = false
)

@Composable
fun DLCSelectionDialog(
    visible: Boolean,
    gameName: String,
    dlcList: List<DLCInfo>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val selectedIds = remember(dlcList) {
        mutableStateMapOf<String, Boolean>().apply {
            dlcList.forEach { put(it.id, it.initialSelected || it.isInstalled) }
        }
    }

    val totalDownloadSize = remember(selectedIds.toMap()) {
        dlcList.filter { selectedIds[it.id] == true && !it.isInstalled }.sumOf { it.downloadSize }
    }

    val totalInstallSize = remember(selectedIds.toMap()) {
        dlcList.filter { selectedIds[it.id] == true && !it.isInstalled }.sumOf { it.installSize }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "DLC for $gameName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        dlcList.forEach { dlc ->
                            DLCItem(
                                dlc = dlc,
                                isSelected = selectedIds[dlc.id] ?: false,
                                onToggle = {
                                    if (!dlc.isInstalled) {
                                        selectedIds[dlc.id] = it
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Total Download: ${StorageUtils.formatBinarySize(totalDownloadSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Total Install: ${StorageUtils.formatBinarySize(totalInstallSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val selected = selectedIds.filter { it.value }.keys.toList()
                            onSave(selected)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun DLCItem(
    dlc: DLCInfo,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !dlc.isInstalled) { onToggle(!isSelected) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { if (!dlc.isInstalled) onToggle(it) },
            enabled = !dlc.isInstalled
        )
        
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = dlc.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    text = "Download: ${StorageUtils.formatBinarySize(dlc.downloadSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Install: ${StorageUtils.formatBinarySize(dlc.installSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (dlc.isInstalled) {
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
