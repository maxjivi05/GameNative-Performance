package app.gamenative.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.enums.Orientation
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import java.util.EnumSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsEditorScreen(
    profileId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { InputControlsManager(context) }
    val profile = remember(profileId) { manager.getProfile(profileId) }
    
    var showElementEditor by remember { mutableStateOf(false) }
    var elementToEdit by remember { mutableStateOf<com.winlator.inputcontrols.ControlElement?>(null) }
    var inputControlsView by remember { mutableStateOf<InputControlsView?>(null) }

    DisposableEffect(Unit) {
        // Force landscape and hide status bar
        PluviaApp.events.emit(AndroidEvent.SetSystemUIVisibility(false))
        PluviaApp.events.emit(AndroidEvent.SetAllowedOrientation(EnumSet.of(Orientation.LANDSCAPE)))
        
        onDispose {
            // Restore settings
            PluviaApp.events.emit(AndroidEvent.SetSystemUIVisibility(true))
            PluviaApp.events.emit(AndroidEvent.SetAllowedOrientation(EnumSet.of(Orientation.UNSPECIFIED)))
        }
    }

    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Profile not found")
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                                    InputControlsView(ctx).apply {
                                        setEditMode(true)
                                        setProfile(profile)
                                        inputControlsView = this
                                        
                                        // Elements MUST be loaded after the view has valid dimensions and rotation is stable
                                        // Waiting 500ms ensures the landscape transition is complete
                                        postDelayed({
                                            if (profile != null) {
                                                profile.loadElements(this)
                                                invalidate()
                                            }
                                        }, 500)
                                    }            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Toolbar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .wrapContentSize(),
            shape = MaterialTheme.shapes.medium,
            color = Color.Black.copy(alpha = 0.7f),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                
                VerticalDivider(modifier = Modifier.height(24.dp), color = Color.White.copy(alpha = 0.3f))

                IconButton(onClick = {
                    inputControlsView?.addElement()
                    inputControlsView?.invalidate()
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
                IconButton(onClick = {
                    val selected = inputControlsView?.selectedElement
                    if (selected != null) {
                        elementToEdit = selected
                        showElementEditor = true
                    }
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = {
                    inputControlsView?.removeElement()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
                
                VerticalDivider(modifier = Modifier.height(24.dp), color = Color.White.copy(alpha = 0.3f))

                IconButton(onClick = {
                    profile.save()
                    onBack()
                }) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }
        }
    }

    if (showElementEditor && elementToEdit != null && inputControlsView != null) {
        app.gamenative.ui.component.dialog.ElementEditorDialog(
            element = elementToEdit!!,
            view = inputControlsView!!,
            onDismiss = { showElementEditor = false },
            onSave = { showElementEditor = false }
        )
    }
}
