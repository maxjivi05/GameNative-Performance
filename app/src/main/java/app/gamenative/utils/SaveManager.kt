package app.gamenative.utils

import android.content.Context
import android.net.Uri
import com.winlator.container.Container
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SaveManager {

    private const val WINE_USER_XUSER = "xuser"
    private const val WINE_USER_STEAMUSER = "steamuser"

    fun exportSave(context: Context, container: Container, gameTitle: String, destinationUri: Uri): Boolean {
        val rootDir = container.rootDir
        val driveC = File(rootDir, ".wine/drive_c")
        if (!driveC.exists()) {
            Timber.e("Container drive_c not found: ${driveC.absolutePath}")
            return false
        }

        val itemsToExport = mutableListOf<Pair<File, String>>() // File to Relative Path in Zip
        val normalizedTitle = gameTitle.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

        val searchPaths = listOf(
            "users/$WINE_USER_XUSER/Saved Games",
            "users/$WINE_USER_XUSER/AppData/Local",
            "users/$WINE_USER_XUSER/AppData/LocalLow",
            "users/$WINE_USER_XUSER/AppData/Roaming",
            "users/$WINE_USER_XUSER/Documents",
            "users/$WINE_USER_STEAMUSER/Saved Games",
            "users/$WINE_USER_STEAMUSER/AppData/Local",
            "users/$WINE_USER_STEAMUSER/AppData/LocalLow",
            "users/$WINE_USER_STEAMUSER/AppData/Roaming",
            "users/$WINE_USER_STEAMUSER/Documents",
            "users/Public/Documents",
            "ProgramData"
        )

        for (relativePath in searchPaths) {
            val baseDir = File(driveC, relativePath)
            if (baseDir.exists()) {
                baseDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        val folderName = file.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                        if (folderName.contains(normalizedTitle) || normalizedTitle.contains(folderName)) {
                            // Remap steamuser to xuser in the ZIP path
                            val zipRelativePath = relativePath.replace(WINE_USER_STEAMUSER, WINE_USER_XUSER)
                            itemsToExport.add(file to "drive_c/$zipRelativePath/${file.name}")
                        }
                    }
                }
            }
        }

        // Deep Search Fallback: If nothing found, look for ANY folder matching the title in the entire drive_c/users
        if (itemsToExport.isEmpty()) {
            val usersDir = File(driveC, "users")
            if (usersDir.exists()) {
                usersDir.walk()
                    .filter { it.isDirectory && it.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase().contains(normalizedTitle) }
                    .take(5) // Limit to avoid zipping the whole drive if title is too generic
                    .forEach { folder ->
                        val relativeToDriveC = folder.absolutePath.substringAfter(driveC.absolutePath).trimStart(File.separatorChar)
                        val zipPath = "drive_c/${relativeToDriveC.replace(WINE_USER_STEAMUSER, WINE_USER_XUSER)}"
                        itemsToExport.add(folder to zipPath)
                    }
            }
        }

        if (itemsToExport.isEmpty()) {
            Timber.w("No save folders found for game: $gameTitle")
            return false
        }

        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    val addedEntries = mutableSetOf<String>()
                    for (item in itemsToExport) {
                        val folder = item.first
                        val zipPathPrefix = item.second
                        
                        folder.walk().forEach { file ->
                            if (file.isFile) {
                                val relativeToFile = file.absolutePath.substring(folder.absolutePath.length)
                                val entryName = (zipPathPrefix + relativeToFile).replace('\\', '/')
                                if (addedEntries.add(entryName)) {
                                    val entry = ZipEntry(entryName)
                                    zos.putNextEntry(entry)
                                    FileInputStream(file).use { fis ->
                                        BufferedInputStream(fis).copyTo(zos)
                                    }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to export save")
            false
        }
    }

    fun importSave(context: Context, container: Container, sourceUri: Uri): Boolean {
        val rootDir = container.rootDir
        val wineDir = File(rootDir, ".wine")
        val driveC = File(wineDir, "drive_c")
        
        if (!wineDir.exists()) wineDir.mkdirs()
        if (!driveC.exists()) driveC.mkdirs()

        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { `is` ->
                ZipInputStream(BufferedInputStream(`is`)).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        var entryName = entry!!.name
                        entryName = entryName.replace('\\', '/')

                        // Remap steamuser to xuser
                        if (entryName.contains("users/$WINE_USER_STEAMUSER", ignoreCase = true)) {
                            entryName = entryName.replace("users/$WINE_USER_STEAMUSER", "users/$WINE_USER_XUSER", ignoreCase = true)
                        }
                        
                        val targetFile = if (entryName.startsWith("drive_c/", ignoreCase = true)) {
                            File(wineDir, entryName)
                        } else if (entryName.startsWith("users/", ignoreCase = true) || 
                                   entryName.startsWith("ProgramData/", ignoreCase = true) ||
                                   entryName.startsWith("windows/", ignoreCase = true)) {
                            File(driveC, entryName)
                        } else {
                            File(driveC, entryName)
                        }
                        
                        if (entry!!.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    zis.copyTo(bos)
                                }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to import save")
            false
        }
    }
}
