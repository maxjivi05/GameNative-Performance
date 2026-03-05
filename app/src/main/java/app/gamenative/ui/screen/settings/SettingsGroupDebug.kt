package app.gamenative.ui.screen.settings

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.CrashHandler
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.service.SteamService
import app.gamenative.ui.component.dialog.CrashLogDialog
import app.gamenative.ui.component.dialog.FileExplorerDialog
import app.gamenative.ui.component.dialog.WineDebugChannelsDialog
import coil.imageLoader
import com.winlator.PrefManager as WinlatorPrefManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsGroupDebug() {
    val context = LocalContext.current
    PrefManager.init(context)
    WinlatorPrefManager.init(context)

    var showFileExplorer by rememberSaveable { mutableStateOf(false) }

    if (showFileExplorer) {
        FileExplorerDialog(onDismiss = { showFileExplorer = false })
    }

    var allWineChannels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showChannelsDialog by remember { mutableStateOf(false) }
    var selectedWineChannels by remember { mutableStateOf(PrefManager.wineDebugChannels.split(",")) }
    LaunchedEffect(Unit) {
        val json = context.assets.open("wine_debug_channels.json").bufferedReader().use { it.readText() }
        allWineChannels = Json.decodeFromString(json)
    }

    WineDebugChannelsDialog(
        openDialog = showChannelsDialog,
        allChannels = allWineChannels,
        currentSelection = selectedWineChannels,
        onSave = { newSelection ->
            selectedWineChannels = newSelection
            PrefManager.wineDebugChannels = newSelection.joinToString(",")
            showChannelsDialog = false
        },
        onDismiss = { showChannelsDialog = false }
    )

    var showLogcatDialog by rememberSaveable { mutableStateOf(false) }
    var enableWineDebugPref by rememberSaveable { mutableStateOf(PrefManager.enableWineDebug) }
    var enableBox86Logs by rememberSaveable { mutableStateOf(WinlatorPrefManager.getBoolean("enable_box86_64_logs", false)) }
    var latestCrashFile: File? by rememberSaveable { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        val crashDir = File(context.getExternalFilesDir(null), "crash_logs")
        latestCrashFile = crashDir.listFiles()?.filter { it.name.startsWith("pluvia_crash_") }?.maxByOrNull { it.lastModified() }
    }

    val saveResultContract = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { resultUri ->
        try { resultUri?.let { uri -> context.contentResolver.openOutputStream(uri)?.use { outputStream -> latestCrashFile?.inputStream()?.use { inputStream -> inputStream.copyTo(outputStream) } } } } catch (e: Exception) { Toast.makeText(context, "Failed to save log", Toast.LENGTH_SHORT).show() }
    }
    val saveLogCat = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { resultUri ->
        try { resultUri?.let { uri -> val logs = CrashHandler.getAppLogs(1000); context.contentResolver.openOutputStream(uri)?.use { it.write(logs.toByteArray()) } } } catch (e: Exception) { Toast.makeText(context, R.string.toast_failed_log_save, Toast.LENGTH_SHORT).show() }
    }

    CrashLogDialog(visible = showLogcatDialog && latestCrashFile != null, fileName = latestCrashFile?.name ?: "No Filename", fileText = latestCrashFile?.readText() ?: "", onSave = { latestCrashFile?.let { file -> saveResultContract.launch(file.name) } }, onDismissRequest = { showLogcatDialog = false })

    var showWineLogDialog by rememberSaveable { mutableStateOf(false) }
    var latestWineLogFile: File? by rememberSaveable { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        val wineLogDir = File(context.getExternalFilesDir(null), "wine_logs"); wineLogDir.mkdirs()
        val wineLogFile = File(wineLogDir, "wine_debug.log"); latestWineLogFile = if (wineLogFile.exists()) wineLogFile else null
    }
    val saveWineLogContract = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { resultUri ->
        try { resultUri?.let { uri -> context.contentResolver.openOutputStream(uri)?.use { output -> latestWineLogFile?.inputStream()?.use { input -> input.copyTo(output) } } } } catch (e: Exception) { Toast.makeText(context, "Failed to save Wine log", Toast.LENGTH_SHORT).show() }
    }

    if (showWineLogDialog && latestWineLogFile != null) {
        CrashLogDialog(visible = showWineLogDialog, fileName = latestWineLogFile?.name ?: "wine_debug.log", fileText = latestWineLogFile?.readText() ?: "", onSave = { latestWineLogFile?.let { file -> saveWineLogContract.launch(file.name) } }, onDismissRequest = { showWineLogDialog = false })
    }

    var masterContainers by rememberSaveable { mutableStateOf(PrefManager.masterContainers) }
    var showManageContainersDialog by rememberSaveable { mutableStateOf(false) }

    if (showManageContainersDialog) {
        app.gamenative.ui.component.dialog.ManageContainersDialog(
            onDismiss = { showManageContainersDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSectionHeader("Advanced")
        SettingsTile(
            title = "Manage Containers",
            subtitle = "View, manage, and delete installed containers",
            icon = Icons.Default.Storage,
            onClick = { showManageContainersDialog = true }
        )
        SettingsTile(
            title = "Master Containers",
            subtitle = "Share the same container for all games using the same Proton version and architecture.",
            icon = Icons.Default.Hub,
            subtitleMaxLines = Int.MAX_VALUE,
            trailing = { Switch(checked = masterContainers, onCheckedChange = { masterContainers = it; PrefManager.masterContainers = it }) }
        )

        SettingsSectionHeader(stringResource(R.string.settings_debug_title))

        SettingsTile(title = "Embedded File Access", subtitle = "Explore internal app files and container data", icon = Icons.Default.Folder, onClick = { showFileExplorer = true })
        SettingsTile(title = stringResource(R.string.settings_save_logcat_title), subtitle = stringResource(R.string.settings_save_logcat_subtitle), icon = Icons.Default.BugReport, onClick = { saveLogCat.launch("app_logs_${CrashHandler.timestamp}.txt") })
        SettingsTile(title = stringResource(R.string.settings_debug_wine_channels_title), subtitle = if (selectedWineChannels.isNotEmpty()) selectedWineChannels.joinToString(",") else "No channels", icon = Icons.Default.List, onClick = { showChannelsDialog = true })
        
        SettingsTile(
            title = stringResource(R.string.settings_debug_wine_logs_title),
            subtitle = stringResource(R.string.settings_debug_wine_logs_subtitle),
            icon = Icons.Default.Description,
            trailing = { Switch(checked = enableWineDebugPref, onCheckedChange = { enableWineDebugPref = it; PrefManager.enableWineDebug = it }) }
        )
        SettingsTile(
            title = stringResource(R.string.settings_debug_box_logs_title),
            subtitle = stringResource(R.string.settings_debug_box_logs_subtitle),
            icon = Icons.Default.Terminal,
            trailing = { Switch(checked = enableBox86Logs, onCheckedChange = { enableBox86Logs = it; WinlatorPrefManager.putBoolean("enable_box86_64_logs", it) }) }
        )

        SettingsTile(title = stringResource(R.string.settings_debug_view_crash_title), subtitle = if (latestCrashFile != null) "View recent crash log" else "No crash logs", icon = Icons.Default.History, enabled = latestCrashFile != null, onClick = { showLogcatDialog = true })
        SettingsTile(title = stringResource(R.string.settings_debug_view_log_title), subtitle = if (latestWineLogFile != null) "View Wine/Box64 log" else "No debug logs", icon = Icons.Default.MenuBook, enabled = latestWineLogFile != null, onClick = { showWineLogDialog = true })

        SettingsSectionHeader("Danger Zone")
        SettingsTile(
            title = stringResource(R.string.settings_debug_clear_prefs_title),
            subtitle = "Long click to clear preferences and exit",
            icon = Icons.Default.DeleteForever,
            onClick = { Toast.makeText(context, "Long click to activate", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.combinedClickable(onLongClick = { PrefManager.init(context); (context as ComponentActivity).finishAffinity() }, onClick = {})
        )
        SettingsTile(
            title = stringResource(R.string.settings_debug_clear_db_title),
            subtitle = "Long click to clear Steam database and exit",
            icon = Icons.Default.Storage,
            onClick = { Toast.makeText(context, "Long click to activate", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.combinedClickable(onLongClick = { SteamService.stop(); SteamService.clearDatabase(); (context as ComponentActivity).finishAffinity() }, onClick = {})
        )
    }
}
