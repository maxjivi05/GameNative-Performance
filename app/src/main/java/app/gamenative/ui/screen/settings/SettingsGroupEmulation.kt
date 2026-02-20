package app.gamenative.ui.screen.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.component.dialog.Box64PresetsDialog
import app.gamenative.ui.component.dialog.ContainerConfigDialog
import app.gamenative.ui.component.dialog.FEXCorePresetsDialog
import app.gamenative.ui.component.dialog.OrientationDialog
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.utils.ContainerUtils
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink

@Composable
fun SettingsGroupEmulation() {
    SettingsGroup(title = { Text(text = stringResource(R.string.settings_emulation_title)) }) {
        var showOrientationDialog by rememberSaveable { mutableStateOf(false) }
        var showPresetsDialog by rememberSaveable { mutableStateOf(false) }

        OrientationDialog(
            openDialog = showOrientationDialog,
            onDismiss = { showOrientationDialog = false },
        )

        if (showPresetsDialog) {
            PresetsDialog(
                open = showPresetsDialog,
                onDismiss = { showPresetsDialog = false },
            )
        }

        var showComponentsManager by rememberSaveable { mutableStateOf(false) }
        if (showComponentsManager) {
            app.gamenative.ui.screen.settings.ComponentsManagerDialog(
                open = showComponentsManager,
                onDismiss = { showComponentsManager = false },
            )
        }

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_emulation_components_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_emulation_components_subtitle)) },
            onClick = { showComponentsManager = true },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = "Presets") },
            subtitle = { Text(text = "Manage Default Settings, Box64, and FEXCore presets") },
            onClick = { showPresetsDialog = true },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_emulation_orientations_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_emulation_orientations_subtitle)) },
            onClick = { showOrientationDialog = true },
        )
    }
}
