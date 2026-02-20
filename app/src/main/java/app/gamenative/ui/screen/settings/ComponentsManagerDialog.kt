package app.gamenative.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import java.util.concurrent.CountDownLatch

// ─── Navigation ─────────────────────────────────────────────────────────────

private enum class CompNav { SELECTOR, DETAIL }

// ─── Component enum (alphabetical order) ────────────────────────────────────

internal enum class GNComponent(val displayName: String) {
    BOX64("Box64"),
    DRIVER("Driver"),
    DXVK("DXVK"),
    FEXCORE("FEXCore"),
    VKD3D("VKD3D"),
    WINE_PROTON("Wine/Proton"),
    WOWBOX64("WowBox64"),
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

    var nav by remember { mutableStateOf(CompNav.SELECTOR) }
    var selectedComponent by remember { mutableStateOf<GNComponent?>(null) }
    var showDriverDialog by remember { mutableStateOf(false) }
    var showWineProtonDialog by remember { mutableStateOf(false) }

    // Sub-dialogs appear on top of main dialog
    if (showDriverDialog) {
        DriverManagerDialog(
            open = true,
            initialSource = DriverSource.MTR,
            onDismiss = { showDriverDialog = false },
        )
    }
    if (showWineProtonDialog) {
        WineProtonManagerDialog(
            open = true,
            onDismiss = { showWineProtonDialog = false },
        )
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
                        when (comp) {
                            GNComponent.DRIVER      -> showDriverDialog = true
                            GNComponent.WINE_PROTON -> showWineProtonDialog = true
                            else -> {
                                selectedComponent = comp
                                nav = CompNav.DETAIL
                            }
                        }
                    },
                    onDismiss = onDismiss,
                )
                CompNav.DETAIL -> ComponentDetailScreen(
                    component = selectedComponent!!,
                    onBack = {
                        nav = CompNav.SELECTOR
                        selectedComponent = null
                    },
                )
            }
        }
    }
}

// ─── Screen 1: Component selector ────────────────────────────────────────────

@Composable
private fun ComponentSelectorScreen(
    onSelectComponent: (GNComponent) -> Unit,
    onDismiss: () -> Unit,
) {
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
                            Button(
                                onClick = { if (!isWorking) downloadAndInstall(asset) },
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("⬇ ${nightlyLabel(asset.releaseName)} Nightly")
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
                    Button(
                        onClick = { if (!isWorking) downloadAndInstall(latestStable) },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Text("⬇ Install Latest Stable")
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
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
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedAsset = if (isSelected) null else asset },
                            )
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
