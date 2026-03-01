package app.gamenative.gamefixes

import app.gamenative.data.GameSource

val EPIC_Fix_8297739d460e443096287f0a09777e54: KeyedGameFix = KeyedRegistryKeyFix(
    gameSource = GameSource.EPIC,
    gameId = "8297739d460e443096287f0a09777e54",
    registryKey = """Software\Wow6432Node\Bethesda Softworks\Fallout4""",
    defaultValues = mapOf(
        "Installed Path" to INSTALL_PATH_PLACEHOLDER,
    ),
)
