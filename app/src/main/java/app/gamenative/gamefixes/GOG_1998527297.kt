package app.gamenative.gamefixes

import app.gamenative.data.GameSource

val GOG_Fix_1998527297: KeyedGameFix = KeyedRegistryKeyFix(
    gameSource = GameSource.GOG,
    gameId = "1998527297",
    registryKey = """Software\Wow6432Node\Bethesda Softworks\Fallout4""",
    defaultValues = mapOf(
        "Installed Path" to INSTALL_PATH_PLACEHOLDER,
    ),
)
