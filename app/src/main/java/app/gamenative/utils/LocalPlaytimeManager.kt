package app.gamenative.utils

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class PlaytimeEntry(
    val totalMinutes: Long = 0,
    val lastSessionMinutes: Long = 0,
    val lastPlayed: Long = 0
)

object LocalPlaytimeManager {
    private const val FILENAME = "local_playtime.json"
    private val activeSessions = ConcurrentHashMap<String, Long>()
    private var cache: MutableMap<String, PlaytimeEntry> = mutableMapOf()
    private var isLoaded = false

    private fun getFile(context: Context): File {
        return File(context.filesDir, FILENAME)
    }

    private fun loadIfNeeded(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            try {
                val file = getFile(context)
                if (file.exists()) {
                    val content = file.readText()
                    val data = Json.decodeFromString<Map<String, PlaytimeEntry>>(content)
                    cache = data.toMutableMap()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load local playtime")
            }
            isLoaded = true
        }
    }

    private fun save(context: Context) {
        try {
            val file = getFile(context)
            val content = Json.encodeToString(cache)
            file.writeText(content)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save local playtime")
        }
    }

    fun startSession(appId: String) {
        activeSessions[appId] = System.currentTimeMillis()
        Timber.d("Started playtime session for $appId")
    }

    fun endSession(context: Context, appId: String) {
        val startTime = activeSessions.remove(appId)
        if (startTime == null) {
            Timber.w("No active session found for $appId to end")
            return
        }

        loadIfNeeded(context)
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        val durationMinutes = durationMs / 60000

        // Only record if at least 1 minute or just sum up ms? 
        // User asked for minutes. Let's accumulate minutes for simplicity or keep ms internally if we want precision.
        // But the requested output is HH:MM. 
        // Let's store minutes to match the requested format ease.
        
        synchronized(this) {
            val current = cache[appId] ?: PlaytimeEntry()
            val newTotal = current.totalMinutes + durationMinutes
            val newEntry = current.copy(
                totalMinutes = newTotal,
                lastSessionMinutes = durationMinutes,
                lastPlayed = endTime
            )
            cache[appId] = newEntry
            save(context)
        }
        Timber.i("Ended session for $appId. Duration: ${durationMinutes}m. Total: ${cache[appId]?.totalMinutes}m")
    }

    fun getPlaytime(context: Context, appId: String): PlaytimeEntry {
        loadIfNeeded(context)
        return cache[appId] ?: PlaytimeEntry()
    }
}
