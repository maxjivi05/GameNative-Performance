package app.gamenative.service.amazon

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * PKCE (Proof Key for Code Exchange) utilities for Amazon Games OAuth.
 *
 * Amazon doesn't use a fixed client_id/secret like Epic or GOG – instead the
 * client dynamically generates a device_serial + code_verifier pair per login.
 *
 * Flow (matching nile's `authorization.py`):
 * 1. Generate a random device_serial (UUID hex, uppercase)
 * 2. Derive client_id  = hex(device_serial # DEVICE_TYPE)
 * 3. Generate code_verifier  (32 random bytes → base64url, no padding)
 * 4. Derive code_challenge   = base64url(SHA-256(code_verifier)), no padding
 */
object AmazonPKCEGenerator {

    private val secureRandom = SecureRandom()

    /**
     * Generates a random device serial (UUID with hyphens removed, uppercase).
     * Matches nile: `uuid.uuid4().hex.upper()`
     */
    fun generateDeviceSerial(): String {
        return UUID.randomUUID().toString().replace("-", "").uppercase()
    }

    /**
     * Builds the dynamic client_id by hex-encoding "serial#DEVICE_TYPE".
     * Matches nile: `bytes(f"{serial}#{DEVICE_TYPE}", "utf-8").hex()`
     */
    fun generateClientId(deviceSerial: String): String {
        val raw = "$deviceSerial#${AmazonConstants.DEVICE_TYPE}"
        return raw.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a PKCE code verifier (32 random bytes → base64url, no padding).
     * Matches nile: `base64.urlsafe_b64encode(os.urandom(32)).rstrip(b"=")`
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Derives the PKCE code challenge from a code verifier.
     * code_challenge = base64url(SHA-256(code_verifier)), no padding.
     * Matches nile: `base64.urlsafe_b64encode(hashlib.sha256(code_verifier).digest()).rstrip(b"=")`
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
