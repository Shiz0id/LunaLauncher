package com.lunasysman.launcher.data

import android.content.Context
import com.lunasysman.launcher.core.justtype.model.JustTypeCategory
import com.lunasysman.launcher.core.justtype.model.JustTypeNotificationsOptions
import com.lunasysman.launcher.core.justtype.registry.JustTypeProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JustTypeRegistry(
    private val context: Context,
    private val dao: JustTypeProviderDao,
    private val prefs: JustTypePrefs,
) {
    val providers: Flow<List<JustTypeProviderConfig>> =
        dao.observeAllOrdered().map { entities ->
            entities.mapNotNull { it.toConfigOrNull() }
        }

    val defaultSearchProviderId: Flow<String?> = prefs.defaultSearchProviderId

    val notificationsOptions: Flow<JustTypeNotificationsOptions> = prefs.notificationsOptions

    suspend fun initialize() {
        JustTypeRegistryInitializer(
            context = context,
            dao = dao,
            prefs = prefs,
        ).initializeFromAssets()
    }

    suspend fun setProviderEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    suspend fun setProviderOrderIndex(id: String, orderIndex: Int) {
        dao.setOrderIndex(id, orderIndex)
    }

    suspend fun setDefaultSearchProviderId(id: String) {
        prefs.setDefaultSearchProviderId(id)
    }

    suspend fun setNotificationsMaxResults(value: Int) {
        prefs.setNotificationsMaxResults(value)
    }

    suspend fun setNotificationsMatchText(enabled: Boolean) {
        prefs.setNotificationsMatchText(enabled)
    }

    suspend fun setNotificationsMatchNames(enabled: Boolean) {
        prefs.setNotificationsMatchNames(enabled)
    }

    suspend fun setNotificationsShowActions(enabled: Boolean) {
        prefs.setNotificationsShowActions(enabled)
    }

    private fun JustTypeProviderEntity.toConfigOrNull(): JustTypeProviderConfig? {
        val cat =
            when (category.lowercase()) {
                "apps" -> JustTypeCategory.APPS
                "notifications" -> JustTypeCategory.NOTIFICATIONS
                "action" -> JustTypeCategory.ACTION
                "dbsearch" -> JustTypeCategory.DBSEARCH
                "search" -> JustTypeCategory.SEARCH
                else -> return null
            }

        return JustTypeProviderConfig(
            id = id,
            category = cat,
            displayName = displayName,
            enabled = enabled,
            orderIndex = orderIndex,
            version = version,
            source = source,
            urlTemplate = urlTemplate,
            suggestUrlTemplate = suggestUrlTemplate,
            canPromotePrimaryResult = canPromotePrimaryResult,
        )
    }
}
