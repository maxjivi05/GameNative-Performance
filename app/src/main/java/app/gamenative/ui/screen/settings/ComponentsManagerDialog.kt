package app.gamenative.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch

// ─── Navigation ─────────────────────────────────────────────────────────────

private enum class CompNav { SELECTOR, DETAIL, DRIVER_DETAIL, WINE_PROTON_DETAIL }

// ─── Component enum (alphabetical order) ────────────────────────────────────

internal enum class GNComponent(val displayName: String) {
    BOX64("Box64"),
    DRIVER("Driver"),
    DXVK("DXVK"),
    FEXCORE("FEXCore"),
    VKD3D("VKD3D"),
    WINE_PROTON("Wine/Proton"),
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

private data class GHRelease(val tagName: String, val assets: List<GHAsset>)
private data class GHAsset(val name: String, val downloadUrl: String, val releaseName: String = "")

// ─── Tag patterns per component ──────────────────────────────────────────────

private val nightlyPrefixes = mapOf(
    GNComponent.DXVK     to listOf("dxvk-nightly-", "dxvk-arm64ec-nightly-"),
    GNComponent.VKD3D    to listOf("vk3dk-nightly-", "vk3dk-arm64ec-nightly-"),
    GNComponent.BOX64    to listOf("box64-nightly-", "bionic-box64-nightly-"),
    GNComponent.WOWBOX64 to listOf("wowbox64-nightly-"),
    GNComponent.FEXCORE  to listOf("fex-nightly-"),
)

private val stablePrefixes = mapOf(
    GNComponent.DXVK     to listOf("Stable-Dxvk", "Stable-Arm64ec-Dxvk"),
    GNComponent.VKD3D    to listOf("Stable-Vk3dk", "Stable-Arm64ec-Vk3dk"),
    GNComponent.BOX64    to listOf("Stable-Box64"),
    GNComponent.WOWBOX64 to listOf("Stable-wowbox64"),
    GNComponent.FEXCORE  to listOf("Stable-FEX"),
)

// Extract version from asset file name: FIRST dash-separated segment that STARTS with a digit.
// e.g. "Box64-0.4.1-fix-0.wcp"        → "0.4.1"  (not "0", the trailing patch number)
//      "Dxvk-2.7.1-gplasync-1.wcp"    → "2.7.1"
//      "FEX-2601.wcp"                  → "2601"
//      "FEX-2512G.wcp"                 → "2512G"
//      "Vk3dk-Proton-3.0b-2763dd2-0"  → "3.0b"
private fun extractVersionFromFilename(name: String): String {
    val base = name.removeSuffix(".wcp")
    return base.split("-").firstOrNull { seg -> seg.isNotEmpty() && seg[0].isDigit() } ?: ""
}

// Compare a single segment like "5", "5a", "2550", "2550a" — letter suffix makes it bigger.
private fun compareSegment(a: String, b: String): Int {
    val aNum = a.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    val bNum = b.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    if (aNum != bNum) return aNum - bNum
    return a.dropWhile { it.isDigit() }.compareTo(b.dropWhile { it.isDigit() })
}

// Compare dotted version strings numerically with letter-suffix support per segment.
// e.g. "0.4.1" > "0.3.5", "2601" > "2550", "2550a" > "2550", "1b" > "1a"
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

// Human-readable label for a nightly build based on its release tag
private fun nightlyLabel(tagName: String): String = when {
    tagName.startsWith("bionic-box64-nightly")     -> "Box64 Bionic (WIP)"
    tagName.startsWith("box64-nightly")             -> "Box64 Standard"
    tagName.startsWith("dxvk-arm64ec-nightly")      -> "DXVK ARM64EC"
    tagName.startsWith("dxvk-nightly")              -> "DXVK GPLAsync"
    tagName.startsWith("fex-nightly")               -> "FEXCore ARM64EC"
    tagName.startsWith("vk3dk-arm64ec-nightly")     -> "VKD3D ARM64EC"
    tagName.startsWith("vk3dk-nightly")             -> "VKD3D Standard"
    tagName.startsWith("wowbox64-nightly")          -> "WowBox64"
    else                                             -> tagName
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
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
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

        // Title bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Components",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        HorizontalDivider()

        // Component buttons (alphabetical – driven by enum declaration order)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GNComponent.entries.forEach { component ->
                val isInstalled = component.getContentTypes().any { type ->
                    val profiles = mgr.getProfiles(type)
                    profiles != null && profiles.any { it.remoteUrl == null }
                }

                Button(
                    onClick = {
                        if (isInstalled && component != GNComponent.DRIVER && component != GNComponent.WINE_PROTON) {
                            Toast.makeText(ctx, "Already Installed", Toast.LENGTH_SHORT).show()
                        }
                        onSelectComponent(component)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInstalled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isInstalled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isInstalled && component != GNComponent.DRIVER && component != GNComponent.WINE_PROTON) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = component.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Install Custom Button
            Button(
                onClick = onInstallCustom,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text(
                    text = "Install Custom",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        HorizontalDivider()

        // Bottom close button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

// ─── Screen 2: Component detail (nightly + stable releases) ──────────────────

@Composable
private fun ComponentDetailScreen(
    component: GNComponent,
    onBack: () -> Unit,
) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr   = remember(ctx) { ContentsManager(ctx) }

    // All GitHub releases (fetched once)
    var allReleases by remember { mutableStateOf<List<GHRelease>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(true) }
    var loadError   by remember { mutableStateOf<String?>(null) }

    // Installed profiles
    var installedProfiles = remember { mutableStateListOf<ContentProfile>() }
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

    LaunchedEffect(component) {
        refreshInstalled()
    }

    // Derived lists
    val nightlyAssets = remember(allReleases) {
        (nightlyPrefixes[component] ?: emptyList()).flatMap { prefix ->
            allReleases
                .filter { it.tagName.startsWith(prefix) }
                .take(1) // only the most recent release for each prefix
                .flatMap { release ->
                    release.assets
                        .filter { it.name.endsWith(".wcp") }
                        .map { it.copy(releaseName = release.tagName) }
                }
        }
    }

    val stableAssets = remember(allReleases) {
        (stablePrefixes[component] ?: emptyList())
            .flatMap { prefix ->
                allReleases
                    .filter { it.tagName.startsWith(prefix) }
                    .flatMap { release ->
                        release.assets
                            .filter { it.name.endsWith(".wcp") }
                            .map { it.copy(releaseName = release.tagName) }
                    }
            }
            .sortedWith { a, b ->
                // Sort by version in the asset file name, highest first
                compareVersionStrings(
                    extractVersionFromFilename(b.name),
                    extractVersionFromFilename(a.name),
                )
            }
    }

    // Install state
    var isWorking        by remember { mutableStateOf(false) }
    var workMessage      by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(-1f) } // -1 = indeterminate
    var selectedAsset    by remember { mutableStateOf<GHAsset?>(null) }
    var deleteTarget     by remember { mutableStateOf<ContentProfile?>(null) }

    // Fetch releases when screen opens (or component changes)
    LaunchedEffect(component) {
        isLoading = true
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(XNICK_API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                Net.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            loadError = "HTTP ${response.code}: ${response.message}"
                            isLoading = false
                        }
                        return@withContext
                    }
                    val releases = parseGHReleases(response.body?.string() ?: "[]")
                    withContext(Dispatchers.Main) {
                        allReleases = releases
                        isLoading   = false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ComponentsManager: fetch releases failed")
                withContext(Dispatchers.Main) {
                    loadError = e.message ?: "Unknown error"
                    isLoading = false
                }
            }
        }
    }

    // Download + install a single .wcp asset
    val downloadAndInstall: (GHAsset) -> Unit = { asset ->
        scope.launch {
            isWorking        = true
            workMessage      = "Downloading ${asset.name}…"
            downloadProgress = -1f
            try {
                val destFile = File(ctx.cacheDir, asset.name)

                // 1. Download
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(asset.downloadUrl).build()
                    Net.http.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val body = response.body ?: throw Exception("Empty response body")
                        val total = body.contentLength()
                        var downloaded = 0L
                        FileOutputStream(destFile).use { out ->
                            body.byteStream().use { inp ->
                                val buf = ByteArray(8192)
                                var n: Int
                                while (inp.read(buf).also { n = it } != -1) {
                                    out.write(buf, 0, n)
                                    downloaded += n
                                    if (total > 0) {
                                        val p = downloaded.toFloat() / total
                                        withContext(Dispatchers.Main) { downloadProgress = p }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Extract / validate
                withContext(Dispatchers.Main) {
                    workMessage      = "Validating…"
                    downloadProgress = -1f
                }

                val uri = Uri.fromFile(destFile)
                val (profile, fail) = withContext(Dispatchers.IO) {
                    var p: ContentProfile? = null
                    var f: ContentsManager.InstallFailedReason? = null
                    val latch = CountDownLatch(1)
                    mgr.extraContentFile(uri, object : ContentsManager.OnInstallFinishedCallback {
                        override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                            f = reason; latch.countDown()
                        }
                        override fun onSucceed(prof: ContentProfile) {
                            p = prof; latch.countDown()
                        }
                    })
                    latch.await()
                    p to f
                }

                if (profile == null) {
                    Toast.makeText(ctx, "Validation failed: $fail", Toast.LENGTH_SHORT).show()
                    destFile.delete()
                    return@launch
                }

                // 3. Finish install
                withContext(Dispatchers.Main) { workMessage = "Installing…" }

                val msg = withContext(Dispatchers.IO) {
                    var result = ""
                    val latch  = CountDownLatch(1)
                    mgr.finishInstallContent(profile, object : ContentsManager.OnInstallFinishedCallback {
                        override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                            result = when (reason) {
                                ContentsManager.InstallFailedReason.ERROR_EXIST   -> "Already installed"
                                ContentsManager.InstallFailedReason.ERROR_NOSPACE -> "Not enough storage"
                                else -> "Install failed: $reason"
                            }
                            latch.countDown()
                        }
                        override fun onSucceed(prof: ContentProfile) {
                            result = "Installed ${prof.verName} ✓"
                            latch.countDown()
                        }
                    })
                    latch.await()
                    result
                }

                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                destFile.delete()
                refreshInstalled()

            } catch (e: Exception) {
                Timber.e(e, "ComponentsManager: install error")
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isWorking        = false
                workMessage      = ""
                downloadProgress = -1f
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove Component") },
            text = { Text("Are you sure you want to uninstall ${deleteTarget!!.verName}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        mgr.removeContent(deleteTarget!!)
                        refreshInstalled()
                        withContext(Dispatchers.Main) { deleteTarget = null }
                    }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Column(modifier = Modifier.fillMaxSize()) {

        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Text(
                text = component.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                textAlign = TextAlign.Center,
            )
        }

        HorizontalDivider()

        // Scrollable body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {

            // Repo source button
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(XNICK_REPO_URL))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Xnick417x", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Source: Winlator-Bionic-Nightly-wcp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Fetching latest releases…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (loadError != null) {
                Text(
                    text = "Failed to load releases: $loadError",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {

                // ── Nightly section ──────────────────────────────────────────
                Text(
                    text = "Nightly",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                if (nightlyAssets.isEmpty()) {
                    Text(
                        text = "No nightly builds found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        nightlyAssets.forEach { asset ->
                            val isInstalled = installedProfiles.any { 
                                it.verName.contains(extractVersionFromFilename(asset.name)) || 
                                it.verName.contains(nightlyLabel(asset.releaseName)) 
                            }
                            Button(
                                onClick = { 
                                    if (isInstalled) Toast.makeText(ctx, "Already Installed", Toast.LENGTH_SHORT).show()
                                    if (!isWorking) downloadAndInstall(asset) 
                                },
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (isInstalled) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.buttonColors()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isInstalled) {
                                        Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("⬇ ${nightlyLabel(asset.releaseName)} Nightly")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // ── Stable section ───────────────────────────────────────────
                Text(
                    text = "Stable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                val latestStable = stableAssets.firstOrNull()
                if (latestStable == null) {
                    Text(
                        text = "No stable releases found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val isInstalled = installedProfiles.any { it.verName.contains(extractVersionFromFilename(latestStable.name)) }
                    Button(
                        onClick = { 
                            if (isInstalled) Toast.makeText(ctx, "Already Installed", Toast.LENGTH_SHORT).show()
                            if (!isWorking) downloadAndInstall(latestStable) 
                        },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (isInstalled) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isInstalled) {
                                Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("⬇ Install Latest Stable")
                        }
                    }
                }

                // ── Download progress ────────────────────────────────────────
                if (isWorking) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = workMessage,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (downloadProgress in 0f..1f) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // ── Stable releases list ─────────────────────────────────────
                Text(
                    text = "Stable Releases",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                if (stableAssets.isEmpty()) {
                    Text(
                        text = "No stable releases available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    stableAssets.forEach { asset ->
                        val isSelected = selectedAsset == asset
                        val matchingProfile = installedProfiles.find { it.verName.contains(extractVersionFromFilename(asset.name)) }
                        val isInstalled = matchingProfile != null
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        if (isInstalled) Toast.makeText(ctx, "Already Installed", Toast.LENGTH_SHORT).show()
                                        selectedAsset = if (isSelected) null else asset
                                    },
                                )
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isInstalled) {
                                Checkbox(checked = true, onCheckedChange = null)
                            } else {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedAsset = if (isSelected) null else asset },
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = asset.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = asset.releaseName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isInstalled) {
                                IconButton(onClick = { deleteTarget = matchingProfile }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // ── Custom / Installed Only section ──────────────────────────
                val customInstalled = installedProfiles.filter { prof ->
                    stableAssets.none { it.name.contains(prof.verName) || prof.verName.contains(extractVersionFromFilename(it.name)) } &&
                    nightlyAssets.none { prof.verName.contains(extractVersionFromFilename(it.name)) }
                }

                if (customInstalled.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Installed Custom",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    customInstalled.forEach { prof ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = true, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prof.verName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (!prof.desc.isNullOrEmpty()) {
                                    Text(
                                        text = prof.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { deleteTarget = prof }) {
                                Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Bottom action bar ────────────────────────────────────────────────
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }

            if (selectedAsset != null) {
                Button(
                    onClick = {
                        if (!isWorking) downloadAndInstall(selectedAsset!!)
                    },
                    enabled = !isWorking,
                ) {
                    Text("Install")
                }
            }
        }
    }
}

// ─── Screen 3: Driver Manager ────────────────────────────────────────────────

private enum class DriverSource { GN, MTR }

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
    var lastMessage by remember { mutableStateOf<String?>(null) }
    
    // Manifests
    var driverManifest by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mtrDriverManifest by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoadingManifest by remember { mutableStateOf(true) }
    
    // Dropdown state
    var isExpanded by remember { mutableStateOf(false) }
    var selectedDriverKey by remember { mutableStateOf("") }

    // Installed drivers
    val installedDrivers = remember { mutableStateListOf<String>() }
    val driverMeta = remember { mutableStateMapOf<String, Pair<String, String>>() }
    var driverToDelete by remember { mutableStateOf<String?>(null) }

    val refreshDriverList: () -> Unit = {
        installedDrivers.clear()
        driverMeta.clear()
        try {
            val list = AdrenotoolsManager(ctx).enumarateInstalledDrivers()
            installedDrivers.addAll(list)
            val mgr = AdrenotoolsManager(ctx)
            list.forEach { id ->
                val name = mgr.getDriverName(id)
                val version = mgr.getDriverVersion(id)
                driverMeta[id] = name to version
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        refreshDriverList()
        scope.launch(Dispatchers.IO) {
            try {
                // Fetch GN
                val gnReq = Request.Builder().url("https://raw.githubusercontent.com/utkarshdalal/gamenative-landing-page/refs/heads/main/data/manifest.json").build()
                Net.http.newCall(gnReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                        val map = json.entries.associate { it.key to it.value.toString().trim('"') }
                        withContext(Dispatchers.Main) { driverManifest = map }
                    }
                }
                // Fetch MTR
                val mtrReq = Request.Builder().url("https://api.github.com/repos/maxjivi05/Components/contents/Drivers").build()
                Net.http.newCall(mtrReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val jsonArr = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                        val map = jsonArr.associate {
                            val obj = it.jsonObject
                            obj["name"]!!.toString().trim('"') to obj["download_url"]!!.toString().trim('"')
                        }
                        withContext(Dispatchers.Main) { mtrDriverManifest = map }
                    }
                }
                withContext(Dispatchers.Main) { isLoadingManifest = false }
            } catch (e: Exception) {
                Timber.e(e, "Driver manifest load failed")
                withContext(Dispatchers.Main) { isLoadingManifest = false }
            }
        }
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
                lastMessage = res
                if (res.startsWith("Installed")) refreshDriverList()
                Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
                SteamService.isImporting = false
                isImporting = false
            }
        }
    }

    val downloadAndInstall = { fileName: String, url: String? ->
        scope.launch {
            isDownloading = true
            downloadProgress = 0f
            downloadBytes = 0L
            totalBytes = -1L
            try {
                val destFile = File(ctx.cacheDir, fileName)
                if (url == null) {
                    // GN path via SteamService fallback
                    var lastUpdate = 0L
                    SteamService.fetchFileWithFallback(fileName = "drivers/$fileName", dest = destFile, context = ctx) { p ->
                         val now = System.currentTimeMillis()
                         if (now - lastUpdate > 300) {
                             lastUpdate = now
                             scope.launch(Dispatchers.Main) { downloadProgress = p.coerceIn(0f, 1f) }
                         }
                    }
                } else {
                    // MTR direct URL
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(url).build()
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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Text("Driver Manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 8.dp))
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
                val currentManifest = if (selectedSource == DriverSource.GN) driverManifest else mtrDriverManifest
                if (currentManifest.isNotEmpty()) {
                    Text("Available Online", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = !isExpanded }) {
                        OutlinedTextField(
                            value = selectedDriverKey, 
                            onValueChange = {}, 
                            readOnly = true, 
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) }, 
                            modifier = Modifier.fillMaxWidth().menuAnchor(), 
                            placeholder = { Text("Select a driver") }
                        )
                        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                            val keys = currentManifest.keys.toList().sortedDescending()
                            keys.forEach { k -> DropdownMenuItem(text = { Text(k) }, onClick = { selectedDriverKey = k; isExpanded = false }) }
                        }
                    }
                    if (selectedDriverKey.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (selectedSource == DriverSource.GN) downloadAndInstall(currentManifest[selectedDriverKey]!!, null)
                                else downloadAndInstall(selectedDriverKey, currentManifest[selectedDriverKey])
                            },
                            enabled = !isDownloading && !isImporting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isDownloading) "Downloading..." else "Download & Install") }
                        if (isDownloading) LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            
            Button(
                onClick = { SteamService.isImporting = true; launcher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                enabled = !isImporting && !isDownloading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
            ) { Text("Import from Storage (.zip)") }
            
            if (isImporting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            
            Text("Installed Drivers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            if (installedDrivers.isEmpty()) {
                Text("No custom drivers installed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                installedDrivers.forEach { id ->
                    val meta = driverMeta[id]
                    val display = if (!meta?.first.isNullOrEmpty()) meta?.first!! else id
                    Row(
                         modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                         verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(display, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { driverToDelete = id }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
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
                    try {
                        AdrenotoolsManager(ctx).removeDriver(driverToDelete!!)
                        refreshDriverList()
                    } catch(e: Exception) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                    driverToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { driverToDelete = null }) { Text("Cancel") } }
        )
    }
}

// ─── Screen 4: Wine/Proton Manager ───────────────────────────────────────────

private data class WineReleaseItem(
    val name: String,
    val version: String,
    val url: String?,       // if null, use SteamService.fetchFileWithFallback (GameNative repo)
    val fileName: String,   // filename for storage
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WineProtonDetailScreen(
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember(ctx) { ContentsManager(ctx) }
    
    var wineReleases by remember { mutableStateOf<List<WineReleaseItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isExpanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<WineReleaseItem?>(null) }
    
    var isBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusMsg by remember { mutableStateOf("") }
    
    val installedProfiles = remember { mutableStateListOf<ContentProfile>() }
    var deleteTarget by remember { mutableStateOf<ContentProfile?>(null) }
    
    val refreshInstalled = {
        installedProfiles.clear()
        val wine = mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)
        val proton = mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)
        val combined = (wine ?: emptyList()) + (proton ?: emptyList())
        installedProfiles.addAll(combined.filter { it.remoteUrl == null }.distinctBy { it.type.toString() + it.verName })
    }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { mgr.syncContents() }
        refreshInstalled()
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch GameNative internal component manifest
                val gnItems = try {
                    val req = Request.Builder().url("https://downloads.gamenative.app/component-manifest.json").build()
                    Net.http.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                            json.entries
                                .filter { it.key.startsWith("wine", true) || it.key.startsWith("proton", true) }
                                .map { 
                                    val name = it.key
                                    val file = it.value.toString().removeSurrounding("\"")
                                    // Extract version for sorting: "wine-9.3" -> "9.3"
                                    val ver = extractVersionFromFilename(name)
                                    WineReleaseItem(name, ver, null, file)
                                }
                        } else emptyList()
                    }
                } catch (e: Exception) { 
                    Timber.e(e, "Wine GN manifest error")
                    emptyList() 
                }

                // 2. Fetch ALL GameNative proton-wine releases (X86_64, ARM64EC, etc.)
                val gnProtonWineItems = try {
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/GameNative/proton-wine/releases?per_page=50")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    Net.http.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val releases = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                            releases.flatMap { release ->
                                val assets = release.jsonObject["assets"]?.jsonArray ?: emptyList()
                                assets.mapNotNull { element ->
                                    val obj = element.jsonObject
                                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                    val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                    if (!name.endsWith(".wcp", ignoreCase = true)) return@mapNotNull null

                                    // For display name, use the full filename without extension to avoid confusion (e.g. Proton-10.0-4)
                                    val displayName = name.removeSuffix(".wcp")
                                    
                                    // For sorting, extract the version part
                                    val ver = extractVersionFromFilename(name)
                                    
                                    WineReleaseItem(displayName, ver, url, name)
                                }
                            }
                        } else emptyList()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "GameNative proton-wine manifest error")
                    emptyList()
                }

                // 3. Merge and Sort (Newest Version First)
                // Filter out Nick's repo as requested and combine GN internal + GN proton-wine
                val combined = (gnItems + gnProtonWineItems).sortedWith { a, b ->
                    // Primary sort by version string
                    val verCmp = compareVersionStrings(b.version, a.version)
                    if (verCmp != 0) verCmp else b.name.compareTo(a.name) // Secondary sort by full name
                }

                withContext(Dispatchers.Main) { 
                    wineReleases = combined.distinctBy { it.fileName } 
                }
            } catch (e: Exception) { 
                Timber.e(e, "Wine manifest error") 
            }
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }
    
    val downloadAndInstall = { item: WineReleaseItem ->
        scope.launch {
            isBusy = true; progress = 0f; statusMsg = "Downloading..."
            try {
                val dest = File(ctx.cacheDir, item.fileName)
                var lastUpdate = 0L

                if (item.url != null) {
                    // Direct URL download (GitHub)
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(item.url).build()
                        Net.http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val body = resp.body ?: throw IOException("Empty body")
                            val total = body.contentLength()
                            val inp = body.byteStream()
                            FileOutputStream(dest).use { out ->
                                val buf = ByteArray(8192)
                                var downloaded = 0L
                                var n: Int
                                while (inp.read(buf).also { n = it } != -1) {
                                    out.write(buf, 0, n)
                                    downloaded += n.toLong()
                                    val now = System.currentTimeMillis()
                                    if (total > 0 && now - lastUpdate > 300) {
                                        lastUpdate = now
                                        val p = downloaded.toFloat() / total
                                        withContext(Dispatchers.Main) { progress = p }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Fallback download (GameNative)
                    SteamService.fetchFileWithFallback(item.fileName, dest, ctx) { p ->
                        if (System.currentTimeMillis() - lastUpdate > 300) {
                            lastUpdate = System.currentTimeMillis()
                            scope.launch(Dispatchers.Main) { progress = p.coerceIn(0f, 1f) }
                        }
                    }
                }

                statusMsg = "Installing..."
                progress = -1f // indeterminate
                
                val installSuccess = withContext(Dispatchers.IO) {
                    installWcpRobustly(ctx, mgr, Uri.fromFile(dest)) { currentStatus ->
                        scope.launch(Dispatchers.Main) { statusMsg = currentStatus }
                    }
                }
                
                dest.delete()
                
                if (installSuccess) {
                    mgr.syncContents()
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(ctx, "Installed Successfully", Toast.LENGTH_SHORT).show()
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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
             scope.launch {
                 try {
                     isBusy = true
                     progress = -1f // indeterminate for initial extraction
                     statusMsg = "Importing .wcp package..."
                     
                     val success = withContext(Dispatchers.IO) {
                         installWcpRobustly(ctx, mgr, it) { currentStatus ->
                             scope.launch(Dispatchers.Main) { statusMsg = currentStatus }
                         }
                     }
                     
                     if (success) {
                         mgr.syncContents()
                         refreshInstalled()
                         Toast.makeText(ctx, "Imported Successfully", Toast.LENGTH_SHORT).show()
                     } else {
                         Toast.makeText(ctx, "Import Failed", Toast.LENGTH_SHORT).show()
                     }
                 } catch (e: Exception) {
                     Timber.e(e, "Manual import error")
                     Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                 } finally {
                     isBusy = false
                     statusMsg = ""
                     progress = 0f
                 }
             }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Text("Wine/Proton Manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 8.dp))
        }
        HorizontalDivider()
        
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Note: Only Bionic-based builds are supported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
            
            if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            else if (wineReleases.isNotEmpty()) {
                Text("Available Online", style = MaterialTheme.typography.titleMedium)
                ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = !isExpanded }) {
                    OutlinedTextField(
                        value = selectedItem?.name ?: "", 
                        onValueChange = {}, 
                        readOnly = true, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) }, 
                        modifier = Modifier.fillMaxWidth().menuAnchor(), 
                        placeholder = { Text("Select Version") }
                    )
                    ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                        wineReleases.forEach { item -> 
                            DropdownMenuItem(
                                text = { Text(item.name) }, 
                                onClick = { selectedItem = item; isExpanded = false }
                            ) 
                        }
                    }
                }
                if (selectedItem != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { downloadAndInstall(selectedItem!!) }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isBusy) "Working..." else "Download & Install")
                    }
                    if (isBusy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            
            Button(
                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
            ) { Text("Import .wcp Package") }
            
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            
            Text("Installed Versions", style = MaterialTheme.typography.titleMedium)
            if (installedProfiles.isEmpty()) Text("No versions found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                installedProfiles.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.type}: ${p.verName}", style = MaterialTheme.typography.bodyMedium)
                            if (p.desc.isNotEmpty()) Text(p.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleteTarget = p }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
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
        val assets  = obj["assets"]?.jsonArray?.mapNotNull { assetEl ->
            val aObj = assetEl.jsonObject
            val name = aObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url  = aObj["browser_download_url"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            GHAsset(name = name, downloadUrl = url)
        } ?: emptyList()
        GHRelease(tagName = tagName, assets = assets)
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
                if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST) {
                    // Treat "already exists" as a special success or at least non-terminal error
                    continuation.resume(null) { /* cancel cleanup */ }
                } else {
                    continuation.resume(null) { /* cancel cleanup */ }
                }
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
