package com.lunasysman.launcher.core.justtype.model

sealed interface JustTypeItemUi {
    data class NotificationActionUi(
        val index: Int,
        val title: String,
        val requiresText: Boolean,
    )

    data class LaunchPointItem(
        val lpId: String,
    ) : JustTypeItemUi

    data class ActionItem(
        val actionId: String,
        val title: String,
        val subtitle: String? = null,
        val argument: String? = null,
    ) : JustTypeItemUi

    data class DbRowItem(
        val providerId: String,
        val stableId: String,
        val title: String,
        val subtitle: String?,
    ) : JustTypeItemUi

    data class SearchTemplateItem(
        val providerId: String,
        val title: String,
        val query: String,
    ) : JustTypeItemUi

    /**
     * Notification search result with optional actionable verbs.
     *
     * Represents a notification as a first-class search result. Live notifications
     * have inline actions (Reply, Mark as Read, etc.) while historical notifications
     * (dismissed within the retention period) appear without actions.
     *
     * UI indicators:
     * - Live notifications: Show "LIVE" tag, actions available
     * - Historical notifications: No tag or "OLD" tag, no actions
     *
     * @property notificationKey Unique notification identifier (sbn.key)
     * @property title Notification title (e.g., "Alex")
     * @property subtitle Notification text (e.g., "Hey, are you free tonight?")
     * @property actions Available actions (Reply, Mark as Read, Open, etc.) - empty for historical
     * @property timestamp When notification was posted
     * @property isLive True if notification is still active, false if dismissed but retained
     */
    data class NotificationItem(
        val notificationKey: String,
        val title: String,
        val subtitle: String?,
        val actions: List<NotificationActionUi>,
        val timestamp: Long,
        val isLive: Boolean = true,
    ) : JustTypeItemUi
}

fun JustTypeItemUi.stableKey(): String =
    when (this) {
        is JustTypeItemUi.LaunchPointItem -> "lp:$lpId"
        is JustTypeItemUi.ActionItem -> if (argument.isNullOrBlank()) "action:$actionId" else "action:$actionId:$argument"
        is JustTypeItemUi.DbRowItem -> "db:$providerId:$stableId"
        is JustTypeItemUi.SearchTemplateItem -> "search:$providerId:$query"
        is JustTypeItemUi.NotificationItem -> "notification:$notificationKey"
    }
