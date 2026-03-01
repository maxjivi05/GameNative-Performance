package app.gamenative.gamefixes

import android.content.Context
import app.gamenative.data.GameSource
import com.winlator.core.WineRegistryEditor
import com.winlator.xenvironment.ImageFs
import timber.log.Timber
import java.io.File

class RegistryKeyFix(
    private val registryKey: String,
    private val defaultValues: Map<String, String>,
) : GameFix {
    override fun apply(
        context: Context,
        gameId: String,
        installPath: String,
        installPathWindows: String
    ): Boolean {
        Timber.i("RegistryKeyFix: Applying fix for $gameId at $registryKey")
        val imageFs = ImageFs.find(context) ?: return false
        val systemRegFile = File(imageFs.getRootDir(), ImageFs.WINEPREFIX + "/system.reg")
        
        if (!systemRegFile.exists()) {
            Timber.e("RegistryKeyFix: system.reg not found at ${systemRegFile.path}")
            return false
        }

        try {
            val registryEditor = WineRegistryEditor(systemRegFile)
            for ((key, value) in defaultValues) {
                val resolvedValue = value.replace(INSTALL_PATH_PLACEHOLDER, installPathWindows)
                registryEditor.setStringValue(registryKey, key, resolvedValue)
            }
            registryEditor.close()
            return true
        } catch (e: Exception) {
            Timber.e(e, "RegistryKeyFix: Error applying fix for $gameId")
            return false
        }
    }
}

class KeyedRegistryKeyFix(
    override val gameSource: GameSource,
    override val gameId: String,
    registryKey: String,
    defaultValues: Map<String, String>,
) : KeyedGameFix, GameFix by RegistryKeyFix(registryKey, defaultValues)
