package app.gamenative.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.enums.AppTheme
import app.gamenative.events.AndroidEvent
import app.gamenative.service.SteamService
import app.gamenative.service.amazon.AmazonAuthManager
import app.gamenative.service.amazon.AmazonService
import app.gamenative.service.epic.EpicAuthManager
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGAuthManager
import app.gamenative.service.gog.GOGService
import app.gamenative.ui.component.dialog.LoadingDialog
import app.gamenative.ui.component.dialog.MessageDialog
import app.gamenative.ui.component.dialog.SingleChoiceDialog
import app.gamenative.ui.screen.auth.AmazonOAuthActivity
import app.gamenative.ui.screen.auth.EpicOAuthActivity
import app.gamenative.ui.screen.auth.GOGOAuthActivity
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.utils.IconSwitcher
import app.gamenative.utils.LocaleHelper
import com.materialkolor.PaletteStyle
import com.winlator.core.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.math.roundToInt

private suspend fun handleAmazonAuthentication(context: Context, authCode: String, coroutineScope: CoroutineScope, onLoadingChange: (Boolean) -> Unit, onError: (String?) -> Unit, onSuccess: () -> Unit, onDialogClose: () -> Unit) {
    onLoadingChange(true); onError(null)
    try {
        val result = AmazonService.authenticateWithCode(context, authCode)
        if (result.isSuccess) { AmazonService.start(context); AmazonService.triggerLibrarySync(context); PluviaApp.events.emit(AndroidEvent.StoreAuthChanged); onSuccess(); onLoadingChange(false); onDialogClose() }
        else { onError(result.exceptionOrNull()?.message ?: "Authentication failed"); onLoadingChange(false) }
    } catch (e: Exception) { onError(e.message ?: "Authentication failed"); onLoadingChange(false) }
}

private suspend fun handleGogAuthentication(context: Context, authCode: String, coroutineScope: CoroutineScope, onLoadingChange: (Boolean) -> Unit, onError: (String?) -> Unit, onSuccess: (Int) -> Unit, onDialogClose: () -> Unit) {
    onLoadingChange(true); onError(null)
    try {
        val result = GOGService.authenticateWithCode(context, authCode)
        if (result.isSuccess) { GOGService.start(context); GOGService.triggerLibrarySync(context); PluviaApp.events.emit(AndroidEvent.StoreAuthChanged); onSuccess(0); onLoadingChange(false); onDialogClose() }
        else { onError(result.exceptionOrNull()?.message ?: "Authentication failed"); onLoadingChange(false) }
    } catch (e: Exception) { onError(e.message ?: "Authentication failed"); onLoadingChange(false) }
}

private suspend fun handleEpicAuthentication(context: Context, authCode: String, coroutineScope: CoroutineScope, onLoadingChange: (Boolean) -> Unit, onError: (String?) -> Unit, onSuccess: () -> Unit, onDialogClose: () -> Unit) {
    onLoadingChange(true); onError(null)
    try {
        val result = EpicService.authenticateWithCode(context, authCode)
        if (result.isSuccess) { EpicService.start(context); EpicService.triggerLibrarySync(context); PluviaApp.events.emit(AndroidEvent.StoreAuthChanged); onSuccess(); onLoadingChange(false); onDialogClose() }
        else { onError(result.exceptionOrNull()?.message ?: "Authentication failed"); onLoadingChange(false) }
    } catch (e: Exception) { onError(e.message ?: "Authentication failed"); onLoadingChange(false) }
}

@Composable
fun SettingsGroupInterface(
    appTheme: AppTheme,
    paletteStyle: PaletteStyle,
    onAppTheme: (AppTheme) -> Unit,
    onPaletteStyle: (PaletteStyle) -> Unit,
    onNavigateToSteamLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    var openWebLinks by rememberSaveable { mutableStateOf(PrefManager.openWebLinksExternally) }
    var showStatusBarRestartDialog by rememberSaveable { mutableStateOf(false) }
    var pendingStatusBarValue by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showStatusBarLoadingDialog by rememberSaveable { mutableStateOf(false) }
    var hideStatusBar by rememberSaveable { mutableStateOf(PrefManager.hideStatusBarWhenNotInGame) }
    var openLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageRestartDialog by rememberSaveable { mutableStateOf(false) }
    var pendingLanguageCode by rememberSaveable { mutableStateOf<String?>(null) }
    var showLanguageLoadingDialog by rememberSaveable { mutableStateOf(false) }
    val languageCodes = remember { LocaleHelper.getSupportedLanguageCodes() }
    val languageNames = remember { LocaleHelper.getSupportedLanguageNames() }
    var selectedLanguageIndex by rememberSaveable { mutableStateOf(languageCodes.indexOf(PrefManager.appLanguage).takeIf { it >= 0 } ?: 0) }

    val steamRegionsMap: Map<Int, String> = remember {
        val jsonString = context.assets.open("steam_regions.json").bufferedReader().use { it.readText() }
        Json.decodeFromString<Map<String, String>>(jsonString).mapKeys { it.key.toInt() }
    }
    val steamRegionsList = remember {
        val entries = steamRegionsMap.toList()
        val (autoEntries, otherEntries) = entries.partition { it.first == 0 }
        autoEntries + otherEntries.sortedBy { it.second }
    }
    var openRegionDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRegionIndex by rememberSaveable { mutableStateOf(steamRegionsList.indexOfFirst { it.first == PrefManager.cellId }.takeIf { it >= 0 } ?: 0) }

    var gogLoginLoading by rememberSaveable { mutableStateOf(false) }
    var showGOGLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var gogLogoutLoading by rememberSaveable { mutableStateOf(false) }
    var epicLoginLoading by rememberSaveable { mutableStateOf(false) }
    var showEpicLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var epicLogoutLoading by rememberSaveable { mutableStateOf(false) }
    var amazonLoginLoading by rememberSaveable { mutableStateOf(false) }
    var showSteamLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var steamLogoutLoading by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope

    val gogOAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)?.let { code ->
                lifecycleScope.launch { handleGogAuthentication(context, code, lifecycleScope, { gogLoginLoading = it }, { if (it != null) Toast.makeText(context, it, Toast.LENGTH_LONG).show() }, { Toast.makeText(context, R.string.gog_login_success_title, Toast.LENGTH_SHORT).show() }, {}) }
            }
        }
    }
    val epicOAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(EpicOAuthActivity.EXTRA_AUTH_CODE)?.let { code ->
                lifecycleScope.launch { handleEpicAuthentication(context, code, lifecycleScope, { epicLoginLoading = it }, { if (it != null) Toast.makeText(context, it, Toast.LENGTH_LONG).show() }, { Toast.makeText(context, R.string.epic_login_success_title, Toast.LENGTH_SHORT).show() }, {}) }
            }
        }
    }
    val amazonOAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(AmazonOAuthActivity.EXTRA_AUTH_CODE)?.let { code ->
                lifecycleScope.launch { handleAmazonAuthentication(context, code, lifecycleScope, { amazonLoginLoading = it }, { if (it != null) Toast.makeText(context, it, Toast.LENGTH_LONG).show() }, { Toast.makeText(context, R.string.amazon_login_success_title, Toast.LENGTH_SHORT).show() }, {}) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsSectionHeader(stringResource(R.string.settings_interface_title))
        SettingsTile(title = stringResource(R.string.settings_interface_external_links_title), subtitle = stringResource(R.string.settings_interface_external_links_subtitle), icon = Icons.Default.OpenInNew, trailing = { Switch(checked = openWebLinks, onCheckedChange = { openWebLinks = it; PrefManager.openWebLinksExternally = it }) })
        SettingsTile(title = stringResource(R.string.settings_interface_hide_statusbar_title), subtitle = stringResource(R.string.settings_interface_hide_statusbar_subtitle), icon = Icons.Default.Fullscreen, trailing = { Switch(checked = hideStatusBar, onCheckedChange = { newValue -> hideStatusBar = newValue; pendingStatusBarValue = newValue; showStatusBarRestartDialog = true }) })
        SettingsTile(title = stringResource(R.string.settings_language), subtitle = LocaleHelper.getLanguageDisplayName(PrefManager.appLanguage), icon = Icons.Default.Language, onClick = { openLanguageDialog = true })

        var selectedVariant by rememberSaveable { mutableStateOf(if (PrefManager.useAltLauncherIcon || PrefManager.useAltNotificationIcon) 1 else 0) }
        Column {
            Text(text = stringResource(R.string.settings_interface_icon_style), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconVariantCard(label = stringResource(R.string.settings_theme_default), launcherIconRes = R.mipmap.ic_launcher, notificationIconRes = R.drawable.ic_notification, selected = selectedVariant == 0, onClick = { selectedVariant = 0; PrefManager.useAltLauncherIcon = false; PrefManager.useAltNotificationIcon = false; IconSwitcher.applyLauncherIcon(context, false) })
                IconVariantCard(label = stringResource(R.string.settings_theme_alternate), launcherIconRes = R.mipmap.ic_launcher_alt, notificationIconRes = R.drawable.ic_notification_alt, selected = selectedVariant == 1, onClick = { selectedVariant = 1; PrefManager.useAltLauncherIcon = true; PrefManager.useAltNotificationIcon = true; IconSwitcher.applyLauncherIcon(context, true) })
            }
        }

        SettingsSectionHeader("Game Stores")
        SettingsTile(title = "Steam", subtitle = if (SteamService.isLoggedIn) "Logged in" else stringResource(R.string.steam_settings_login_subtitle), icon = Icons.Default.Cloud, onClick = { if (!SteamService.isLoggedIn) onNavigateToSteamLogin() else showSteamLogoutDialog = true }, trailing = { Icon(imageVector = if (SteamService.isLoggedIn) Icons.Default.Logout else Icons.Default.Login, contentDescription = null, tint = if (SteamService.isLoggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) })
        val isGogLoggedIn = GOGAuthManager.hasStoredCredentials(context)
        SettingsTile(title = "GOG", subtitle = if (isGogLoggedIn) "Logged in" else stringResource(R.string.gog_settings_login_subtitle), icon = painterResource(R.drawable.ic_gog), onClick = { if (!isGogLoggedIn) gogOAuthLauncher.launch(Intent(context, GOGOAuthActivity::class.java)) else showGOGLogoutDialog = true }, trailing = { Icon(imageVector = if (isGogLoggedIn) Icons.Default.Logout else Icons.Default.Login, contentDescription = null, tint = if (isGogLoggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) })
        val isEpicLoggedIn = EpicAuthManager.hasStoredCredentials(context)
        SettingsTile(title = "Epic Games", subtitle = if (isEpicLoggedIn) "Logged in" else stringResource(R.string.epic_settings_login_subtitle), icon = painterResource(R.drawable.ic_epic), onClick = { if (!isEpicLoggedIn) epicOAuthLauncher.launch(Intent(context, EpicOAuthActivity::class.java)) else showEpicLogoutDialog = true }, trailing = { Icon(imageVector = if (isEpicLoggedIn) Icons.Default.Logout else Icons.Default.Login, contentDescription = null, tint = if (isEpicLoggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) })
        val isAmazonLoggedIn = AmazonAuthManager.hasStoredCredentials(context)
        SettingsTile(title = "Amazon Games", subtitle = if (isAmazonLoggedIn) "Logged in" else stringResource(R.string.amazon_settings_login_subtitle), icon = Icons.Default.ShoppingCart, onClick = { if (!isAmazonLoggedIn) amazonOAuthLauncher.launch(Intent(context, AmazonOAuthActivity::class.java)) else { lifecycleScope.launch(Dispatchers.IO) { AmazonAuthManager.logout(context) }; Toast.makeText(context, R.string.amazon_logout_success, Toast.LENGTH_SHORT).show() } }, trailing = { Icon(imageVector = if (isAmazonLoggedIn) Icons.Default.Logout else Icons.Default.Login, contentDescription = null, tint = if (isAmazonLoggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) })

        SettingsSectionHeader(stringResource(R.string.settings_downloads_title))
        var wifiOnlyDownload by rememberSaveable { mutableStateOf(PrefManager.downloadOnWifiOnly) }
        SettingsTile(title = stringResource(R.string.settings_interface_wifi_only_title), subtitle = stringResource(R.string.settings_interface_wifi_only_subtitle), icon = Icons.Default.Wifi, trailing = { Switch(checked = wifiOnlyDownload, onCheckedChange = { wifiOnlyDownload = it; PrefManager.downloadOnWifiOnly = it }) })
        val downloadSpeedLabels = listOf(stringResource(R.string.settings_download_slow), stringResource(R.string.settings_download_medium), stringResource(R.string.settings_download_fast), stringResource(R.string.settings_download_blazing))
        val downloadSpeedValues = remember { listOf(8, 16, 24, 32) }
        var downloadSpeedValue by rememberSaveable { mutableStateOf(downloadSpeedValues.indexOf(PrefManager.downloadSpeed).takeIf { it >= 0 }?.toFloat() ?: 2f) }
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.05f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)); Column { Text(text = stringResource(R.string.settings_download_speed), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(text = stringResource(R.string.settings_download_heat_warning), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) } }
                Spacer(Modifier.height(16.dp)); Slider(value = downloadSpeedValue, onValueChange = { newIndex -> downloadSpeedValue = newIndex; val index = newIndex.roundToInt().coerceIn(0, 3); PrefManager.downloadSpeed = downloadSpeedValues[index] }, valueRange = 0f..3f, steps = 2)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { downloadSpeedLabels.forEach { label -> Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp) } }
            }
        }

        SettingsSectionHeader("Storage")
        val sm = context.getSystemService(StorageManager::class.java)
        val dirs = remember { context.getExternalFilesDirs(null).filterNotNull().filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }.filter { sm.getStorageVolume(it)?.isPrimary != true } }
        val labels = remember(dirs) { dirs.map { dir -> sm.getStorageVolume(dir)?.getDescription(context) ?: dir.name } }
        var useExternalStorage by rememberSaveable { mutableStateOf(PrefManager.useExternalStorage) }
        SettingsTile(title = stringResource(R.string.settings_interface_external_storage_title), subtitle = if (dirs.isEmpty()) stringResource(R.string.settings_interface_no_external_storage) else stringResource(R.string.settings_interface_external_storage_subtitle), icon = Icons.Default.SdCard, enabled = dirs.isNotEmpty(), trailing = { Switch(checked = useExternalStorage, onCheckedChange = { useExternalStorage = it; PrefManager.useExternalStorage = it; if (it && dirs.isNotEmpty()) PrefManager.externalStoragePath = dirs[0].absolutePath }, enabled = dirs.isNotEmpty()) })
        if (useExternalStorage && dirs.isNotEmpty()) {
            var selectedIndex by rememberSaveable { mutableStateOf(dirs.indexOfFirst { it.absolutePath == PrefManager.externalStoragePath }.takeIf { it >= 0 } ?: 0) }
            SettingsTile(title = stringResource(R.string.settings_interface_storage_volume_title), subtitle = labels.getOrNull(selectedIndex) ?: "", icon = Icons.Default.Storage, onClick = { /* Volume selection dialog could go here */ })
        }
        SettingsTile(title = stringResource(R.string.settings_interface_download_server_title), subtitle = steamRegionsList.getOrNull(selectedRegionIndex)?.second ?: stringResource(R.string.settings_region_default), icon = Icons.Default.Public, onClick = { openRegionDialog = true })
    }

    // DIALOGS
    SingleChoiceDialog(openDialog = openRegionDialog, icon = Icons.Default.Map, iconDescription = stringResource(R.string.settings_interface_download_server_title), title = stringResource(R.string.settings_interface_download_server_title), items = steamRegionsList.map { it.second }, currentItem = selectedRegionIndex, onSelected = { index -> selectedRegionIndex = index; val selectedId = steamRegionsList[index].first; PrefManager.cellId = selectedId; PrefManager.cellIdManuallySet = selectedId != 0 }, onDismiss = { openRegionDialog = false })
    MessageDialog(visible = showStatusBarRestartDialog, title = stringResource(R.string.settings_interface_restart_required_title), message = stringResource(R.string.settings_language_restart_message), confirmBtnText = stringResource(R.string.settings_language_restart_confirm), dismissBtnText = stringResource(R.string.cancel), onConfirmClick = { showStatusBarRestartDialog = false; val newValue = pendingStatusBarValue ?: return@MessageDialog; PrefManager.hideStatusBarWhenNotInGame = newValue; showStatusBarLoadingDialog = true; pendingStatusBarValue = null }, onDismissRequest = { showStatusBarRestartDialog = false; hideStatusBar = PrefManager.hideStatusBarWhenNotInGame; pendingStatusBarValue = null }, onDismissClick = { showStatusBarRestartDialog = false; hideStatusBar = PrefManager.hideStatusBarWhenNotInGame; pendingStatusBarValue = null })
    LaunchedEffect(showStatusBarLoadingDialog) { if (showStatusBarLoadingDialog) { delay(500); AppUtils.restartApplication(context) } }
    LoadingDialog(visible = showStatusBarLoadingDialog, progress = -1f, message = context.getString(R.string.settings_saving_restarting))
    SingleChoiceDialog(openDialog = openLanguageDialog, icon = Icons.Default.Map, iconDescription = stringResource(R.string.settings_language), title = stringResource(R.string.settings_select_language), items = languageNames, currentItem = selectedLanguageIndex, onSelected = { index -> selectedLanguageIndex = index; val selectedCode = languageCodes[index]; if (selectedCode != PrefManager.appLanguage) { pendingLanguageCode = selectedCode; showLanguageRestartDialog = true }; openLanguageDialog = false }, onDismiss = { openLanguageDialog = false })
    MessageDialog(visible = showLanguageRestartDialog, title = stringResource(R.string.settings_language_restart_title), message = stringResource(R.string.settings_language_restart_message), confirmBtnText = stringResource(R.string.settings_language_restart_confirm), dismissBtnText = stringResource(R.string.cancel), onConfirmClick = { showLanguageRestartDialog = false; val newLanguage = pendingLanguageCode ?: return@MessageDialog; PrefManager.appLanguage = newLanguage; showLanguageLoadingDialog = true; pendingLanguageCode = null }, onDismissRequest = { showLanguageRestartDialog = false; selectedLanguageIndex = languageCodes.indexOf(PrefManager.appLanguage).takeIf { it >= 0 } ?: 0; pendingLanguageCode = null }, onDismissClick = { showLanguageRestartDialog = false; selectedLanguageIndex = languageCodes.indexOf(PrefManager.appLanguage).takeIf { it >= 0 } ?: 0; pendingLanguageCode = null })
    LaunchedEffect(showLanguageLoadingDialog) { if (showLanguageLoadingDialog) { delay(500); AppUtils.restartApplication(context) } }
    LoadingDialog(visible = showLanguageLoadingDialog, progress = -1f, message = stringResource(R.string.settings_language_changing))
    LoadingDialog(visible = gogLoginLoading || epicLoginLoading || amazonLoginLoading, progress = -1f, message = stringResource(R.string.main_loading))
    
    MessageDialog(visible = showGOGLogoutDialog, title = stringResource(R.string.gog_logout_confirm_title), message = stringResource(R.string.gog_logout_confirm_message), confirmBtnText = stringResource(R.string.gog_logout_confirm), dismissBtnText = stringResource(R.string.cancel), onConfirmClick = { showGOGLogoutDialog = false; gogLogoutLoading = true; coroutineScope.launch { GOGService.logout(context); gogLogoutLoading = false } }, onDismissRequest = { showGOGLogoutDialog = false }, onDismissClick = { showGOGLogoutDialog = false })
    LoadingDialog(visible = gogLogoutLoading, progress = -1f, message = stringResource(R.string.gog_logout_in_progress))
    MessageDialog(visible = showEpicLogoutDialog, title = stringResource(R.string.epic_logout_confirm_title), message = stringResource(R.string.epic_logout_confirm_message), confirmBtnText = stringResource(R.string.epic_logout_confirm), dismissBtnText = stringResource(R.string.cancel), onConfirmClick = { showEpicLogoutDialog = false; epicLogoutLoading = true; coroutineScope.launch { EpicService.logout(context); epicLogoutLoading = false } }, onDismissRequest = { showEpicLogoutDialog = false }, onDismissClick = { showEpicLogoutDialog = false })
    LoadingDialog(visible = epicLogoutLoading, progress = -1f, message = stringResource(R.string.epic_logout_in_progress))
    MessageDialog(visible = showSteamLogoutDialog, title = stringResource(R.string.steam_logout_confirm_title), message = stringResource(R.string.steam_logout_confirm_message), confirmBtnText = stringResource(R.string.steam_logout_confirm), dismissBtnText = stringResource(R.string.cancel), onConfirmClick = { showSteamLogoutDialog = false; steamLogoutLoading = true; coroutineScope.launch { SteamService.logOut(); steamLogoutLoading = false } }, onDismissRequest = { showSteamLogoutDialog = false }, onDismissClick = { showSteamLogoutDialog = false })
    LoadingDialog(visible = steamLogoutLoading, progress = -1f, message = stringResource(R.string.steam_logout_in_progress))
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(text = title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(bottom = 12.dp, top = 8.dp))
}

@Composable
fun SettingsTile(
    title: String,
    subtitle: String? = null,
    icon: Any? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 1,
    trailing: @Composable (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .then(if (enabled && onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    when (icon) {
                        is ImageVector -> Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(text = subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = subtitleMaxLines, overflow = if (subtitleMaxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip)
                }
            }
            if (trailing != null) Box(modifier = Modifier.padding(start = 12.dp)) { trailing() }
            else if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun IconVariantCard(label: String, launcherIconRes: Int, notificationIconRes: Int, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    Card(modifier = Modifier.clickable { onClick() }, shape = RoundedCornerShape(12.dp), border = border, colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = if (selected) 0.1f else 0.05f))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.BottomEnd) {
                AndroidView(modifier = Modifier.matchParentSize(), factory = { ctx -> ImageView(ctx).apply { setImageResource(launcherIconRes); scaleType = ImageView.ScaleType.CENTER_CROP } })
                Image(painter = painterResource(id = notificationIconRes), contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.size(8.dp)); Text(text = label, color = if (selected) MaterialTheme.colorScheme.primary else Color.White)
        }
    }
}
