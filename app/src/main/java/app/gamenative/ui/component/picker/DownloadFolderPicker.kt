package app.gamenative.ui.component.picker

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.gamenative.R
import app.gamenative.ui.components.getPathFromTreeUri

data class DownloadFolderPicker(
    val launchPicker: () -> Unit,
)

/**
 * Helper for remembering a folder picker launcher that returns a resolved file path for downloads.
 */
@Composable
fun rememberDownloadFolderPicker(
    onPathSelected: (String) -> Unit,
    onFailure: (String) -> Unit = {},
    onCancel: () -> Unit = {},
): DownloadFolderPicker {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) {
            onCancel()
            return@rememberLauncherForActivityResult
        }

        val path = getPathFromTreeUri(uri)
        if (path != null) {
            // Persist permission for this URI so we can access it later
            try {
                val takeFlags: Int = (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Ignore if we can't persist, we have temporary access for now
            }
            onPathSelected(path)
        } else {
            onFailure(context.getString(R.string.custom_game_folder_picker_error))
        }
    }

    return remember {
        DownloadFolderPicker(
            launchPicker = { pickerLauncher.launch(null) },
        )
    }
}
