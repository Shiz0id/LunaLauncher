package com.lunasysman.launcher.core.justtype.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.lunasysman.launcher.core.justtype.model.NotificationAction
import com.lunasysman.launcher.core.justtype.model.NotificationActionSurface

/**
 * Executes notification actions with graceful error handling.
 *
 * Philosophy: Never show a dead button.
 * - Verify notification exists before execution
 * - Handle PendingIntent cancellation gracefully
 * - Provide clear user feedback on failures
 *
 * Security:
 * - Uses Android's PendingIntent capability tokens
 * - No privilege escalation (actions run with notification sender's permissions)
 * - RemoteInput is validated and properly formatted
 */
class NotificationActionExecutor(
    private val context: Context,
    private val indexer: NotificationIndexer,
) {

    companion object {
        private const val TAG = "NotificationActionExecutor"
    }

    /**
     * Result of attempting to execute a notification action.
     */
    sealed class ExecutionResult {
        /**
         * Action was successfully sent.
         */
        data object Success : ExecutionResult()

        /**
         * Notification no longer exists (was dismissed before action could execute).
         */
        data class NotificationDismissed(val message: String) : ExecutionResult()

        /**
         * PendingIntent was cancelled (app uninstalled, notification cleared, etc.).
         */
        data class IntentCancelled(val message: String) : ExecutionResult()

        /**
         * Generic error during execution.
         */
        data class Error(val message: String, val cause: Throwable?) : ExecutionResult()
    }

    /**
     * Executes a notification's content intent (main tap action).
     *
     * @param notificationKey The notification identifier
     * @return Execution result with success or failure details
     */
    suspend fun executeContentIntent(notificationKey: String): ExecutionResult {
        val surface = indexer.get(notificationKey)
            ?: return ExecutionResult.NotificationDismissed("This notification is no longer available")

        val contentIntent = surface.contentIntent
            ?: return ExecutionResult.Error("This notification has no tap action", null)

        return executePendingIntent(contentIntent, "content intent for ${surface.title}")
    }

    /**
     * Executes a specific notification action by index.
     *
     * @param notificationKey The notification identifier
     * @param actionIndex The zero-based action index
     * @param replyText Optional text for RemoteInput (inline replies)
     * @return Execution result with success or failure details
     */
    suspend fun executeAction(
        notificationKey: String,
        actionIndex: Int,
        replyText: String? = null,
    ): ExecutionResult {
        val surface = indexer.get(notificationKey)
            ?: return ExecutionResult.NotificationDismissed("This notification is no longer available")

        if (actionIndex !in surface.actions.indices) {
            return ExecutionResult.Error("Invalid action index: $actionIndex", null)
        }

        val action = surface.actions[actionIndex]

        return if (replyText != null && action.remoteInput != null) {
            executeRemoteInputAction(action, replyText, surface)
        } else {
            executePendingIntent(action.intent, "action '${action.title}' for ${surface.title}")
        }
    }

    /**
     * Executes a RemoteInput action (e.g., inline message reply).
     *
     * Uses ActivityOptions to ensure the target app can process the reply,
     * even if it's in the background.
     */
    private suspend fun executeRemoteInputAction(
        action: NotificationAction,
        replyText: String,
        surface: NotificationActionSurface,
    ): ExecutionResult {
        val remoteInput = action.remoteInput
            ?: return ExecutionResult.Error("Action does not support text input", null)

        try {
            // Build intent with RemoteInput data
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            android.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

            // Use ActivityOptions to ensure the app can process the reply in background.
            val options = ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }

            // Send the PendingIntent with RemoteInput data
            action.intent.send(context, 0, intent, null, null, null, options.toBundle())

            Log.d(TAG, "RemoteInput action sent: ${action.title} for ${surface.title}")
            return ExecutionResult.Success
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "RemoteInput action cancelled: ${action.title}", e)
            return ExecutionResult.IntentCancelled("This action is no longer available")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing RemoteInput action: ${action.title}", e)
            return ExecutionResult.Error("Failed to send reply", e)
        }
    }

    /**
     * Executes a raw PendingIntent with error handling.
     *
     * Uses ActivityOptions to ensure the target app is brought to the foreground,
     * even if it's not currently running or is in the background.
     */
    private suspend fun executePendingIntent(
        pendingIntent: PendingIntent,
        description: String,
    ): ExecutionResult {
        return try {
            // Use ActivityOptions to ensure the app launches to the foreground.
            // Without this, PendingIntents may not bring background apps to front.
            val options = ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
            Log.d(TAG, "PendingIntent sent: $description")
            ExecutionResult.Success
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "PendingIntent cancelled: $description", e)
            ExecutionResult.IntentCancelled("This action is no longer available")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing PendingIntent: $description", e)
            ExecutionResult.Error("Failed to execute action", e)
        }
    }

    /**
     * Validates that a notification and action still exist before execution.
     *
     * This is a defensive check to prevent showing stale actions in the UI.
     *
     * @param notificationKey The notification identifier
     * @param actionIndex The action index (or null for content intent)
     * @return True if the notification and action are still valid
     */
    fun validate(notificationKey: String, actionIndex: Int? = null): Boolean {
        val surface = indexer.get(notificationKey) ?: return false

        return if (actionIndex != null) {
            actionIndex in surface.actions.indices
        } else {
            surface.contentIntent != null
        }
    }
}
