package com.lunasysman.launcher.core.justtype.engine

import com.lunasysman.launcher.core.justtype.model.JustTypeCategory
import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.model.JustTypeNotificationsOptions
import com.lunasysman.launcher.core.justtype.model.JustTypeSectionUi
import com.lunasysman.launcher.core.justtype.model.JustTypeUiState
import com.lunasysman.launcher.core.justtype.providers.ActionsProvider
import com.lunasysman.launcher.core.justtype.providers.AppsProvider
import com.lunasysman.launcher.core.justtype.providers.NotificationsProvider
import com.lunasysman.launcher.core.justtype.notifications.NotificationIndexer
import com.lunasysman.launcher.core.justtype.registry.JustTypeProviderConfig
import com.lunasysman.launcher.core.model.LaunchPoint

object JustTypeEngine {

    /**
     * Extracts the Android package name from a LaunchPoint ID.
     * ID format: "android:{packageName}/{activityName}@..." or "android:{packageName}/{activityName}"
     * Returns null for non-Android IDs or malformed IDs.
     */
    private fun extractPackageName(launchPointId: String): String? {
        val base = launchPointId.substringBefore("@")
        if (!base.startsWith("android:")) return null
        val remainder = base.removePrefix("android:")
        val pkg = remainder.substringBefore("/").trim()
        return pkg.ifEmpty { null }
    }
    fun buildState(
        query: String,
        allApps: List<LaunchPoint>,
        favorites: List<LaunchPoint>,
        providers: List<JustTypeProviderConfig>,
        defaultSearchProviderId: String?,
        contactsItems: List<JustTypeItemUi>,
        notificationIndexer: NotificationIndexer?,
        notificationsOptions: JustTypeNotificationsOptions = JustTypeNotificationsOptions(),
        nowEpochMs: Long,
    ): JustTypeUiState {
        // Parse @Category filter (e.g., "@Apps chrome" -> filter to APPS, query "chrome")
        val (categoryFilter, effectiveQuery) = JustTypeCategory.parseCategoryFilter(query)
        val q = effectiveQuery.trim()
        val enabled = providers.filter { it.enabled }

        // Helper to check if a category should be shown
        fun shouldShowCategory(category: JustTypeCategory): Boolean =
            categoryFilter == null || categoryFilter == category

        val actionProviders =
            enabled
                .filter { it.category == JustTypeCategory.ACTION }
                .sortedWith(compareBy<JustTypeProviderConfig> { it.orderIndex }.thenBy { it.id })

        val actions =
            if (!shouldShowCategory(JustTypeCategory.ACTION)) {
                emptyList()
            } else {
                actionProviders.mapNotNull { cfg ->
                    ActionsProvider.defaultActionInfoFor(
                        id = cfg.id,
                        titleOverride = cfg.displayName,
                    )
                }.let { enabledActions ->
                    ActionsProvider.itemsFor(query = q, enabledActionsInOrder = enabledActions)
                }
            }

        val appsEnabled = enabled.any { it.category == JustTypeCategory.APPS && it.id == "apps" } &&
            shouldShowCategory(JustTypeCategory.APPS)

        val appsItems =
            if (!appsEnabled) {
                emptyList()
            } else {
                AppsProvider.itemsFor(
                    query = q,
                    allApps = allApps,
                    favorites = favorites,
                    nowEpochMs = nowEpochMs,
                    maxItems = if (q.isEmpty()) 12 else 50,
                )
            }

        // Related notifications: when searching for apps, also show notifications from those apps.
        // E.g., searching "Snapchat" shows Snapchat app AND recent Snapchat notifications.
        val relatedNotificationsEnabled = enabled.any { it.category == JustTypeCategory.NOTIFICATIONS && it.id == "notifications" }
        val relatedNotificationItems: List<JustTypeItemUi.NotificationItem> =
            if (!relatedNotificationsEnabled || notificationIndexer == null || q.isBlank() || appsItems.isEmpty()) {
                emptyList()
            } else {
                // Extract package names from matched app IDs
                val matchedPackages = appsItems
                    .mapNotNull { item -> extractPackageName(item.lpId) }
                    .toSet()

                if (matchedPackages.isEmpty()) {
                    emptyList()
                } else {
                    NotificationsProvider.itemsForPackages(
                        packageNames = matchedPackages,
                        indexer = notificationIndexer,
                        options = notificationsOptions,
                    )
                }
            }

        // Notifications (live action surfaces)
        // Query-based notification search is disabled - notifications only appear as "related notifications"
        // when an app is matched (e.g., searching "Edge" shows Edge app + Edge notifications).
        // This prevents confusing results where searching "Edge" would match any notification
        // containing the word "Edge" in its text, regardless of which app sent it.
        // The @Notifications filter still shows all notifications when active with no query.
        val notificationsEnabled = enabled.any { it.category == JustTypeCategory.NOTIFICATIONS && it.id == "notifications" } &&
            shouldShowCategory(JustTypeCategory.NOTIFICATIONS)
        val notificationItems =
            if (!notificationsEnabled || notificationIndexer == null) {
                emptyList()
            } else {
                // Exclude related notifications (already shown above apps) to avoid duplicates
                val relatedKeys = relatedNotificationItems.map { it.notificationKey }.toSet()
                // Only show notifications when @Notifications filter is active (show all)
                // Don't do query-based text search for notifications - use related notifications instead
                val isNotificationsFilterActive = categoryFilter == JustTypeCategory.NOTIFICATIONS
                if (isNotificationsFilterActive) {
                    NotificationsProvider.itemsFor(
                        query = q,
                        indexer = notificationIndexer,
                        options = notificationsOptions,
                        showAllIfBlank = true,
                    ).filterNot { it.notificationKey in relatedKeys }
                } else {
                    // No query-based notification search - rely on related notifications only
                    emptyList()
                }
            }

        val contactsEnabled = enabled.any { it.category == JustTypeCategory.DBSEARCH && it.id == "contacts" } &&
            shouldShowCategory(JustTypeCategory.DBSEARCH)
        val contactsSectionItems = if (contactsEnabled) contactsItems else emptyList()
        val contactMatches =
            if (!contactsEnabled) {
                emptyList()
            } else {
                contactsSectionItems.filterIsInstance<JustTypeItemUi.DbRowItem>()
            }

        val searchProviders =
            if (!shouldShowCategory(JustTypeCategory.SEARCH)) {
                emptyList()
            } else {
                enabled
                    .filter { it.category == JustTypeCategory.SEARCH && !it.urlTemplate.isNullOrBlank() }
                    .sortedWith(compareBy<JustTypeProviderConfig> { it.orderIndex }.thenBy { it.id })
            }

        val defaultSearchCfg = defaultSearchProviderId?.let { id -> searchProviders.firstOrNull { it.id == id } }
        val promotedSearchCfg = defaultSearchCfg?.takeIf { it.canPromotePrimaryResult }
        val promotedPrimaryItem =
            if (q.isBlank() || promotedSearchCfg == null) {
                null
            } else {
                JustTypeItemUi.SearchTemplateItem(
                    providerId = promotedSearchCfg.id,
                    title = promotedSearchCfg.displayName ?: promotedSearchCfg.id,
                    query = q,
                )
            }

        val otherSearchProviders =
            if (defaultSearchProviderId.isNullOrBlank()) {
                searchProviders.filterNot { it.id == promotedSearchCfg?.id }
            } else {
                val preferred = searchProviders.firstOrNull { it.id == defaultSearchProviderId }
                val rest = searchProviders.filterNot { it.id == promotedSearchCfg?.id || it.id == defaultSearchProviderId }
                if (preferred == null || preferred.id == promotedSearchCfg?.id) rest else listOf(preferred) + rest
            }
        val otherSearchItems =
            if (q.isBlank()) {
                emptyList()
            } else {
                otherSearchProviders.map { cfg ->
                    JustTypeItemUi.SearchTemplateItem(
                        providerId = cfg.id,
                        title = cfg.displayName ?: cfg.id,
                        query = q,
                    )
                }
            }

        // Contact-based smart actions: when exactly one contact matches with a phone number,
        // offer Call and Text actions for that contact
        val callContactAction: JustTypeItemUi.ActionItem?
        val textContactAction: JustTypeItemUi.ActionItem?
        if (q.isBlank()) {
            callContactAction = null
            textContactAction = null
        } else {
            val callable = contactMatches.filter { !it.subtitle.isNullOrBlank() }
            if (callable.size == 1) {
                val c = callable.single()
                callContactAction = JustTypeItemUi.ActionItem(
                    actionId = "call",
                    title = "Call ${c.title}",
                    subtitle = c.subtitle,
                    argument = c.subtitle,
                )
                textContactAction = JustTypeItemUi.ActionItem(
                    actionId = "text",
                    title = "Text ${c.title}",
                    subtitle = c.subtitle,
                    argument = c.subtitle,
                )
            } else {
                callContactAction = null
                textContactAction = null
            }
        }

        val searchWebFallbackAction: JustTypeItemUi.ActionItem? =
            if (q.isBlank()) {
                null
            } else {
                val appsCount = if (appsEnabled) appsItems.size else Int.MAX_VALUE
                val contactsCount = if (contactsEnabled) contactMatches.size else Int.MAX_VALUE
                val showFallback = appsCount <= 3 || contactsCount <= 3
                if (showFallback) {
                    JustTypeItemUi.ActionItem(
                        actionId = "search_web",
                        title = "Search Web for \"$q\"",
                        subtitle = null,
                        argument = q,
                    )
                } else {
                    null
                }
            }

        // Contextual actions only appear when not in category filter mode
        // (they are derived from other categories and would be confusing in filtered view)
        val contextualActions =
            if (categoryFilter != null) {
                emptyList()
            } else {
                buildList {
                    if (callContactAction != null) add(callContactAction)
                    if (textContactAction != null) add(textContactAction)
                    if (searchWebFallbackAction != null) add(searchWebFallbackAction)
                }
            }

        val sections = buildList {
            if (appsItems.isNotEmpty()) {
                add(
                    JustTypeSectionUi(
                        providerId = "apps",
                        title = "Apps",
                        category = JustTypeCategory.APPS,
                        items = appsItems,
                    ),
                )
            }
            // Related notifications appear immediately after apps (same-app priority)
            // E.g., searching "Snapchat" shows Snapchat notifications right below the app
            if (relatedNotificationItems.isNotEmpty()) {
                add(
                    JustTypeSectionUi(
                        providerId = "related_notifications",
                        title = "", // No header - appears as continuation of app results
                        category = JustTypeCategory.NOTIFICATIONS,
                        items = relatedNotificationItems,
                    ),
                )
            }
            // Other notifications (query-matched but not from matched apps)
            if (notificationItems.isNotEmpty()) {
                add(
                    JustTypeSectionUi(
                        providerId = "notifications",
                        title = "NOTIFICATIONS",
                        category = JustTypeCategory.NOTIFICATIONS,
                        items = notificationItems,
                    ),
                )
            }
            if (contextualActions.isNotEmpty()) {
                add(
                    JustTypeSectionUi(
                        providerId = "context_actions",
                        title = "SMART ACTIONS",
                        category = JustTypeCategory.ACTION,
                        items = contextualActions,
                    ),
                )
            }
            if (contactsSectionItems.isNotEmpty()) {
                add(
                    JustTypeSectionUi(
                        providerId = "contacts",
                        title = "CONTACTS",
                        category = JustTypeCategory.DBSEARCH,
                        items = contactsSectionItems,
                    ),
                )
            }
            if (promotedPrimaryItem != null) {
                add(
                    JustTypeSectionUi(
                        providerId = "primary_search",
                        title = "",
                        category = JustTypeCategory.SEARCH,
                        items = listOf(promotedPrimaryItem),
                    ),
                )
            }
            if (otherSearchItems.isNotEmpty()) {
                add(
                    JustTypeSectionUi(
                        providerId = "more_searches",
                        title = "MORE SEARCHES",
                        category = JustTypeCategory.SEARCH,
                        items = otherSearchItems,
                    ),
                )
            }
            if (actions.isNotEmpty()) {
                add(
                    JustTypeSectionUi(
                        providerId = "quick_actions",
                        title = "QUICK ACTIONS",
                        category = JustTypeCategory.ACTION,
                        items = actions,
                    ),
                )
            }
        }

        return JustTypeUiState(
            query = query,
            sections = sections,
            categoryFilter = categoryFilter,
        )
    }
}
