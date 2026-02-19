package app.gamenative.service.amazon

import android.content.Context
import app.gamenative.data.AmazonGame
import app.gamenative.db.dao.AmazonGameDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Amazon library manager — the Amazon equivalent of GOGManager / EpicManager.
 *
 * Responsibilities:
 *  1. Load stored credentials via [AmazonAuthManager].
 *  2. Call [AmazonApiClient.getEntitlements] to retrieve the user's library.
 *  3. Upsert results into the [AmazonGameDao] Room table, preserving install status.
 */
@Singleton
class AmazonManager @Inject constructor(
    private val amazonGameDao: AmazonGameDao,
    @ApplicationContext private val context: Context,
) {

    /**
     * Refresh the Amazon library from the API and persist results to the DB.
     * Safe to call from a coroutine running on any dispatcher.
     */
    suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        Timber.i("[Amazon] Starting library refresh…")

        val credentialsResult = AmazonAuthManager.getStoredCredentials(context)
        if (credentialsResult.isFailure) {
            Timber.w("[Amazon] No stored credentials — ${credentialsResult.exceptionOrNull()?.message}")
            return@withContext
        }
        val credentials = credentialsResult.getOrNull()!!

        val games = AmazonApiClient.getEntitlements(
            bearerToken = credentials.accessToken,
            deviceSerial = credentials.deviceSerial,
        )

        if (games.isEmpty()) {
            Timber.w("[Amazon] No entitlements returned from API")
            return@withContext
        }

        amazonGameDao.upsertPreservingInstallStatus(games)
        Timber.i("[Amazon] Library refresh complete — ${games.size} game(s) in DB")
    }

    /**
     * Look up a single [AmazonGame] by its product ID (e.g. "amzn1.adg.product.XXXX").
     * Returns null if not found.
     */
    suspend fun getGameById(productId: String): AmazonGame? = withContext(Dispatchers.IO) {
        amazonGameDao.getById(productId)
    }

    /** Return all Amazon games from the DB (for cache population). */
    suspend fun getAllGames(): List<AmazonGame> = withContext(Dispatchers.IO) {
        amazonGameDao.getAllAsList()
    }

    /** Mark a game as installed and persist its install path, size, and version. */
    suspend fun markInstalled(productId: String, installPath: String, installSize: Long, versionId: String = "") =
        withContext(Dispatchers.IO) {
            amazonGameDao.markAsInstalled(productId, installPath, installSize, versionId)
            Timber.i("[Amazon] Marked installed: $productId at $installPath (${installSize}B, version=$versionId)")
        }

    /** Mark a game as not installed (clears install path and size). */
    suspend fun markUninstalled(productId: String) = withContext(Dispatchers.IO) {
        amazonGameDao.markAsUninstalled(productId)
        Timber.i("[Amazon] Marked uninstalled: $productId")
    }

    /** Update the cached download size for a game (e.g. after fetching the manifest). */
    suspend fun updateDownloadSize(productId: String, size: Long) = withContext(Dispatchers.IO) {
        amazonGameDao.updateDownloadSize(productId, size)
        Timber.i("[Amazon] Updated download size for $productId: $size bytes")
    }

    /** Record playtime after a game session ends. */
    suspend fun updatePlaytime(productId: String, lastPlayed: Long, playTimeMinutes: Long) = withContext(Dispatchers.IO) {
        amazonGameDao.updatePlaytime(productId, lastPlayed, playTimeMinutes)
        Timber.i("[Amazon] Updated playtime for $productId: lastPlayed=$lastPlayed, totalMinutes=$playTimeMinutes")
    }

    /** Get the stored bearer token (needed by AmazonDownloadManager). */
    suspend fun getBearerToken(): String? = withContext(Dispatchers.IO) {
        AmazonAuthManager.getStoredCredentials(context).getOrNull()?.accessToken
    }
}
