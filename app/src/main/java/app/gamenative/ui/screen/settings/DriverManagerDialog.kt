package app.gamenative.ui.screen.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import app.gamenative.R
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.winlator.contents.AdrenotoolsManager
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Surface
import app.gamenative.ui.theme.PluviaTheme
import android.content.res.Configuration
import android.widget.Toast
import app.gamenative.service.SteamService
import app.gamenative.ui.component.dialog.LoadingDialog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.URL
import timber.log.Timber
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import okhttp3.Response
import java.io.FileOutputStream
import kotlinx.coroutines.delay

object Net {
    val http: OkHttpClient by lazy { OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0,  TimeUnit.MILLISECONDS)     // no per-packet timer
        .pingInterval(30, TimeUnit.SECONDS)         // keep HTTP/2 alive
        .retryOnConnectionFailure(true)             // default, but explicit
        .build() }

}

enum class DriverSource { GN, MTR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverManagerDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    initialSource: DriverSource = DriverSource.MTR,
) {
    if (!open) return
    val ctx = LocalContext.current
    var lastMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(-1L) }
    val scope = rememberCoroutineScope()

    // Source selection
    var selectedSource by remember { mutableStateOf(initialSource) }

    // Driver manifest handling
    var driverManifest by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mtrDriverManifest by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoadingManifest by remember { mutableStateOf(true) }
    var manifestError by remember { mutableStateOf<String?>(null) }

    // Dropdown state
    var isExpanded by remember { mutableStateOf(false) }
    var selectedDriverKey by remember { mutableStateOf("") }

    // Gather installed custom drivers via AdrenotoolsManager and allow refreshing
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

    // Load manifests
    LaunchedEffect(Unit) {
        refreshDriverList()

        // Fetch GN manifest
        scope.launch(Dispatchers.IO) {
            try {
                val manifestUrl = "https://raw.githubusercontent.com/utkarshdalal/gamenative-landing-page/refs/heads/main/data/manifest.json"
                val request = Request.Builder().url(manifestUrl).build()
                val response = Net.http.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: "{}"
                    val jsonObject = Json.decodeFromString<JsonObject>(jsonString)
                    val manifest = jsonObject.entries.associate { it.key to it.value.toString().trim('"') }
                    withContext(Dispatchers.Main) {
                        driverManifest = manifest
                        if (selectedSource == DriverSource.GN) isLoadingManifest = false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "DriverManagerDialog: Error loading GN manifest")
            }
        }

        // Fetch MTR manifest
        scope.launch(Dispatchers.IO) {
            try {
                val mtrApiUrl = "https://api.github.com/repos/maxjivi05/Components/contents/Drivers"
                val request = Request.Builder().url(mtrApiUrl).build()
                val response = Net.http.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: "[]"
                    val jsonArray = Json.parseToJsonElement(jsonString).jsonArray
                    val manifest = jsonArray.associate { 
                        val obj = it.jsonObject
                        obj["name"]!!.toString().trim('"') to obj["download_url"]!!.toString().trim('"')
                    }
                    withContext(Dispatchers.Main) {
                        mtrDriverManifest = manifest
                        if (selectedSource == DriverSource.MTR) isLoadingManifest = false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "DriverManagerDialog: Error loading MTR manifest")
            }
        }
    }

    LaunchedEffect(selectedSource) {
        selectedDriverKey = ""
        isLoadingManifest = when (selectedSource) {
            DriverSource.GN -> driverManifest.isEmpty()
            DriverSource.MTR -> mtrDriverManifest.isEmpty()
        }
    }

    LoadingDialog(
        visible = isDownloading,
        progress = downloadProgress,
        message = stringResource(R.string.downloading),
    )

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isImporting = true
                val res = withContext(Dispatchers.IO) { handlePickedUri(ctx, it) }
                lastMessage = res
                if (res.startsWith("Installed driver:")) refreshDriverList()
                Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
                SteamService.isImporting = false
                isImporting = false
            }
        }
    }

    // Common download and install logic
    val downloadAndInstall = { fileName: String, url: String? ->
        scope.launch {
            isDownloading = true
            downloadProgress = 0f
            downloadBytes = 0L
            totalBytes = -1L
            try {
                val destFile = File(ctx.cacheDir, fileName)
                if (url == null) {
                    // GN path
                    var lastUpdate = 0L
                    SteamService.fetchFileWithFallback(fileName = "drivers/$fileName", dest = destFile, context = ctx) { progress ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 300) {
                            lastUpdate = now
                            scope.launch(Dispatchers.Main) { downloadProgress = progress.coerceIn(0f, 1f) }
                        }
                    }
                } else {
                    // MTR path
                    withContext(Dispatchers.IO) {
                        val request = Request.Builder().url(url).build()
                        Net.http.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw IOException("Unexpected code $response")
                            val body = response.body ?: throw IOException("Empty body")
                            totalBytes = body.contentLength()
                            val inputStream = body.byteStream()
                            val outputStream = FileOutputStream(destFile)
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                downloadBytes += bytesRead
                                if (totalBytes > 0) {
                                    downloadProgress = downloadBytes.toFloat() / totalBytes
                                }
                            }
                            outputStream.flush()
                            outputStream.close()
                            inputStream.close()
                        }
                    }
                }

                isDownloading = false
                isInstalling = true
                val res = withContext(Dispatchers.IO) { handlePickedUri(ctx, Uri.fromFile(destFile)) }
                withContext(Dispatchers.Main) {
                    lastMessage = res
                    if (res.startsWith("Installed driver:")) refreshDriverList()
                    Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
                }
                destFile.delete()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isDownloading = false
                isInstalling = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.driver_manager), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState())
            ) {
                // Source Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedSource = DriverSource.GN },
                        modifier = Modifier.weight(1f),
                        colors = if (selectedSource == DriverSource.GN) androidx.compose.material3.ButtonDefaults.buttonColors() else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                    ) { Text("GN") }
                    Button(
                        onClick = { selectedSource = DriverSource.MTR },
                        modifier = Modifier.weight(1f),
                        colors = if (selectedSource == DriverSource.MTR) androidx.compose.material3.ButtonDefaults.buttonColors() else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                    ) { Text("MTR") }
                }

                Text(
                    text = "Import a custom graphics driver package",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (isLoadingManifest) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(text = "Loading available drivers...", modifier = Modifier.weight(1f))
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    val currentManifest = if (selectedSource == DriverSource.GN) driverManifest else mtrDriverManifest
                    if (currentManifest.isNotEmpty()) {
                        Text(text = "Available online drivers (${selectedSource.name}):", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        ExposedDropdownMenuBox(
                            expanded = isExpanded,
                            onExpandedChange = { isExpanded = !isExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedDriverKey,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                placeholder = { Text("Select a driver") }
                            )
                            ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                                val keys = currentManifest.keys.toList()
                                val sortedKeys = if (selectedSource == DriverSource.MTR) {
                                    keys.sortedWith { a, b ->
                                        // Simple version-aware comparator
                                        val vA = a.substringAfter("_v", "").substringBefore("_")
                                        val vB = b.substringAfter("_v", "").substringBefore("_")
                                        
                                        if (vA.isNotEmpty() && vB.isNotEmpty()) {
                                            val partsA = vA.split('.').mapNotNull { it.toIntOrNull() }
                                            val partsB = vB.split('.').mapNotNull { it.toIntOrNull() }
                                            
                                            var result = 0
                                            val maxParts = maxOf(partsA.size, partsB.size)
                                            for (i in 0 until maxParts) {
                                                val pA = partsA.getOrElse(i) { 0 }
                                                val pB = partsB.getOrElse(i) { 0 }
                                                if (pA != pB) {
                                                    result = pB.compareTo(pA) // Descending
                                                    break
                                                }
                                            }
                                            if (result != 0) result else b.compareTo(a)
                                        } else {
                                            b.compareTo(a) // Fallback to descending string sort
                                        }
                                    }
                                } else {
                                    keys.sorted()
                                }

                                sortedKeys.forEach { driverKey ->
                                    DropdownMenuItem(
                                        text = { Text(driverKey) },
                                        onClick = {
                                            selectedDriverKey = driverKey
                                            isExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedDriverKey.isNotEmpty()) {
                            Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (selectedSource == DriverSource.GN) downloadAndInstall(currentManifest[selectedDriverKey]!!, null)
                                        else downloadAndInstall(selectedDriverKey, currentManifest[selectedDriverKey])
                                    },
                                    enabled = !isDownloading && !isImporting
                                ) { Text(stringResource(R.string.download)) }
                                if (isDownloading) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        LinearProgressIndicator(progress = downloadProgress)
                                        Text(text = if (totalBytes > 0) "${formatBytes(downloadBytes)} / ${formatBytes(totalBytes)}" else "Downloading...")
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Local driver import section
                Text(
                    text = "Import from local storage:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = {
                        SteamService.isImporting = true
                        launcher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                    },
                    enabled = !isImporting && !isDownloading,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(stringResource(R.string.import_zip_from_device))
                }

                if (isImporting) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Importing driver...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (installedDrivers.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Installed custom drivers",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        for (id in installedDrivers) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                val meta = driverMeta[id]
                                val display = buildString {
                                    if (!meta?.first.isNullOrEmpty()) append(meta?.first) else append(id)
                                }
                                Text(
                                    text = display,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = { driverToDelete = id },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    // Confirmation dialog for deletion
                    driverToDelete?.let { id ->
                        AlertDialog(
                            onDismissRequest = { driverToDelete = null },
                            title = { Text(text = stringResource(R.string.confirm_delete)) },
                            text = { Text(text = stringResource(R.string.remove_driver_confirmation, id)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    try {
                                        AdrenotoolsManager(ctx).removeDriver(id)
                                        lastMessage = "Removed driver: $id"
                                        Toast.makeText(ctx, "Removed driver: $id", Toast.LENGTH_SHORT).show()
                                        refreshDriverList()
                                    } catch (e: Exception) {
                                        lastMessage = "Error removing $id: ${e.message}"
                                        Toast.makeText(ctx, "Error removing $id: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    driverToDelete = null
                                }) {
                                    Text(
                                        text = "Delete",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { driverToDelete = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
    )
}

private fun handlePickedUri(context: Context, uri: Uri): String {
    return try {
        val name = AdrenotoolsManager(context).installDriver(uri)
        if (name.isNotEmpty()) {
            "Installed driver: $name"
        } else {
            "Failed to install driver: driver already installed or .zip corrupted"
        }
    } catch (e: Exception) {
        "Error importing driver: ${e.message}"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview
@Composable
private fun Preview_DriverManagerDialog() {
    PluviaTheme {
        Surface {
            DriverManagerDialog(open = true, onDismiss = { })
        }
    }
}