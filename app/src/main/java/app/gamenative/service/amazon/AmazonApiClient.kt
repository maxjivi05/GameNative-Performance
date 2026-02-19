package app.gamenative.service.amazon

import app.gamenative.data.AmazonGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Low-level HTTP client for the Amazon Gaming Distribution service.
 *
 * Mirrors nile's `Library.request_distribution()` and `Library._get_sync_request_data()`.
 * Endpoint: https://gaming.amazon.com/api/distribution/entitlements
 * Target:   com.amazon.animusdistributionservice.entitlement.AnimusEntitlementsService.GetEntitlements
 */
object AmazonApiClient {

    private const val ENTITLEMENTS_URL =
        "https://gaming.amazon.com/api/distribution/entitlements"

    /**
     * Base URL for the public distribution service — used by GetGameDownload, GetLiveVersionIds, etc.
     * Distinct from ENTITLEMENTS_URL (which is only for GetEntitlements).
     * Confirmed from nile: constants.AMAZON_GAMING_DISTRIBUTION
     */
    private const val DISTRIBUTION_URL =
        "https://gaming.amazon.com/api/distribution/v2/public"

    private const val GET_ENTITLEMENTS_TARGET =
        "com.amazon.animusdistributionservice.entitlement.AnimusEntitlementsService.GetEntitlements"

    private const val GET_GAME_DOWNLOAD_TARGET =
        "com.amazon.animusdistributionservice.external.AnimusDistributionService.GetGameDownload"

    private const val GET_LIVE_VERSION_IDS_TARGET =
        "com.amazon.animusdistributionservice.external.AnimusDistributionService.GetLiveVersionIds"

    private const val USER_AGENT = "com.amazon.agslauncher.win/3.0.9202.1"
    private const val KEY_ID = "d5dc8b8b-86c8-4fc4-ae93-18c0def5314d"

    /** Result of a GetGameDownload call. */
    data class GameDownloadSpec(
        val downloadUrl: String,
        val versionId: String,
    )

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Fetches all entitlements (owned games) for the authenticated user.
     *
     * @param bearerToken  The `access_token` from [AmazonAuthManager].
     * @param deviceSerial The device serial number (used to compute hardwareHash).
     * @return A list of [AmazonGame] objects parsed from the response.
     */
    suspend fun getEntitlements(
        bearerToken: String,
        deviceSerial: String,
    ): List<AmazonGame> = withContext(Dispatchers.IO) {
        val games = mutableMapOf<String, AmazonGame>() // keyed by product id to deduplicate
        val hardwareHash = sha256Upper(deviceSerial)
        var nextToken: String? = null

        Timber.i("[Amazon] Fetching entitlements (hardwareHash=${hardwareHash.take(8)}…)")

        do {
            val requestBody = buildRequestBody(nextToken, hardwareHash)
            val responseJson = postJson(
                url = ENTITLEMENTS_URL,
                target = GET_ENTITLEMENTS_TARGET,
                bearerToken = bearerToken,
                body = requestBody,
            ) ?: break

            val entitlementsArray = responseJson.optJSONArray("entitlements")
            if (entitlementsArray != null) {
                for (i in 0 until entitlementsArray.length()) {
                    val entitlement = entitlementsArray.getJSONObject(i)
                    val game = parseEntitlement(entitlement) ?: continue
                    // Deduplicate by product id (nile does the same)
                    games[game.id] = game
                }
                Timber.d("[Amazon] Page returned ${entitlementsArray.length()} entitlements, total so far: ${games.size}")
            }

            nextToken = if (responseJson.has("nextToken")) {
                responseJson.getString("nextToken").also {
                    Timber.d("[Amazon] Got nextToken, fetching next page…")
                }
            } else null

        } while (nextToken != null)

        Timber.i("[Amazon] Fetched ${games.size} total entitlements")
        games.values.toList()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun buildRequestBody(nextToken: String?, hardwareHash: String): JSONObject =
        JSONObject().apply {
            put("Operation", "GetEntitlements")
            put("clientId", "Sonic")
            put("syncPoint", JSONObject.NULL)
            put("nextToken", if (nextToken != null) nextToken else JSONObject.NULL)
            put("maxResults", 50)
            put("productIdFilter", JSONObject.NULL)
            put("keyId", KEY_ID)
            put("hardwareHash", hardwareHash)
        }

    private fun postJson(
        url: String,
        target: String,
        bearerToken: String,
        body: JSONObject,
    ): JSONObject? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("X-Amz-Target", target)
                setRequestProperty("x-amzn-token", bearerToken)
                setRequestProperty("UserAgent", USER_AGENT)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Content-Encoding", "amz-1.0")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "(no body)"
                Timber.e("[Amazon] HTTP $responseCode from $url (target=${target.substringAfterLast('.')}): $errorBody")
                return null
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            JSONObject(responseText)
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] POST to $url failed")
            null
        }
    }

    /**
     * Parse a single entitlement JSON object into an [AmazonGame].
     * Entitlement shape (from nile): { product: { id, title, productDetail: { details: {...} }, ... }, ... }
     */
    private fun parseEntitlement(entitlement: JSONObject): AmazonGame? {
        val product = entitlement.optJSONObject("product") ?: return null
        val id = product.optString("id", "").ifEmpty { return null }
        val title = product.optString("title", "")
        val purchasedDate = entitlement.optString("purchasedDate", "")

        // Top-level entitlement UUID  — needed for GetGameDownload, NOT the product ID
        val entitlementId = entitlement.optString("id", "")

        // productDetail sits between product and details:
        // product -> productDetail -> details
        //                         -> iconUrl  (box art lives here, NOT inside details)
        val productDetail = product.optJSONObject("productDetail")
        val details = productDetail?.optJSONObject("details")

        val developer = details?.optString("developer", "") ?: ""
        val publisher = details?.optString("publisher", "") ?: ""
        val releaseDate = details?.optString("releaseDate", "") ?: ""
        val downloadSize = details?.optLong("fileSize", 0L) ?: 0L

        val artUrl = resolveArtUrl(productDetail, details)
        val heroUrl = resolveHeroUrl(details)
        val productSku = product.optString("sku", "")

        return AmazonGame(
            id = id,
            entitlementId = entitlementId,
            title = title,
            artUrl = artUrl,
            heroUrl = heroUrl,
            purchasedDate = purchasedDate,
            developer = developer,
            publisher = publisher,
            releaseDate = releaseDate,
            downloadSize = downloadSize,
            productSku = productSku,
            productJson = product.toString(),
        )
    }

    /**
     * Resolve the primary (icon/box) artwork URL.
     *
     * Live API structure (confirmed from device logs):
     *   product -> productDetail -> iconUrl          (box art)
     *   product -> productDetail -> details -> logoUrl  (transparent logo PNG, fallback)
     */
    private fun resolveArtUrl(productDetail: JSONObject?, details: JSONObject?): String {
        // Primary: iconUrl lives directly on productDetail, NOT inside details
        val iconUrl = productDetail?.optString("iconUrl", "") ?: ""
        if (iconUrl.isNotEmpty()) return iconUrl

        // Fallback: transparent logo PNG inside details
        val logoUrl = details?.optString("logoUrl", "") ?: ""
        if (logoUrl.isNotEmpty()) return logoUrl

        return ""
    }

    /**
     * Resolve the hero/background artwork URL for the detail screen.
     *
     * Live API structure (confirmed from device logs):
     *   product -> productDetail -> details -> backgroundUrl1  (primary background)
     *   product -> productDetail -> details -> backgroundUrl2  (secondary background)
     */
    private fun resolveHeroUrl(details: JSONObject?): String {
        val bg1 = details?.optString("backgroundUrl1", "") ?: ""
        if (bg1.isNotEmpty()) return bg1

        val bg2 = details?.optString("backgroundUrl2", "") ?: ""
        if (bg2.isNotEmpty()) return bg2

        return ""
    }

    /** SHA-256 of [input], hex-encoded in UPPERCASE — matches nile's hardwareHash. */
    private fun sha256Upper(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.uppercase()
    }

    // ── Download API ─────────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the download manifest spec for a game.
     *
     * Mirrors nile's `Library.get_game_manifest(id)` where `id` is the top-level
     * entitlement UUID (NOT the product ID).
     *
     * @param entitlementId  The UUID from [AmazonGame.entitlementId].
     * @param bearerToken    The `access_token` from [AmazonAuthManager].
     * @return [GameDownloadSpec] on success, null on failure.
     */
    suspend fun fetchGameDownload(
        entitlementId: String,
        bearerToken: String,
    ): GameDownloadSpec? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("entitlementId", entitlementId)
            put("Operation", "GetGameDownload")
        }

        Timber.tag("Amazon").d("fetchGameDownload: entitlementId=$entitlementId, token=${bearerToken.take(20)}…")

        val response = postJson(
            url = DISTRIBUTION_URL,
            target = GET_GAME_DOWNLOAD_TARGET,
            bearerToken = bearerToken,
            body = body,
        ) ?: return@withContext null

        val downloadUrl = response.optString("downloadUrl", "").ifEmpty {
            Timber.e("[Amazon] GetGameDownload: missing downloadUrl in response: ${response.toString().take(500)}")
            return@withContext null
        }
        val versionId = response.optString("versionId", "")
        Timber.i("[Amazon] GetGameDownload: versionId=$versionId url=$downloadUrl")
        GameDownloadSpec(downloadUrl = downloadUrl, versionId = versionId)
    }

    // ── Live version checking ───────────────────────────────────────────────────────

    /**
     * Fetches live version IDs for a list of product IDs.
     *
     * Mirrors nile’s `Library.get_live_version_ids(product_ids)` which calls
     * `AnimusDistributionService.GetLiveVersionIds`.
     *
     * @param adgProductIds  List of Amazon product IDs (e.g. "amzn1.adg.product.XXXX").
     * @param bearerToken    The `access_token` from [AmazonAuthManager].
     * @return Map of productId → liveVersionId, or null on failure.
     */
    suspend fun fetchLiveVersionIds(
        adgProductIds: List<String>,
        bearerToken: String,
    ): Map<String, String>? = withContext(Dispatchers.IO) {
        if (adgProductIds.isEmpty()) return@withContext emptyMap()

        val idsArray = org.json.JSONArray(adgProductIds)
        val body = JSONObject().apply {
            put("adgProductIds", idsArray)
            put("Operation", "GetLiveVersionIds")
        }

        Timber.tag("Amazon").d("fetchLiveVersionIds: ${adgProductIds.size} product(s)")

        val response = postJson(
            url = DISTRIBUTION_URL,
            target = GET_LIVE_VERSION_IDS_TARGET,
            bearerToken = bearerToken,
            body = body,
        ) ?: return@withContext null

        // Response shape: { "adgProductIdToVersionIdMap": { "productId1": "versionId1", ... } }
        val versions = response.optJSONObject("adgProductIdToVersionIdMap")
        if (versions == null) {
            Timber.tag("Amazon").w("GetLiveVersionIds: no 'adgProductIdToVersionIdMap' in response: ${response.toString().take(500)}")
            return@withContext null
        }

        val result = mutableMapOf<String, String>()
        for (key in versions.keys()) {
            result[key] = versions.optString(key, "")
        }
        Timber.tag("Amazon").i("GetLiveVersionIds: ${result.size} version(s) returned")
        result
    }

    /**
     * Checks whether a single game has an update available by comparing the stored
     * [AmazonGame.versionId] with the live version from Amazon.
     *
     * @return `true` if the live version differs from the stored version, `false` if up-to-date,
     *         or `null` on API failure.
     */
    suspend fun isUpdateAvailable(
        productId: String,
        storedVersionId: String,
        bearerToken: String,
    ): Boolean? = withContext(Dispatchers.IO) {
        val liveVersions = fetchLiveVersionIds(listOf(productId), bearerToken)
            ?: return@withContext null
        val liveVersion = liveVersions[productId]
        if (liveVersion.isNullOrEmpty()) {
            Timber.tag("Amazon").w("isUpdateAvailable: no live version returned for $productId")
            return@withContext null
        }
        val updateAvailable = liveVersion != storedVersionId
        Timber.tag("Amazon").i(
            "isUpdateAvailable: productId=$productId stored=$storedVersionId live=$liveVersion update=$updateAvailable"
        )
        updateAvailable
    }

    // ── Download size pre-fetch ──────────────────────────────────────────────────────────────

    /**
     * Fetch the total download size (in bytes) for a game by downloading and parsing
     * its manifest. This contacts the Amazon distribution service, downloads the
     * manifest.proto, and sums the file sizes.
     *
     * @param entitlementId  The UUID from [AmazonGame.entitlementId].
     * @param bearerToken    The `access_token` from [AmazonAuthManager].
     * @return Total install size in bytes, or null on failure.
     */
    suspend fun fetchDownloadSize(
        entitlementId: String,
        bearerToken: String,
    ): Long? = withContext(Dispatchers.IO) {
        Timber.tag("Amazon").d("fetchDownloadSize: entitlementId=$entitlementId")

        val spec = fetchGameDownload(entitlementId, bearerToken) ?: run {
            Timber.tag("Amazon").w("fetchDownloadSize: failed to get download spec")
            return@withContext null
        }

        val manifestUrl = appendPath(spec.downloadUrl, "manifest.proto")
        Timber.tag("Amazon").d("fetchDownloadSize: manifest URL = $manifestUrl")

        val manifestBytes = try {
            URL(manifestUrl).openStream().use { it.readBytes() }
        } catch (e: Exception) {
            Timber.tag("Amazon").e(e, "fetchDownloadSize: failed to fetch manifest.proto")
            return@withContext null
        }

        try {
            val manifest = AmazonManifest.parse(manifestBytes)
            Timber.tag("Amazon").i("fetchDownloadSize: totalInstallSize = ${manifest.totalInstallSize}")
            manifest.totalInstallSize
        } catch (e: Exception) {
            Timber.tag("Amazon").e(e, "fetchDownloadSize: failed to parse manifest")
            null
        }
    }

    // ── SDK / Launcher channel ──────────────────────────────────────────────────────────────

    /** Amazon Games Launcher channel ID — source for FuelSDK + AmazonGamesSDK DLLs. */
    private const val LAUNCHER_CHANNEL_ID = "87d38116-4cbf-4af0-a371-a5b498975346"

    /**
     * Fetches the download spec for the Amazon Games Launcher / SDK channel.
     *
     * Mirrors nile's `Library.get_sdk()` — calls
     * `GET {DISTRIBUTION_URL}/download/channel/{channelId}`.
     *
     * The response has the same shape as GetGameDownload (`downloadUrl`, `versionId`).
     *
     * @param bearerToken  The `access_token` from [AmazonAuthManager].
     * @return [GameDownloadSpec] on success, null on failure.
     */
    suspend fun fetchSdkDownload(
        bearerToken: String,
    ): GameDownloadSpec? = withContext(Dispatchers.IO) {
        val url = "$DISTRIBUTION_URL/download/channel/$LAUNCHER_CHANNEL_ID"
        Timber.tag("Amazon").d("fetchSdkDownload: GET $url")

        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("x-amzn-token", bearerToken)
                setRequestProperty("UserAgent", USER_AGENT)
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            val responseCode = connection.responseCode
            if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "(no body)"
                Timber.tag("Amazon").e("fetchSdkDownload: HTTP $responseCode: $errorBody")
                return@withContext null
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(responseText)

            val downloadUrl = json.optString("downloadUrl", "").ifEmpty {
                Timber.tag("Amazon").e("fetchSdkDownload: missing downloadUrl")
                return@withContext null
            }
            val versionId = json.optString("versionId", "")
            Timber.tag("Amazon").i("fetchSdkDownload: versionId=$versionId url=${downloadUrl.take(80)}…")
            GameDownloadSpec(downloadUrl = downloadUrl, versionId = versionId)
        } catch (e: Exception) {
            Timber.tag("Amazon").e(e, "fetchSdkDownload failed")
            null
        }
    }

    /** Append [segment] to the path portion of [baseUrl], before any query string. */
    internal fun appendPath(baseUrl: String, segment: String): String {
        val qIdx = baseUrl.indexOf('?')
        return if (qIdx == -1) {
            "$baseUrl/$segment"
        } else {
            val path = baseUrl.substring(0, qIdx)
            val query = baseUrl.substring(qIdx)
            "$path/$segment$query"
        }
    }
}
