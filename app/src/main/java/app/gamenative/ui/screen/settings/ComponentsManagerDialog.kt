package app.gamenative.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.gamenative.R
import app.gamenative.service.SteamService
import app.gamenative.utils.Net
import com.winlator.contents.AdrenotoolsManager
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
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.resume

// ─── Component enum ────────────────────────────────────

internal enum class GNComponent(val displayName: String, val icon: ImageVector) {
    WINE_PROTON("Wine/Proton", Icons.Default.WineBar),
    DRIVER("Driver", Icons.Default.Memory),
    BOX64("Box64", Icons.Default.Terminal),
    DXVK("DXVK", Icons.Default.Layers),
    FEXCORE("FEXCore", Icons.Default.Bolt),
    VKD3D("VKD3D", Icons.Default.ViewInAr),
    WOWBOX64("WowBox64", Icons.Default.Widgets);

    fun getContentTypes(): List<ContentProfile.ContentType> = when (this) {
        BOX64 -> listOf(ContentProfile.ContentType.CONTENT_TYPE_BOX64)
        DRIVER -> listOf(ContentProfile.ContentType.CONTENT_TYPE_TURNIP)
        DXVK -> listOf(ContentProfile.ContentType.CONTENT_TYPE_DXVK)
        FEXCORE -> listOf(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)
        VKD3D -> listOf(ContentProfile.ContentType.CONTENT_TYPE_VKD3D)
        WINE_PROTON -> listOf(ContentProfile.ContentType.CONTENT_TYPE_WINE, ContentProfile.ContentType.CONTENT_TYPE_PROTON)
        WOWBOX64 -> listOf(ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64)
    }
}

// ─── GitHub data models ──────────────────────────────────────────────

private data class GHRelease(val tagName: String, val assets: List<GHAsset>, val publishedAt: String = "")
private data class GHAsset(val name: String, val downloadUrl: String, val releaseName: String = "", val releaseDate: String = "")

// ─── Main Dialog ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ComponentsManagerDialog(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember(context) { ContentsManager(context) }
    
    var selectedComponent by rememberSaveable { mutableStateOf(GNComponent.WINE_PROTON) }
    val sidebarScrollState = rememberScrollState()

    var isWorking by remember { mutableStateOf(false) }
    var workMessage by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(-1f) }

    val performInstall: (Uri) -> Unit = { uri ->
        scope.launch {
            isWorking = true; workMessage = "Processing .wcp..."
            try {
                val success = withContext(Dispatchers.IO) {
                    installWcpRobustly(context, mgr, uri) { scope.launch(Dispatchers.Main) { workMessage = it } }
                }
                if (success) {
                    mgr.syncContents()
                    Toast.makeText(context, "Installation Complete ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally { isWorking = false; workMessage = "" }
        }
    }

    val customPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { performInstall(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(2f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(8.dp, CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }

                Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.height(44.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Components", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    IconButton(
                        onClick = { customPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(8.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Add, "Install ${selectedComponent.displayName}", tint = Color.White)
                    }
                }
            }

            // MAIN CONTENT
            Row(modifier = Modifier.fillMaxSize().padding(top = 72.dp)) {
                // SIDEBAR
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight()
                        .verticalScroll(sidebarScrollState)
                        .padding(start = 24.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GNComponent.entries.forEach { comp ->
                        SidebarItem(
                            label = comp.displayName,
                            icon = comp.icon,
                            selected = selectedComponent == comp,
                            onClick = { selectedComponent = comp }
                        )
                    }
                }

                // CONTENT AREA
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 24.dp, bottom = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                ) {
                    AnimatedContent(
                        targetState = selectedComponent,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
                    ) { comp ->
                        when (comp) {
                            GNComponent.WINE_PROTON -> WineProtonContent(mgr, isWorking, { isWorking = it }, { workMessage = it }, { downloadProgress = it })
                            GNComponent.DRIVER -> DriverContent(isWorking, { isWorking = it }, { workMessage = it }, { downloadProgress = it })
                            else -> GenericComponentContent(comp, mgr, isWorking, { isWorking = it }, { workMessage = it }, { downloadProgress = it })
                        }
                    }
                }
            }
            
            // PROGRESS BAR (Bottom Sticky)
            if (isWorking) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(workMessage, color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        if (downloadProgress >= 0) {
                            LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.dp))
        Text(text = label, color = contentColor, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

// ─── Specialized Tiles ──────────────────────────────────────────────────────

@Composable
private fun ComponentTile(
    title: String,
    subtitle: String,
    isInstalled: Boolean,
    isBusy: Boolean,
    isUpgrade: Boolean = false,
    onAction: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(if (isInstalled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Download, null, tint = if (isInstalled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            
            if (isInstalled && onDelete != null) {
                IconButton(onClick = onDelete, enabled = !isBusy) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
            
            Button(
                onClick = onAction,
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isUpgrade) MaterialTheme.colorScheme.tertiary else if (isInstalled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isUpgrade) MaterialTheme.colorScheme.onTertiary else if (isInstalled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (isUpgrade) "Upgrade" else if (isInstalled) "Reinstall" else "Install", fontSize = 13.sp)
            }
        }
    }
}

// ─── Generic Component Content (Box64, DXVK, etc) ──────────────────────────

@Composable
private fun GenericComponentContent(comp: GNComponent, mgr: ContentsManager, isBusy: Boolean, setBusy: (Boolean) -> Unit, setWorkMsg: (String) -> Unit, setProgress: (Float) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<GHRelease>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val installedProfiles = remember { mutableStateListOf<ContentProfile>() }
    var selectedFilter by rememberSaveable { mutableStateOf("Download") }
    var selectedType by rememberSaveable { mutableStateOf("All") }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val refresh: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            mgr.syncContents()
            val list = mutableListOf<ContentProfile>()
            comp.getContentTypes().forEach { type ->
                val profiles = mgr.getProfiles(type)
                if (profiles != null) list.addAll(profiles)
            }
            withContext(Dispatchers.Main) {
                installedProfiles.clear()
                installedProfiles.addAll(list)
            }
        }
    }

    LaunchedEffect(comp) {
        refresh()
        isLoading = true
        selectedType = "All"
        withContext(Dispatchers.IO) {
            try {
                val allReleases = mutableListOf<GHRelease>()
                val req = Request.Builder().url("https://api.github.com/repos/Xnick417x/Winlator-Bionic-Nightly-wcp/releases?per_page=100").header("Accept", "application/vnd.github.v3+json").build()
                Net.http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        allReleases.addAll(parseGHReleases(resp.body?.string() ?: "[]"))
                    }
                }
                
                val nPrefixes = nightlyPrefixes[comp] ?: emptyList()
                val sPrefixes = stablePrefixes[comp] ?: emptyList()
                val extraPrefixes = if (comp == GNComponent.DXVK) listOf("dxvk", "gplasync", "sarek", "nvapi") else emptyList()

                sPrefixes.forEach { tag ->
                    fetchGHReleaseByTag(tag)?.let { allReleases.add(it) }
                }

                withContext(Dispatchers.Main) { 
                    releases = allReleases.filter { rel -> 
                        (nPrefixes + sPrefixes + extraPrefixes).any { prefix -> rel.tagName.contains(prefix, true) || rel.assets.any { it.name.contains(prefix, true) } }
                    }.distinctBy { it.tagName }
                }
            } catch (e: Exception) { Timber.e(e) } finally { withContext(Dispatchers.Main) { isLoading = false } }
        }
    }

    val componentTypes = when(comp) {
        GNComponent.DXVK -> listOf("All", "Stable", "Gplasync", "NVAPI", "Sarek", "Arm64EC", "Nightly")
        GNComponent.VKD3D -> listOf("All", "Stable", "Arm64EC", "Nightly")
        GNComponent.BOX64 -> listOf("All", "Stable", "Bionic", "Nightly")
        GNComponent.WOWBOX64 -> listOf("All", "Stable", "Nightly")
        GNComponent.FEXCORE -> listOf("All", "Stable", "Nightly")
        else -> listOf("All")
    }

    LaunchedEffect(selectedFilter) {
        if (selectedFilter == "Installed") {
            selectedType = "All"
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(comp.displayName, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                listOf("Download", "Installed").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White.copy(alpha = 0.6f), selectedLabelColor = Color.White),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                
                Spacer(Modifier.weight(1f))
                
                Box {
                    OutlinedButton(
                        onClick = { typeDropdownExpanded = true },
                        enabled = selectedFilter != "Installed",
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (selectedFilter == "Installed") Color.White.copy(alpha = 0.3f) else Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, if (selectedFilter == "Installed") Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.widthIn(max = 140.dp)
                    ) {
                        Text(selectedType, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A)).border(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        componentTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, color = Color.White) },
                                onClick = { selectedType = type; typeDropdownExpanded = false }
                            )
                        }
                    }
                }
            }
        }
        
        if (isLoading) { item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
        else {
            val allAssets = if (selectedFilter == "Installed") {
                installedProfiles.map { profile ->
                    GHAsset(name = profile.verName, downloadUrl = "", releaseName = "Installed", releaseDate = "")
                }
            } else {
                releases.flatMap { rel -> 
                    rel.assets.filter { it.name.endsWith(".wcp") }
                        .map { it.copy(releaseName = rel.tagName, releaseDate = rel.publishedAt) } 
                }.filter { findInstalledProfile(it.name, installedProfiles) == null }
            }

            val filteredByType = allAssets.filter { asset ->
                val name = asset.name.lowercase()
                val rel = asset.releaseName.lowercase()
                val isBionic = name.contains("bionic", true) || rel.contains("bionic", true)
                val isArm64EC = name.contains("arm64ec", true)
                val isNVAPI = name.contains("nvapi", true)
                val isSarek = name.contains("sarek", true)
                val isGplasync = (name.contains("gplasync", true) || name.contains("async", true) || name.contains("pre-reg", true)) && !isSarek
                val isAnyNightly = (name.contains("nightly", true) || name.contains("pre-reg", true) || name.contains("nightlies", true) || rel.contains("nightly", true) || rel.contains("pre-reg", true) || rel.contains("nightlies", true)) && !name.contains("pre-reg", true)
                val isStable = name.contains("stable", true) || rel.contains("stable", true) || (!isAnyNightly && !isBionic && !isArm64EC && !isNVAPI && !isGplasync && !isSarek) || name.contains("pre-reg", true)

                when (selectedType) {
                    "All" -> true
                    "Nightly" -> isAnyNightly
                    "Arm64EC" -> isArm64EC
                    "NVAPI" -> isNVAPI
                    "Gplasync" -> isGplasync && !isArm64EC && !isNVAPI
                    "Stable" -> (isStable || name.contains("pre-reg", true) || rel.contains("stable", true)) && !isAnyNightly && !isArm64EC && !isNVAPI && !isSarek
                    "Sarek" -> isSarek
                    "Bionic" -> isBionic
                    else -> !isAnyNightly && (asset.name.contains(selectedType, ignoreCase = true) || asset.releaseName.contains(selectedType, ignoreCase = true))
                }
            }

            val finalAssets = if (selectedFilter == "Download") {
                val sorted = filteredByType.sortedWith { a, b ->
                    val catA = getAssetCategory(a.name, a.releaseName, comp)
                    val catB = getAssetCategory(b.name, b.releaseName, comp)
                    if (catA != catB) catA.compareTo(catB)
                    else compareVersions(b.name, a.name)
                }
                
                val nightlies = sorted.filter { it.name.contains("nightly", true) || it.releaseName.contains("nightly", true) || it.name.contains("pre-reg", true) || it.releaseName.contains("pre-reg", true) }
                val nonNightlies = sorted.filter { !(it.name.contains("nightly", true) || it.releaseName.contains("nightly", true) || it.name.contains("pre-reg", true) || it.releaseName.contains("pre-reg", true)) }
                val latestNightlies = nightlies.groupBy { it.name.substringBeforeLast("-") }.map { it.value.first() }
                
                (nonNightlies + latestNightlies).sortedWith { a, b ->
                    val catA = getAssetCategory(a.name, a.releaseName, comp)
                    val catB = getAssetCategory(b.name, b.releaseName, comp)
                    if (catA != catB) catA.compareTo(catB)
                    else compareVersions(b.name, a.name)
                }
            } else filteredByType.sortedWith { a, b -> compareVersions(b.name, a.name) }

            if (finalAssets.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No items found", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(finalAssets) { asset ->
                    val profile = findInstalledProfile(asset.name, installedProfiles)
                    val isInstalled = profile != null
                    ComponentTile(
                        title = asset.name.removeSuffix(".wcp"),
                        subtitle = if (isInstalled) "Installed Version ✓" else formatRelativeTime(asset.releaseDate),
                        isInstalled = isInstalled,
                        isBusy = isBusy,
                        onAction = { downloadAndInstall(context, mgr, asset, scope, setBusy, setWorkMsg, setProgress, refresh) },
                        onDelete = if (isInstalled) { { scope.launch(Dispatchers.IO) { mgr.removeContent(profile!!) ; refresh() } ; Unit } } else null
                    )
                }
            }
        }
    }
}

// ─── Driver Content ─────────────────────────────────────────────────────────

@Composable
private fun DriverContent(isBusy: Boolean, setBusy: (Boolean) -> Unit, setWorkMsg: (String) -> Unit, setProgress: (Float) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSource by remember { mutableStateOf("MTR") }
    var drivers by remember { mutableStateOf<List<DriverItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val installed = remember { mutableStateListOf<String>() }
    val driverMeta = remember { mutableStateMapOf<String, Pair<String, String>>() }

    val refresh: () -> Unit = {
        installed.clear(); driverMeta.clear()
        try {
            val mgr = AdrenotoolsManager(context)
            val list = mgr.enumarateInstalledDrivers()
            installed.addAll(list)
            list.forEach { id -> driverMeta[id] = mgr.getDriverName(id) to mgr.getDriverVersion(id) }
        } catch (_: Exception) {}
    }

    LaunchedEffect(selectedSource) {
        refresh()
        if (selectedSource == "Installed") {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val url = if (selectedSource == "GN") "https://raw.githubusercontent.com/utkarshdalal/gamenative-landing-page/refs/heads/main/data/manifest.json" else "https://api.github.com/repos/maxjivi05/Components/contents/Drivers"
                val req = Request.Builder().url(url).build()
                Net.http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val text = resp.body?.string() ?: ""
                        val list = if (selectedSource == "GN") {
                            Json.parseToJsonElement(text).jsonObject.entries.map { DriverItem(it.key, it.value.toString().trim('"')) }
                        } else {
                            Json.parseToJsonElement(text).jsonArray.map { val o = it.jsonObject; DriverItem(o["name"]!!.toString().trim('"'), o["download_url"]!!.toString().trim('"')) }
                        }
                        
                        val sortedList = list.sortedWith(
                            compareByDescending<DriverItem> { it.name.contains("MTR", true) }
                            .thenByDescending { it.name }
                        )
                        
                        withContext(Dispatchers.Main) { drivers = sortedList }
                    }
                }
            } catch (e: Exception) { Timber.e(e) } finally { withContext(Dispatchers.Main) { isLoading = false } }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Drivers", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            listOf("MTR", "GN", "Installed").forEach { src ->
                FilterChip(
                    selected = selectedSource == src,
                    onClick = { selectedSource = src },
                    label = { Text(src) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White.copy(alpha = 0.6f), selectedLabelColor = Color.White),
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (isLoading) { Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (selectedSource == "Installed") {
            // Show only installed drivers
            if (installed.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No drivers installed", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(installed.toList()) { id ->
                        val name = driverMeta[id]?.first ?: id
                        val version = driverMeta[id]?.second ?: ""
                        ComponentTile(
                            title = name,
                            subtitle = if (version.isNotEmpty()) "Version: $version" else "Installed",
                            isInstalled = true,
                            isBusy = isBusy,
                            onAction = { /* Reinstall not applicable from installed view */ },
                            onDelete = { scope.launch(Dispatchers.IO) { AdrenotoolsManager(context).removeDriver(id); refresh() } ; Unit }
                        )
                    }
                }
            }
        }
        else {
            val (installedDrivers, availableDrivers) = drivers.partition { item ->
                installed.any { id -> (driverMeta[id]?.first ?: id).contains(item.name.removeSuffix(".zip"), true) }
            }
            
            val finalDrivers = installedDrivers + availableDrivers

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(finalDrivers) { item ->
                    val isInstalled = installed.any { id -> (driverMeta[id]?.first ?: id).contains(item.name.removeSuffix(".zip"), true) }
                    ComponentTile(
                        title = item.name,
                        subtitle = if (isInstalled) "Installed" else "Available",
                        isInstalled = isInstalled,
                        isBusy = isBusy,
                        onAction = { downloadDriver(context, item, selectedSource == "GN", scope, setBusy, setWorkMsg, setProgress, refresh) },
                        onDelete = if (isInstalled) { { scope.launch(Dispatchers.IO) { val id = installed.find { (driverMeta[it]?.first ?: it).contains(item.name.removeSuffix(".zip"), true) }; id?.let { AdrenotoolsManager(context).removeDriver(it) }; refresh() } ; Unit } } else null
                    )
                }
            }
        }
    }
}

// ─── Wine/Proton Content ────────────────────────────────────────────────────

@Composable
private fun WineProtonContent(mgr: ContentsManager, isBusy: Boolean, setBusy: (Boolean) -> Unit, setWorkMsg: (String) -> Unit, setProgress: (Float) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<WineReleaseItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val installed = remember { mutableStateListOf<ContentProfile>() }
    var selectedFilter by rememberSaveable { mutableStateOf("Download") }
    var selectedType by rememberSaveable { mutableStateOf("All") }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val refresh: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            installed.clear()
            val wine = mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)
            val proton = mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)
            val combined = (wine ?: emptyList()) + (proton ?: emptyList())
            val filtered = combined.filter { it.remoteUrl == null }.distinctBy { it.type.toString() + it.verName }
            withContext(Dispatchers.Main) { installed.addAll(filtered) }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val gn = fetchGNWineReleases()
                val nickGN = fetchNickTagReleases("GameNative")
                val nickWine = fetchNickTagReleases("Wine")
                val k11 = fetchK11Releases()
                val combined = (gn + nickGN + nickWine + k11).distinctBy { it.url ?: "${it.fileName}|${it.releaseDate}" }.sortedByDescending { it.releaseDate }
                withContext(Dispatchers.Main) { releases = combined }
            } catch (e: Exception) { Timber.e(e) } finally { withContext(Dispatchers.Main) { isLoading = false } }
        }
    }

    val wineTypes = listOf("All", "GE-Proton", "Wine-Staging", "Wine-Col", "Kron4ek", "Nightly")

    LaunchedEffect(selectedFilter) {
        if (selectedFilter == "Installed") {
            selectedType = "All"
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Wine / Proton", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                listOf("Download", "Installed").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White.copy(alpha = 0.6f), selectedLabelColor = Color.White),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                Box {
                    OutlinedButton(
                        onClick = { typeDropdownExpanded = true },
                        enabled = selectedFilter != "Installed",
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (selectedFilter == "Installed") Color.White.copy(alpha = 0.3f) else Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, if (selectedFilter == "Installed") Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.widthIn(max = 140.dp)
                    ) {
                        Text(selectedType, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A)).border(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        wineTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, color = Color.White) },
                                onClick = { selectedType = type; typeDropdownExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) { item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
        else {
            val allItems = if (selectedFilter == "Installed") {
                installed.map { profile ->
                    WineReleaseItem(name = profile.verName, version = "", url = null, fileName = profile.verName, releaseDate = "", source = "Installed")
                }.sortedWith { a, b -> compareVersions(b.name, a.name) }
            } else {
                releases.filter { item ->
                    installed.none { assetKeyMatchesExact(it.verName, item.fileName.removeSuffix(".wcp")) }
                }
            }

            val filteredByType = allItems.filter { item ->
                val name = item.name.lowercase()
                val isNightly = name.contains("nightly", true) || name.contains("pre-reg", true) || item.fileName.lowercase().contains("nightly", true)
                
                if (selectedType == "All") true
                else if (selectedType == "Nightly") isNightly
                else {
                    !isNightly && (item.name.contains(selectedType, ignoreCase = true) || item.fileName.contains(selectedType, ignoreCase = true))
                }
            }

            val finalReleases = if (selectedFilter == "Download") {
                val sorted = filteredByType.sortedWith { a, b ->
                    val catA = getWineCategory(a.name, a.fileName)
                    val catB = getWineCategory(b.name, b.fileName)
                    if (catA != catB) catA.compareTo(catB)
                    else {
                        val dateCompare = b.releaseDate.compareTo(a.releaseDate)
                        if (dateCompare != 0) dateCompare else compareVersions(b.name, a.name)
                    }
                }
                
                val nightlies = sorted.filter { it.name.contains("nightly", true) || it.fileName.contains("nightly", true) || it.name.contains("pre-reg", true) || it.fileName.contains("pre-reg", true) }
                val nonNightlies = sorted.filter { !(it.name.contains("nightly", true) || it.fileName.contains("nightly", true) || it.name.contains("pre-reg", true) || it.fileName.contains("pre-reg", true)) }
                val latestNightlies = nightlies.groupBy { it.name.substringBeforeLast("-") }.map { it.value.first() }
                
                (nonNightlies + latestNightlies).sortedWith { a, b ->
                    val catA = getWineCategory(a.name, a.fileName)
                    val catB = getWineCategory(b.name, b.fileName)
                    if (catA != catB) catA.compareTo(catB)
                    else {
                        val dateCompare = b.releaseDate.compareTo(a.releaseDate)
                        if (dateCompare != 0) dateCompare else compareVersions(b.name, a.name)
                    }
                }
            } else filteredByType.sortedWith { a, b -> compareVersions(b.name, a.name) }

            if (finalReleases.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No items found", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(finalReleases) { item ->
                    val profile = installed.find { assetKeyMatchesExact(it.verName, item.fileName.removeSuffix(".wcp")) }
                    val isInstalled = profile != null
                    val installedDate = if (isInstalled) releases.find { it.fileName == item.fileName }?.releaseDate ?: "" else ""
                    val isUpgrade = !isInstalled && item.releaseDate > installedDate && installed.any { wineFamily(it.verName) == wineFamily(item.fileName) }

                    ComponentTile(
                        title = item.name,
                        subtitle = if (isInstalled) "Installed Version ✓" else "[${item.source}] ${formatRelativeTime(item.releaseDate)}",
                        isInstalled = isInstalled,
                        isBusy = isBusy,
                        isUpgrade = isUpgrade,
                        onAction = { if (isUpgrade) upgradeWine(context, mgr, installed.find { wineFamily(it.verName) == wineFamily(item.fileName) }!!, item, scope, setBusy, setWorkMsg, setProgress, refresh) else downloadWine(context, mgr, item, scope, setBusy, setWorkMsg, setProgress, refresh) },
                        onDelete = if (isInstalled) { { scope.launch(Dispatchers.IO) { mgr.removeContent(profile!!); refresh() } ; Unit } } else null
                    )
                }
            }
        }
    }
    }


// ─── Logic Helpers ──────────────────────────────────────────────────────────

private fun getAssetCategory(name: String, releaseName: String, comp: GNComponent): Int {
    val n = name.lowercase()
    val r = releaseName.lowercase()
    val isNightly = n.contains("nightly") || n.contains("pre-reg") || n.contains("nightlies") || r.contains("nightly") || r.contains("pre-reg") || r.contains("nightlies")
    if (isNightly) return 10 

    val isStable = n.contains("stable") || r.contains("stable")

    if (comp == GNComponent.DXVK) {
        return when {
            (isStable && !n.contains("gplasync") && !n.contains("arm64ec") && !n.contains("sarek") && !n.contains("nvapi")) || 
            (!isNightly && !n.contains("gplasync") && !n.contains("arm64ec") && !n.contains("sarek") && !n.contains("nvapi")) -> 0
            n.contains("gplasync") -> 1
            n.contains("arm64ec") -> 2
            n.contains("sarek") -> 3
            n.contains("nvapi") -> 4
            else -> 5
        }
    }
    
    return when {
        isStable || (!isNightly && !n.contains("bionic") && !n.contains("arm64ec")) -> 0
        n.contains("arm64ec") -> 1
        n.contains("bionic") -> 2
        else -> 3
    }
}

private fun getWineCategory(name: String, fileName: String): Int {
    val n = name.lowercase()
    val f = fileName.lowercase()
    if (n.contains("nightly") || n.contains("pre-reg") || n.contains("nightlies") || f.contains("nightly") || f.contains("pre-reg") || f.contains("nightlies")) return 10
    return 0 
}

private fun downloadAndInstall(ctx: android.content.Context, mgr: ContentsManager, asset: GHAsset, scope: kotlinx.coroutines.CoroutineScope, setBusy: (Boolean) -> Unit, setWorkMsg: (String) -> Unit, setProgress: (Float) -> Unit, onComplete: () -> Unit) {
    scope.launch {
        setBusy(true); setWorkMsg("Downloading ${asset.name}..."); setProgress(0f)
        try {
            val dest = File(ctx.cacheDir, asset.name)
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(asset.downloadUrl).build()
                Net.http.newCall(req).execute().use { resp ->
                    val total = resp.body?.contentLength() ?: 0L; var dl = 0L
                    FileOutputStream(dest).use { out -> resp.body?.byteStream()?.use { inp ->
                        val buf = ByteArray(8192); var n: Int
                        while (inp.read(buf).also { n = it } != -1) { out.write(buf, 0, n); dl += n; if (total > 0) withContext(Dispatchers.Main) { setProgress(dl.toFloat() / total) } }
                    } }
                }
            }
            setWorkMsg("Installing..."); setProgress(-1f)
            val ok = withContext(Dispatchers.IO) { installWcpRobustly(ctx, mgr, Uri.fromFile(dest)) { scope.launch(Dispatchers.Main) { setWorkMsg(it) } } }
            dest.delete(); if (ok) { mgr.syncContents(); onComplete(); Toast.makeText(ctx, "Installed ✓", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } finally { setBusy(false) }
    }
}

private fun downloadDriver(ctx: android.content.Context, item: DriverItem, isGN: Boolean, scope: kotlinx.coroutines.CoroutineScope, setBusy: (Boolean) -> Unit, setWorkMsg: (String) -> Unit, setProgress: (Float) -> Unit, onComplete: () -> Unit) {
    scope.launch {
        setBusy(true); setWorkMsg("Downloading ${item.name}..."); setProgress(0f)
        try {
            val dest = File(ctx.cacheDir, item.name + (if (item.name.endsWith(".zip")) "" else ".zip"))
            if (isGN) { SteamService.fetchFileWithFallback("drivers/${item.url}", dest, ctx) { scope.launch(Dispatchers.Main) { setProgress(it) } } }
            else {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(item.url).build()
                    Net.http.newCall(req).execute().use { resp ->
                        val total = resp.body?.contentLength() ?: 0L; var dl = 0L
                        FileOutputStream(dest).use { out -> resp.body?.byteStream()?.use { inp ->
                            val buf = ByteArray(8192); var n: Int
                            while (inp.read(buf).also { n = it } != -1) { out.write(buf, 0, n); dl += n; if (total > 0) withContext(Dispatchers.Main) { setProgress(dl.toFloat() / total) } }
                        } }
                    }
                }
            }
            setWorkMsg("Installing..."); setProgress(-1f)
            val res = withContext(Dispatchers.IO) { try { val n = AdrenotoolsManager(ctx).installDriver(Uri.fromFile(dest)); if (n.isNotEmpty()) "Installed ✓" else "Failed" } catch (e: Exception) { e.message ?: "Error" } }
            dest.delete(); Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show(); if (res.startsWith("Installed")) onComplete()
        } catch (e: Exception) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } finally { setBusy(false) }
    }
}

private fun downloadWine(ctx: android.content.Context, mgr: ContentsManager, item: WineReleaseItem, scope: kotlinx.coroutines.CoroutineScope, setBusy: (Boolean) -> Unit, setWorkMsg: (String) -> Unit, setProgress: (Float) -> Unit, onComplete: () -> Unit) {
    scope.launch {
        setBusy(true); setWorkMsg("Downloading ${item.name}..."); setProgress(0f)
        try {
            val dest = File(ctx.cacheDir, item.fileName)
            if (item.url != null) {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(item.url).build()
                    Net.http.newCall(req).execute().use { resp ->
                        val total = resp.body?.contentLength() ?: 0L; var dl = 0L
                        FileOutputStream(dest).use { out -> resp.body?.byteStream()?.use { inp ->
                            val buf = ByteArray(8192); var n: Int
                            while (inp.read(buf).also { n = it } != -1) { out.write(buf, 0, n); dl += n; if (total > 0) withContext(Dispatchers.Main) { setProgress(dl.toFloat() / total) } }
                        } }
                    }
                }
            } else { SteamService.fetchFileWithFallback(item.fileName, dest, ctx) { scope.launch(Dispatchers.Main) { setProgress(it) } } }
            setWorkMsg("Installing..."); setProgress(-1f)
            val ok = withContext(Dispatchers.IO) { installWcpRobustly(ctx, mgr, Uri.fromFile(dest)) { scope.launch(Dispatchers.Main) { setWorkMsg(it) } } }
            dest.delete(); if (ok) { mgr.syncContents(); onComplete(); Toast.makeText(ctx, "Installed ✓", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } finally { setBusy(false) }
    }
}

private fun upgradeWine(ctx: android.content.Context, mgr: ContentsManager, old: ContentProfile, item: WineReleaseItem, scope: kotlinx.coroutines.CoroutineScope, setBusy: (Boolean) -> Unit, setWorkMsg: (String) -> Unit, setProgress: (Float) -> Unit, onComplete: () -> Unit) {
    scope.launch {
        setBusy(true); setWorkMsg("Downloading ${item.name}..."); setProgress(0f)
        try {
            val dest = File(ctx.cacheDir, item.fileName)
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(item.url!!).build()
                Net.http.newCall(req).execute().use { resp ->
                    val total = resp.body?.contentLength() ?: 0L; var dl = 0L
                    FileOutputStream(dest).use { out -> resp.body?.byteStream()?.use { inp ->
                        val buf = ByteArray(8192); var n: Int
                        while (inp.read(buf).also { n = it } != -1) { out.write(buf, 0, n); dl += n; if (total > 0) withContext(Dispatchers.Main) { setProgress(dl.toFloat() / total) } }
                    } }
                }
            }
            setWorkMsg("Removing old..."); withContext(Dispatchers.IO) { mgr.removeContent(old); mgr.syncContents() }
            setWorkMsg("Installing new..."); val ok = withContext(Dispatchers.IO) { installWcpRobustly(ctx, mgr, Uri.fromFile(dest)) { scope.launch(Dispatchers.Main) { setWorkMsg(it) } } }
            dest.delete(); if (ok) { mgr.syncContents(); onComplete(); Toast.makeText(ctx, "Upgraded ✓", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } finally { setBusy(false) }
    }
}

// ─── Models & Utils ──────────────────────────────────────────────────────────

private data class DriverItem(val name: String, val url: String)
private data class WineReleaseItem(val name: String, val version: String, val url: String?, val fileName: String, val releaseDate: String = "", val source: String = "")

private fun wineFamily(name: String): String {
    val b = name.removeSuffix(".wcp"); val d = b.lastIndexOf('-')
    return if (d > 0 && b.substring(d + 1).all { it.isDigit() }) b.substring(0, d) else b
}

private fun extractVersionFromFilename(name: String): String = name.removeSuffix(".wcp").split("-").firstOrNull { it.isNotEmpty() && it[0].isDigit() } ?: name

private fun formatRelativeTime(iso: String): String = try {
    val dur = Duration.between(Instant.parse(iso), Instant.now())
    val d = dur.toDays(); val h = dur.toHours() % 24
    if (d > 0) "${d}d ${h}h ago" else "${dur.toHours()}h ago"
} catch (_: Exception) { "" }

private fun assetUniqueKey(n: String): String = n.removeSuffix(".wcp")
private fun assetKeyMatchesExact(v: String, k: String): Boolean {
    val vn = v.lowercase().removeSuffix(".wcp").trim()
    val kn = k.lowercase().removeSuffix(".wcp").trim()
    if (vn == kn) return true
    
    // Normalize by removing common prefixes
    val prefixes = listOf("dxvk-", "vk3dk-", "box64-", "bionic-box64-", "wowbox64-", "fex-", "stable-", "nightly-", "arm64ec-", "gplasync-", "nvapi-")
    var pvn = vn
    var pkn = kn
    prefixes.forEach { 
        if (pvn.startsWith(it)) pvn = pvn.removePrefix(it)
        if (pkn.startsWith(it)) pkn = pkn.removePrefix(it)
    }
    
    return pvn == pkn || vn.endsWith("-$kn") || kn.endsWith("-$vn") || vn.contains(kn) || kn.contains(vn)
}
private fun findInstalledProfile(n: String, p: List<ContentProfile>): ContentProfile? = p.find { assetKeyMatchesExact(it.verName, assetUniqueKey(n)) }

private fun fetchGHReleaseByTag(tag: String): GHRelease? = try {
    val req = Request.Builder().url("https://api.github.com/repos/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/tags/$tag").header("Accept", "application/vnd.github.v3+json").build()
    Net.http.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
            val o = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
            val d = o["published_at"]?.jsonPrimitive?.content ?: ""
            val t = o["tag_name"]?.jsonPrimitive?.content ?: tag
            val assets = (o["assets"]?.jsonArray ?: emptyList()).mapNotNull { a ->
                val ao = a.jsonObject
                val n = ao["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val u = ao["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                GHAsset(n, u, releaseName = t, releaseDate = d)
            }
            GHRelease(t, assets, d)
        } else null
    }
} catch (_: Exception) { null }

private fun parseGHReleases(json: String): List<GHRelease> = try {
    Json.parseToJsonElement(json).jsonArray.map { el ->
        val o = el.jsonObject
        GHRelease(o["tag_name"]?.jsonPrimitive?.content ?: "", o["assets"]?.jsonArray?.mapNotNull { a ->
            val ao = a.jsonObject; val an = ao["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val au = ao["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            GHAsset(an, au)
        } ?: emptyList(), o["published_at"]?.jsonPrimitive?.content ?: "")
    }
} catch (_: Exception) { emptyList() }

private suspend fun fetchGNWineReleases(): List<WineReleaseItem> = try {
    val req = Request.Builder().url("https://api.github.com/repos/GameNative/proton-wine/releases?per_page=100").header("Accept", "application/vnd.github.v3+json").build()
    Net.http.newCall(req).execute().use { resp ->
        Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray.flatMap { rel ->
            val o = rel.jsonObject; val d = o["published_at"]?.jsonPrimitive?.content ?: ""
            (o["assets"]?.jsonArray ?: emptyList()).mapNotNull { a ->
                val n = a.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val u = a.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!n.endsWith(".wcp")) return@mapNotNull null
                WineReleaseItem(n.removeSuffix(".wcp"), extractVersionFromFilename(n), u, n, d, "GN")
            }
        }
    }
} catch (_: Exception) { emptyList() }

private suspend fun fetchNickTagReleases(tag: String): List<WineReleaseItem> = try {
    val req = Request.Builder().url("https://api.github.com/repos/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/tags/$tag").header("Accept", "application/vnd.github.v3+json").build()
    Net.http.newCall(req).execute().use { resp ->
        val o = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject; val d = o["published_at"]?.jsonPrimitive?.content ?: ""
        (o["assets"]?.jsonArray ?: emptyList()).mapNotNull { a ->
            val n = a.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val u = a.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!n.endsWith(".wcp")) return@mapNotNull null
            WineReleaseItem(n.removeSuffix(".wcp"), extractVersionFromFilename(n), u, n, d, "Nick")
        }
    }
} catch (_: Exception) { emptyList() }

private suspend fun fetchK11Releases(): List<WineReleaseItem> = try {
    val req = Request.Builder().url("https://api.github.com/repos/K11MCH1/Winlator101/releases/tags/wine_col").header("Accept", "application/vnd.github.v3+json").build()
    Net.http.newCall(req).execute().use { resp ->
        val o = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject; val d = o["published_at"]?.jsonPrimitive?.content ?: ""
        (o["assets"]?.jsonArray ?: emptyList()).mapNotNull { a ->
            val n = a.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val u = a.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!n.endsWith(".wcp")) return@mapNotNull null
            WineReleaseItem(n.removeSuffix(".wcp"), extractVersionFromFilename(n), u, n, d, "K11MCH1")
        }
    }
} catch (_: Exception) { emptyList() }

private suspend fun installWcpRobustly(ctx: android.content.Context, mgr: ContentsManager, uri: Uri, onStatus: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        onStatus("Validating..."); val p = suspendInstallCallback<ContentProfile> { callback -> mgr.extraContentFile(uri, callback) } ?: return@withContext false
        onStatus("Installing ${p.verName}..."); suspendInstallCallback<ContentProfile> { callback -> mgr.finishInstallContent(p, callback) } != null
    } catch (_: Exception) { false }
}

private suspend fun <T> suspendInstallCallback(block: (ContentsManager.OnInstallFinishedCallback) -> Unit): T? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    block(object : ContentsManager.OnInstallFinishedCallback {
        override fun onFailed(r: ContentsManager.InstallFailedReason, e: Exception?) { if (cont.isActive) cont.resume(null) }
        override fun onSucceed(res: ContentProfile) { if (cont.isActive) @Suppress("UNCHECKED_CAST") cont.resume(res as T) }
    })
}

private fun compareVersions(v1: String, v2: String): Int {
    val regex = Regex("([0-9]+)|([a-z]+)")
    fun getParts(v: String): List<String> {
        var clean = v.lowercase().removeSuffix(".wcp")
        val prefixes = listOf("dxvk-nvapi-arm64ec-", "dxvk-nvapi-", "dxvk-arm64ec-", "dxvk-sarek-async-", "dxvk-sarek-", "dxvk-", "dxvk_", "dxvk", "vkd3d-proton-", "vkd3d-", "box64-bionic-", "box64-", "box86-", "wowbox64-", "fex-")
        for (p in prefixes) {
            if (clean.startsWith(p)) {
                clean = clean.removePrefix(p)
                break
            }
        }
        return regex.findAll(clean).map { it.value }.toList()
    }

    val parts1 = getParts(v1)
    val parts2 = getParts(v2)
    
    val length = maxOf(parts1.size, parts2.size)
    for (i in 0 until length) {
        val p1 = parts1.getOrNull(i)
        val p2 = parts2.getOrNull(i)
        
        if (p1 == p2) continue
        if (p1 == null) return -1
        if (p2 == null) return 1
        
        val isNum1 = p1[0].isDigit()
        val isNum2 = p2[0].isDigit()
        
        if (isNum1 && isNum2) {
            val n1 = p1.toIntOrNull() ?: 0
            val n2 = p2.toIntOrNull() ?: 0
            if (n1 != n2) return n1.compareTo(n2)
        } else if (!isNum1 && !isNum2) {
            val cmp = p1.compareTo(p2)
            if (cmp != 0) return cmp
        } else {
            return if (isNum1) 1 else -1
        }
    }
    return 0
}

private val nightlyPrefixes = mapOf(
    GNComponent.DXVK to listOf("dxvk-nightly-", "dxvk-arm64ec-nightly-", "dxvk-nvapi-nightly-", "dxvk-nvapi-arm64ec-nightly-", "pre-reg"), 
    GNComponent.VKD3D to listOf("vk3dk-nightly-", "vk3dk-arm64ec-nightly-"), 
    GNComponent.BOX64 to listOf("box64-nightly-", "bionic-box64-nightly-"), 
    GNComponent.WOWBOX64 to listOf("wowbox64-nightly-"), 
    GNComponent.FEXCORE to listOf("fex-nightly-")
)
private val stablePrefixes = mapOf(GNComponent.DXVK to listOf("Stable-Dxvk", "Stable-Arm64ec-Dxvk", "Sarek"), GNComponent.VKD3D to listOf("Stable-Vk3dk", "Stable-Arm64ec-Vk3dk"), GNComponent.BOX64 to listOf("Stable-Box64"), GNComponent.WOWBOX64 to listOf("Stable-wowbox64"), GNComponent.FEXCORE to listOf("Stable-FEX"))
