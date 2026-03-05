package app.gamenative.utils

import android.content.Context
import com.winlator.container.Container
import com.winlator.contents.AdrenotoolsManager
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import com.winlator.core.GPUHelper
import com.winlator.core.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import app.gamenative.PrefManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import java.util.Locale

object ManifestComponentHelper {
    data class InstalledContentLists(
        val dxvk: List<String>,
        val vkd3d: List<String>,
        val box64: List<String>,
        val wowBox64: List<String>,
        val fexcore: List<String>,
        val wine: List<String>,
        val proton: List<String>,
    )

    data class InstalledContentListsAndDrivers(
        val installed: InstalledContentLists,
        val installedDrivers: List<String>,
    )

    data class ComponentAvailability(
        val manifest: ManifestData,
        val installed: InstalledContentLists,
        val installedDrivers: List<String>,
    )

    data class VersionOptionList(
        val labels: List<String>,
        val ids: List<String>,
        val muted: List<Boolean>,
    )

    fun filterManifestByVariant(entries: List<ManifestEntry>, variant: String?): List<ManifestEntry> {
        return entries.filter { entry -> entry.variant?.lowercase(Locale.ENGLISH) == variant?.lowercase(Locale.ENGLISH) }
    }

    suspend fun loadInstalledContentLists(
        context: Context,
    ): InstalledContentListsAndDrivers = withContext(Dispatchers.IO) {
        val installedDrivers = AdrenotoolsManager(context).enumarateInstalledDrivers()

        val installedContent = try {
            val mgr = ContentsManager(context)
            mgr.syncContents()

            fun profilesToDisplay(
                list: List<ContentProfile>?,
            ): List<String> {
                if (list == null) return emptyList()
                return list.filter { profile -> 
                    profile.remoteUrl == null
                }.map { profile ->
                    val entry = ContentsManager.getEntryName(profile)
                    val firstDash = entry.indexOf('-')
                    if (firstDash >= 0 && firstDash + 1 < entry.length) entry.substring(firstDash + 1) else entry
                }
            }

            InstalledContentLists(
                dxvk = profilesToDisplay(
                    mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK),
                ),
                vkd3d = profilesToDisplay(
                    mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D),
                ),
                box64 = profilesToDisplay(
                    mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_BOX64),
                ),
                wowBox64 = profilesToDisplay(
                    mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64),
                ),
                fexcore = profilesToDisplay(
                    mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE),
                ),
                wine = profilesToDisplay(
                    mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE),
                ),
                proton = profilesToDisplay(
                    mgr.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON),
                ),
            )
        } catch (_: Exception) {
            InstalledContentLists(
                dxvk = emptyList(),
                vkd3d = emptyList(),
                box64 = emptyList(),
                wowBox64 = emptyList(),
                fexcore = emptyList(),
                wine = emptyList(),
                proton = emptyList(),
            )
        }

        InstalledContentListsAndDrivers(
            installed = installedContent,
            installedDrivers = installedDrivers,
        )
    }

    /**
     * Aggregates online releases from various GitHub repos into the manifest structure.
     */
    suspend fun fetchAllOnlineReleases(): Map<String, List<ManifestEntry>> {
        return withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, MutableList<ManifestEntry>>()
            
            // Helper to parse assets from a release object
            fun parseAssets(relObj: kotlinx.serialization.json.JsonObject, tagName: String) {
                val assets = relObj["assets"]?.jsonArray ?: return
                assets.forEach { asset ->
                    val n = asset.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                    val u = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: ""
                    if (n.endsWith(".wcp", true)) {
                        val lower = n.lowercase()
                        val type = when {
                            lower.contains("dxvk") -> ManifestContentTypes.DXVK
                            lower.contains("vk3dk") || lower.contains("vkd3d") -> ManifestContentTypes.VKD3D
                            lower.contains("wowbox64") -> ManifestContentTypes.WOWBOX64
                            lower.contains("box64") -> ManifestContentTypes.BOX64
                            lower.contains("fex") -> ManifestContentTypes.FEXCORE
                            tagName == "GameNative" || tagName == "Wine" || tagName == "wine_col" || lower.contains("wine") || lower.contains("proton") -> {
                                if (lower.contains("vkd3d")) null else ManifestContentTypes.PROTON
                            }
                            else -> null
                        }
                        if (type != null) {
                            val list = result.getOrPut(type) { mutableListOf() }
                            list.add(ManifestEntry(id = n.removeSuffix(".wcp"), name = n.removeSuffix(".wcp"), url = u, variant = "bionic"))
                        }
                    }
                }
            }

            // 1. Fetch from GameNative proton-wine
            try {
                val gnReq = okhttp3.Request.Builder().url("https://api.github.com/repos/GameNative/proton-wine/releases?per_page=100").build()
                Net.http.newCall(gnReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val releases = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                        releases.forEach { rel ->
                            parseAssets(rel.jsonObject, rel.jsonObject["tag_name"]?.jsonPrimitive?.content ?: "")
                        }
                    }
                }
            } catch (e: Exception) { Timber.e(e, "ManifestHelper: GN fetch failed") }

            // 2. Fetch from Nick's Nightly (Universal for many components)
            try {
                val nickReq = okhttp3.Request.Builder().url("https://api.github.com/repos/Xnick417x/Winlator-Bionic-Nightly-wcp/releases?per_page=100").build()
                Net.http.newCall(nickReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val releases = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                        releases.forEach { rel ->
                            parseAssets(rel.jsonObject, rel.jsonObject["tag_name"]?.jsonPrimitive?.content ?: "")
                        }
                    }
                }
            } catch (e: Exception) { Timber.e(e, "ManifestHelper: Nick fetch failed") }

            // 3. Fetch from K11MCH1 Winlator101 (wine_col tag)
            try {
                val k11Req = okhttp3.Request.Builder().url("https://api.github.com/repos/K11MCH1/Winlator101/releases/tags/wine_col").build()
                Net.http.newCall(k11Req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val rel = Json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                        parseAssets(rel, "wine_col")
                    }
                }
            } catch (e: Exception) { Timber.e(e, "ManifestHelper: K11MCH1 fetch failed") }

            // 4. Fetch from maxjivi05 Components (Drivers)
            try {
                val driverReq = okhttp3.Request.Builder().url("https://api.github.com/repos/maxjivi05/Components/contents/Drivers").build()
                Net.http.newCall(driverReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val jsonArr = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                        val list = result.getOrPut(ManifestContentTypes.DRIVER) { mutableListOf() }
                        jsonArr.forEach { el ->
                            val obj = el.jsonObject
                            val n = obj["name"]?.jsonPrimitive?.content ?: ""
                            val u = obj["download_url"]?.jsonPrimitive?.content ?: ""
                            if (n.endsWith(".zip", true)) {
                                list.add(ManifestEntry(id = n.removeSuffix(".zip"), name = n.removeSuffix(".zip"), url = u))
                            }
                        }
                    }
                }
            } catch (e: Exception) { Timber.e(e, "ManifestHelper: Driver fetch failed") }

            result.mapValues { entry -> entry.value.distinctBy { it.id } }
        }
    }

    suspend fun loadComponentAvailability(
        context: Context,
        forceRefresh: Boolean = false
    ): ComponentAvailability = withContext(Dispatchers.IO) {
        val installed = loadInstalledContentLists(context)
        var manifest = ManifestRepository.loadManifest(context) ?: ManifestData.empty()
        
        val now = System.currentTimeMillis()
        val cachedAt = PrefManager.onlineReleasesFetchedAt
        val isStale = forceRefresh || (now - cachedAt >= 24 * 60 * 60 * 1000L) // 24 hours
        
        val onlineReleases = if (!isStale) {
            val cachedJson = PrefManager.onlineReleasesJson
            try {
                if (cachedJson.isNotEmpty()) {
                    Json.decodeFromString<Map<String, List<ManifestEntry>>>(cachedJson)
                } else fetchAllOnlineReleases().also { saveOnlineReleases(it) }
            } catch (e: Exception) {
                fetchAllOnlineReleases().also { saveOnlineReleases(it) }
            }
        } else {
            fetchAllOnlineReleases().also { saveOnlineReleases(it) }
        }

        if (onlineReleases.isNotEmpty()) {
            val updatedItems = manifest.items.toMutableMap()
            onlineReleases.forEach { (type, entries) ->
                val existing = updatedItems[type].orEmpty()
                updatedItems[type] = (existing + entries).distinctBy { it.id }
            }
            manifest = manifest.copy(items = updatedItems)
        }

        ComponentAvailability(
            manifest = manifest,
            installed = installed.installed,
            installedDrivers = installed.installedDrivers,
        )
    }

    private fun saveOnlineReleases(releases: Map<String, List<ManifestEntry>>) {
        try {
            PrefManager.onlineReleasesJson = Json.encodeToString(releases)
            PrefManager.onlineReleasesFetchedAt = System.currentTimeMillis()
        } catch (e: Exception) {
            Timber.e(e, "ManifestHelper: save failed")
        }
    }

    fun buildAvailableVersions(
        base: List<String>,
        installed: List<String>,
        manifest: List<ManifestEntry> = emptyList(),
    ): List<String> {
        val combined = (base + installed + manifest.map { it.id }).distinct()
        return sortVersionStrings(combined)
    }

    /**
     * Sorts version strings from newest to oldest.
     */
    fun sortVersionStrings(versions: List<String>): List<String> {
        return versions.sortedWith { v1, v2 ->
            compareVersions(v2, v1) // Sort descending (newest first)
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        
        val length = minOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            if (parts1[i] != parts2[i]) return parts1[i].compareTo(parts2[i])
        }
        return parts1.size.compareTo(parts2.size)
    }

    fun buildVersionOptionList(
        baseVersions: List<String>,
        installedVersions: List<String>,
        manifestEntries: List<ManifestEntry>,
        isDriver: Boolean = false
    ): VersionOptionList {
        val labels = mutableListOf<String>()
        val ids = mutableListOf<String>()
        val muted = mutableListOf<Boolean>()

        // Group 1: Installed (Base + Already installed)
        val installedGroup = (baseVersions + installedVersions).distinct()
        
        // Group 2: Not installed (In manifest but not in installedGroup)
        val manifestNotInstalled = manifestEntries.filter { entry -> 
            !installedGroup.any { it.equals(entry.id, true) }
        }

        // Sorting logic for each group
        val sortedInstalled = if (isDriver) {
            val mtr = installedGroup.filter { it.contains("MTR", true) }.sortedWith { a, b -> compareVersions(b, a) }
            val gn = installedGroup.filter { !it.contains("MTR", true) }.sortedWith { a, b -> compareVersions(b, a) }
            mtr + gn
        } else {
            installedGroup.sortedWith { a, b -> compareVersions(b, a) }
        }

        val sortedManifest = if (isDriver) {
            val mtr = manifestNotInstalled.filter { it.id.contains("MTR", true) }.sortedWith { a, b -> compareVersions(b.id, a.id) }
            val gn = manifestNotInstalled.filter { !it.id.contains("MTR", true) }.sortedWith { a, b -> compareVersions(b.id, a.id) }
            mtr + gn
        } else {
            manifestNotInstalled.sortedWith { a, b -> compareVersions(b.id, a.id) }
        }

        // Populate the final list: Installed first
        sortedInstalled.forEach { id ->
            ids.add(id)
            labels.add(id)
            muted.add(false)
        }

        // Then available to download
        sortedManifest.forEach { entry ->
            ids.add(entry.id)
            labels.add(entry.name + " (Download)")
            muted.add(true)
        }

        return VersionOptionList(labels, ids, muted)
    }

    data class DxvkContext(
        val labels: List<String>,
        val ids: List<String>,
        val muted: List<Boolean>,
        val isVortekLike: Boolean,
    )

    fun buildDxvkContext(
        containerVariant: String,
        graphicsDrivers: List<String>,
        graphicsDriverIndex: Int,
        dxWrappers: List<String>,
        dxWrapperIndex: Int,
        inspectionMode: Boolean,
        isBionicVariant: Boolean,
        dxvkVersionsBase: List<String>,
        dxvkOptions: VersionOptionList,
    ): DxvkContext {
        val driverType = if (graphicsDriverIndex in graphicsDrivers.indices) {
            StringUtils.parseIdentifier(graphicsDrivers[graphicsDriverIndex])
        } else ""
        
        val isVortekLike = (containerVariant.equals(Container.GLIBC, true) && 
            (driverType == "vortek" || driverType == "adreno" || driverType == "sd-8-elite"))

        val options = if (isBionicVariant) dxvkOptions else {
            val sortedBase = dxvkVersionsBase.sortedWith { a, b -> compareVersions(b, a) }
            VersionOptionList(sortedBase, sortedBase, List(sortedBase.size) { false })
        }

        val finalIds = mutableListOf<String>()
        val finalLabels = mutableListOf<String>()
        val finalMuted = mutableListOf<Boolean>()

        finalIds.add("Disabled")
        finalLabels.add("Disabled")
        finalMuted.add(false)

        finalIds.addAll(options.ids)
        finalLabels.addAll(options.labels)
        finalMuted.addAll(options.muted)

        return DxvkContext(finalLabels, finalIds, finalMuted, isVortekLike)
    }

    fun findManifestEntryForVersion(
        version: String,
        entries: List<ManifestEntry>,
    ): ManifestEntry? {
        val normalized = version.trim()
        if (normalized.isEmpty()) return null
        return entries.firstOrNull { entry ->
            normalized.equals(entry.id, ignoreCase=true)
        }
    }

    fun versionExists(
        version: String,
        base: List<String>,
        installed: List<String> = emptyList(),
    ): Boolean {
        val normalized = version.trim()
        if (normalized.isEmpty()) return false
        return base.any { it.equals(normalized, ignoreCase = true) } ||
                installed.any { it.equals(normalized, ignoreCase = true) }
    }
}
