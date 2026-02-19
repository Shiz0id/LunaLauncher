package com.lunasysman.launcher.deck

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log

/**
 * Manages AppWidget lifecycle for the Widget Deck.
 *
 * Only the foreground card's widgets should be live. Background cards use cached bitmaps.
 * This class handles allocation, binding, creation, and cleanup of widget host views.
 */
class DeckWidgetHost(
    private val context: Context,
) {
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost: AppWidgetHost = AppWidgetHost(context, HOST_ID)

    /** Live host views keyed by appWidgetId — used for bitmap capture. */
    private val liveViews = mutableMapOf<Int, AppWidgetHostView>()

    private var listening = false

    /** Start listening for widget updates. Call when deck is opened. */
    fun startListening() {
        if (!listening) {
            try {
                appWidgetHost.startListening()
                listening = true
                Log.d(TAG, "AppWidgetHost startListening")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startListening", e)
            }
        }
    }

    /** Stop listening for widget updates. Call when deck is dismissed. */
    fun stopListening() {
        if (listening) {
            try {
                appWidgetHost.stopListening()
                listening = false
                Log.d(TAG, "AppWidgetHost stopListening")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stopListening", e)
            }
        }
    }

    /** Allocate a new widget ID for binding. */
    fun allocateWidgetId(): Int {
        return try {
            appWidgetHost.allocateAppWidgetId().also {
                Log.d(TAG, "Allocated appWidgetId=$it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to allocate widget ID", e)
            -1
        }
    }

    /** Delete a widget ID (cleanup). */
    fun deleteWidgetId(appWidgetId: Int) {
        liveViews.remove(appWidgetId)
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            Log.d(TAG, "Deleted appWidgetId=$appWidgetId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete widget ID $appWidgetId", e)
        }
    }

    /**
     * Bind a widget ID to a provider. Returns true if binding succeeded without
     * needing user permission, false otherwise.
     */
    fun bindWidgetIfAllowed(appWidgetId: Int, provider: ComponentName, options: Bundle? = null): Boolean {
        return try {
            val opts = options ?: Bundle().apply {
                putInt(
                    AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                )
            }
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider, opts).also {
                Log.d(TAG, "bindWidgetIfAllowed id=$appWidgetId provider=$provider result=$it")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException binding widget $appWidgetId", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "IllegalArgumentException binding widget $appWidgetId", e)
            false
        }
    }

    /**
     * Create a live AppWidgetHostView for the given widget ID.
     * Pushes size options to the widget so it knows how much space it has.
     * Returns null if the widget info is unavailable.
     */
    fun createHostView(appWidgetId: Int, widthDp: Int = 0, heightDp: Int = 0): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            Log.w(TAG, "No AppWidgetInfo for id=$appWidgetId")
            return null
        }
        return try {
            // Push size options BEFORE creating the view so the first RemoteViews
            // the provider sends is already sized correctly.
            val w = if (widthDp > 0) widthDp else info.minWidth.coerceAtLeast(160)
            val h = if (heightDp > 0) heightDp else info.minHeight.coerceAtLeast(120)
            updateWidgetSize(appWidgetId, w, h)

            appWidgetHost.createView(context, appWidgetId, info).apply {
                setAppWidget(appWidgetId, info)
            }.also { view ->
                liveViews[appWidgetId] = view
                Log.d(TAG, "Created host view for appWidgetId=$appWidgetId provider=${info.provider} size=${w}x${h}dp")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create host view for widget $appWidgetId", e)
            null
        }
    }

    /** Get the live AppWidgetHostView for a widget, or null if not created yet. */
    fun getLiveView(appWidgetId: Int): AppWidgetHostView? = liveViews[appWidgetId]

    /** Get widget provider info, or null. */
    fun getWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)

    /** Update size options for a widget. */
    fun updateWidgetSize(appWidgetId: Int, widthDp: Int, heightDp: Int) {
        try {
            val options = Bundle().apply {
                putInt(
                    AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                )
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update widget size for $appWidgetId", e)
        }
    }

    /** Check if an activity component is exported (safe to launch). */
    fun isExportedActivity(component: ComponentName): Boolean =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(component, 0).exported
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val TAG = "DeckWidgetHost"
        private const val HOST_ID = 2048
    }
}
