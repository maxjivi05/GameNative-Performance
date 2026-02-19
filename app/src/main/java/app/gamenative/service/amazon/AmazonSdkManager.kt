package app.gamenative.service.amazon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Amazon Games SDK files (FuelSDK, AmazonGamesSDK) needed for DRM authentication.
 *
 * Flow (mirrors nile's `Library.get_sdk()`):
 *  1. `GET {distributionUrl}/download/channel/{launcherChannelId}` → downloadUrl + versionId
 *  2. `GET {downloadUrl}/manifest.proto` → parse for files under "Amazon Games Services"
 *  3. Download each SDK file from `{downloadUrl}/files/{sha256_hex}`
 *  4. Cache on disk at `{context.filesDir}/amazon_sdk/Amazon Games Services/…`
 *
 * At game launch time, [deploySdkToPrefix] copies the cached SDK files into the Wine prefix
 * so the FuelPump DRM DLLs are where games expect them (`C:\ProgramData\Amazon Games Services\…`).
 *
 * SDK files are downloaded once and cached persistently across launches.
 * A version file tracks the last-downloaded versionId to avoid re-downloading.
 */
object AmazonSdkManager {

    private const val TAG = "AmazonSDK"
    private const val SDK_DIR = "amazon_sdk"
    private const val VERSION_FILE = ".sdk_version"

    /** Filter: only download manifest files whose path contains this. */
    private const val SDK_PATH_FILTER = "Amazon Games Services"

    /**
     * Fine-grained filter for SDK files within the launcher manifest.
     *
     * The launcher manifest contains ~1300 files (the entire Amazon Games Launcher app).
     * Games only need a tiny subset — exactly what Nile downloads:
     *
     *   - `FuelSDK_x64.dll`  (in `Amazon Games Services/Legacy/`)
     *   - `AmazonGamesSDK_*` (in `Amazon Games Services/` — e.g. `AmazonGamesSDK_x64.dll`)
     *
     * Reference: nile/api/self_update.py `get_sdk()`:
     *   ```python
     *   if 'FuelSDK_x64.dll' in file.path or 'AmazonGamesSDK_' in file.path:
     *   ```
     *
     * Returns true if [path] is an essential SDK file that should be downloaded.
     */
    private fun isSdkFile(path: String): Boolean {
        // Skip macOS resource fork files (._*) — these match the keyword filter
        // but are useless on Android/Wine
        if (path.contains("/._")) return false

        return path.contains("FuelSDK_x64.dll") || path.contains("AmazonGamesSDK_")
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Ensure SDK files are downloaded and cached locally.
     *
     * If the SDK is already cached at the current version, this is a no-op.
     * Called once during the first Amazon game launch.
     *
     * @return `true` if SDK files are available (either already cached or freshly downloaded).
     */
    suspend fun ensureSdkFiles(context: Context, bearerToken: String): Boolean =
        withContext(Dispatchers.IO) {
            val sdkRoot = File(context.filesDir, SDK_DIR)

            // ── 1. Check if already downloaded ──────────────────────────────
            if (isSdkCached(sdkRoot)) {
                Timber.tag(TAG).d("SDK already cached at ${sdkRoot.absolutePath}")
                return@withContext true
            }

            Timber.tag(TAG).i("SDK not cached — starting download")

            // ── 2. Fetch launcher channel download spec ─────────────────────
            val spec = AmazonApiClient.fetchSdkDownload(bearerToken)
            if (spec == null) {
                Timber.tag(TAG).e("Failed to fetch SDK download spec")
                return@withContext false
            }

            // ── 3. Download and parse manifest ──────────────────────────────
            val manifestUrl = AmazonApiClient.appendPath(spec.downloadUrl, "manifest.proto")
            Timber.tag(TAG).d("Fetching SDK manifest: $manifestUrl")

            val manifestBytes = fetchBytes(manifestUrl)
            if (manifestBytes == null) {
                Timber.tag(TAG).e("Failed to download SDK manifest.proto")
                return@withContext false
            }

            val manifest = try {
                AmazonManifest.parse(manifestBytes)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to parse SDK manifest")
                return@withContext false
            }

            // ── 4. Filter for SDK files ─────────────────────────────────────
            val sdkFiles = manifest.allFiles.filter { isSdkFile(it.path) }
            if (sdkFiles.isEmpty()) {
                Timber.tag(TAG).w("No SDK files found in launcher manifest (${manifest.allFiles.size} total files)")
                // Still write the version file so we don't retry every launch
                writeVersionFile(sdkRoot, spec.versionId)
                return@withContext false
            }

            Timber.tag(TAG).i("Found ${sdkFiles.size} SDK files to download")

            // ── 5. Download each SDK file ───────────────────────────────────
            sdkRoot.mkdirs()
            var downloaded = 0
            var failed = 0

            for (file in sdkFiles) {
                val hashHex = file.hashBytes.joinToString("") { "%02x".format(it) }
                val fileUrl = AmazonApiClient.appendPath(spec.downloadUrl, "files/$hashHex")
                val destFile = File(sdkRoot, file.unixPath)

                // Skip already-downloaded files
                if (destFile.exists() && destFile.length() == file.size) {
                    Timber.tag(TAG).d("  skip (exists): ${file.unixPath}")
                    downloaded++
                    continue
                }

                val success = downloadFile(fileUrl, destFile)
                if (success) {
                    downloaded++
                    Timber.tag(TAG).d("  ok: ${file.unixPath} (${file.size} bytes)")
                } else {
                    failed++
                    Timber.tag(TAG).w("  FAILED: ${file.unixPath}")
                }
            }

            Timber.tag(TAG).i("SDK download complete: $downloaded OK, $failed failed out of ${sdkFiles.size}")

            if (failed == 0) {
                writeVersionFile(sdkRoot, spec.versionId)
            }

            // Return true even with partial failures — some SDK files are better than none
            downloaded > 0
        }

    /**
     * Copy cached SDK files into the Wine prefix's ProgramData directory.
     *
     * Called synchronously during Amazon game launch (from the setup thread).
     * The target path is `{winePrefix}/drive_c/ProgramData/Amazon Games Services/…`.
     *
     * Files are placed preserving their manifest paths, which matches how Nile
     * stores them and how games expect them:
     *  - `Amazon Games Services/Legacy/FuelSDK_x64.dll`     (FUEL_DIR)
     *  - `Amazon Games Services/AmazonGamesSDK_x64.dll`     (sibling)
     *  - `Amazon Games Services/AmazonGamesSDK_x86.dll`     (sibling)
     *  - `Amazon Games Services/AmazonGamesSDK/`            (AMAZON_GAMES_SDK_PATH — kept as empty dir)
     *
     * @param prefixProgramData The Wine prefix ProgramData directory
     *        (e.g. `/path/to/.wine/drive_c/ProgramData`).
     * @return Number of files deployed, or -1 if SDK cache doesn't exist.
     */
    fun deploySdkToPrefix(context: Context, prefixProgramData: File): Int {
        val sdkRoot = File(context.filesDir, SDK_DIR)
        val sdkServicesDir = File(sdkRoot, SDK_PATH_FILTER)

        if (!sdkServicesDir.exists()) {
            Timber.tag(TAG).w("SDK cache not found at ${sdkServicesDir.absolutePath}")
            return -1
        }

        val targetDir = File(prefixProgramData, SDK_PATH_FILTER)
        targetDir.mkdirs()

        // Ensure the AmazonGamesSDK subdirectory exists (env var AMAZON_GAMES_SDK_PATH points here)
        File(targetDir, "AmazonGamesSDK").mkdirs()

        var deployed = 0
        sdkServicesDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
            val relativePath = srcFile.relativeTo(sdkServicesDir).path
            val destFile = File(targetDir, relativePath)

            // Only copy if missing or size changed
            if (!destFile.exists() || destFile.length() != srcFile.length()) {
                destFile.parentFile?.mkdirs()
                srcFile.copyTo(destFile, overwrite = true)
                deployed++
                Timber.tag(TAG).d("  deployed: $relativePath (${srcFile.length()} bytes)")
            }
        }

        Timber.tag(TAG).i("Deployed $deployed SDK file(s) to ${targetDir.absolutePath}")
        return deployed
    }

    /**
     * Whether the SDK has been downloaded (at least the version file exists and
     * there is at least one file under the SDK services directory).
     */
    private fun isSdkCached(sdkRoot: File): Boolean {
        val versionFile = File(sdkRoot, VERSION_FILE)
        if (!versionFile.exists()) return false
        val sdkServicesDir = File(sdkRoot, SDK_PATH_FILTER)
        return sdkServicesDir.exists() && sdkServicesDir.walkTopDown().any { it.isFile }
    }

    private fun writeVersionFile(sdkRoot: File, versionId: String) {
        try {
            sdkRoot.mkdirs()
            File(sdkRoot, VERSION_FILE).writeText(versionId)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to write SDK version file")
        }
    }

    // ── Download helpers ─────────────────────────────────────────────────────

    private fun fetchBytes(url: String): ByteArray? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            conn.inputStream.use { it.readBytes() }
        } else {
            Timber.tag(TAG).e("fetchBytes: HTTP ${conn.responseCode} for $url")
            null
        }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "fetchBytes failed: $url")
        null
    }

    private fun downloadFile(url: String, destFile: File): Boolean = try {
        destFile.parentFile?.mkdirs()
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 120_000

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            conn.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            if (destFile.exists()) destFile.delete()
            tmpFile.renameTo(destFile)
            true
        } else {
            Timber.tag(TAG).e("downloadFile: HTTP ${conn.responseCode} for $url")
            tmpFile.delete()
            false
        }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "downloadFile failed: $url")
        false
    }
}
