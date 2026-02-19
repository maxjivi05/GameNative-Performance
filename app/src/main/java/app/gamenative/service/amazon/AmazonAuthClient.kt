package app.gamenative.service.amazon

import app.gamenative.utils.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Response from Amazon device registration (maps to nile's token payload).
 */
data class AmazonAuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val tokenType: String,
)

/**
 * Low-level HTTP client for Amazon's OAuth / device-auth APIs.
 *
 * Uses the same OkHttp instance (`Net.http`) as the Epic and GOG clients.
 */
object AmazonAuthClient {

    private val httpClient = Net.http
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * Register a new device with Amazon, exchanging the PKCE authorization
     * code for access + refresh tokens.
     *
     * Matches nile `authorization.py  → _register_device()`.
     *
     * @param authorizationCode  The `openid.oa2.authorization_code` captured from the redirect.
     * @param codeVerifier       The PKCE code verifier generated at the start of the flow.
     * @param deviceSerial       The random device serial used for this session.
     * @param clientId           The hex-encoded client_id (`serial#DEVICE_TYPE`).
     * @return [AmazonAuthResponse] on success, or a failure Result.
     */
    suspend fun registerDevice(
        authorizationCode: String,
        codeVerifier: String,
        deviceSerial: String,
        clientId: String,
    ): Result<AmazonAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("auth_data", JSONObject().apply {
                    put("authorization_code", authorizationCode)
                    put("client_domain", "DeviceLegacy")
                    put("client_id", clientId)
                    put("code_algorithm", "SHA-256")
                    put("code_verifier", codeVerifier)
                    put("use_global_authentication", false)
                })
                put("registration_data", JSONObject().apply {
                    put("app_name", AmazonConstants.APP_NAME)
                    put("app_version", AmazonConstants.APP_VERSION)
                    put("device_model", "Windows")
                    put("device_name", JSONObject.NULL)
                    put("device_serial", deviceSerial)
                    put("device_type", AmazonConstants.DEVICE_TYPE)
                    put("domain", "Device")
                    put("os_version", "10.0.19044.0")
                })
                put("requested_extensions", JSONArray().apply { put("customer_info"); put("device_info") })
                put("requested_token_type", JSONArray().apply { put("bearer"); put("mac_dms") })
                put("user_context_map", JSONObject())
            }

            val request = Request.Builder()
                .url(AmazonConstants.AUTH_REGISTER_URL)
                .header("User-Agent", AmazonConstants.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Timber.e("[Amazon] Device registration failed: ${response.code} - $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)

            // The response nests tokens inside response → success → tokens → bearer
            val tokensObj = json
                .getJSONObject("response")
                .getJSONObject("success")
                .getJSONObject("tokens")
                .getJSONObject("bearer")

            val authResponse = AmazonAuthResponse(
                accessToken = tokensObj.getString("access_token"),
                refreshToken = tokensObj.getString("refresh_token"),
                expiresIn = tokensObj.optInt("expires_in", 3600),
                tokenType = tokensObj.optString("token_type", "bearer"),
            )

            Timber.i("[Amazon] Device registration successful")
            Result.success(authResponse)
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Device registration exception")
            Result.failure(e)
        }
    }

    /**
     * Refresh an access token using a stored refresh token.
     *
     * Matches nile `authorization.py  → _refresh_token()`.
     */
    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
    ): Result<AmazonAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("source_token", refreshToken)
                put("source_token_type", "refresh_token")
                put("requested_token_type", "access_token")
                put("app_name", AmazonConstants.APP_NAME)
                put("app_version", AmazonConstants.APP_VERSION)
            }

            val request = Request.Builder()
                .url(AmazonConstants.AUTH_TOKEN_URL)
                .header("User-Agent", AmazonConstants.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-amzn-identity-auth-domain", "api.amazon.com")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Timber.e("[Amazon] Token refresh failed: ${response.code} - $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)

            val authResponse = AmazonAuthResponse(
                accessToken = json.getString("access_token"),
                refreshToken = refreshToken, // refresh token stays the same
                expiresIn = json.optInt("expires_in", 3600),
                tokenType = json.optString("token_type", "bearer"),
            )

            Timber.i("[Amazon] Token refresh successful")
            Result.success(authResponse)
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Token refresh exception")
            Result.failure(e)
        }
    }

    /**
     * De-register a device (logout / revoke tokens).
     *
     * Matches nile `authorization.py  → deregister()`.
     */
    suspend fun deregisterDevice(
        accessToken: String,
        deviceSerial: String,
        clientId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("requested_extensions", listOf("device_info", "customer_info"))
            }

            val request = Request.Builder()
                .url(AmazonConstants.AUTH_DEREGISTER_URL)
                .header("User-Agent", AmazonConstants.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Timber.w("[Amazon] Deregister returned ${response.code}: $errorBody")
                // Non-fatal: credentials will still be cleared locally
            } else {
                Timber.i("[Amazon] Device deregistered successfully")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "[Amazon] Device deregister exception (non-fatal)")
            // Still succeed locally – we'll clear creds regardless
            Result.success(Unit)
        }
    }
}
