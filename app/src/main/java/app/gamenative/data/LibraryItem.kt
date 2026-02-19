package app.gamenative.data

import app.gamenative.Constants
import app.gamenative.utils.CustomGameScanner

enum class GameSource {
    STEAM,
    CUSTOM_GAME,
    GOG,
    EPIC,
    AMAZON
    // Add other platforms here..
}

enum class GameCompatibilityStatus {
    NOT_COMPATIBLE,
    UNKNOWN,
    COMPATIBLE,
    GPU_COMPATIBLE
}

/**
 * Data class for the Library list
 */
data class LibraryItem(
    val index: Int = 0,
    val appId: String = "",
    val name: String = "",
    val iconHash: String = "",
    val isShared: Boolean = false,
    val gameSource: GameSource = GameSource.STEAM,
    val compatibilityStatus: GameCompatibilityStatus? = null,
) {
    val clientIconUrl: String
        get() = when (gameSource) {
            GameSource.STEAM -> if (iconHash.isNotEmpty()) {
                Constants.Library.ICON_URL + "${gameId}/$iconHash.ico"
            } else {
                ""
            }
            GameSource.CUSTOM_GAME -> {
                // Attempt to resolve a local icon from the selected/unique exe folder
                val localPath = CustomGameScanner.findIconFileForCustomGame(appId)
                if (!localPath.isNullOrEmpty()) {
                    if (localPath.startsWith("file://")) localPath else "file://$localPath"
                } else {
                    ""
                }
            }
            GameSource.GOG -> {
                // GoG Images are typically the full URL, but have fallback just in case.
                if (iconHash.isEmpty()) {
                    ""
                } else if (iconHash.startsWith("http")) {
                    iconHash
                } else {
                    "${GOGGame.GOG_IMAGE_BASE_URL}/$iconHash"
                }
            }
            GameSource.EPIC -> {
                iconHash
            }
            GameSource.AMAZON -> {
                iconHash
            }
        }

    /**
     * Helper property to get the game ID as an integer.
     * For Steam/Epic/GOG/Custom: extract the numeric part after the prefix.
     * For Amazon: product IDs are UUID strings (e.g. "amzn1.adg.product.xxx")
     * that cannot be parsed as Int, so we use hashCode() for a stable Int representation.
     * This matches the hashCode() used in AmazonService event emission.
     */
    val gameId: Int
        get() {
            val idPart = appId.removePrefix("${gameSource.name}_")
            return idPart.toIntOrNull()
                ?: if (gameSource == GameSource.AMAZON) idPart.hashCode() else 0
        }
}
