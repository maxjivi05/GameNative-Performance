package com.winlator.container

import androidx.compose.runtime.saveable.mapSaver
import com.winlator.box86_64.Box86_64Preset
import com.winlator.core.DefaultVersion
import com.winlator.core.WineInfo
import com.winlator.core.WineThemeManager
import com.winlator.fexcore.FEXCorePreset
import org.json.JSONObject
import kotlin.String

data class ContainerData(
    val name: String = "",
    val screenSize: String = Container.DEFAULT_SCREEN_SIZE,
    val envVars: String = Container.DEFAULT_ENV_VARS,
    val graphicsDriver: String = Container.DEFAULT_GRAPHICS_DRIVER,
    val graphicsDriverVersion: String = "",
    val graphicsDriverConfig: String = "",
    var dxwrapper: String = Container.DEFAULT_DXWRAPPER,
    val dxwrapperConfig: String = "",
    val audioDriver: String = Container.DEFAULT_AUDIO_DRIVER,
    val wincomponents: String = Container.DEFAULT_WINCOMPONENTS,
    val drives: String = Container.DEFAULT_DRIVES,
    val execArgs: String = "",
    val executablePath: String = "",
    val installPath: String = "",
    val showFPS: Boolean = false,
    val launchRealSteam: Boolean = false,
    val allowSteamUpdates: Boolean = false,
    val steamType: String = "normal",
    val cpuList: String = Container.getFallbackCPUList(),
    val cpuListWoW64: String = Container.getFallbackCPUListWoW64(),
    val wow64Mode: Boolean = true,
    val startupSelection: Byte = Container.STARTUP_SELECTION_ESSENTIAL,
    val box86Version: String = DefaultVersion.BOX86,
    val box64Version: String = DefaultVersion.BOX64,
    val box86Preset: String = Box86_64Preset.COMPATIBILITY,
    val box64Preset: String = Box86_64Preset.COMPATIBILITY,
    val desktopTheme: String = WineThemeManager.DEFAULT_DESKTOP_THEME,
    // container runtime variant (glibc or bionic)
    val containerVariant: String = Container.DEFAULT_VARIANT,
    // wine version identifier (used for bionic variant), defaults to main wine
    val wineVersion: String = Container.DEFAULT_WINE_VERSION,
    // selected 32-bit emulator for WoW64 processes (FEXCore/Box64)
    val emulator: String = Container.DEFAULT_EMULATOR,
    // FEXCore version (used on arm64ec)
    val fexcoreVersion: String = DefaultVersion.FEXCORE,
    // FEXCore settings (arm64ec): TSOMode, X87Mode, MultiBlock
    val fexcoreTSOMode: String = "Fast",
    val fexcoreX87Mode: String = "Fast",
    val fexcoreMultiBlock: String = "Disabled",
    // FEXCore preset (arm64ec)
    val fexcorePreset: String = FEXCorePreset.INTERMEDIATE,
    // wine registry
    val renderer: String = "gl",
    val csmt: Boolean = true,
    val videoPciDeviceID: Int = 1728,
    val offScreenRenderingMode: String = "fbo",
    val strictShaderMath: Boolean = true,
    val useDRI3: Boolean = true,
    val videoMemorySize: String = "2048",
    val mouseWarpOverride: String = "disable",
    val shaderBackend: String = "glsl",
    val useGLSL: String = "enabled",
    val sdlControllerAPI: Boolean = true,
    /** Enable Steam Input **/
    val useSteamInput: Boolean = false,
    /** Enable XInput API **/
    val enableXInput: Boolean = true,
    /** Enable DirectInput API **/
    val enableDInput: Boolean = true,
    /** DirectInput mapper type: 1=standard, 2=XInput mapper **/
    val dinputMapperType: Byte = 1,
    /** Disable external mouse input **/
    val disableMouseInput: Boolean = false,
    /** Touchscreen mode **/
    val touchscreenMode: Boolean = false,
    /** External display input handling: off|touchpad|keyboard|hybrid **/
    val externalDisplayMode: String = Container.DEFAULT_EXTERNAL_DISPLAY_MODE,
    /** Swap game/input between internal and external displays **/
    val externalDisplaySwap: Boolean = false,
    /** Preferred game language (Goldberg) **/
    val language: String = "english",
    val forceDlc: Boolean = false,
    val useLegacyDRM: Boolean = false,
    val unpackFiles: Boolean = false,
    val sharpnessEffect: String = "None",
    val sharpnessLevel: Int = 100,
    val sharpnessDenoise: Int = 100,
    val forceAdrenoClocks: Boolean = false,
    val rootPerformanceMode: Boolean = false,
    val dxvkVersion: String? = null,
    val vkd3dVersion: String? = null,
    val extraData: String = "{}",
    val sessionMetadata: String = "{}",
    val needsUnpacking: Boolean = false,
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("name", name)
        json.put("screenSize", screenSize)
        json.put("envVars", envVars)
        json.put("graphicsDriver", graphicsDriver)
        json.put("graphicsDriverVersion", graphicsDriverVersion)
        json.put("graphicsDriverConfig", graphicsDriverConfig)
        json.put("dxwrapper", dxwrapper)
        json.put("dxwrapperConfig", dxwrapperConfig)
        json.put("audioDriver", audioDriver)
        json.put("wincomponents", wincomponents)
        json.put("drives", drives)
        json.put("execArgs", execArgs)
        json.put("executablePath", executablePath)
        json.put("installPath", installPath)
        json.put("showFPS", showFPS)
        json.put("launchRealSteam", launchRealSteam)
        json.put("allowSteamUpdates", allowSteamUpdates)
        json.put("steamType", steamType)
        json.put("cpuList", cpuList)
        json.put("cpuListWoW64", cpuListWoW64)
        json.put("wow64Mode", wow64Mode)
        json.put("startupSelection", startupSelection.toInt())
        json.put("box86Version", box86Version)
        json.put("box64Version", box64Version)
        json.put("box86Preset", box86Preset)
        json.put("box64Preset", box64Preset)
        json.put("desktopTheme", desktopTheme)
        json.put("containerVariant", containerVariant)
        json.put("wineVersion", wineVersion)
        json.put("emulator", emulator)
        json.put("fexcoreVersion", fexcoreVersion)
        json.put("fexcoreTSOMode", fexcoreTSOMode)
        json.put("fexcoreX87Mode", fexcoreX87Mode)
        json.put("fexcoreMultiBlock", fexcoreMultiBlock)
        json.put("fexcorePreset", fexcorePreset)
        json.put("renderer", renderer)
        json.put("csmt", csmt)
        json.put("videoPciDeviceID", videoPciDeviceID)
        json.put("offScreenRenderingMode", offScreenRenderingMode)
        json.put("strictShaderMath", strictShaderMath)
        json.put("useDRI3", useDRI3)
        json.put("videoMemorySize", videoMemorySize)
        json.put("mouseWarpOverride", mouseWarpOverride)
        json.put("shaderBackend", shaderBackend)
        json.put("useGLSL", useGLSL)
        json.put("sdlControllerAPI", sdlControllerAPI)
        json.put("useSteamInput", useSteamInput)
        json.put("enableXInput", enableXInput)
        json.put("enableDInput", enableDInput)
        json.put("dinputMapperType", dinputMapperType.toInt())
        json.put("disableMouseInput", disableMouseInput)
        json.put("touchscreenMode", touchscreenMode)
        json.put("externalDisplayMode", externalDisplayMode)
        json.put("externalDisplaySwap", externalDisplaySwap)
        json.put("language", language)
        json.put("forceDlc", forceDlc)
        json.put("useLegacyDRM", useLegacyDRM)
        json.put("unpackFiles", unpackFiles)
        json.put("sharpnessEffect", sharpnessEffect)
        json.put("sharpnessLevel", sharpnessLevel)
        json.put("sharpnessDenoise", sharpnessDenoise)
        json.put("forceAdrenoClocks", forceAdrenoClocks)
        json.put("rootPerformanceMode", rootPerformanceMode)
        json.put("extraData", extraData)
        json.put("sessionMetadata", sessionMetadata)
        json.put("needsUnpacking", needsUnpacking)
        if (dxvkVersion != null) json.put("dxvkVersion", dxvkVersion)
        if (vkd3dVersion != null) json.put("vkd3dVersion", vkd3dVersion)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ContainerData {
            val json = JSONObject(jsonString)
            return ContainerData(
                name = json.optString("name", ""),
                screenSize = json.optString("screenSize", Container.DEFAULT_SCREEN_SIZE),
                envVars = json.optString("envVars", Container.DEFAULT_ENV_VARS),
                graphicsDriver = json.optString("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER),
                graphicsDriverVersion = json.optString("graphicsDriverVersion", ""),
                graphicsDriverConfig = json.optString("graphicsDriverConfig", ""),
                dxwrapper = json.optString("dxwrapper", Container.DEFAULT_DXWRAPPER),
                dxwrapperConfig = json.optString("dxwrapperConfig", ""),
                audioDriver = json.optString("audioDriver", Container.DEFAULT_AUDIO_DRIVER),
                wincomponents = json.optString("wincomponents", Container.DEFAULT_WINCOMPONENTS),
                drives = json.optString("drives", Container.DEFAULT_DRIVES),
                execArgs = json.optString("execArgs", ""),
                executablePath = json.optString("executablePath", ""),
                installPath = json.optString("installPath", ""),
                showFPS = json.optBoolean("showFPS", false),
                launchRealSteam = json.optBoolean("launchRealSteam", false),
                allowSteamUpdates = json.optBoolean("allowSteamUpdates", false),
                steamType = json.optString("steamType", "normal"),
                cpuList = json.optString("cpuList", Container.getFallbackCPUList()),
                cpuListWoW64 = json.optString("cpuListWoW64", Container.getFallbackCPUListWoW64()),
                wow64Mode = json.optBoolean("wow64Mode", true),
                startupSelection = json.optInt("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL.toInt()).toByte(),
                box86Version = json.optString("box86Version", DefaultVersion.BOX86),
                box64Version = json.optString("box64Version", DefaultVersion.BOX64),
                box86Preset = json.optString("box86Preset", Box86_64Preset.COMPATIBILITY),
                box64Preset = json.optString("box64Preset", Box86_64Preset.COMPATIBILITY),
                desktopTheme = json.optString("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME),
                containerVariant = json.optString("containerVariant", Container.DEFAULT_VARIANT),
                wineVersion = json.optString("wineVersion", WineInfo.MAIN_WINE_VERSION.identifier()),
                emulator = json.optString("emulator", Container.DEFAULT_EMULATOR),
                fexcoreVersion = json.optString("fexcoreVersion", DefaultVersion.FEXCORE),
                fexcoreTSOMode = json.optString("fexcoreTSOMode", "Fast"),
                fexcoreX87Mode = json.optString("fexcoreX87Mode", "Fast"),
                fexcoreMultiBlock = json.optString("fexcoreMultiBlock", "Disabled"),
                fexcorePreset = json.optString("fexcorePreset", FEXCorePreset.INTERMEDIATE),
                renderer = json.optString("renderer", "gl"),
                csmt = json.optBoolean("csmt", true),
                videoPciDeviceID = json.optInt("videoPciDeviceID", 1728),
                offScreenRenderingMode = json.optString("offScreenRenderingMode", "fbo"),
                strictShaderMath = json.optBoolean("strictShaderMath", true),
                useDRI3 = json.optBoolean("useDRI3", true),
                videoMemorySize = json.optString("videoMemorySize", "2048"),
                mouseWarpOverride = json.optString("mouseWarpOverride", "disable"),
                shaderBackend = json.optString("shaderBackend", "glsl"),
                useGLSL = json.optString("useGLSL", "enabled"),
                sdlControllerAPI = json.optBoolean("sdlControllerAPI", true),
                useSteamInput = json.optBoolean("useSteamInput", false),
                enableXInput = json.optBoolean("enableXInput", true),
                enableDInput = json.optBoolean("enableDInput", true),
                dinputMapperType = json.optInt("dinputMapperType", 1).toByte(),
                disableMouseInput = json.optBoolean("disableMouseInput", false),
                touchscreenMode = json.optBoolean("touchscreenMode", false),
                externalDisplayMode = json.optString("externalDisplayMode", Container.DEFAULT_EXTERNAL_DISPLAY_MODE),
                externalDisplaySwap = json.optBoolean("externalDisplaySwap", false),
                language = json.optString("language", "english"),
                forceDlc = json.optBoolean("forceDlc", false),
                useLegacyDRM = json.optBoolean("useLegacyDRM", false),
                unpackFiles = json.optBoolean("unpackFiles", false),
                sharpnessEffect = json.optString("sharpnessEffect", "None"),
                sharpnessLevel = json.optInt("sharpnessLevel", 100),
                sharpnessDenoise = json.optInt("sharpnessDenoise", 100),
                forceAdrenoClocks = json.optBoolean("forceAdrenoClocks", false),
                rootPerformanceMode = json.optBoolean("rootPerformanceMode", false),
                extraData = json.optString("extraData", "{}"),
                sessionMetadata = json.optString("sessionMetadata", "{}"),
                needsUnpacking = json.optBoolean("needsUnpacking", false),
                dxvkVersion = if (json.has("dxvkVersion")) json.getString("dxvkVersion") else null,
                vkd3dVersion = if (json.has("vkd3dVersion")) json.getString("vkd3dVersion") else null,
            )
        }

        val Saver = mapSaver(
            save = { state ->
                mapOf(
                    "name" to state.name,
                    "screenSize" to state.screenSize,
                    "envVars" to state.envVars,
                    "graphicsDriver" to state.graphicsDriver,
                    "graphicsDriverVersion" to state.graphicsDriverVersion,
                    "graphicsDriverConfig" to state.graphicsDriverConfig,
                    "dxwrapper" to state.dxwrapper,
                    "dxwrapperConfig" to state.dxwrapperConfig,
                    "audioDriver" to state.audioDriver,
                    "wincomponents" to state.wincomponents,
                    "drives" to state.drives,
                    "execArgs" to state.execArgs,
                    "executablePath" to state.executablePath,
                    "installPath" to state.installPath,
                    "showFPS" to state.showFPS,
                    "launchRealSteam" to state.launchRealSteam,
                    "allowSteamUpdates" to state.allowSteamUpdates,
                    "steamType" to state.steamType,
                    "cpuList" to state.cpuList,
                    "cpuListWoW64" to state.cpuListWoW64,
                    "wow64Mode" to state.wow64Mode,
                    "startupSelection" to state.startupSelection,
                    "box86Version" to state.box86Version,
                    "box64Version" to state.box64Version,
                    "box86Preset" to state.box86Preset,
                    "box64Preset" to state.box64Preset,
                    "desktopTheme" to state.desktopTheme,
                    "containerVariant" to state.containerVariant,
                    "wineVersion" to state.wineVersion,
                    "emulator" to state.emulator,
                    "fexcoreVersion" to state.fexcoreVersion,
                    "fexcoreTSOMode" to state.fexcoreTSOMode,
                    "fexcoreX87Mode" to state.fexcoreX87Mode,
                    "fexcoreMultiBlock" to state.fexcoreMultiBlock,
                    "fexcorePreset" to state.fexcorePreset,
                    "sdlControllerAPI" to state.sdlControllerAPI,
                    "useSteamInput" to state.useSteamInput,
                    "enableXInput" to state.enableXInput,
                    "enableDInput" to state.enableDInput,
                    "dinputMapperType" to state.dinputMapperType,
                    "disableMouseInput" to state.disableMouseInput,
                    "touchscreenMode" to state.touchscreenMode,
                    "externalDisplayMode" to state.externalDisplayMode,
                    "externalDisplaySwap" to state.externalDisplaySwap,
                    "useDRI3" to state.useDRI3,
                    "language" to state.language,
                    "forceDlc" to state.forceDlc,
                    "useLegacyDRM" to state.useLegacyDRM,
                    "unpackFiles" to state.unpackFiles,
                    "sharpnessEffect" to state.sharpnessEffect,
                    "sharpnessLevel" to state.sharpnessLevel,
                    "sharpnessDenoise" to state.sharpnessDenoise,
                    "forceAdrenoClocks" to state.forceAdrenoClocks,
                    "rootPerformanceMode" to state.rootPerformanceMode,
                    "extraData" to state.extraData,
                    "sessionMetadata" to state.sessionMetadata,
                    "needsUnpacking" to state.needsUnpacking,
                    "dxvkVersion" to state.dxvkVersion,
                    "vkd3dVersion" to state.vkd3dVersion,
                    "renderer" to state.renderer,
                    "csmt" to state.csmt,
                    "videoPciDeviceID" to state.videoPciDeviceID,
                    "offScreenRenderingMode" to state.offScreenRenderingMode,
                    "strictShaderMath" to state.strictShaderMath,
                    "videoMemorySize" to state.videoMemorySize,
                    "mouseWarpOverride" to state.mouseWarpOverride,
                    "shaderBackend" to state.shaderBackend,
                    "useGLSL" to state.useGLSL,
                )
            },
            restore = { savedMap ->
                ContainerData(
                    name = savedMap["name"] as String,
                    screenSize = savedMap["screenSize"] as String,
                    envVars = savedMap["envVars"] as String,
                    graphicsDriver = savedMap["graphicsDriver"] as String,
                    graphicsDriverVersion = savedMap["graphicsDriverVersion"] as String,
                    graphicsDriverConfig = (savedMap["graphicsDriverConfig"] as? String) ?: "",
                    dxwrapper = savedMap["dxwrapper"] as String,
                    dxwrapperConfig = savedMap["dxwrapperConfig"] as String,
                    audioDriver = savedMap["audioDriver"] as String,
                    wincomponents = savedMap["wincomponents"] as String,
                    drives = savedMap["drives"] as String,
                    execArgs = savedMap["execArgs"] as String,
                    executablePath = savedMap["executablePath"] as String,
                    installPath = savedMap["installPath"] as String,
                    showFPS = savedMap["showFPS"] as Boolean,
                    launchRealSteam = savedMap["launchRealSteam"] as Boolean,
                    allowSteamUpdates = savedMap["allowSteamUpdates"] as Boolean,
                    steamType = (savedMap["steamType"] as? String) ?: "normal",
                    cpuList = savedMap["cpuList"] as String,
                    cpuListWoW64 = savedMap["cpuListWoW64"] as String,
                    wow64Mode = savedMap["wow64Mode"] as Boolean,
                    startupSelection = savedMap["startupSelection"] as Byte,
                    box86Version = savedMap["box86Version"] as String,
                    box64Version = savedMap["box64Version"] as String,
                    box86Preset = savedMap["box86Preset"] as String,
                    box64Preset = savedMap["box64Preset"] as String,
                    desktopTheme = savedMap["desktopTheme"] as String,
                    containerVariant = (savedMap["containerVariant"] as? String) ?: Container.DEFAULT_VARIANT,
                    wineVersion = (savedMap["wineVersion"] as? String) ?: WineInfo.MAIN_WINE_VERSION.identifier(),
                    emulator = (savedMap["emulator"] as? String) ?: Container.DEFAULT_EMULATOR,
                    fexcoreVersion = (savedMap["fexcoreVersion"] as? String) ?: DefaultVersion.FEXCORE,
                    fexcoreTSOMode = (savedMap["fexcoreTSOMode"] as? String) ?: "Fast",
                    fexcoreX87Mode = (savedMap["fexcoreX87Mode"] as? String) ?: "Fast",
                    fexcoreMultiBlock = (savedMap["fexcoreMultiBlock"] as? String) ?: "Disabled",
                    fexcorePreset = (savedMap["fexcorePreset"] as? String) ?: FEXCorePreset.INTERMEDIATE,
                    sdlControllerAPI = savedMap["sdlControllerAPI"] as Boolean,
                    useSteamInput = (savedMap["useSteamInput"] as? Boolean) ?: false,
                    enableXInput = savedMap["enableXInput"] as Boolean,
                    enableDInput = savedMap["enableDInput"] as Boolean,
                    dinputMapperType = savedMap["dinputMapperType"] as Byte,
                    disableMouseInput = savedMap["disableMouseInput"] as Boolean,
                    touchscreenMode = savedMap["touchscreenMode"] as Boolean,
                    externalDisplayMode = (savedMap["externalDisplayMode"] as? String) ?: Container.DEFAULT_EXTERNAL_DISPLAY_MODE,
                    externalDisplaySwap = (savedMap["externalDisplaySwap"] as? Boolean) ?: false,
                    useDRI3 = (savedMap["useDRI3"] as? Boolean) ?: true,
                    language = (savedMap["language"] as? String) ?: "english",
                    forceDlc = (savedMap["forceDlc"] as? Boolean) ?: false,
                    useLegacyDRM = (savedMap["useLegacyDRM"] as? Boolean) ?: false,
                    unpackFiles = (savedMap["unpackFiles"] as? Boolean) ?: false,
                    sharpnessEffect = (savedMap["sharpnessEffect"] as? String) ?: "None",
                    sharpnessLevel = (savedMap["sharpnessLevel"] as? Int) ?: 100,
                    sharpnessDenoise = (savedMap["sharpnessDenoise"] as? Int) ?: 100,
                    forceAdrenoClocks = (savedMap["forceAdrenoClocks"] as? Boolean) ?: false,
                    rootPerformanceMode = (savedMap["rootPerformanceMode"] as? Boolean) ?: false,
                    extraData = (savedMap["extraData"] as? String) ?: "{}",
                    sessionMetadata = (savedMap["sessionMetadata"] as? String) ?: "{}",
                    needsUnpacking = (savedMap["needsUnpacking"] as? Boolean) ?: false,
                    dxvkVersion = (savedMap["dxvkVersion"] as? String),
                    vkd3dVersion = (savedMap["vkd3dVersion"] as? String),
                    renderer = (savedMap["renderer"] as? String) ?: "gl",
                    csmt = (savedMap["csmt"] as? Boolean) ?: true,
                    videoPciDeviceID = (savedMap["videoPciDeviceID"] as? Int) ?: 1728,
                    offScreenRenderingMode = (savedMap["offScreenRenderingMode"] as? String) ?: "fbo",
                    strictShaderMath = (savedMap["strictShaderMath"] as? Boolean) ?: true,
                    videoMemorySize = (savedMap["videoMemorySize"] as? String) ?: "2048",
                    mouseWarpOverride = (savedMap["mouseWarpOverride"] as? String) ?: "disable",
                    shaderBackend = (savedMap["shaderBackend"] as? String) ?: "glsl",
                    useGLSL = (savedMap["useGLSL"] as? String) ?: "enabled",
                )
            },
        )
    }
}
