package app.gamenative.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.gamenative.PrefManager
import app.gamenative.ui.theme.customBackground
import app.gamenative.ui.theme.customDestructive
import app.gamenative.ui.theme.customPrimary
import app.gamenative.ui.theme.customSecondary
import app.gamenative.utils.CustomGameScanner

@Composable
fun SetupWizardScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var hasFilePermission by remember { mutableStateOf(CustomGameScanner.hasStoragePermission(context, "/sdcard")) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var masterContainersEnabled by remember { mutableStateOf(PrefManager.masterContainers) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // Re-check permissions when activity is resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasFilePermission = CustomGameScanner.hasStoragePermission(context, "/sdcard")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(customBackground)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.95f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Setup Wizard",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Grant necessary permissions and configure your experience.",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left side: Permissions
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClickableSetupCard(
                        title = "Storage Access",
                        description = "Required to scan and run games.",
                        isCompleted = hasFilePermission,
                        onClick = { CustomGameScanner.requestManageExternalStoragePermission(context) }
                    )

                    ClickableSetupCard(
                        title = "Notifications",
                        description = "Track downloads and updates. (Optional)",
                        isCompleted = hasNotificationPermission,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                hasNotificationPermission = true
                            }
                        }
                    )
                }

                // Right side: Features
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = customSecondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                masterContainersEnabled = !masterContainersEnabled
                                PrefManager.masterContainers = masterContainersEnabled
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Master Containers",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = masterContainersEnabled,
                                    onCheckedChange = {
                                        masterContainersEnabled = it
                                        PrefManager.masterContainers = it
                                    },
                                    modifier = Modifier.scale(0.7f),
                                    colors = SwitchDefaults.colors(checkedThumbColor = customPrimary, checkedTrackColor = customPrimary.copy(alpha = 0.5f))
                                )
                            }
                            Text(
                                text = "Saves space by using shared resources.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Bottom info or Finish
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                if (hasFilePermission) {
                                    PrefManager.setupCompleted = true
                                    onFinished()
                                }
                            },
                            enabled = hasFilePermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customPrimary,
                                disabledContainerColor = customPrimary.copy(alpha = 0.3f)
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp).fillMaxWidth(0.9f)
                        ) {
                            Text("Let's Go!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClickableSetupCard(
    title: String,
    description: String,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isCompleted) Color.Green else customDestructive
    Card(
        colors = CardDefaults.cardColors(containerColor = customSecondary),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isCompleted) {
                    Text(text = "✓", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = Color.LightGray, fontSize = 11.sp)
        }
    }
}
