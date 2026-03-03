package app.gamenative.ui.component.dialog

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import app.gamenative.R
import com.winlator.core.KeyValueSet
import com.winlator.renderer.GLRenderer
import com.winlator.renderer.effects.*
import java.util.*

@Composable
fun ScreenEffectDialog(
    renderer: GLRenderer?,
    onDismiss: () -> Unit
) {
    if (renderer == null) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    var profiles by remember { 
        val set = preferences.getStringSet("screen_effect_profiles", emptySet()) ?: emptySet()
        mutableStateOf(set.toMutableSet())
    }
    var selectedProfileName by remember { 
        mutableStateOf(preferences.getString("last_screen_effect_profile", "") ?: "") 
    }

    // State for sliders and toggles
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var gamma by remember { mutableFloatStateOf(1.0f) }
    var enableFXAA by remember { mutableStateOf(false) }
    var enableCRT by remember { mutableStateOf(false) }
    var enableToon by remember { mutableStateOf(false) }
    var enableNTSC by remember { mutableStateOf(false) }

    // Initialize from current renderer state
    LaunchedEffect(Unit) {
        val composer = renderer.effectComposer ?: return@LaunchedEffect
        val colorEffect = composer.getEffect(ColorEffect::class.java) as? ColorEffect
        if (colorEffect != null) {
            brightness = colorEffect.brightness * 100f
            contrast = colorEffect.contrast * 100f
            gamma = colorEffect.gamma
        }
        enableFXAA = composer.getEffect(FXAAEffect::class.java) != null
        enableCRT = composer.getEffect(CRTEffect::class.java) != null
        enableToon = composer.getEffect(ToonEffect::class.java) != null
        enableNTSC = composer.getEffect(NTSCCombinedEffect::class.java) != null
    }

    fun updateRenderer() {
        val composer = renderer.effectComposer ?: return
        
        // Color Effect
        var colorEffect = composer.getEffect(ColorEffect::class.java) as? ColorEffect
        if (brightness == 0f && contrast == 0f && gamma == 1.0f) {
            if (colorEffect != null) composer.removeEffect(colorEffect)
        } else {
            if (colorEffect == null) colorEffect = ColorEffect()
            colorEffect.brightness = brightness / 100f
            colorEffect.contrast = contrast / 100f
            colorEffect.gamma = gamma
            composer.addEffect(colorEffect)
        }

        // FXAA
        if (enableFXAA) {
            if (composer.getEffect(FXAAEffect::class.java) == null) composer.addEffect(FXAAEffect())
        } else {
            composer.getEffect(FXAAEffect::class.java)?.let { composer.removeEffect(it) }
        }

        // CRT
        if (enableCRT) {
            if (composer.getEffect(CRTEffect::class.java) == null) composer.addEffect(CRTEffect())
        } else {
            composer.getEffect(CRTEffect::class.java)?.let { composer.removeEffect(it) }
        }

        // Toon
        if (enableToon) {
            if (composer.getEffect(ToonEffect::class.java) == null) composer.addEffect(ToonEffect())
        } else {
            composer.getEffect(ToonEffect::class.java)?.let { composer.removeEffect(it) }
        }

        // NTSC
        if (enableNTSC) {
            if (composer.getEffect(NTSCCombinedEffect::class.java) == null) composer.addEffect(NTSCCombinedEffect())
        } else {
            composer.getEffect(NTSCCombinedEffect::class.java)?.let { composer.removeEffect(it) }
        }
    }

    // Instant application when state changes
    LaunchedEffect(brightness, contrast, gamma, enableFXAA, enableCRT, enableToon, enableNTSC) {
        updateRenderer()
    }

    fun loadProfile(name: String) {
        if (name.isEmpty()) return
        val profileStr = profiles.find { it.startsWith("$name:") } ?: return
        val parts = profileStr.split(":", limit = 2)
        if (parts.size > 1 && parts[1].isNotEmpty()) {
            val settings = KeyValueSet(parts[1])
            brightness = settings.getFloat("brightness", 0f)
            contrast = settings.getFloat("contrast", 0f)
            gamma = settings.getFloat("gamma", 1.0f)
            enableFXAA = settings.getBoolean("fxaa", false)
            enableCRT = settings.getBoolean("crt_shader", false)
            enableToon = settings.getBoolean("toon_shader", false)
            enableNTSC = settings.getBoolean("ntsc_effect", false)
        }
    }

    fun saveCurrentToProfile(name: String) {
        if (name.isEmpty()) return
        val settings = KeyValueSet()
        settings.put("brightness", brightness)
        settings.put("contrast", contrast)
        settings.put("gamma", gamma)
        settings.put("fxaa", enableFXAA)
        settings.put("crt_shader", enableCRT)
        settings.put("toon_shader", enableToon)
        settings.put("ntsc_effect", enableNTSC)

        val newProfiles = profiles.toMutableSet()
        newProfiles.removeAll { it.startsWith("$name:") }
        newProfiles.add("$name:${settings}")
        
        profiles = newProfiles
        preferences.edit()
            .putStringSet("screen_effect_profiles", newProfiles)
            .putString("last_screen_effect_profile", name)
            .apply()
    }

    var showAddProfileDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.screen_effect),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile Selection
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Profile",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var expanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        onClick = { expanded = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (selectedProfileName.isEmpty()) "-- Default --" else selectedProfileName,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("-- Default --") },
                                            onClick = {
                                                selectedProfileName = ""
                                                expanded = false
                                                brightness = 0f
                                                contrast = 0f
                                                gamma = 1.0f
                                                enableFXAA = false
                                                enableCRT = false
                                                enableToon = false
                                                enableNTSC = false
                                            }
                                        )
                                        profiles.map { it.split(":")[0] }.sorted().forEach { name ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    selectedProfileName = name
                                                    loadProfile(name)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(onClick = { showAddProfileDialog = true }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Profile")
                                }
                                
                                if (selectedProfileName.isNotEmpty()) {
                                    IconButton(onClick = {
                                        val newProfiles = profiles.toMutableSet()
                                        newProfiles.removeAll { it.startsWith("$selectedProfileName:") }
                                        profiles = newProfiles
                                        preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply()
                                        selectedProfileName = ""
                                    }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Toggles (2 per row)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Effects",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EffectToggle(label = "FXAA", checked = enableFXAA, onCheckedChange = { enableFXAA = it }, modifier = Modifier.weight(1f))
                                EffectToggle(label = "CRT Shader", checked = enableCRT, onCheckedChange = { enableCRT = it }, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EffectToggle(label = "Toon Shader", checked = enableToon, onCheckedChange = { enableToon = it }, modifier = Modifier.weight(1f))
                                EffectToggle(label = "NTSC Combined", checked = enableNTSC, onCheckedChange = { enableNTSC = it }, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Sliders
                    item {
                        EffectSlider(
                            label = stringResource(R.string.brightness),
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = -100f..100f
                        )
                    }
                    item {
                        EffectSlider(
                            label = stringResource(R.string.contrast),
                            value = contrast,
                            onValueChange = { contrast = it },
                            valueRange = -100f..100f
                        )
                    }
                    item {
                        EffectSlider(
                            label = stringResource(R.string.gamma),
                            value = gamma,
                            onValueChange = { gamma = it },
                            valueRange = 0.1f..2.0f
                        )
                    }
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            brightness = 0f
                            contrast = 0f
                            gamma = 1.0f
                            enableFXAA = false
                            enableCRT = false
                            enableToon = false
                            enableNTSC = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                    Button(
                        onClick = {
                            if (selectedProfileName.isNotEmpty()) saveCurrentToProfile(selectedProfileName)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }

    if (showAddProfileDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            title = { Text("New Profile") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotEmpty()) {
                        val newProfiles = profiles.toMutableSet()
                        newProfiles.add("$newName:")
                        profiles = newProfiles
                        preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply()
                        selectedProfileName = newName
                    }
                    showAddProfileDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (label == stringResource(R.string.gamma)) String.format("%.2f", value) else "${value.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EffectToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label, 
                style = MaterialTheme.typography.bodySmall, 
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}
