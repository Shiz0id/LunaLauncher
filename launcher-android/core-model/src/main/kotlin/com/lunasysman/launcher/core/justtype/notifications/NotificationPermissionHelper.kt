package com.lunasysman.launcher.core.justtype.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Helper for checking and requesting notification listener permission.
 *
 * This permission must be granted by the user in Android Settings.
 * It cannot be requested via standard runtime permission APIs.
 */
object NotificationPermissionHelper {

    /**
     * Checks if the notification listener service is enabled for this app.
     *
     * @param context Application context
     * @return True if notification access is granted
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val packageName = context.packageName
        val enabledNotificationListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledNotificationListeners?.contains(packageName) == true
    }

    /**
     * Opens Android Settings to the notification listener settings page.
     *
     * @param context Context to start the activity
     */
    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Preference key for tracking if permission was requested before.
     */
    const val PREF_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
}
