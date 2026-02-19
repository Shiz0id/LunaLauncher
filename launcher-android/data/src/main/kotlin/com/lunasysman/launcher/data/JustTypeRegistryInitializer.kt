package com.lunasysman.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first

class JustTypeRegistryInitializer(
    private val context: Context,
    private val dao: JustTypeProviderDao,
    private val prefs: JustTypePrefs,
) {
    suspend fun initializeFromAssets(
        defaultsAssetName: String = "justtype_defaults.json",
        custAssetName: String = "justtype_cust.json",
    ) {
        val existing = dao.getAll().associateBy { it.id }
        val defaults = loadDefaults(defaultsAssetName)

        val merged = defaults.map { def ->
            val current = existing[def.id]
            if (current == null) {
                def.copy(source = "default")
            } else if (def.version > current.version) {
                def.copy(
                    enabled = current.enabled,
                    source = current.source,
                )
            } else {
                current
            }
        }

        val mergedById = merged.associateBy { it.id }.toMutableMap()
        val mergedItems = mergedById.values.toMutableList()
        dao.upsertAll(mergedItems)

        applyCustPatchIfPresent(custAssetName)

        ensureDefaultSearchProvider()
    }

    private suspend fun applyCustPatchIfPresent(assetName: String) {
        val json = readAssetOrNull(assetName) ?: return
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.getString("id")
            if (obj.optBoolean("remove", false)) {
                dao.deleteById(id)
                continue
            }
            val enabled = obj.opt("enabled")
            if (enabled is Boolean) {
                dao.setEnabled(id, enabled)
            }
            val order = obj.opt("order")
            if (order is Number) {
                dao.setOrderIndex(id, order.toInt())
            }
        }
    }

    private suspend fun ensureDefaultSearchProvider() {
        val providers = dao.getAll()
        val enabledSearch = providers.filter { it.category == "search" && it.enabled }.sortedWith(compareBy<JustTypeProviderEntity> { it.orderIndex }.thenBy { it.id })
        if (enabledSearch.isEmpty()) return

        val currentDefault = prefs.defaultSearchProviderId.first()

        val ok = currentDefault != null && enabledSearch.any { it.id == currentDefault }
        if (!ok) {
            prefs.setDefaultSearchProviderId(enabledSearch.first().id)
        }
    }

    private fun loadDefaults(assetName: String): List<JustTypeProviderEntity> {
        val json = requireNotNull(readAssetOrNull(assetName)) { "Missing asset: $assetName" }
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(obj.toProviderEntity())
            }
        }
    }

    private fun JSONObject.toProviderEntity(): JustTypeProviderEntity =
        JustTypeProviderEntity(
            id = getString("id"),
            category = getString("category"),
            displayName = optString("displayName").ifBlank { null },
            enabled = optBoolean("enabled", true),
            orderIndex = optInt("orderIndex", 0),
            version = optInt("version", 1),
            source = optString("source", "default"),
            urlTemplate = optString("urlTemplate").ifBlank { null },
            suggestUrlTemplate = optString("suggestUrlTemplate").ifBlank { null },
            canPromotePrimaryResult = optBoolean("canPromotePrimaryResult", false),
        )

    private fun readAssetOrNull(name: String): String? =
        try {
            context.assets.open(name).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
}
