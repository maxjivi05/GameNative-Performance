package app.gamenative.ui.screen.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.utils.Net
import app.gamenative.service.SteamService
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import okhttp3.Request
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import timber.log.Timber
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.Json
import androidx.compose.ui.Alignment
import androidx.compose.material3.LinearProgressIndicator

enum class ContentSource { GN, MTR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsManagerDialog(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var pendingProfile by remember { mutableStateOf<ContentProfile?>(null) }
    val untrustedFiles = remember { mutableStateListOf<ContentProfile.ContentFile>() }
    var showUntrustedConfirm by remember { mutableStateOf(false) }

    val mgr = remember(ctx) { ContentsManager(ctx) }

    // Source selection
    var selectedSource by remember { mutableStateOf(ContentSource.MTR) }
    var mtrContents by remember { mutableStateOf<List<ContentProfile>>(emptyList()) }
    var isLoadingMtr by remember { mutableStateOf(false) }

    // Dropdown state for MTR
    var selectedMtrType by remember { mutableStateOf(ContentProfile.ContentType.CONTENT_TYPE_DXVK) }
    var typeExpandedMtr by remember { mutableStateOf(false) }
    var selectedMtrProfile by remember { mutableStateOf<ContentProfile?>(null) }
    var profileExpandedMtr by remember { mutableStateOf(false) }

    // Installed list state
    var currentType by remember { mutableStateOf(ContentProfile.ContentType.CONTENT_TYPE_DXVK) }
    val installedProfiles = remember { mutableStateListOf<ContentProfile>() }
    var typeExpanded by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ContentProfile?>(null) }

    val refreshInstalled: () -> Unit = {
        try { mgr.syncContents() } catch (_: Exception) {}
        installedProfiles.clear()
        try {
            val list = mgr.getProfiles(currentType)
            if (list != null) installedProfiles.addAll(list.filter { it.remoteUrl == null })
        } catch (_: Exception) {}
    }

    LaunchedEffect(currentType) {
        withContext(Dispatchers.IO) { mgr.syncContents() }
        refreshInstalled()
    }

    // Load MTR contents
    LaunchedEffect(selectedSource) {
        if (selectedSource == ContentSource.MTR && mtrContents.isEmpty()) {
            isLoadingMtr = true
            scope.launch(Dispatchers.IO) {
                try {
                    val url = "https://raw.githubusercontent.com/maxjivi05/Components/main/contents.json"
                    val request = Request.Builder().url(url).build()
                    Net.http.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val jsonString = response.body?.string() ?: "[]"
                            val jsonArray = Json.parseToJsonElement(jsonString).jsonArray
                            val profiles = jsonArray.map { 
                                val obj = it.jsonObject
                                val typeStr = obj["type"]!!.toString().trim('"')
                                val type = when(typeStr.lowercase()) {
                                    "dxvk" -> ContentProfile.ContentType.CONTENT_TYPE_DXVK
                                    "vkd3d" -> ContentProfile.ContentType.CONTENT_TYPE_VKD3D
                                    "box64" -> ContentProfile.ContentType.CONTENT_TYPE_BOX64
                                    "wowbox64" -> ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                                    "fexcore" -> ContentProfile.ContentType.CONTENT_TYPE_FEXCORE
                                    else -> ContentProfile.ContentType.CONTENT_TYPE_WINE
                                }
                                ContentProfile().apply {
                                    this.type = type
                                    this.verName = obj["verName"]!!.toString().trim('"')
                                    this.verCode = obj["verCode"]!!.toString().trim('"').toInt()
                                    this.remoteUrl = obj["remoteUrl"]!!.toString().trim('"')
                                }
                            }
                            withContext(Dispatchers.Main) { mtrContents = profiles }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "ContentsManagerDialog: Error loading MTR contents")
                } finally {
                    isLoadingMtr = false
                }
            }
        }
    }

    val downloadAndInstallContent = { profile: ContentProfile ->
        scope.launch {
            isBusy = true
            statusMessage = "Downloading..."
            try {
                val fileName = profile.remoteUrl!!.substringAfterLast('/')
                val destFile = File(ctx.cacheDir, fileName)
                
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(profile.remoteUrl!!).build()
                    Net.http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw java.io.IOException("Unexpected code $response")
                        val body = response.body ?: throw java.io.IOException("Empty body")
                        val inputStream = body.byteStream()
                        val outputStream = java.io.FileOutputStream(destFile)
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()
                    }
                }

                statusMessage = "Validating..."
                val uri = Uri.fromFile(destFile)
                val result = withContext(Dispatchers.IO) {
                    var outProfile: ContentProfile? = null
                    var fail: ContentsManager.InstallFailedReason? = null
                    val latch = CountDownLatch(1)
                    mgr.extraContentFile(uri, object : ContentsManager.OnInstallFinishedCallback {
                        override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception) { fail = reason; latch.countDown() }
                        override fun onSucceed(p: ContentProfile) { outProfile = p; latch.countDown() }
                    })
                    latch.await()
                    outProfile to fail
                }

                val (extractedProfile, fail) = result
                if (extractedProfile != null) {
                    performFinishInstall(ctx, mgr, extractedProfile) {
                        refreshInstalled()
                        statusMessage = null
                        isBusy = false
                    }
                } else {
                    Toast.makeText(ctx, "Install failed: $fail", Toast.LENGTH_SHORT).show()
                    isBusy = false
                }
                destFile.delete()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                isBusy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            statusMessage = "Validating content..."
            val result = withContext(Dispatchers.IO) {
                var profile: ContentProfile? = null
                var failReason: ContentsManager.InstallFailedReason? = null
                var err: Exception? = null
                val latch = CountDownLatch(1)
                try {
                    mgr.extraContentFile(uri, object : ContentsManager.OnInstallFinishedCallback {
                        override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception) {
                            failReason = reason
                            err = e
                            latch.countDown()
                        }

                        override fun onSucceed(profileArg: ContentProfile) {
                            profile = profileArg
                            latch.countDown()
                        }
                    })
                } catch (e: Exception) {
                    err = e
                    latch.countDown()
                }
                latch.await()
                Triple(profile, failReason, err)
            }

            val (profile, fail, error) = result
            if (profile == null) {
                val msg = when (fail) {
                    ContentsManager.InstallFailedReason.ERROR_BADTAR -> "File cannot be recognized"
                    ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> "Profile not found in content"
                    ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> "Profile cannot be recognized"
                    ContentsManager.InstallFailedReason.ERROR_EXIST -> "Content already exists"
                    ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> "Content is incomplete"
                    ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> "Content cannot be trusted"
                    ContentsManager.InstallFailedReason.ERROR_NOSPACE -> "Not enough space"
                    else -> "Unable to install content"
                }
                statusMessage = error?.message?.let { "$msg: $it" } ?: msg
                Toast.makeText(ctx, statusMessage, Toast.LENGTH_SHORT).show()
                isBusy = false
                return@launch
            }

            pendingProfile = profile
            // Compute untrusted files and show confirmation if any
            val files = withContext(Dispatchers.IO) { mgr.getUnTrustedContentFiles(profile) }
            untrustedFiles.clear()
            untrustedFiles.addAll(files)
            if (untrustedFiles.isNotEmpty()) {
                showUntrustedConfirm = true
                statusMessage = "This content includes files outside the trusted set."
                isBusy = false
            } else {
                // Safe to finish install directly
                performFinishInstall(ctx, mgr, profile) { _ ->
                    // Hide details and refresh installed list
                    pendingProfile = null
                    currentType = profile.type
                    refreshInstalled()
                    statusMessage = null
                    isBusy = false
                }
            }
            SteamService.isImporting = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.contents_manager), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (selectedSource == ContentSource.MTR) {
                    if (isLoadingMtr) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    } else {
                        Text("Online Components (MTR):", style = MaterialTheme.typography.titleMedium)
                        
                        // Component Type Dropdown
                        ExposedDropdownMenuBox(
                            expanded = typeExpandedMtr,
                            onExpandedChange = { typeExpandedMtr = !typeExpandedMtr },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedMtrType.toString(),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpandedMtr) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                placeholder = { Text("Select component type") }
                            )
                            ExposedDropdownMenu(expanded = typeExpandedMtr, onDismissRequest = { typeExpandedMtr = false }) {
                                val types = listOf(
                                    ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                                    ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                                    ContentProfile.ContentType.CONTENT_TYPE_BOX64,
                                    ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
                                    ContentProfile.ContentType.CONTENT_TYPE_FEXCORE
                                )
                                types.forEach { t ->
                                    DropdownMenuItem(text = { Text(t.toString()) }, onClick = { selectedMtrType = t; selectedMtrProfile = null; typeExpandedMtr = false })
                                }
                            }
                        }

                        // Component Version Dropdown
                        val availableProfiles = mtrContents.filter { it.type == selectedMtrType }
                        ExposedDropdownMenuBox(
                            expanded = profileExpandedMtr,
                            onExpandedChange = { profileExpandedMtr = !profileExpandedMtr },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedMtrProfile?.verName ?: "",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpandedMtr) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                placeholder = { Text("Select version") }
                            )
                            ExposedDropdownMenu(expanded = profileExpandedMtr, onDismissRequest = { profileExpandedMtr = false }) {
                                availableProfiles.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.verName) }, onClick = { selectedMtrProfile = p; profileExpandedMtr = false })
                                }
                            }
                        }

                        if (selectedMtrProfile != null) {
                            Button(
                                onClick = { downloadAndInstallContent(selectedMtrProfile!!) },
                                enabled = !isBusy,
                                modifier = Modifier.padding(top = 16.dp)
                            ) { Text("Download & Install") }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "Import local components (.wcp):",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = {
                        SteamService.isImporting = true
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !isBusy,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) { Text(stringResource(R.string.import_wcp_from_device)) }

                if (isBusy) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        Text(text = statusMessage ?: stringResource(R.string.working))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Text(text = stringResource(R.string.installed_contents), style = MaterialTheme.typography.titleMedium)

                // Content type selector for installed
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = currentType.toString(),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        placeholder = { Text(stringResource(R.string.select_type)) }
                    )

                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        val allowed = listOf(
                            ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                            ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                            ContentProfile.ContentType.CONTENT_TYPE_BOX64,
                            ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
                            ContentProfile.ContentType.CONTENT_TYPE_FEXCORE
                        )
                        allowed.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.toString()) },
                                onClick = {
                                    currentType = t
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Installed list
                if (installedProfiles.isEmpty()) {
                    Text(
                        text = "No installed content for this type.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        installedProfiles.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "${p.verName} (${p.verCode})", style = MaterialTheme.typography.bodyMedium)
                                    if (!p.desc.isNullOrEmpty()) {
                                        Text(text = p.desc, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(
                                    onClick = { deleteTarget = p },
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
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )

    if (showUntrustedConfirm && pendingProfile != null) {
        AlertDialog(
            onDismissRequest = { showUntrustedConfirm = false },
            title = { Text(stringResource(R.string.untrusted_files_detected)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = "This content includes files outside the trusted set. Review and confirm to proceed.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    untrustedFiles.forEach { cf ->
                        Text(text = "- ${cf.target}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val profile = pendingProfile ?: return@TextButton
                    showUntrustedConfirm = false
                    isBusy = true
                    scope.launch {
                        performFinishInstall(ctx, mgr, profile) {
                            pendingProfile = null
                            currentType = profile.type
                            refreshInstalled()
                            statusMessage = null
                            isBusy = false
                        }
                    }
                }) { Text(stringResource(R.string.install_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { showUntrustedConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.remove_content)) },
            text = { Text(stringResource(R.string.remove_content_confirmation, target.verName, target.verCode)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { mgr.removeContent(target) }
                        refreshInstalled()
                        Toast.makeText(ctx, "Removed ${target.verName}", Toast.LENGTH_SHORT).show()
                        deleteTarget = null
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth())
    }
}

private suspend fun performFinishInstall(
    context: Context,
    mgr: ContentsManager,
    profile: ContentProfile,
    onDone: (String) -> Unit,
) {
    val msg = withContext(Dispatchers.IO) {
        var message = ""
        val latch = CountDownLatch(1)
        try {
            mgr.finishInstallContent(profile, object : ContentsManager.OnInstallFinishedCallback {
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception) {
                    message = when (reason) {
                        ContentsManager.InstallFailedReason.ERROR_EXIST -> "Content already exists"
                        ContentsManager.InstallFailedReason.ERROR_NOSPACE -> "Not enough space"
                        else -> "Failed to install content"
                    }
                    latch.countDown()
                }

                override fun onSucceed(profileArg: ContentProfile) {
                    message = "Content installed successfully"
                    latch.countDown()
                }
            })
        } catch (e: Exception) {
            message = "Installation error: ${e.message}"
            latch.countDown()
        }
        latch.await()
        message
    }
    onDone(msg)
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}