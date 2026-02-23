package app.gamenative.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import com.winlator.contents.AdrenotoolsManager
import app.gamenative.service.SteamService
import app.gamenative.utils.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.Duration
import kotlin.coroutines.resume

// ─── Navigation ─────────────────────────────────────────────────────────────

private enum class CompNav { SELECTOR, DETAIL, DRIVER_DETAIL, WINE_PROTON_DETAIL }

// ─── Component enum (alphabetical order) ────────────────────────────────────

internal enum class GNComponent(val displayName: String) {
    WINE_PROTON("Wine/Proton"),
    DRIVER("Driver"),
    BOX64("Box64"),
    DXVK("DXVK"),
    FEXCORE("FEXCore"),
    VKD3D("VKD3D"),
    WOWBOX64("WowBox64");

    fun getContentTypes(): List<com.winlator.contents.ContentProfile.ContentType> = when (this) {
        BOX64 -> listOf(com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64)
        DRIVER -> listOf(com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_TURNIP)
        DXVK -> listOf(com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_DXVK)
        FEXCORE -> listOf(com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)
        VKD3D -> listOf(com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_VKD3D)
        WINE_PROTON -> listOf(com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_WINE, com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_PROTON)
        WOWBOX64 -> listOf(com.winlator.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64)
    }
}

// ─── GitHub release data models ──────────────────────────────────────────────

private data class GHRelease(
    val tagName: String,
    val assets: List<GHAsset>,
    val publishedAt: String = ""
)

private data class GHAsset(
    val name: String,
    val downloadUrl: String,
    val releaseName: String = "",
    val releaseDate: String = ""
)

// ─── Component variant: one per sub-type (e.g. DXVK GPLAsync, DXVK NVAPI…) ──

internal data class ComponentVariant(
    val label: String,
    val nightlyTagPrefix: String?,  // null if this variant has no nightly
    val stableTagPrefix: String?,   // null if this variant has no stable
)

private val componentVariants: Map<GNComponent, List<ComponentVariant>> = mapOf(
    GNComponent.DXVK to listOf(
        ComponentVariant("GPLAsync",       "dxvk-nightly-",               "Stable-Dxvk"),
        ComponentVariant("ARM64EC",        "dxvk-arm64ec-nightly-",       "Stable-Arm64ec-Dxvk"),
        ComponentVariant("NVAPI GPLAsync", "dxvk-nvapi-nightly-",         null),
        ComponentVariant("NVAPI ARM64EC",  "dxvk-nvapi-arm64ec-nightly-", null),
        ComponentVariant("Sarek",          null,                           "Sarek"),
    ),
    GNComponent.VKD3D to listOf(
        ComponentVariant("Standard", "vk3dk-nightly-",         "Stable-Vk3dk"),
        ComponentVariant("ARM64EC",  "vk3dk-arm64ec-nightly-", "Stable-Arm64ec-Vk3dk"),
    ),
    GNComponent.BOX64 to listOf(
        ComponentVariant("Standard",    "box64-nightly-",        "Stable-Box64"),
        ComponentVariant("Bionic (WIP)", "bionic-box64-nightly-", null),
    ),
    GNComponent.WOWBOX64 to listOf(
        ComponentVariant("WowBox64", "wowbox64-nightly-", "Stable-wowbox64"),
    ),
    GNComponent.FEXCORE to listOf(
        ComponentVariant("ARM64EC", "fex-nightly-", "Stable-FEX"),
    ),
)

// ─── Tag patterns per component (legacy – used for broad release fetch) ───────

private val nightlyPrefixes = mapOf(
    GNComponent.DXVK     to listOf("dxvk-nightly-", "dxvk-arm64ec-nightly-",
                                    "dxvk-nvapi-nightly-", "dxvk-nvapi-arm64ec-nightly-"),
    GNComponent.VKD3D    to listOf("vk3dk-nightly-", "vk3dk-arm64ec-nightly-"),
    GNComponent.BOX64    to listOf("box64-nightly-", "bionic-box64-nightly-"),
    GNComponent.WOWBOX64 to listOf("wowbox64-nightly-"),
    GNComponent.FEXCORE  to listOf("fex-nightly-"),
)

private val stablePrefixes = mapOf(
    GNComponent.DXVK     to listOf("Stable-Dxvk", "Stable-Arm64ec-Dxvk", "Sarek"),
    GNComponent.VKD3D    to listOf("Stable-Vk3dk", "Stable-Arm64ec-Vk3dk"),
    GNComponent.BOX64    to listOf("Stable-Box64"),
    GNComponent.WOWBOX64 to listOf("Stable-wowbox64"),
    GNComponent.FEXCORE  to listOf("Stable-FEX"),
)

// Extract version from asset file name
private fun extractVersionFromFilename(name: String): String {
    val base = name.removeSuffix(".wcp")
    // If it contains "nightly", include the full base name to differentiate variants (e.g. bionic vs normal)
    if (name.contains("nightly", ignoreCase = true)) {
        return base
    }
    // Otherwise try to find the version number part
    return base.split("-").firstOrNull { seg -> seg.isNotEmpty() && seg[0].isDigit() } ?: base
}

private fun compareSegment(a: String, b: String): Int {
    val aNum = a.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    val bNum = b.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    if (aNum != bNum) return aNum - bNum
    return a.dropWhile { it.isDigit() }.compareTo(b.dropWhile { it.isDigit() })
}

private fun compareVersionStrings(a: String, b: String): Int {
    val aParts = a.split(".")
    val bParts = b.split(".")
    val maxLen = maxOf(aParts.size, bParts.size)
    for (i in 0 until maxLen) {
        val av = if (i < aParts.size) aParts[i] else ""
        val bv = if (i < bParts.size) bParts[i] else ""
        val cmp = compareSegment(av, bv)
        if (cmp != 0) return cmp
    }
    return 0
}

private fun nightlyLabel(tagName: String): String = when {
    tagName.startsWith("bionic-box64-nightly")          -> "Box64 Bionic (WIP)"
    tagName.startsWith("box64-nightly")                  -> "Box64 Standard"
    tagName.startsWith("dxvk-nvapi-arm64ec-nightly")     -> "DXVK NVAPI ARM64EC"
    tagName.startsWith("dxvk-nvapi-nightly")             -> "DXVK NVAPI GPLAsync"
    tagName.startsWith("dxvk-arm64ec-nightly")           -> "DXVK ARM64EC"
    tagName.startsWith("dxvk-nightly")                   -> "DXVK GPLAsync"
    tagName.startsWith("fex-nightly")                    -> "FEXCore ARM64EC"
    tagName.startsWith("vk3dk-arm64ec-nightly")          -> "VKD3D ARM64EC"
    tagName.startsWith("vk3dk-nightly")                  -> "VKD3D Standard"
    tagName.startsWith("wowbox64-nightly")               -> "WowBox64"
    else                                                  -> tagName
}

private fun formatRelativeTime(isoDate: String): String {
    if (isoDate.isEmpty()) return ""
    return try {
        val instant = Instant.parse(isoDate)
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        buildString {
            if (days > 0) append("${days}d ")
            if (days > 0 || hours > 0) append("${hours}h ")
            append("${minutes}m ago")
        }.trim()
    } catch (e: Exception) {
        ""
    }
}

// Returns the unique key for an asset: filename without .wcp extension.
// Used to match installed profiles to the exact release asset.
private fun assetUniqueKey(fileName: String): String = fileName.removeSuffix(".wcp")

// Checks whether an asset is installed by matching its unique key against
// installed profile verNames. Uses bidirectional contains to handle cases
// where ContentsManager abbreviates the verName.
private fun isAssetInstalled(assetFileName: String, profiles: List<ContentProfile>): Boolean {
    val key = assetUniqueKey(assetFileName)
    return profiles.any { prof ->
        prof.verName.equals(key, ignoreCase = true) ||
        prof.verName.contains(key, ignoreCase = true) ||
        key.contains(prof.verName, ignoreCase = true)
    }
}

private fun findInstalledProfile(assetFileName: String, profiles: List<ContentProfile>): ContentProfile? {
    val key = assetUniqueKey(assetFileName)
    return profiles.find { prof ->
        prof.verName.equals(key, ignoreCase = true) ||
        prof.verName.contains(key, ignoreCase = true) ||
        key.contains(prof.verName, ignoreCase = true)
    }
}

private const val XNICK_REPO_URL = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp"
private const val XNICK_API_URL  =
    "https://api.github.com/repos/Xnick417x/Winlator-Bionic-Nightly-wcp/releases?per_page=100"

// ─── Root dialog composable ──────────────────────────────────────────────────

@Composable
fun ComponentsManagerDialog(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember(ctx) { ContentsManager(ctx) }

    var nav by remember { mutableStateOf(CompNav.SELECTOR) }
    var selectedComponent by remember { mutableStateOf<GNComponent?>(null) }

    var isWorking by remember { mutableStateOf(false) }
    var workMessage by remember { mutableStateOf("") }

    val performInstall: (Uri) -> Unit = { uri ->
        scope.launch {
            isWorking = true
            workMessage = "Processing .wcp..."
            try {
                val success = withContext(Dispatchers.IO) {
                    installWcpRobustly(ctx, mgr, uri) { currentStatus ->
                        scope.launch(Dispatchers.Main) { workMessage = currentStatus }
                    }
                }
                
                if (success) {
                    mgr.syncContents()
                    Toast.makeText(ctx, "Installation Complete ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Installation Failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "performInstall error")
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isWorking = false
                workMessage = ""
            }
        }
    }

    val customPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { performInstall(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        // Back Handler for physical back button INSIDE the Dialog
        BackHandler(enabled = true) {
            if (nav != CompNav.SELECTOR) {
                nav = CompNav.SELECTOR
                selectedComponent = null
            } else {
                onDismiss()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape, // Full screen rectangular
            color = MaterialTheme.colorScheme.surface,
        ) {
            when (nav) {
                CompNav.SELECTOR -> ComponentSelectorScreen(
                    onSelectComponent = { comp ->
                        selectedComponent = comp
                        when (comp) {
                            GNComponent.DRIVER      -> nav = CompNav.DRIVER_DETAIL
                            GNComponent.WINE_PROTON -> nav = CompNav.WINE_PROTON_DETAIL
                            else -> nav = CompNav.DETAIL
                        }
                    },
                    onInstallCustom = { customPicker.launch(arrayOf("*/*")) },
                    onDismiss = onDismiss,
                )
                CompNav.DETAIL -> ComponentDetailScreen(
                    component = selectedComponent!!,
                    onBack = {
                        nav = CompNav.SELECTOR
                        selectedComponent = null
                    },
                )
                CompNav.DRIVER_DETAIL -> DriverDetailScreen(
                    onBack = {
                        nav = CompNav.SELECTOR
                        selectedComponent = null
                    }
                )
                CompNav.WINE_PROTON_DETAIL -> WineProtonDetailScreen(
                    onBack = {
                        nav = CompNav.SELECTOR
                        selectedComponent = null
                    }
                )
            }
        }
    }
}

// ─── Screen 1: Component selector ────────────────────────────────────────────

@Composable
private fun ComponentSelectorScreen(
    onSelectComponent: (GNComponent) -> Unit,
    onInstallCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val mgr = remember { ContentsManager(ctx) }
    
    // Periodically refresh if needed, but for now we sync on start
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { mgr.syncContents() }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Title bar - Centered
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Components",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        HorizontalDivider()

        // Component buttons: fixed order (Wine/Proton, Driver, ABC...)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Compute installed state per component
            val components = GNComponent.entries.toList()
            val installedSet = components.filter { comp ->
                if (comp == GNComponent.DRIVER) {
                    AdrenotoolsManager(ctx).enumarateInstalledDrivers().isNotEmpty()
                } else {
                    comp.getContentTypes().any { type ->
                        val profiles = mgr.getProfiles(type)
                        profiles != null && profiles.any { it.remoteUrl == null }
                    }
                }
            }.toSet()

            components.forEach { component ->
                Button(
                    onClick = { onSelectComponent(component) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(
                        text = component.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onInstallCustom,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text("Install Custom", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        HorizontalDivider()

        // Bottom action bar - Back button at bottom left
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart) // Bottom Left
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Close")
            }
        }
    }
}

// ─── Screen 2: Component detail (variant-based, two sorted zones) ─────────────

@Composable
private fun ComponentDetailScreen(
    component: GNComponent,
    onBack: () -> Unit,
) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr   = remember(ctx) { ContentsManager(ctx) }

    var allReleases by remember { mutableStateOf<List<GHRelease>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(true) }
    var loadError   by remember { mutableStateOf<String?>(null) }

    val installedProfiles = remember { mutableStateListOf<ContentProfile>() }
    val refreshInstalled: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            mgr.syncContents()
            val list = mutableListOf<ContentProfile>()
            component.getContentTypes().forEach { type ->
                mgr.getProfiles(type)?.let { list.addAll(it) }
            }
            withContext(Dispatchers.Main) {
                installedProfiles.clear()
                installedProfiles.addAll(list)
            }
        }
    }

    var isWorking        by remember { mutableStateOf(false) }
    var workMessage      by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(-1f) }
    var deleteTarget     by remember { mutableStateOf<ContentProfile?>(null) }

    LaunchedEffect(component) { refreshInstalled() }

    // Fetch all releases from Nick's repo
    LaunchedEffect(component) {
        isLoading = true; loadError = null
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(XNICK_API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                Net.http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            loadError = "HTTP ${resp.code}: ${resp.message}"; isLoading = false
                        }
                        return@withContext
                    }
                    val releases = parseGHReleases(resp.body?.string() ?: "[]")
                    withContext(Dispatchers.Main) { allReleases = releases; isLoading = false }
                }
            } catch (e: Exception) {
                Timber.e(e, "ComponentsManager: fetch releases failed")
                withContext(Dispatchers.Main) { loadError = e.message ?: "Unknown error"; isLoading = false }
            }
        }
    }

    // Download + install a single .wcp asset
    val downloadAndInstall: (GHAsset) -> Unit = { asset ->
        scope.launch {
            isWorking = true; workMessage = "Downloading ${asset.name}…"; downloadProgress = -1f
            try {
                val destFile = File(ctx.cacheDir, asset.name)
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(asset.downloadUrl).build()
                    Net.http.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val body = response.body ?: throw Exception("Empty response body")
                        val total = body.contentLength(); var downloaded = 0L
                        FileOutputStream(destFile).use { out ->
                            body.byteStream().use { inp ->
                                val buf = ByteArray(8192); var n: Int
                                while (inp.read(buf).also { n = it } != -1) {
                                    out.write(buf, 0, n); downloaded += n
                                    if (total > 0) withContext(Dispatchers.Main) {
                                        downloadProgress = downloaded.toFloat() / total
                                    }
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) { workMessage = "Installing…"; downloadProgress = -1f }
                val success = withContext(Dispatchers.IO) {
                    installWcpRobustly(ctx, mgr, Uri.fromFile(destFile)) { s ->
                        scope.launch(Dispatchers.Main) { workMessage = s }
                    }
                }
                destFile.delete()
                if (success) {
                    mgr.syncContents()
                    Toast.makeText(ctx, "Installed ✓", Toast.LENGTH_SHORT).show()
                    refreshInstalled()
                } else {
                    Toast.makeText(ctx, "Installation Failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "ComponentsManager: install error")
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isWorking = false; workMessage = ""; downloadProgress = -1f
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove Component") },
            text = { Text("Uninstall ${deleteTarget!!.verName}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        mgr.removeContent(deleteTarget!!)
                        refreshInstalled()
                        withContext(Dispatchers.Main) { deleteTarget = null }
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Column(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(component.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(XNICK_REPO_URL))) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Source: Winlator-Bionic-Nightly-wcp", fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Fetching latest releases…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                loadError != null -> {
                    Text(
                        "Failed to load releases: $loadError",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                else -> {
                    val variants = componentVariants[component] ?: emptyList()
                    variants.forEachIndexed { idx, variant ->
                        if (idx > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        }

                        // ── Variant header ────────────────────────────────────
                        Text(
                            variant.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))

                        // ── Nightly sub-section for this variant ──────────────
                        if (variant.nightlyTagPrefix != null) {
                            val variantNightlies = allReleases
                                .filter { it.tagName.startsWith(variant.nightlyTagPrefix) }
                                .flatMap { release ->
                                    release.assets
                                        .filter { it.name.endsWith(".wcp") }
                                        .map { it.copy(releaseName = release.tagName, releaseDate = release.publishedAt) }
                                }
                                .sortedByDescending { it.releaseDate }

                            if (variantNightlies.isEmpty()) {
                                Text(
                                    "No nightly builds found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val latestNightly = variantNightlies.first()
                                Button(
                                    onClick = { if (!isWorking) downloadAndInstall(latestNightly) },
                                    enabled = !isWorking,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                ) {
                                    Text("⬇ Download Latest Nightly")
                                }
                                Spacer(Modifier.height(8.dp))

                                // Installed nightlies first, then available
                                val installedNightlies = variantNightlies.filter {
                                    isAssetInstalled(it.name, installedProfiles)
                                }
                                val availableNightlies = variantNightlies.filter {
                                    !isAssetInstalled(it.name, installedProfiles)
                                }

                                if (installedNightlies.isNotEmpty()) {
                                    Text(
                                        "Installed",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        installedNightlies.forEach { asset ->
                                            AssetRow(
                                                asset = asset,
                                                isInstalled = true,
                                                isWorking = isWorking,
                                                onInstall = { downloadAndInstall(asset) },
                                                onDelete = {
                                                    deleteTarget = findInstalledProfile(asset.name, installedProfiles)
                                                },
                                            )
                                        }
                                    }
                                }

                                if (availableNightlies.isNotEmpty()) {
                                    Text(
                                        "Available",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        availableNightlies.forEach { asset ->
                                            AssetRow(
                                                asset = asset,
                                                isInstalled = false,
                                                isWorking = isWorking,
                                                onInstall = { downloadAndInstall(asset) },
                                                onDelete = {},
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Stable sub-section for this variant ───────────────
                        if (variant.stableTagPrefix != null) {
                            if (variant.nightlyTagPrefix != null) Spacer(Modifier.height(12.dp))
                            Text(
                                "Stable",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            val variantStables = allReleases
                                .filter { it.tagName.startsWith(variant.stableTagPrefix) }
                                .flatMap { release ->
                                    release.assets
                                        .filter { it.name.endsWith(".wcp") }
                                        .map { it.copy(releaseName = release.tagName, releaseDate = release.publishedAt) }
                                }
                                .sortedWith { a, b ->
                                    val cmp = compareVersionStrings(
                                        extractVersionFromFilename(b.name),
                                        extractVersionFromFilename(a.name),
                                    )
                                    if (cmp != 0) cmp else b.releaseDate.compareTo(a.releaseDate)
                                }

                            if (variantStables.isEmpty()) {
                                Text(
                                    "No stable releases found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Button(
                                    onClick = { if (!isWorking) downloadAndInstall(variantStables.first()) },
                                    enabled = !isWorking,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary,
                                    ),
                                ) {
                                    Text("⬇ Download Latest Stable")
                                }
                                Spacer(Modifier.height(8.dp))

                                val installedStables = variantStables.filter {
                                    isAssetInstalled(it.name, installedProfiles)
                                }
                                val availableStables = variantStables.filter {
                                    !isAssetInstalled(it.name, installedProfiles)
                                }

                                if (installedStables.isNotEmpty()) {
                                    Text(
                                        "Installed",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        installedStables.forEach { asset ->
                                            AssetRow(
                                                asset = asset,
                                                isInstalled = true,
                                                isWorking = isWorking,
                                                onInstall = { downloadAndInstall(asset) },
                                                onDelete = {
                                                    deleteTarget = findInstalledProfile(asset.name, installedProfiles)
                                                },
                                            )
                                        }
                                    }
                                }

                                if (availableStables.isNotEmpty()) {
                                    Text(
                                        "Available",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        availableStables.forEach { asset ->
                                            AssetRow(
                                                asset = asset,
                                                isInstalled = false,
                                                isWorking = isWorking,
                                                onInstall = { downloadAndInstall(asset) },
                                                onDelete = {},
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Custom-installed (not matched by any known asset) ─────
                    val allKnownAssets = allReleases.flatMap { it.assets }
                    val customInstalled = installedProfiles.filter { prof ->
                        allKnownAssets.none { isAssetInstalled(it.name, listOf(prof)) }
                    }
                    if (customInstalled.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Installed (Custom)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        customInstalled.forEach { prof ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prof.verName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (!prof.desc.isNullOrEmpty()) {
                                        Text(prof.desc, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { deleteTarget = prof }) {
                                    Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // ── Sticky download/install progress bar ────────────────────────────
        if (isWorking) {
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = workMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (downloadProgress in 0f..1f) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Indeterminate — shown during extraction / installation phase
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        HorizontalDivider()
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        }
    }
}

// ─── Reusable asset row ───────────────────────────────────────────────────────

@Composable
private fun AssetRow(
    asset: GHAsset,
    isInstalled: Boolean,
    isWorking: Boolean,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    val bgColor = if (isInstalled)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name.removeSuffix(".wcp"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isInstalled) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (asset.releaseDate.isNotEmpty()) {
                Text(
                    text = formatRelativeTime(asset.releaseDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isInstalled) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            TextButton(onClick = onInstall, enabled = !isWorking) {
                Text("Install")
            }
        }
    }
}

// ─── Screen 3: Driver Manager ────────────────────────────────────────────────

private enum class DriverSource { GN, MTR }

private data class DriverItem(
    val name: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverDetailScreen(
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSource by remember { mutableStateOf(DriverSource.MTR) }
    
    // States
    var isDownloading by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(-1L) }
    
    // Data
    var availableDrivers by remember { mutableStateOf<List<DriverItem>>(emptyList()) }
    var isLoadingManifest by remember { mutableStateOf(true) }

    // Installed drivers
    val installedDrivers = remember { mutableStateListOf<String>() }
    val driverMeta = remember { mutableStateMapOf<String, Pair<String, String>>() }
    var driverToDelete by remember { mutableStateOf<String?>(null) }

    val refreshDriverList: () -> Unit = {
        installedDrivers.clear()
        driverMeta.clear()
        try {
            val mgr = AdrenotoolsManager(ctx)
            val list = mgr.enumarateInstalledDrivers()
            installedDrivers.addAll(list)
            list.forEach { id ->
                val name = mgr.getDriverName(id)
                val version = mgr.getDriverVersion(id)
                driverMeta[id] = name to version
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(selectedSource) {
        isLoadingManifest = true
        availableDrivers = emptyList()
        withContext(Dispatchers.IO) {
            try {
                if (selectedSource == DriverSource.GN) {
                    val gnReq = Request.Builder().url("https://raw.githubusercontent.com/utkarshdalal/gamenative-landing-page/refs/heads/main/data/manifest.json").build()
                    Net.http.newCall(gnReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                            val list = json.entries.map { DriverItem(it.key, it.value.toString().trim('"')) }
                                .sortedByDescending { it.name }
                            withContext(Dispatchers.Main) { availableDrivers = list }
                        }
                    }
                } else {
                    val mtrReq = Request.Builder().url("https://api.github.com/repos/maxjivi05/Components/contents/Drivers").build()
                    Net.http.newCall(mtrReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonArr = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                            val list = jsonArr.map {
                                val obj = it.jsonObject
                                DriverItem(obj["name"]!!.toString().trim('"'), obj["download_url"]!!.toString().trim('"'))
                            }.sortedByDescending { it.name }
                            withContext(Dispatchers.Main) { availableDrivers = list }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Driver manifest load failed")
            } finally {
                withContext(Dispatchers.Main) { isLoadingManifest = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDriverList()
    }

    val handlePickedUri: suspend (Uri) -> String = { uri ->
        try {
            val name = AdrenotoolsManager(ctx).installDriver(uri)
            if (name.isNotEmpty()) "Installed driver: $name" else "Failed (corrupt or exists)"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                isImporting = true
                val res = withContext(Dispatchers.IO) { handlePickedUri(it) }
                if (res.startsWith("Installed")) refreshDriverList()
                Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
                SteamService.isImporting = false
                isImporting = false
            }
        }
    }

    val downloadAndInstall = { item: DriverItem ->
        scope.launch {
            isDownloading = true
            downloadProgress = 0f
            downloadBytes = 0L
            totalBytes = -1L
            try {
                val destFile = File(ctx.cacheDir, item.name + (if (item.name.endsWith(".zip")) "" else ".zip"))
                if (selectedSource == DriverSource.GN) {
                    var lastUpdate = 0L
                    SteamService.fetchFileWithFallback(fileName = "drivers/${item.url}", dest = destFile, context = ctx) { p ->
                         val now = System.currentTimeMillis()
                         if (now - lastUpdate > 300) {
                             lastUpdate = now
                             scope.launch(Dispatchers.Main) { downloadProgress = p.coerceIn(0f, 1f) }
                         }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(item.url).build()
                        Net.http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val body = resp.body ?: throw IOException("Empty body")
                            totalBytes = body.contentLength()
                            val inp = body.byteStream()
                            FileOutputStream(destFile).use { out ->
                                val buf = ByteArray(8192)
                                var n: Int
                                while (inp.read(buf).also { n = it } != -1) {
                                    out.write(buf, 0, n)
                                    downloadBytes += n
                                    if (totalBytes > 0) scope.launch(Dispatchers.Main) { downloadProgress = downloadBytes.toFloat() / totalBytes }
                                }
                            }
                        }
                    }
                }
                isDownloading = false
                isInstalling = true
                val res = withContext(Dispatchers.IO) { handlePickedUri(Uri.fromFile(destFile)) }
                Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
                if (res.startsWith("Installed")) refreshDriverList()
                destFile.delete()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isDownloading = false
                isInstalling = false
            }
        }
    }
    
    // UI Structure
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Driver Manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Source Toggle
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { selectedSource = DriverSource.GN }, modifier = Modifier.weight(1f), colors = if (selectedSource == DriverSource.GN) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()) { Text("GN") }
                Button(onClick = { selectedSource = DriverSource.MTR }, modifier = Modifier.weight(1f), colors = if (selectedSource == DriverSource.MTR) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()) { Text("MTR") }
            }
            
            if (isLoadingManifest) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                // Split availableDrivers into matched (installed) and unmatched (available)
                val availableItems = availableDrivers.filter { item ->
                    installedDrivers.none { id ->
                        val name = driverMeta[id]?.first ?: id
                        name.contains(item.name.removeSuffix(".zip"), ignoreCase = true) || 
                        item.name.contains(name, ignoreCase = true) ||
                        id.contains(item.name.removeSuffix(".zip"), ignoreCase = true)
                    }
                }

                // ── Installed section ─────────────────────────────────────
                if (installedDrivers.isNotEmpty()) {
                    Text("Installed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        installedDrivers.forEach { id ->
                            val meta = driverMeta[id]
                            val displayName = meta?.first ?: id
                            val version = meta?.second ?: ""
                            DriverRow(
                                name = if (version.isNotEmpty()) "$displayName ($version)" else displayName,
                                isInstalled = true,
                                isWorking = isDownloading || isInstalling || isImporting,
                                onInstall = {},
                                onDelete = { driverToDelete = id }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                }

                // ── Available section ─────────────────────────────────────
                if (availableItems.isNotEmpty()) {
                    Text("Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableItems.forEach { item ->
                            DriverRow(
                                name = item.name,
                                isInstalled = false,
                                isWorking = isDownloading || isInstalling || isImporting,
                                onInstall = { downloadAndInstall(item) },
                                onDelete = {}
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // "Installed Drivers" section - fallback for unmatched drivers (custom installs)
                // (Already handled by installedDrivers at top now, but keeping local filter for safety)
                val unmatchedInstalled = installedDrivers.filter { id -> 
                    availableDrivers.none { item ->
                        val name = driverMeta[id]?.first ?: id
                        name.contains(item.name.removeSuffix(".zip"), ignoreCase = true) || 
                        item.name.contains(name, ignoreCase = true) ||
                        id.contains(item.name.removeSuffix(".zip"), ignoreCase = true)
                    }
                }
                // No longer need unmatchedInstalled as it's redundant with the new unified installed section
            }
            
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Button(
                onClick = { SteamService.isImporting = true; launcher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                enabled = !isImporting && !isDownloading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
            ) { Text("Import from Storage (.zip)") }
        }
        
        // Sticky Progress Bar
        if (isDownloading || isInstalling || isImporting) {
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = if (isDownloading) "Downloading..." else if (isInstalling) "Installing..." else "Importing...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (isDownloading && downloadProgress > 0f) {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Footer
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
             TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        }
    }
    
    if (driverToDelete != null) {
        AlertDialog(
            onDismissRequest = { driverToDelete = null },
            title = { Text("Delete Driver") },
            text = { Text("Remove $driverToDelete?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            AdrenotoolsManager(ctx).removeDriver(driverToDelete!!)
                            withContext(Dispatchers.Main) { refreshDriverList() }
                        } catch(e: Exception) { 
                            withContext(Dispatchers.Main) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                        }
                        withContext(Dispatchers.Main) { driverToDelete = null }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { driverToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DriverRow(
    name: String,
    isInstalled: Boolean,
    isWorking: Boolean,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    val bgColor = if (isInstalled)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isInstalled) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (isInstalled) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            TextButton(onClick = onInstall, enabled = !isWorking) {
                Text("Install")
            }
        }
    }
}

// ─── Screen 4: Wine/Proton Manager ───────────────────────────────────────────

private data class WineReleaseItem(
    val name: String,
    val version: String,
    val url: String?,       // if null, use SteamService.fetchFileWithFallback (GameNative repo)
    val fileName: String,   // filename for storage
    val releaseDate: String = ""
)

// Detect wine vs proton family from filename for upgrade-grouping
private fun wineFamily(fileName: String): String {
    val lower = fileName.lowercase()
    return if (lower.contains("proton")) "proton" else "wine"
}

@Composable
private fun WineProtonDetailScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember(ctx) { ContentsManager(ctx) }

    var wineReleases by remember { mutableStateOf<List<WineReleaseItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusMsg by remember { mutableStateOf("") }

    val installedProfiles = remember { mutableStateListOf<ContentProfile>() }
    var deleteTarget by remember { mutableStateOf<ContentProfile?>(null) }

    val refreshInstalled: () -> Unit = {
        installedProfiles.clear()
        val wine = mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)
        val proton = mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)
        val combined = (wine ?: emptyList()) + (proton ?: emptyList())
        installedProfiles.addAll(
            combined.filter { it.remoteUrl == null }.distinctBy { it.type.toString() + it.verName }
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { mgr.syncContents() }
        refreshInstalled()
        scope.launch(Dispatchers.IO) {
            try {
                // 1. GameNative proton-wine GitHub releases (with dates)
                val gnProtonItems = try {
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/GameNative/proton-wine/releases?per_page=50")
                        .header("Accept", "application/vnd.github.v3+json").build()
                    Net.http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList()
                        val releases = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                        releases.flatMap { rel ->
                            val obj = rel.jsonObject
                            val date = obj["published_at"]?.jsonPrimitive?.content ?: ""
                            (obj["assets"]?.jsonArray ?: emptyList()).mapNotNull { el ->
                                val aObj = el.jsonObject
                                val n = aObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                val u = aObj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                if (!n.endsWith(".wcp", ignoreCase = true)) return@mapNotNull null
                                WineReleaseItem(n.removeSuffix(".wcp"), extractVersionFromFilename(n), u, n, date)
                            }
                        }
                    }
                } catch (e: Exception) { Timber.e(e, "proton-wine fetch error"); emptyList() }

                // 2. Nick's repo GameNative tag (GN-proton builds)
                val nickGameNativeItems = try {
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/tags/GameNative")
                        .header("Accept", "application/vnd.github.v3+json").build()
                    Net.http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList()
                        val obj = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                        val date = obj["published_at"]?.jsonPrimitive?.content ?: ""
                        (obj["assets"]?.jsonArray ?: emptyList()).mapNotNull { el ->
                            val aObj = el.jsonObject
                            val n = aObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val u = aObj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            if (!n.endsWith(".wcp", ignoreCase = true)) return@mapNotNull null
                            WineReleaseItem(n.removeSuffix(".wcp"), extractVersionFromFilename(n), u, n, date)
                        }
                    }
                } catch (e: Exception) { Timber.e(e, "Nick GameNative fetch error"); emptyList() }

                // 3. Merge, deduplicate by fileName, sort newest first
                val combined = (gnProtonItems + nickGameNativeItems)
                    .distinctBy { it.fileName }
                    .sortedWith { a, b ->
                        if (a.releaseDate.isNotEmpty() && b.releaseDate.isNotEmpty())
                            b.releaseDate.compareTo(a.releaseDate)
                        else compareVersionStrings(b.version, a.version)
                    }
                withContext(Dispatchers.Main) { wineReleases = combined }
            } catch (e: Exception) {
                Timber.e(e, "Wine/Proton manifest error")
            }
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    // Download and install a single WineReleaseItem
    val downloadAndInstall: (WineReleaseItem) -> Unit = { item ->
        scope.launch {
            isBusy = true; progress = 0f; statusMsg = "Downloading ${item.name}…"
            try {
                val dest = File(ctx.cacheDir, item.fileName)
                var lastUpdate = 0L
                if (item.url != null) {
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(item.url).build()
                        Net.http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val body = resp.body ?: throw IOException("Empty body")
                            val total = body.contentLength(); var dl = 0L
                            FileOutputStream(dest).use { out ->
                                body.byteStream().use { inp ->
                                    val buf = ByteArray(8192); var n: Int
                                    while (inp.read(buf).also { n = it } != -1) {
                                        out.write(buf, 0, n); dl += n
                                        val now = System.currentTimeMillis()
                                        if (total > 0 && now - lastUpdate > 300) {
                                            lastUpdate = now
                                            withContext(Dispatchers.Main) { progress = dl.toFloat() / total }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    SteamService.fetchFileWithFallback(item.fileName, dest, ctx) { p ->
                        if (System.currentTimeMillis() - lastUpdate > 300) {
                            lastUpdate = System.currentTimeMillis()
                            scope.launch(Dispatchers.Main) { progress = p.coerceIn(0f, 1f) }
                        }
                    }
                }
                statusMsg = "Installing…"; progress = -1f
                val ok = withContext(Dispatchers.IO) {
                    installWcpRobustly(ctx, mgr, Uri.fromFile(dest)) { s ->
                        scope.launch(Dispatchers.Main) { statusMsg = s }
                    }
                }
                dest.delete()
                if (ok) {
                    mgr.syncContents()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Installed ✓", Toast.LENGTH_SHORT).show()
                        refreshInstalled()
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(ctx, "Install Failed", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally { isBusy = false; progress = 0f; statusMsg = "" }
        }
    }

    // Upgrade: uninstall old, install new (no delete prompt needed)
    val upgradeInstall: (ContentProfile, WineReleaseItem) -> Unit = { oldProf, newItem ->
        scope.launch {
            isBusy = true; progress = 0f; statusMsg = "Downloading ${newItem.name}…"
            try {
                val dest = File(ctx.cacheDir, newItem.fileName)
                var lastUpdate = 0L
                if (newItem.url != null) {
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(newItem.url).build()
                        Net.http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val body = resp.body ?: throw IOException("Empty body")
                            val total = body.contentLength(); var dl = 0L
                            FileOutputStream(dest).use { out ->
                                body.byteStream().use { inp ->
                                    val buf = ByteArray(8192); var n: Int
                                    while (inp.read(buf).also { n = it } != -1) {
                                        out.write(buf, 0, n); dl += n
                                        val now = System.currentTimeMillis()
                                        if (total > 0 && now - lastUpdate > 300) {
                                            lastUpdate = now
                                            withContext(Dispatchers.Main) { progress = dl.toFloat() / total }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    SteamService.fetchFileWithFallback(newItem.fileName, dest, ctx) { p ->
                        if (System.currentTimeMillis() - lastUpdate > 300) {
                            lastUpdate = System.currentTimeMillis()
                            scope.launch(Dispatchers.Main) { progress = p.coerceIn(0f, 1f) }
                        }
                    }
                }
                // Uninstall old first
                statusMsg = "Removing old version…"; progress = -1f
                withContext(Dispatchers.IO) {
                    mgr.removeContent(oldProf)
                    mgr.syncContents()
                }
                // Install new
                statusMsg = "Installing ${newItem.name}…"
                val ok = withContext(Dispatchers.IO) {
                    installWcpRobustly(ctx, mgr, Uri.fromFile(dest)) { s ->
                        scope.launch(Dispatchers.Main) { statusMsg = s }
                    }
                }
                dest.delete()
                if (ok) {
                    mgr.syncContents()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Upgraded to ${newItem.name} ✓", Toast.LENGTH_SHORT).show()
                        refreshInstalled()
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(ctx, "Upgrade Failed", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally { isBusy = false; progress = 0f; statusMsg = "" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                isBusy = true; progress = -1f; statusMsg = "Importing…"
                try {
                    val ok = withContext(Dispatchers.IO) {
                        installWcpRobustly(ctx, mgr, it) { s -> scope.launch(Dispatchers.Main) { statusMsg = s } }
                    }
                    if (ok) {
                        mgr.syncContents()
                        withContext(Dispatchers.Main) {
                            refreshInstalled()
                            Toast.makeText(ctx, "Imported ✓", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) { Toast.makeText(ctx, "Import Failed", Toast.LENGTH_SHORT).show() }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Manual import error")
                    Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally { isBusy = false; statusMsg = ""; progress = 0f }
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text("Wine / Proton", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {

            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Fetching releases…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else -> {
                    // ── Installed section ─────────────────────────────────────
                    val matchedInstalled = installedProfiles.filter { prof ->
                        wineReleases.any { isAssetInstalled(it.fileName, listOf(prof)) }
                    }
                    val unmatchedInstalled = installedProfiles.filter { prof ->
                        wineReleases.none { isAssetInstalled(it.fileName, listOf(prof)) }
                    }

                    if (matchedInstalled.isNotEmpty() || unmatchedInstalled.isNotEmpty()) {
                        Text("Installed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            matchedInstalled.sortedByDescending { prof ->
                                wineReleases.find { isAssetInstalled(it.fileName, listOf(prof)) }?.releaseDate ?: ""
                            }.forEach { prof ->
                                val matchedRelease = wineReleases.find { isAssetInstalled(it.fileName, listOf(prof)) }
                                // Check for newer version in same family
                                val family = matchedRelease?.let { wineFamily(it.fileName) } ?: wineFamily(prof.verName)
                                val newerRelease = wineReleases
                                    .filter { wineFamily(it.fileName) == family }
                                    .firstOrNull { candidate ->
                                        matchedRelease != null &&
                                        candidate.releaseDate.isNotEmpty() &&
                                        matchedRelease.releaseDate.isNotEmpty() &&
                                        candidate.releaseDate > matchedRelease.releaseDate &&
                                        !isAssetInstalled(candidate.fileName, installedProfiles)
                                    }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small,
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prof.verName, fontWeight = FontWeight.SemiBold)
                                        if (matchedRelease?.releaseDate?.isNotEmpty() == true) {
                                            Text(
                                                "Released ${formatRelativeTime(matchedRelease.releaseDate)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (newerRelease != null) {
                                        TextButton(
                                            onClick = { if (!isBusy) upgradeInstall(prof, newerRelease) },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary,
                                            ),
                                        ) { Text("Upgrade") }
                                    }
                                    IconButton(onClick = { deleteTarget = prof }) {
                                        Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            // Unmatched installed (custom/local)
                            unmatchedInstalled.forEach { prof ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small,
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prof.verName, fontWeight = FontWeight.SemiBold)
                                        Text("Custom install", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { deleteTarget = prof }) {
                                        Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Available section ─────────────────────────────────────
                    val available = wineReleases.filter { item ->
                        !isAssetInstalled(item.fileName, installedProfiles)
                    }

                    if (available.isNotEmpty()) {
                        Text("Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            available.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.small,
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold)
                                        if (item.releaseDate.isNotEmpty()) {
                                            Text(
                                                "Released ${formatRelativeTime(item.releaseDate)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { if (!isBusy) downloadAndInstall(item) },
                                        enabled = !isBusy,
                                    ) { Text("Install") }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    } else if (!isLoading && installedProfiles.isEmpty()) {
                        Text(
                            "No releases found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) { Text("Import .wcp Package") }
                }
            }
        }

        // ── Sticky download/install progress bar ────────────────────────────
        if (isBusy) {
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = statusMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (progress in 0f..1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Indeterminate — during uninstall / extraction / installation phase
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        HorizontalDivider()
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Uninstall") },
            text = { Text("Remove ${deleteTarget!!.verName}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        mgr.removeContent(deleteTarget!!)
                        mgr.syncContents()
                        withContext(Dispatchers.Main) { refreshInstalled(); deleteTarget = null }
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

// ─── GitHub JSON parser ──────────────────────────────────────────────────────

private fun parseGHReleases(jsonStr: String): List<GHRelease> = try {
    Json.parseToJsonElement(jsonStr).jsonArray.map { element ->
        val obj     = element.jsonObject
        val tagName = obj["tag_name"]?.jsonPrimitive?.content ?: ""
        val publishedAt = obj["published_at"]?.jsonPrimitive?.content ?: ""
        val assets  = obj["assets"]?.jsonArray?.mapNotNull { assetEl ->
            val aObj = assetEl.jsonObject
            val name = aObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url  = aObj["browser_download_url"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            GHAsset(name = name, downloadUrl = url)
        } ?: emptyList()
        GHRelease(tagName = tagName, assets = assets, publishedAt = publishedAt)
    }
} catch (e: Exception) {
    Timber.e(e, "ComponentsManager: JSON parse error")
    emptyList()
}

/**
 * Performs a robust, non-blocking installation of a .wcp package using coroutines.
 * Replaces old latch-based logic that caused UI freezes.
 */
private suspend fun installWcpRobustly(
    context: android.content.Context,
    mgr: ContentsManager,
    uri: Uri,
    onStatusUpdate: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        onStatusUpdate("Validating package...")
        val profile = suspendInstallCallback<ContentProfile> { callback ->
            mgr.extraContentFile(uri, callback)
        } ?: return@withContext false

        onStatusUpdate("Installing ${profile.verName}...")
        val finishedProfile = suspendInstallCallback<ContentProfile> { callback ->
            mgr.finishInstallContent(profile, callback)
        }
        
        finishedProfile != null
    } catch (e: Exception) {
        Timber.e(e, "Robust install failed")
        false
    }
}

/**
 * Helper to convert legacy async callbacks into suspendable functions.
 */
private suspend fun <T> suspendInstallCallback(
    block: (ContentsManager.OnInstallFinishedCallback) -> Unit
): T? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    block(object : ContentsManager.OnInstallFinishedCallback {
        override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
            if (continuation.isActive) {
                // If it exists, we now allow overwrite, so it's a success path in the context of the initial validation check?
                // Actually, for extraContentFile (validation), ERROR_EXIST shouldn't happen or means something different.
                // For finishInstallContent, ERROR_EXIST returns "Already installed" in the legacy code, but we want to allow it.
                // However, ContentsManager likely handles the overwrite internally if we handle the error or if we check before.
                // But since we can't change ContentsManager easily, we'll assume robust install handles it or we accept the error as non-fatal if possible.
                // For now, return null on failure.
                continuation.resume(null) { /* cancel cleanup */ }
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun onSucceed(result: ContentProfile) {
            if (continuation.isActive) {
                continuation.resume(result as T) { /* cancel cleanup */ }
            }
        }
    })
}
