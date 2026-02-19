package com.lunasysman.launcher

import android.app.Application
import com.lunasysman.launcher.core.justtype.notifications.LunaNotificationListenerService
import com.lunasysman.launcher.core.justtype.notifications.NotificationIndexer

class LauncherApplication : Application() {
    lateinit var container: LauncherContainer
        private set

    /**
     * Notification indexer for Just Type integration.
     * Shared singleton accessed by NotificationListenerService and Just Type providers.
     */
    lateinit var notificationIndexer: NotificationIndexer
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize notification indexer BEFORE creating container
        // (container needs access to it during initialization)
        notificationIndexer = NotificationIndexer()
        LunaNotificationListenerService.initializeIndexer(notificationIndexer)

        // Flush old historical notifications (older than 4 days)
        // This prevents unbounded growth of the notification index
        notificationIndexer.flushOldHistorical()

        container = LauncherContainer.create(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            container.iconRepository.clearMemory()
        }
    }
}
