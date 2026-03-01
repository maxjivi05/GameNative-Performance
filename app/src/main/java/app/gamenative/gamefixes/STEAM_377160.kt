package app.gamenative.gamefixes

import app.gamenative.data.GameSource

val STEAM_Fix_377160: KeyedGameFix = KeyedRegistryKeyFix(
    gameSource = GameSource.STEAM,
    gameId = "377160",
    registryKey = """Software\Wow6432Node\Bethesda Softworks\Fallout4""",
    defaultValues = mapOf(
        "Installed Path" to INSTALL_PATH_PLACEHOLDER,
    ),
)
