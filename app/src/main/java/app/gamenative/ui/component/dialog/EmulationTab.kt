package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import app.gamenative.R
import app.gamenative.ui.component.settings.frontendFullWidth
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.winlator.contents.ContentProfile
import com.winlator.container.Container
import com.winlator.core.StringUtils
import java.util.Locale

@Composable
fun EmulationTabContent(state: ContainerConfigState) {
    val config = state.config.value
    val wineIsX8664 = config.wineVersion.contains("x86_64", true)
    val wineIsArm64Ec = config.wineVersion.contains("arm64ec", true)

    app.gamenative.ui.component.settings.FrontendAwareSettingsGroupNoScope() {
        if (config.containerVariant.equals(Container.BIONIC, ignoreCase = true)) {
            Row(modifier = Modifier.frontendFullWidth()) {
                if (wineIsArm64Ec) {
                    val fexcoreIndex = state.fexcoreOptions.ids.indexOfFirst { it == config.fexcoreVersion }.coerceAtLeast(0)
                    SettingsListDropdown(
                        modifier = Modifier.weight(1f),
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.fexcore_version), softWrap = false, overflow = TextOverflow.Ellipsis) },
                        value = fexcoreIndex,
                        items = state.fexcoreOptions.labels,
                        itemMuted = state.fexcoreOptions.muted,
                        onItemSelected = { idx ->
                            val selectedId = state.fexcoreOptions.ids.getOrNull(idx).orEmpty()
                            val isManifestNotInstalled = state.fexcoreOptions.muted.getOrNull(idx) == true
                            val manifestEntry = state.fexcoreManifestById[selectedId]
                            if (isManifestNotInstalled && manifestEntry != null) {
                                state.launchManifestContentInstall(
                                    manifestEntry,
                                    ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                                ) {
                                    state.config.value = config.copy(fexcoreVersion = selectedId)
                                }
                            } else {
                                state.config.value = config.copy(fexcoreVersion = selectedId.ifEmpty { state.fexcoreOptions.labels[idx] })
                            }
                        },
                    )
                }

                SettingsListDropdown(
                    modifier = Modifier.weight(1f),
                    colors = settingsTileColors(),
                    title = { Text(text = stringResource(R.string.emulator_64bit), softWrap = false, overflow = TextOverflow.Ellipsis) },
                    value = state.emulator64Index.value,
                    items = state.emulatorEntries,
                    enabled = false,
                    onItemSelected = { },
                )
                LaunchedEffect(wineIsX8664, wineIsArm64Ec) {
                    state.emulator64Index.value = if (wineIsX8664) 1 else 0
                }

                SettingsListDropdown(
                    modifier = Modifier.weight(1f),
                    colors = settingsTileColors(),
                    title = { Text(text = stringResource(R.string.emulator_32bit), softWrap = false, overflow = TextOverflow.Ellipsis) },
                    value = state.emulator32Index.value,
                    items = state.emulatorEntries,
                    enabled = when {
                        wineIsX8664 -> false
                        wineIsArm64Ec -> true
                        else -> true
                    },
                    onItemSelected = { idx ->
                        state.emulator32Index.value = idx
                        state.config.value = config.copy(emulator = state.emulatorEntries[idx])
                    },
                )
            }
            LaunchedEffect(wineIsX8664) {
                if (wineIsX8664) {
                    state.emulator32Index.value = 1
                    if (config.emulator != state.emulatorEntries[1]) {
                        state.config.value = config.copy(emulator = state.emulatorEntries[1])
                    }
                }
            }
            LaunchedEffect(wineIsArm64Ec) {
                if (wineIsArm64Ec) {
                    if (state.emulator32Index.value !in 0..1) state.emulator32Index.value = 0
                    if (config.emulator.isEmpty()) {
                        state.config.value = config.copy(emulator = state.emulatorEntries[0])
                    }
                }
            }
        }

        val box64Index = state.box64Options.ids.indexOfFirst { it == config.box64Version }.coerceAtLeast(0)
        val box64ManifestMap = if (config.wineVersion.contains("arm64ec", true)) {
            state.wowBox64ManifestById
        } else {
            state.box64ManifestById
        }
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.box64_version)) },
            value = box64Index,
            items = state.box64Options.labels,
            itemMuted = state.box64Options.muted,
            onItemSelected = { idx ->
                val selectedId = state.box64Options.ids.getOrNull(idx).orEmpty()
                val isManifestNotInstalled = state.box64Options.muted.getOrNull(idx) == true
                val manifestEntry = box64ManifestMap[selectedId.lowercase(Locale.ENGLISH)]
                if (isManifestNotInstalled && manifestEntry != null) {
                    val expectedType = if (config.wineVersion.contains("arm64ec", true)) {
                        ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                    } else {
                        ContentProfile.ContentType.CONTENT_TYPE_BOX64
                    }
                    state.launchManifestContentInstall(manifestEntry, expectedType) {
                        state.config.value = config.copy(box64Version = selectedId)
                    }
                } else {
                    state.config.value = config.copy(box64Version = selectedId.ifEmpty { StringUtils.parseIdentifier(state.box64Options.labels[idx]) })
                }
            },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.box64_preset)) },
            value = state.box64Presets.indexOfFirst { it.id == config.box64Preset }.coerceAtLeast(0),
            items = state.box64Presets.map { it.name },
            onItemSelected = {
                state.config.value = config.copy(box64Preset = state.box64Presets[it].id)
            },
        )
        if (config.containerVariant.equals(Container.BIONIC, ignoreCase = true)
            && config.wineVersion.contains("arm64ec", ignoreCase = true)) {
            SettingsListDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.fexcore_preset)) },
                value = state.fexcorePresets.indexOfFirst { it.id == config.fexcorePreset }.coerceAtLeast(0),
                items = state.fexcorePresets.map { it.name },
                onItemSelected = {
                    state.config.value = config.copy(fexcorePreset = state.fexcorePresets[it].id)
                },
            )

            if (config.fexcorePreset.startsWith(com.winlator.fexcore.FEXCorePreset.CUSTOM)) {
                SettingsListDropdown(
                    colors = settingsTileColors(),
                    title = { Text(text = "FEXCore TSO Mode") },
                    value = state.fexcoreTSOPresets.indexOfFirst { it.equals(config.fexcoreTSOMode, true) }.coerceAtLeast(0),
                    items = state.fexcoreTSOPresets,
                    onItemSelected = {
                        state.config.value = config.copy(fexcoreTSOMode = state.fexcoreTSOPresets[it])
                    },
                )
                SettingsListDropdown(
                    colors = settingsTileColors(),
                    title = { Text(text = "FEXCore X87 Mode") },
                    value = state.fexcoreX87Presets.indexOfFirst { it.equals(config.fexcoreX87Mode, true) }.coerceAtLeast(0),
                    items = state.fexcoreX87Presets,
                    onItemSelected = {
                        state.config.value = config.copy(fexcoreX87Mode = state.fexcoreX87Presets[it])
                    },
                )
                SettingsListDropdown(
                    colors = settingsTileColors(),
                    title = { Text(text = "FEXCore MultiBlock") },
                    value = state.fexcoreMultiblockValues.indexOfFirst { it.equals(config.fexcoreMultiBlock, true) }.coerceAtLeast(0),
                    items = state.fexcoreMultiblockValues,
                    onItemSelected = {
                        state.config.value = config.copy(fexcoreMultiBlock = state.fexcoreMultiblockValues[it])
                    },
                )
            }
        }
    }
}
