package com.lunasysman.launcher.core.justtype.notifications

import com.lunasysman.launcher.core.justtype.model.NotificationActionSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Index of notifications as searchable action surfaces.
 *
 * Maintains both live (active) and historical (dismissed) notifications.
 * Historical notifications are retained for a configurable period (default 4 days)
 * to allow users to search through recent notification history.
 *
 * Fed by NotificationListenerService, consumed by Just Type search providers.
 *
 * Philosophy:
 * - Live notifications have actionable verbs (Reply, Mark as Read, etc.)
 * - Historical notifications are searchable but have no actions
 * - UI differentiates with "LIVE" tag for active notifications
 *
 * Thread-safety: Uses ConcurrentHashMap for thread-safe operations.
 */
class NotificationIndexer {
    private val _notifications = ConcurrentHashMap<String, NotificationActionSurface>()
    private val _indexVersion = MutableStateFlow(0)

    /**
     * Flow that increments whenever the index changes.
     * Just Type providers can observe this to trigger recomposition.
     */
    val indexVersion: StateFlow<Int> = _indexVersion.asStateFlow()

    companion object {
        /** Default retention period for historical notifications (4 days in milliseconds) */
        const val DEFAULT_RETENTION_MS = 4L * 24L * 60L * 60L * 1000L
    }

    /**
     * Adds or updates a notification in the index as live.
     *
     * @param surface The notification action surface to index
     */
    fun put(surface: NotificationActionSurface) {
        // Ensure the notification is marked as live
        val liveSurface = if (surface.isLive) surface else surface.copy(isLive = true, dismissedAt = null)
        _notifications[surface.key] = liveSurface
        _indexVersion.value++
    }

    /**
     * Marks a notification as dismissed (historical) instead of removing it.
     *
     * The notification remains searchable but loses its actions and is marked
     * as non-live. It will be automatically flushed after the retention period.
     *
     * @param key The notification key (StatusBarNotification.key)
     */
    fun markDismissed(key: String) {
        val existing = _notifications[key] ?: return
        if (!existing.isLive) return // Already dismissed

        // Convert to historical: clear actions/intents, mark as dismissed
        val historical = existing.copy(
            isLive = false,
            dismissedAt = System.currentTimeMillis(),
            contentIntent = null,
            actions = emptyList(),
        )
        _notifications[key] = historical
        _indexVersion.value++
    }

    /**
     * Removes a notification completely from the index.
     *
     * Use [markDismissed] instead to retain for historical search.
     * This method is for cases where the notification should be fully purged.
     *
     * @param key The notification key (StatusBarNotification.key)
     */
    fun remove(key: String) {
        if (_notifications.remove(key) != null) {
            _indexVersion.value++
        }
    }

    /**
     * Marks all notifications from a specific package as dismissed.
     *
     * @param packageName The package to mark as dismissed
     */
    fun markDismissedByPackage(packageName: String) {
        val toUpdate = _notifications.values
            .filter { it.packageName == packageName && it.isLive }

        if (toUpdate.isEmpty()) return

        val now = System.currentTimeMillis()
        toUpdate.forEach { surface ->
            val historical = surface.copy(
                isLive = false,
                dismissedAt = now,
                contentIntent = null,
                actions = emptyList(),
            )
            _notifications[surface.key] = historical
        }
        _indexVersion.value++
    }

    /**
     * Removes all notifications from a specific package completely.
     *
     * @param packageName The package to clear
     */
    fun removeByPackage(packageName: String) {
        val keysToRemove = _notifications.values
            .filter { surface -> surface.packageName == packageName }
            .map { surface -> surface.key }

        if (keysToRemove.isNotEmpty()) {
            keysToRemove.forEach { key -> _notifications.remove(key) }
            _indexVersion.value++
        }
    }

    /**
     * Flushes historical notifications older than the retention period.
     *
     * Should be called periodically (e.g., on app start, daily) to prevent
     * unbounded growth of the notification index.
     *
     * @param retentionMs Retention period in milliseconds (default 4 days)
     * @return Number of notifications flushed
     */
    fun flushOldHistorical(retentionMs: Long = DEFAULT_RETENTION_MS): Int {
        val cutoff = System.currentTimeMillis() - retentionMs
        val toRemove = _notifications.values
            .filter { surface ->
                !surface.isLive && (surface.dismissedAt ?: 0L) < cutoff
            }
            .map { it.key }

        if (toRemove.isEmpty()) return 0

        toRemove.forEach { key -> _notifications.remove(key) }
        _indexVersion.value++
        return toRemove.size
    }

    /**
     * Clears all notifications from the index.
     */
    fun clear() {
        if (_notifications.isNotEmpty()) {
            _notifications.clear()
            _indexVersion.value++
        }
    }

    /**
     * Retrieves a notification surface by key.
     *
     * @param key The notification key
     * @return The notification surface, or null if not found
     */
    fun get(key: String): NotificationActionSurface? = _notifications[key]

    /**
     * Retrieves a live notification surface by key.
     *
     * @param key The notification key
     * @return The notification surface if live, or null if not found or dismissed
     */
    fun getLive(key: String): NotificationActionSurface? =
        _notifications[key]?.takeIf { it.isLive }

    /**
     * Searches notifications by query string.
     *
     * Searches both live and historical notifications.
     * Results are sorted by recency (newest first), with live notifications
     * prioritized over historical ones with the same timestamp.
     *
     * @param query Search query (trimmed, lowercased internally)
     * @param maxResults Maximum number of results to return
     * @param matchText Whether to match against notification text (EXTRA_TEXT)
     * @param matchNames Whether to match against extracted person names
     * @param includeLive Whether to include live notifications (default true)
     * @param includeHistorical Whether to include historical notifications (default true)
     * @return List of matching notification surfaces, sorted by timestamp descending
     */
    fun search(
        query: String,
        maxResults: Int = 10,
        matchText: Boolean = true,
        matchNames: Boolean = true,
        includeLive: Boolean = true,
        includeHistorical: Boolean = true,
    ): List<NotificationActionSurface> {
        if (query.isBlank()) return emptyList()

        val lowerQuery = query.trim().lowercase()

        return _notifications.values
            .filter { surface ->
                // Filter by live/historical status
                (includeLive && surface.isLive) || (includeHistorical && !surface.isLive)
            }
            .filter { surface ->
                surface.title.lowercase().contains(lowerQuery) ||
                    (matchText && surface.text?.lowercase()?.contains(lowerQuery) == true) ||
                    (matchNames && surface.names.any { name -> name.lowercase().contains(lowerQuery) })
            }
            .sortedWith(
                compareByDescending<NotificationActionSurface> { it.timestamp }
                    .thenByDescending { it.isLive } // Live notifications first
            )
            .take(maxResults)
    }

    /**
     * Gets all notifications, sorted by recency.
     *
     * @param maxResults Maximum number of results
     * @param includeLive Whether to include live notifications (default true)
     * @param includeHistorical Whether to include historical notifications (default true)
     * @return List of all notifications, newest first
     */
    fun getAll(
        maxResults: Int = 50,
        includeLive: Boolean = true,
        includeHistorical: Boolean = true,
    ): List<NotificationActionSurface> {
        return _notifications.values
            .filter { surface ->
                (includeLive && surface.isLive) || (includeHistorical && !surface.isLive)
            }
            .sortedWith(
                compareByDescending<NotificationActionSurface> { it.timestamp }
                    .thenByDescending { it.isLive }
            )
            .take(maxResults)
    }

    /**
     * Gets notifications from specific packages, sorted by recency.
     *
     * Used to show related notifications when searching for apps.
     * For example, searching "Snapchat" shows the app AND recent Snapchat notifications.
     *
     * @param packageNames Set of package names to match
     * @param maxResults Maximum number of results to return
     * @param includeLive Whether to include live notifications (default true)
     * @param includeHistorical Whether to include historical notifications (default true)
     * @return List of notifications from matching packages, newest first
     */
    fun getByPackages(
        packageNames: Set<String>,
        maxResults: Int = 10,
        includeLive: Boolean = true,
        includeHistorical: Boolean = true,
    ): List<NotificationActionSurface> {
        if (packageNames.isEmpty()) return emptyList()

        return _notifications.values
            .filter { surface -> surface.packageName in packageNames }
            .filter { surface ->
                (includeLive && surface.isLive) || (includeHistorical && !surface.isLive)
            }
            .sortedWith(
                compareByDescending<NotificationActionSurface> { it.timestamp }
                    .thenByDescending { it.isLive }
            )
            .take(maxResults)
    }

    /**
     * Gets the count of all notifications (live + historical).
     */
    fun count(): Int = _notifications.size

    /**
     * Gets the count of live notifications only.
     */
    fun countLive(): Int = _notifications.values.count { it.isLive }

    /**
     * Gets the count of historical notifications only.
     */
    fun countHistorical(): Int = _notifications.values.count { !it.isLive }

    /**
     * Checks if a notification exists in the index.
     *
     * @param key The notification key
     * @return True if the notification exists (live or historical)
     */
    fun contains(key: String): Boolean = _notifications.containsKey(key)

    /**
     * Checks if a live notification exists in the index.
     *
     * @param key The notification key
     * @return True if the notification is active
     */
    fun containsLive(key: String): Boolean = _notifications[key]?.isLive == true
}
