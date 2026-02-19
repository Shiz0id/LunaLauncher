package com.lunasysman.launcher.core.justtype.providers

import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.model.JustTypeNotificationsOptions
import com.lunasysman.launcher.core.justtype.notifications.NotificationIndexer

/**
 * Just Type provider for notifications (live and historical).
 *
 * Surfaces notifications as first-class search results with inline actions.
 * This is the core integration between NotificationIndexer and Just Type.
 *
 * Priority model:
 * - LIVE: Active notifications with actionable verbs (Reply, Mark as Read, etc.)
 * - HISTORICAL: Dismissed notifications (within retention period) - searchable but no actions
 *
 * Search behavior:
 * - Matches on: notification title, text, extracted names
 * - Results sorted by recency (newest first), live notifications prioritized
 * - Maximum 5 results to avoid overwhelming the UI
 *
 * UI differentiation:
 * - Live notifications: Show "LIVE" tag, actions available
 * - Historical notifications: No tag, no actions
 *
 * Usage:
 * ```kotlin
 * val indexer = NotificationIndexer()
 * val items = NotificationsProvider.itemsFor(
 *     query = "alex",
 *     indexer = indexer,
 * )
 * ```
 */
object NotificationsProvider {

    /**
     * Searches notifications and returns matching Just Type items.
     *
     * @param query User search query (trimmed internally)
     * @param indexer Notification indexer instance
     * @param options Notification result options
     * @param showAllIfBlank If true, returns all notifications when query is blank
     *                       (used for @Notifications category filter)
     * @return List of notification items, sorted by recency (live first)
     */
    fun itemsFor(
        query: String,
        indexer: NotificationIndexer,
        options: JustTypeNotificationsOptions = JustTypeNotificationsOptions(),
        showAllIfBlank: Boolean = false,
    ): List<JustTypeItemUi.NotificationItem> {
        // Don't show notifications for empty queries unless explicitly requested
        // (too much noise in normal search, but needed for @Notifications filter)
        if (query.isBlank() && !showAllIfBlank) return emptyList()

        val surfaces =
            if (query.isBlank()) {
                // showAllIfBlank is true here (otherwise we returned early)
                indexer.getAll()
            } else {
                indexer.search(
                    query = query,
                    maxResults = options.maxResults.coerceAtLeast(1),
                    matchText = options.matchText,
                    matchNames = options.matchNames,
                )
            }

        return surfaces.map { surface ->
            JustTypeItemUi.NotificationItem(
                notificationKey = surface.key,
                title = surface.title,
                subtitle = surface.text,
                actions =
                    // Only show actions for live notifications
                    if (!options.showActions || !surface.isLive) {
                        emptyList()
                    } else {
                        surface.actions.mapIndexed { idx, action ->
                            JustTypeItemUi.NotificationActionUi(
                                index = idx,
                                title = action.title,
                                requiresText = action.remoteInput != null,
                            )
                        }
                    },
                timestamp = surface.timestamp,
                isLive = surface.isLive,
            )
        }
    }

    /**
     * Gets a single notification by key.
     *
     * Used for action execution: verify notification still exists before
     * attempting to send PendingIntent.
     *
     * @param notificationKey The notification key
     * @param indexer Notification indexer instance
     * @return The notification item, or null if not found
     */
    fun getByKey(
        notificationKey: String,
        indexer: NotificationIndexer,
    ): JustTypeItemUi.NotificationItem? {
        val surface = indexer.get(notificationKey) ?: return null

        return JustTypeItemUi.NotificationItem(
            notificationKey = surface.key,
            title = surface.title,
            subtitle = surface.text,
            actions =
                // Only show actions for live notifications
                if (!surface.isLive) {
                    emptyList()
                } else {
                    surface.actions.mapIndexed { idx, action ->
                        JustTypeItemUi.NotificationActionUi(
                            index = idx,
                            title = action.title,
                            requiresText = action.remoteInput != null,
                        )
                    }
                },
            timestamp = surface.timestamp,
            isLive = surface.isLive,
        )
    }

    /**
     * Gets notifications from specific packages.
     *
     * Used to show related notifications when searching for apps.
     * For example: searching "Snapchat" shows the app AND recent Snapchat notifications.
     *
     * @param packageNames Set of package names to find notifications for
     * @param indexer Notification indexer instance
     * @param options Notification result options
     * @return List of notification items from matching packages, sorted by recency
     */
    fun itemsForPackages(
        packageNames: Set<String>,
        indexer: NotificationIndexer,
        options: JustTypeNotificationsOptions = JustTypeNotificationsOptions(),
    ): List<JustTypeItemUi.NotificationItem> {
        if (packageNames.isEmpty()) return emptyList()

        val surfaces = indexer.getByPackages(packageNames, options.maxResults.coerceAtLeast(1))

        return surfaces.map { surface ->
            JustTypeItemUi.NotificationItem(
                notificationKey = surface.key,
                title = surface.title,
                subtitle = surface.text,
                actions =
                    // Only show actions for live notifications
                    if (!options.showActions || !surface.isLive) {
                        emptyList()
                    } else {
                        surface.actions.mapIndexed { idx, action ->
                            JustTypeItemUi.NotificationActionUi(
                                index = idx,
                                title = action.title,
                                requiresText = action.remoteInput != null,
                            )
                        }
                    },
                timestamp = surface.timestamp,
                isLive = surface.isLive,
            )
        }
    }
}
