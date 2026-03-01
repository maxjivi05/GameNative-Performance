package app.gamenative.gamefixes

import app.gamenative.data.GameSource

val EPIC_Fix_5b79a51599f14cf289c3448a17bc616b: KeyedGameFix = KeyedRegistryKeyFix(
    gameSource = GameSource.EPIC,
    gameId = "5b79a51599f14cf289c3448a17bc616b",
    registryKey = """Software\Wow6432Node\Bethesda Softworks\Fallout4""",
    defaultValues = mapOf(
        "Installed Path" to INSTALL_PATH_PLACEHOLDER,
    ),
)
