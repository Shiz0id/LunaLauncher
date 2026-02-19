package com.lunasysman.launcher.core.justtype.notifications

import android.app.Notification
import android.app.RemoteInput
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.lunasysman.launcher.core.justtype.model.NotificationAction
import com.lunasysman.launcher.core.justtype.model.NotificationActionSurface

/**
 * NotificationListenerService that feeds the NotificationIndexer.
 *
 * Maintains a live index of active notifications as searchable action surfaces.
 * This service must be explicitly enabled by the user in Android Settings.
 *
 * Security model:
 * - Leverages Android's PendingIntent capability tokens
 * - Actions are ephemeral (disappear when notification dismissed)
 * - No persistent storage of intents
 *
 * Integration:
 * - Must be declared in AndroidManifest.xml
 * - Requires BIND_NOTIFICATION_LISTENER_SERVICE permission
 * - User must grant access in Settings > Apps > Special access > Notification access
 */
class LunaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "LunaNotificationListener"

        /**
         * Singleton indexer instance.
         * Shared across the application for Just Type integration.
         */
        @Volatile
        var indexer: NotificationIndexer? = null
            private set

        /**
         * Initialize the indexer singleton.
         * Should be called from Application.onCreate().
         */
        fun initializeIndexer(indexer: NotificationIndexer) {
            this.indexer = indexer
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")

        // Initial sync: index all currently active notifications
        try {
            activeNotifications?.forEach { sbn ->
                indexNotification(sbn)
            }
            Log.d(TAG, "Indexed ${indexer?.count() ?: 0} active notifications")
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing initial notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        indexer?.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        indexNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Mark as dismissed instead of removing - keeps notification searchable in history
        indexer?.markDismissed(sbn.key)
        Log.d(TAG, "Dismissed notification: ${sbn.key}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        // Mark as dismissed instead of removing - keeps notification searchable in history
        indexer?.markDismissed(sbn.key)
        Log.d(TAG, "Dismissed notification: ${sbn.key} (reason: $reason)")
    }

    /**
     * Extracts searchable data from a StatusBarNotification and adds it to the index.
     */
    private fun indexNotification(sbn: StatusBarNotification) {
        val currentIndexer = indexer
        if (currentIndexer == null) {
            Log.w(TAG, "Indexer not initialized, skipping notification")
            return
        }

        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            // Extract title and text
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            // Extract names (for person-based search matching)
            val names = extractNames(extras)

            // Extract actions
            val actions = notification.actions?.mapNotNull { action ->
                val actionTitle = action.title?.toString() ?: return@mapNotNull null
                val remoteInputs = action.remoteInputs

                NotificationAction(
                    title = actionTitle,
                    intent = action.actionIntent,
                    remoteInput = remoteInputs?.firstOrNull(),
                    icon = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        action.getIcon()
                    } else {
                        null
                    },
                )
            } ?: emptyList()

            val surface = NotificationActionSurface(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = text,
                names = names,
                contentIntent = notification.contentIntent,
                actions = actions,
                timestamp = sbn.postTime,
                groupKey = sbn.groupKey,
            )

            currentIndexer.put(surface)
            Log.d(TAG, "Indexed notification: $title (${actions.size} actions)")
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing notification: ${sbn.key}", e)
        }
    }

    /**
     * Extracts person names from notification extras.
     *
     * Searches for:
     * - EXTRA_PEOPLE_LIST (Android 11+)
     * - EXTRA_PEOPLE (legacy)
     * - EXTRA_CONVERSATION_TITLE
     * - Messaging style sender names
     */
    private fun extractNames(extras: android.os.Bundle): List<String> {
        val names = mutableSetOf<String>()

        // Android 11+ person list
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val peopleList = extras.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)
            peopleList?.forEach { person ->
                person.name?.toString()?.let { names.add(it) }
            }
        }

        // Legacy people array
        @Suppress("DEPRECATION")
        extras.getStringArray(Notification.EXTRA_PEOPLE)?.forEach { names.add(it) }

        // Conversation title
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.let { names.add(it) }
        }

        // Messaging style - extract user name from messaging style
        // Note: EXTRA_MESSAGING_STYLE_USER doesn't exist, we need to check messages instead
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                @Suppress("DEPRECATION")
                val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                    extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                )
                messages?.forEach { message ->
                    message.senderPerson?.name?.toString()?.let { names.add(it) }
                        ?: message.sender?.toString()?.let { names.add(it) }
                }
            } catch (e: Exception) {
                // Ignore messaging style extraction errors
            }
        }

        return names.toList()
    }
}
