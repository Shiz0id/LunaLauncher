package com.lunasysman.launcher.core.justtype.model

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.drawable.Icon

/**
 * Represents a notification as a searchable action surface.
 *
 * Notifications can be either "live" (currently active in the status bar) or
 * "historical" (dismissed but retained for search). Historical notifications
 * are kept for a configurable retention period (default 4 days) before being
 * flushed from the index.
 *
 * Philosophy: Notifications are temporary verb surfaces, not just alerts.
 * - Noun: "Alex sent a message"
 * - Verbs: [Reply, Mark as Read, Open] (only available when live)
 *
 * @property key Unique notification key (StatusBarNotification.key)
 * @property packageName Source application package
 * @property title Primary notification title (extracted)
 * @property text Secondary notification text (extracted)
 * @property names Extracted person names for search matching (senders, contacts, etc.)
 * @property contentIntent Main action (tap to open) - only valid when live
 * @property actions Available notification actions (Reply, Archive, etc.) - only valid when live
 * @property timestamp When the notification was posted (System.currentTimeMillis)
 * @property groupKey Notification group identifier (for conversation threads)
 * @property isLive True if notification is still active in status bar, false if dismissed
 * @property dismissedAt When the notification was dismissed (null if still live)
 */
data class NotificationActionSurface(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String?,
    val names: List<String>,
    val contentIntent: PendingIntent?,
    val actions: List<NotificationAction>,
    val timestamp: Long,
    val groupKey: String?,
    val isLive: Boolean = true,
    val dismissedAt: Long? = null,
)

/**
 * Represents a single actionable verb from a notification.
 *
 * @property title Action label shown to user ("Reply", "Mark as read", "Archive")
 * @property intent PendingIntent to execute this action
 * @property remoteInput RemoteInput configuration for inline text entry (e.g., message replies)
 * @property icon Optional icon for the action
 */
data class NotificationAction(
    val title: String,
    val intent: PendingIntent,
    val remoteInput: RemoteInput?,
    val icon: Icon?,
)
