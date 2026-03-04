package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.component.settings.SettingsMultiListDropdown
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch
import com.winlator.contents.ContentProfile
import com.winlator.container.Container
import com.winlator.core.KeyValueSet
import com.winlator.core.StringUtils
import com.winlator.core.envvars.EnvVars
import kotlin.math.roundToInt

import com.winlator.core.PerformanceTuner
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun GraphicsTabContent(state: ContainerConfigState) {
    val context = LocalContext.current
    app.gamenative.ui.component.settings.FrontendAwareSettingsGroupNoScope() {
        if (state.config.value.containerVariant.equals(Container.BIONIC, ignoreCase = true)) {
            // Bionic: Graphics Driver (Wrapper/Wrapper-v2)
            SettingsListDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.graphics_driver)) },
                value = state.bionicDriverIndex.value,
                items = state.bionicGraphicsDrivers,
                onItemSelected = { idx ->
                    state.bionicDriverIndex.value = idx
                    state.config.value = state.config.value.copy(graphicsDriver = StringUtils.parseIdentifier(state.bionicGraphicsDrivers[idx]))
                },
            )
            // Bionic: Graphics Driver Version (stored in graphicsDriverConfig.version; list from manifest + installed)
            SettingsListDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.graphics_driver_version)) },
                value = state.wrapperVersionIndex.value.coerceIn(0, (state.wrapperOptions.labels.size - 1).coerceAtLeast(0)),
                items = state.wrapperOptions.labels,
                itemMuted = state.wrapperOptions.muted,
                onItemSelected = { idx ->
                    val selectedId = state.wrapperOptions.ids.getOrNull(idx).orEmpty()
                    val isManifestNotInstalled = state.wrapperOptions.muted.getOrNull(idx) == true
                    val manifestEntry = state.wrapperManifestById[selectedId]
                    if (isManifestNotInstalled && manifestEntry != null) {
                        state.launchManifestDriverInstall(manifestEntry) {
                            val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                            cfg.put("version", state.wrapperOptions.labels[idx])
                            state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                        }
                        return@SettingsListDropdown
                    }
                    state.wrapperVersionIndex.value = idx
                    val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                    cfg.put("version", selectedId.ifEmpty { state.wrapperOptions.labels[idx] })
                    state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                },
            )
        } else {
            // Non-bionic: existing driver/version UI and Vortek-specific options
            SettingsListDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.graphics_driver)) },
                value = state.graphicsDriverIndex.value,
                items = state.graphicsDrivers.value,
                onItemSelected = {
                    val oldDriver = state.config.value.graphicsDriver
                    val newDriver = StringUtils.parseIdentifier(state.graphicsDrivers.value[it])
                    state.graphicsDriverIndex.value = it
                    if (oldDriver != newDriver) {
                        state.config.value = state.config.value.copy(
                            graphicsDriver = newDriver
                        )
                    }
                },
            )
            SettingsListDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.graphics_driver_version)) },
                value = state.graphicsDriverVersionIndex.value,
                items = state.graphicsDriverVersionOptions.labels,
                itemMuted = state.graphicsDriverVersionOptions.muted,
                onItemSelected = { idx ->
                    val selectedId = state.graphicsDriverVersionOptions.ids[idx]
                    val isMuted = state.graphicsDriverVersionOptions.muted[idx]
                    
                    if (isMuted) {
                        val entry = state.graphicsDriverManifestById[selectedId]
                        if (entry != null) {
                            state.launchManifestDriverInstall(entry) {
                                state.graphicsDriverVersionIndex.value = idx
                                state.config.value = state.config.value.copy(graphicsDriverVersion = selectedId)
                            }
                        }
                    } else {
                        state.graphicsDriverVersionIndex.value = idx
                        val selectedVersion = if (idx == 0) "" else selectedId
                        state.config.value = state.config.value.copy(graphicsDriverVersion = selectedVersion)
                    }
                },
            )
        }

        // --- Performance Toggles (Common) ---
        // Force Adreno Maximum Clocks (Non-Root)
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = "Force Maximum Clocks (Adreno Only)") },
            subtitle = { Text(text = "Loop detection - requests max GPU clocks via Adreno Tools. Best for non-root.") },
            state = state.forceAdrenoClocksChecked.value,
            onCheckedChange = { checked ->
                state.forceAdrenoClocksChecked.value = checked
                if (checked) {
                    state.rootPerformanceModeChecked.value = false
                    state.config.value = state.config.value.copy(forceAdrenoClocks = true, rootPerformanceMode = false)
                } else {
                    state.config.value = state.config.value.copy(forceAdrenoClocks = false)
                }
            },
        )
        // Root Maximum Performance
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = "Root Maximum Performance") },
            subtitle = { Text(text = "Requires Root. Loop detection - instant rewrite of CPU/GPU clocks if changed.") },
            state = state.rootPerformanceModeChecked.value,
            enabled = !state.forceAdrenoClocksChecked.value,
            onCheckedChange = { checked ->
                if (checked) {
                    PerformanceTuner.checkRootAccessAsync { hasRoot ->
                        if (hasRoot) {
                            state.rootPerformanceModeChecked.value = true
                            state.config.value = state.config.value.copy(rootPerformanceMode = true)
                        } else {
                            Toast.makeText(context, "Root access required for this feature!", Toast.LENGTH_LONG).show()
                            state.rootPerformanceModeChecked.value = false
                        }
                    }
                } else {
                    state.rootPerformanceModeChecked.value = false
                    state.config.value = state.config.value.copy(rootPerformanceMode = false)
                }
            },
        )

        // DX Wrappers (Common)
        DxWrapperSection(state)

        // BCn Emulation (Common)
        BCnEmulationSection(state)

        // Surface Format (Common)
        SurfaceFormatSection(state)

        // --- Sharpness Boost (vkBasalt) ---
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.sharpness_effect)) },
            value = state.sharpnessEffectIndex.value,
            items = state.sharpnessDisplayItems,
            onItemSelected = { idx ->
                state.sharpnessEffectIndex.value = idx
                state.config.value = state.config.value.copy(sharpnessEffect = state.sharpnessEffects[idx])
            },
        )

        if (state.config.value.sharpnessEffect != "None") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "${stringResource(R.string.sharpness_level)}: ${state.sharpnessLevel.value}%",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = state.sharpnessLevel.value.toFloat(),
                    onValueChange = {
                        state.sharpnessLevel.value = it.roundToInt()
                        state.config.value = state.config.value.copy(sharpnessLevel = it.roundToInt())
                    },
                    valueRange = 0f..100f
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "${stringResource(R.string.sharpness_denoise)}: ${state.sharpnessDenoise.value}%",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = state.sharpnessDenoise.value.toFloat(),
                    onValueChange = {
                        state.sharpnessDenoise.value = it.roundToInt()
                        state.config.value = state.config.value.copy(sharpnessDenoise = it.roundToInt())
                    },
                    valueRange = 0f..100f
                )
            }
        }

        // Bionic Specific Extras
        if (state.config.value.containerVariant.equals(Container.BIONIC, ignoreCase = true)) {
            // Bionic: Exposed Vulkan Extensions
            SettingsMultiListDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.exposed_vulkan_extensions)) },
                values = state.exposedExtIndices.value,
                items = state.gpuExtensions,
                fallbackDisplay = "all",
                onItemSelected = { idx ->
                    val current = state.exposedExtIndices.value
                    state.exposedExtIndices.value =
                        if (current.contains(idx)) current.filter { it != idx } else current + idx
                    val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                    val allSelected = state.exposedExtIndices.value.size == state.gpuExtensions.size
                    if (allSelected) cfg.put("exposedDeviceExtensions", "all") else cfg.put(
                        "exposedDeviceExtensions",
                        state.exposedExtIndices.value.sorted().joinToString("|") { state.gpuExtensions[it] },
                    )
                    val blacklisted = if (allSelected) "" else
                        state.gpuExtensions.indices
                            .filter { it !in state.exposedExtIndices.value }
                            .sorted()
                            .joinToString(",") { state.gpuExtensions[it] }
                    cfg.put("blacklistedExtensions", blacklisted)
                    state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                },
            )
            // Bionic: Max Device Memory
            run {
                val memValues = listOf("0", "512", "1024", "2048", "4096")
                val memLabels = listOf("0 MB", "512 MB", "1024 MB", "2048 MB", "4096 MB")
                SettingsListDropdown(
                    colors = settingsTileColors(),
                    title = { Text(text = stringResource(R.string.max_device_memory)) },
                    value = state.maxDeviceMemoryIndex.value.coerceIn(0, memValues.lastIndex),
                    items = memLabels,
                    onItemSelected = { idx ->
                        state.maxDeviceMemoryIndex.value = idx
                        val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                        cfg.put("maxDeviceMemory", memValues[idx])
                        state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                    },
                )
            }
            // Bionic: Use Adrenotools Turnip
            SettingsSwitch(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.use_adrenotools_turnip)) },
                state = state.adrenotoolsTurnipChecked.value,
                onCheckedChange = { checked ->
                    state.adrenotoolsTurnipChecked.value = checked
                    val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                    cfg.put("adrenotoolsTurnip", if (checked) "1" else "0")
                    state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                },
            )
        } else {
            // Vortek/Adreno specific GLIBC settings
            run {
                val driverType = StringUtils.parseIdentifier(state.graphicsDrivers.value.getOrNull(state.graphicsDriverIndex.value).orEmpty())
                val isVortekLike = state.config.value.containerVariant.equals(Container.GLIBC) && (driverType == "vortek" || driverType == "adreno" || driverType == "sd-8-elite")
                if (isVortekLike) {
                    val vkVersions = listOf("1.0", "1.1", "1.2", "1.3")
                    SettingsListDropdown(
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.vulkan_version)) },
                        value = state.vkMaxVersionIndex.value.coerceIn(0, 3),
                        items = vkVersions,
                        onItemSelected = { idx ->
                            state.vkMaxVersionIndex.value = idx
                            val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                            cfg.put("vkMaxVersion", vkVersions[idx])
                            state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                        },
                    )
                    SettingsMultiListDropdown(
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.exposed_vulkan_extensions)) },
                        values = state.exposedExtIndices.value,
                        items = state.gpuExtensions,
                        fallbackDisplay = "all",
                        onItemSelected = { idx ->
                            val current = state.exposedExtIndices.value
                            state.exposedExtIndices.value =
                                if (current.contains(idx)) current.filter { it != idx } else current + idx
                            val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                            val allSelected = state.exposedExtIndices.value.size == state.gpuExtensions.size
                            if (allSelected) cfg.put("exposedDeviceExtensions", "all") else cfg.put(
                                "exposedDeviceExtensions",
                                state.exposedExtIndices.value.sorted().joinToString("|") { state.gpuExtensions[it] },
                            )
                            val blacklisted = if (allSelected) "" else
                                state.gpuExtensions.indices
                                    .filter { it !in state.exposedExtIndices.value }
                                    .sorted()
                                    .joinToString(",") { state.gpuExtensions[it] }
                            cfg.put("blacklistedExtensions", blacklisted)
                            state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                        },
                    )
                    val imageSizes = listOf("64", "128", "256", "512", "1024")
                    val imageLabels = listOf("64", "128", "256", "512", "1024").map { "$it MB" }
                    SettingsListDropdown(
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.image_cache_size)) },
                        value = state.imageCacheIndex.value.coerceIn(0, imageSizes.lastIndex),
                        items = imageLabels,
                        onItemSelected = { idx ->
                            state.imageCacheIndex.value = idx
                            val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                            cfg.put("imageCacheSize", imageSizes[idx])
                            state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                        },
                    )
                    val memValues = listOf("0", "512", "1024", "2048", "4096")
                    val memLabels = listOf("0 MB", "512 MB", "1024 MB", "2048 MB", "4096 MB")
                    SettingsListDropdown(
                        colors = settingsTileColors(),
                        title = { Text(text = stringResource(R.string.max_device_memory)) },
                        value = state.maxDeviceMemoryIndex.value.coerceIn(0, memValues.lastIndex),
                        items = memLabels,
                        onItemSelected = { idx ->
                            state.maxDeviceMemoryIndex.value = idx
                            val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                            cfg.put("maxDeviceMemory", memValues[idx])
                            state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
                        },
                    )
                }
            }
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.use_dri3)) },
            subtitle = { Text(text = stringResource(R.string.use_dri3_description)) },
            state = state.config.value.useDRI3,
            onCheckedChange = {
                state.config.value = state.config.value.copy(useDRI3 = it)
            },
        )

        // --- Sharpness Boost (vkBasalt) ---
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.sharpness_effect)) },
            value = state.sharpnessEffectIndex.value,
            items = state.sharpnessDisplayItems,
            onItemSelected = { idx ->
                state.sharpnessEffectIndex.value = idx
                state.config.value = state.config.value.copy(sharpnessEffect = state.sharpnessEffects[idx])
            },
        )

        if (state.config.value.sharpnessEffect != "None") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "${stringResource(R.string.sharpness_level)}: ${state.sharpnessLevel.value}%",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = state.sharpnessLevel.value.toFloat(),
                    onValueChange = {
                        state.sharpnessLevel.value = it.roundToInt()
                        state.config.value = state.config.value.copy(sharpnessLevel = it.roundToInt())
                    },
                    valueRange = 0f..100f
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "${stringResource(R.string.sharpness_denoise)}: ${state.sharpnessDenoise.value}%",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = state.sharpnessDenoise.value.toFloat(),
                    onValueChange = {
                        state.sharpnessDenoise.value = it.roundToInt()
                        state.config.value = state.config.value.copy(sharpnessDenoise = it.roundToInt())
                    },
                    valueRange = 0f..100f
                )
            }
        }
    }
}

@Composable
private fun DxWrapperSection(state: ContainerConfigState) {
    // Calculate effective versions (fallback to legacy if new fields are null)
    val legacyWrapper = state.config.value.dxwrapper
    val legacyDxvkVer = KeyValueSet(state.config.value.dxwrapperConfig).get("version")
    val legacyVkd3dVer = KeyValueSet(state.config.value.dxwrapperConfig).get("vkd3dVersion")

    val effectiveDxvk = state.config.value.dxvkVersion ?: if (legacyWrapper.startsWith("dxvk")) legacyDxvkVer else "Disabled"
    val effectiveVkd3d = state.config.value.vkd3dVersion ?: if (legacyWrapper.startsWith("vkd3d")) legacyVkd3dVer else "Disabled"

    // DXVK Dropdown
    run {
        val dxvkCtx = state.currentDxvkContext()
        val items = dxvkCtx.labels
        val itemIds = dxvkCtx.ids
        val itemMuted = dxvkCtx.muted

        val selectedIndex = itemIds.indexOf(effectiveDxvk).let {
            if (it >= 0) it else itemIds.indexOfFirst { id -> id != "Disabled" && effectiveDxvk.startsWith(id) }.coerceAtLeast(0)
        }

        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.dxvk_version)) },
            value = selectedIndex,
            items = items,
            itemMuted = itemMuted,
            onItemSelected = { idx ->
                val selectedId = itemIds.getOrNull(idx) ?: "Disabled"
                if (selectedId == "Disabled") {
                    val kvs = KeyValueSet(state.config.value.dxwrapperConfig)
                    kvs.put("version", "Disabled")
                    state.config.value = state.config.value.copy(dxvkVersion = "Disabled", dxwrapperConfig = kvs.toString())
                    state.dxvkVersionIndex.value = 0
                    return@SettingsListDropdown
                }

                val isMuted = itemMuted.getOrNull(idx) == true
                val manifestEntry = state.dxvkManifestById[selectedId]
                
                if (isMuted && manifestEntry != null) {
                    state.launchManifestContentInstall(
                        manifestEntry,
                        ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                    ) {
                        state.config.value = state.config.value.copy(dxvkVersion = selectedId)
                    }
                    return@SettingsListDropdown
                }

                // Update both new and legacy fields to ensure persistence and compatibility
                val kvs = KeyValueSet(state.config.value.dxwrapperConfig)
                kvs.put("version", selectedId)
                state.config.value = state.config.value.copy(dxvkVersion = selectedId, dxwrapperConfig = kvs.toString())
                state.dxvkVersionIndex.value = idx
            },
        )
    }

    // VKD3D Dropdown
    run {
        val vkd3dItems = listOf("Disabled") + state.vkd3dOptions.labels
        val vkd3dIds = listOf("Disabled") + state.vkd3dOptions.ids
        val vkd3dMuted = listOf(false) + state.vkd3dOptions.muted

        val selectedIndex = vkd3dIds.indexOf(effectiveVkd3d).let {
            if (it >= 0) it else vkd3dIds.indexOfFirst { id -> id != "Disabled" && effectiveVkd3d.startsWith(id) }.coerceAtLeast(0)
        }

        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = "VKD3D Version") },
            value = selectedIndex,
            items = vkd3dItems,
            itemMuted = vkd3dMuted,
            onItemSelected = { idx ->
                val selectedId = vkd3dIds.getOrNull(idx) ?: "Disabled"
                if (selectedId == "Disabled") {
                    val kvs = KeyValueSet(state.config.value.dxwrapperConfig)
                    kvs.put("vkd3dVersion", "Disabled")
                    state.config.value = state.config.value.copy(vkd3dVersion = "Disabled", dxwrapperConfig = kvs.toString())
                    state.vkd3dVersionIndex.value = 0
                    return@SettingsListDropdown
                }

                val isMuted = vkd3dMuted.getOrNull(idx) == true
                val manifestEntry = state.vkd3dManifestById[selectedId]

                if (isMuted && manifestEntry != null) {
                    state.launchManifestContentInstall(
                        manifestEntry,
                        ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                    ) {
                        state.config.value = state.config.value.copy(vkd3dVersion = selectedId)
                    }
                    return@SettingsListDropdown
                }

                // Update both new and legacy fields to ensure persistence and compatibility
                val kvs = KeyValueSet(state.config.value.dxwrapperConfig)
                kvs.put("vkd3dVersion", selectedId)
                state.config.value = state.config.value.copy(vkd3dVersion = selectedId, dxwrapperConfig = kvs.toString())
                state.vkd3dVersionIndex.value = idx
            },
        )

        if (effectiveVkd3d != "Disabled") {
            val featureLevels = listOf("12_2", "12_1", "12_0", "11_1", "11_0")
            val cfg = KeyValueSet(state.config.value.dxwrapperConfig)
            val currentLevel = cfg.get("vkd3dFeatureLevel", "12_1")
            val currentLevelIndex = featureLevels.indexOf(currentLevel).coerceAtLeast(0)
            SettingsListDropdown(
                colors = settingsTileColors(),
                title = { Text(text = stringResource(R.string.vkd3d_feature_level)) },
                value = currentLevelIndex,
                items = featureLevels,
                onItemSelected = {
                    val selected = featureLevels[it]
                    val currentConfig = KeyValueSet(state.config.value.dxwrapperConfig)
                    currentConfig.put("vkd3dFeatureLevel", selected)
                    state.config.value = state.config.value.copy(dxwrapperConfig = currentConfig.toString())
                },
            )
        }
    }
}

@Composable
private fun BCnEmulationSection(state: ContainerConfigState) {
    run {
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.bcn_emulation)) },
            value = state.bcnEmulationIndex.value.coerceIn(0, state.bcnEmulationEntries.size - 1).coerceAtLeast(0),
            items = state.bcnEmulationEntries,
            onItemSelected = { idx ->
                state.bcnEmulationIndex.value = idx
                val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                cfg.put("bcnEmulation", state.bcnEmulationEntries[idx])
                state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
            },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.bcn_emulation_type)) },
            value = state.bcnEmulationTypeIndex.value.coerceIn(0, state.bcnEmulationTypeEntries.size - 1).coerceAtLeast(0),
            items = state.bcnEmulationTypeEntries,
            onItemSelected = { idx ->
                state.bcnEmulationTypeIndex.value = idx
                val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                cfg.put("bcnEmulationType", state.bcnEmulationTypeEntries[idx])
                state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
            },
        )
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.bcn_emulation_cache)) },
            state = state.bcnEmulationCacheEnabled.value,
            onCheckedChange = { checked ->
                state.bcnEmulationCacheEnabled.value = checked
                val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                cfg.put("bcnEmulationCache", if (checked) "1" else "0")
                state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
            },
        )
    }
}

@Composable
private fun SurfaceFormatSection(state: ContainerConfigState) {
    run {
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.surface_format)) },
            value = state.surfaceFormatIndex.value.coerceIn(0, state.surfaceFormatEntries.size - 1).coerceAtLeast(0),
            items = state.surfaceFormatEntries,
            onItemSelected = { idx ->
                state.surfaceFormatIndex.value = idx
                val cfg = KeyValueSet(state.config.value.graphicsDriverConfig)
                cfg.put("surfaceFormat", state.surfaceFormatEntries[idx])
                state.config.value = state.config.value.copy(graphicsDriverConfig = cfg.toString())
            },
        )
    }
}
