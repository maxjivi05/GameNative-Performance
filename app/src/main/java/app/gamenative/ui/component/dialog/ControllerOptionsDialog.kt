package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Controller",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OptionButton(
                    text = stringResource(R.string.edit_controls),
                    icon = Icons.Default.Edit,
                    onClick = onEditOnScreen
                )

                OptionButton(
                    text = "Select ICP",
                    icon = Icons.Default.List,
                    onClick = onSelectICP
                )

                OptionButton(
                    text = "Import ICP",
                    icon = Icons.Default.FileDownload,
                    onClick = onImportICP
                )

                OptionButton(
                    text = "Export ICP",
                    icon = Icons.Default.FileUpload,
                    onClick = onExportICP
                )

                OptionButton(
                    text = stringResource(R.string.controller_manager),
                    icon = Icons.Default.Gamepad,
                    onClick = onControllerManager
                )

                OptionButton(
                    text = stringResource(R.string.motion_controls),
                    icon = Icons.Default.ScreenRotation,
                    onClick = onMotionControls
                )

                OptionButton(
                    text = stringResource(R.string.edit_physical_controller),
                    icon = Icons.Default.SettingsInputComponent,
                    onClick = onEditPhysicalController
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
