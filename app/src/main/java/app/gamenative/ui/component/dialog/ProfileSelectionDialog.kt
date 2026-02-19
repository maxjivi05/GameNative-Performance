package app.gamenative.ui.component.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.gamenative.R
import com.winlator.inputcontrols.ControlsProfile

@Composable
fun ProfileSelectionDialog(
    profiles: List<ControlsProfile>,
    selectedProfileId: Int,
    onDismiss: () -> Unit,
    onProfileSelected: (ControlsProfile) -> Unit,
    onDeleteProfile: (ControlsProfile) -> Unit,
    onRenameProfile: (ControlsProfile, String) -> Unit
) {
    var profileToRename by remember { mutableStateOf<ControlsProfile?>(null) }
    var newName by remember { mutableStateOf("") }
    var currentSelectedInList by remember { 
        mutableStateOf(profiles.find { it.id == selectedProfileId } ?: profiles.firstOrNull()) 
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Select Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(profiles) { profile ->
                        val isSelected = currentSelectedInList?.id == profile.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentSelectedInList = profile },
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (profile.id == selectedProfileId) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Delete
                    TextButton(
                        onClick = { 
                            currentSelectedInList?.let { onDeleteProfile(it) }
                        },
                        enabled = currentSelectedInList != null && currentSelectedInList?.id != 0,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Middle: Rename
                        TextButton(
                            onClick = { 
                                profileToRename = currentSelectedInList
                                newName = currentSelectedInList?.name ?: ""
                            },
                            enabled = currentSelectedInList != null
                        ) {
                            Text("Rename")
                        }

                        // Right: Close
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
                
                if (currentSelectedInList != null && currentSelectedInList?.id != selectedProfileId) {
                    Button(
                        onClick = { onProfileSelected(currentSelectedInList!!) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Select & Apply")
                    }
                }
            }
        }
    }

    if (profileToRename != null) {
        AlertDialog(
            onDismissRequest = { profileToRename = null },
            title = { Text("Rename Profile") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameProfile(profileToRename!!, newName)
                    profileToRename = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
