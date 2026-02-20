package app.gamenative.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.ui.component.dialog.Box64PresetsDialog
import app.gamenative.ui.component.dialog.ContainerConfigDialog
import app.gamenative.ui.component.dialog.FEXCorePresetsDialog
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.utils.ContainerUtils
import com.alorma.compose.settings.ui.SettingsMenuLink

@Composable
fun PresetsDialog(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return

    var showConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showBox64PresetsDialog by rememberSaveable { mutableStateOf(false) }
    var showFexcorePresetsDialog by rememberSaveable { mutableStateOf(false) }

    if (showConfigDialog) {
        ContainerConfigDialog(
            visible = showConfigDialog,
            title = "Default Settings",
            default = true,
            initialConfig = ContainerUtils.getDefaultContainerData(),
            onDismissRequest = { showConfigDialog = false },
            onSave = {
                showConfigDialog = false
                ContainerUtils.setDefaultContainerData(it)
            },
        )
    }

    if (showBox64PresetsDialog) {
        Box64PresetsDialog(
            visible = showBox64PresetsDialog,
            onDismissRequest = { showBox64PresetsDialog = false },
        )
    }

    if (showFexcorePresetsDialog) {
        FEXCorePresetsDialog(
            visible = showFexcorePresetsDialog,
            onDismissRequest = { showFexcorePresetsDialog = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsMenuLink(
                        colors = settingsTileColors(),
                        title = { Text(text = "Default Settings") },
                        subtitle = { Text(text = stringResource(R.string.settings_emulation_default_config_subtitle)) },
                        onClick = { showConfigDialog = true },
                    )
                    SettingsMenuLink(
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.settings_emulation_box64_presets_title)) },
                        subtitle = { Text(stringResource(R.string.settings_emulation_box64_presets_subtitle)) },
                        onClick = { showBox64PresetsDialog = true },
                    )
                    SettingsMenuLink(
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.fexcore_presets)) },
                        subtitle = { Text(text = stringResource(R.string.fexcore_presets_description)) },
                        onClick = { showFexcorePresetsDialog = true },
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
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
