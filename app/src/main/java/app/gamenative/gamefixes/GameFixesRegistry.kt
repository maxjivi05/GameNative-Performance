package app.gamenative.gamefixes

import android.content.Context
import app.gamenative.data.GameSource
import app.gamenative.utils.ContainerUtils
import app.gamenative.service.gog.GOGConstants
import app.gamenative.service.gog.GOGService
import app.gamenative.service.SteamService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber

object GameFixesRegistry {
    private const val GAME_DRIVE_LETTER = "A"

    private val fixes: Map<Pair<GameSource, String>, GameFix> = listOf(
        GOG_Fix_1454315831,
        GOG_Fix_1454587428,
        GOG_Fix_1458058109,
        STEAM_Fix_22300,
        STEAM_Fix_22380,
        STEAM_Fix_22330,
        EPIC_Fix_59a0c86d02da42e8ba6444cb171e61bf,
    ).associateBy { 
        if (it is KeyedGameFix) it.gameSource to it.gameId 
        else throw IllegalStateException("All fixes must implement KeyedGameFix")
    }

    fun applyFor(context: Context, appId: String) {
        val source = ContainerUtils.extractGameSourceFromContainerId(appId)
        val gameId = ContainerUtils.extractGameIdFromContainerId(appId) ?: return
        val fix = fixes[source to gameId] ?: return
        val paths = resolvePaths(context, source, gameId) ?: return
        val (installPath, installPathWindows) = paths
        
        Timber.tag("GameFixes").d("Applying fix for $source $gameId at $installPath")
        fix.apply(context, gameId, installPath, installPathWindows)
    }

    private fun resolvePaths(context: Context, source: GameSource, gameId: String): Pair<String, String>? {
        return when (source) {
            GameSource.GOG -> {
                val game = runBlocking(Dispatchers.IO) { GOGService.getGOGGameOf(gameId) } ?: return null
                if (!game.isInstalled) return null
                val path = game.installPath.ifEmpty { GOGConstants.getGameInstallPath(game.title) }
                if (path.isEmpty() || !File(path).exists()) return null
                path to "$GAME_DRIVE_LETTER:"
            }
            GameSource.STEAM -> {
                val appInfo = SteamService.getAppInfoOf(gameId.toLong()) ?: return null
                val path = SteamService.getAppInstallPath(context, appInfo)
                if (path.isEmpty() || !File(path).exists()) return null
                path to "$GAME_DRIVE_LETTER:"
            }
            GameSource.EPIC -> {
                val game = app.gamenative.service.epic.EpicService.getGameInfoOf(gameId) ?: return null
                val path = game.installPath
                if (path.isEmpty() || !File(path).exists()) return null
                path to "$GAME_DRIVE_LETTER:"
            }
            else -> null
        }
    }
}
