package app.gamenative.utils.launchdependencies

import android.content.Context
import app.gamenative.data.GameSource
import com.winlator.container.Container
import java.io.File
import app.gamenative.service.SteamService

/**
 * Installs other redistributables (OpenAL, PhysX, XNA Framework) by contributing to the main Wine session's cmd /c chain.
 */
object OtherRedistLaunchStep : LaunchStep {

    override val runOnce: Boolean = true

    override fun appliesTo(container: Container, appId: String, gameSource: GameSource): Boolean {
        // Only apply if there are shared depots (legacy logic from XServerScreen)
        val steamAppId = app.gamenative.utils.ContainerUtils.extractGameIdFromContainerId(appId)
        val downloadableDepots = SteamService.getDownloadableDepots(steamAppId)
        return downloadableDepots.any { (_, depotInfo) ->
            val manifest = depotInfo.manifests["public"]
            manifest == null || manifest.gid == 0L
        }
    }

    override fun run(
        context: Context,
        appId: String,
        container: Container,
        stepRunner: StepRunner,
        gameSource: GameSource,
    ): Boolean {
        val steamAppId = app.gamenative.utils.ContainerUtils.extractGameIdFromContainerId(appId)
        val gameDirPath = SteamService.getAppDirPath(steamAppId)
        val commonRedistDir = File(gameDirPath, "_CommonRedist")

        if (!commonRedistDir.exists() || !commonRedistDir.isDirectory) return false

        // Get the drive letter for the game directory
        val drives = container.drives
        val driveIndex = drives.indexOf(gameDirPath)
        val driveLetter = if (driveIndex > 1) {
            drives[driveIndex - 2]
        } else {
            return false
        }

        val parts = mutableListOf<String>()

        // OpenAL
        val openalDir = File(commonRedistDir, "OpenAL")
        if (openalDir.exists() && openalDir.isDirectory) {
            val openalInstaller = openalDir.walkTopDown()
                .filter { it.isFile &&
                    (it.name.equals("oalinst.exe", ignoreCase = true) ||
                     it.name.startsWith("OpenAL", ignoreCase = true)) &&
                    it.name.endsWith(".exe", ignoreCase = true) }
                .firstOrNull()

            openalInstaller?.let { exeFile ->
                val relativePath = exeFile.relativeTo(commonRedistDir).path.replace('/', '\\')
                val winePath = """$driveLetter:\_CommonRedist\$relativePath"""
                parts.add("wine $winePath /s")
            }
        }

        // PhysX
        val physxDir = File(commonRedistDir, "PhysX")
        if (physxDir.exists() && physxDir.isDirectory) {
            physxDir.walkTopDown()
                .filter { it.isFile && it.name.startsWith("PhysX", ignoreCase = true) &&
                            it.name.endsWith(".msi", ignoreCase = true) }
                .forEach { msiFile ->
                    val relativePath = msiFile.relativeTo(commonRedistDir).path.replace('/', '\\')
                    val winePath = """$driveLetter:\_CommonRedist\$relativePath"""
                    parts.add("wine msiexec /i $winePath /quiet /norestart")
                }
        }

        // XNA Framework
        val xnaDir = File(commonRedistDir, "xnafx")
        if (xnaDir.exists() && xnaDir.isDirectory) {
            xnaDir.walkTopDown()
                .filter { it.isFile && it.name.startsWith("xna", ignoreCase = true) &&
                            it.name.endsWith(".msi", ignoreCase = true) }
                .forEach { msiFile ->
                    val relativePath = msiFile.relativeTo(commonRedistDir).path.replace('/', '\\')
                    val winePath = """$driveLetter:\_CommonRedist\$relativePath"""
                    parts.add("wine msiexec /i $winePath /quiet /norestart")
                }
        }

        val content = if (parts.isEmpty()) null else parts.joinToString(" & ")
        if (content.isNullOrBlank()) return false
        stepRunner.runStepContent(content)
        return true
    }
}
