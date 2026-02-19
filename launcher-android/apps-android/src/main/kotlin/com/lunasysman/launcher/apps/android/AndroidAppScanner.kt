package com.lunasysman.launcher.apps.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.core.model.LaunchPointType

class AndroidAppScanner(
    context: Context,
) {
    private val pm: PackageManager = context.applicationContext.packageManager

    fun scanLaunchableActivities(): List<LaunchPoint> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(query, 0)

        val versionByPackage: Map<String, Long> =
            resolved
                .asSequence()
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
                .associateWith { pkg ->
                    try {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0).longVersionCode
                    } catch (e: Exception) {
                        Log.w("LunaLauncher", "Failed to read versionCode for $pkg", e)
                        0L
                    }
                }

        return resolved
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: ai.name
                val pkg = ai.packageName
                val activity = ai.name
                val id = stableId(pkg, activity)
                val versionCode = versionByPackage[pkg] ?: 0L
                val iconKey = "${id}@${versionCode}"

                ScannedLaunchPoint(
                    id = id,
                    title = label,
                    iconKey = iconKey,
                )
            }
            .distinctBy { it.id }
    }

    fun resolveIntentOrThrow(id: String): Intent {
        val (pkg, activity) = parseAndroidIdOrThrow(id)
        val intent = intentForAndroid(pkg, activity)
        @Suppress("DEPRECATION")
        val resolved = pm.resolveActivity(intent, 0)
        if (resolved == null) {
            Log.w("LunaLauncher", "Unresolvable launch intent: $id")
            throw AndroidLaunchException.Unresolvable(id)
        }
        return intent
    }

    fun loadIconBitmapOrNull(iconKey: String, sizePx: Int = 96): Bitmap? {
        val (pkg, activity) =
            try {
                parseAndroidIdOrThrow(iconKey)
            } catch (e: Exception) {
                Log.w("LunaLauncher", "Invalid iconKey=$iconKey", e)
                return null
            }

        val drawable =
            try {
                pm.getActivityIcon(ComponentName(pkg, activity))
            } catch (e: Exception) {
                Log.w("LunaLauncher", "Failed to load icon for $iconKey", e)
                return null
            }

        return drawableToBitmap(drawable, sizePx, sizePx)
    }

    fun parseAndroidIdOrThrow(id: String): Pair<String, String> {
        val base = id.substringBefore("@")
        require(base.startsWith("android:")) { "Not an Android launch point id: $id" }
        val remainder = base.removePrefix("android:")
        val split = remainder.split("/", limit = 2)
        require(split.size == 2) { "Invalid Android launch point id: $id" }
        val pkg = split[0].trim()
        val activity = split[1].trim()
        require(pkg.isNotEmpty() && activity.isNotEmpty()) { "Invalid Android launch point id: $id" }
        return pkg to activity
    }

    private fun intentForAndroid(packageName: String, activityName: String): Intent {
        val component = ComponentName(packageName, activityName)
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private fun stableId(packageName: String, activityName: String): String =
        "android:$packageName/$activityName"
}

private data class ScannedLaunchPoint(
    override val id: String,
    override val title: String,
    override val iconKey: String?,
) : LaunchPoint {
    override val type: LaunchPointType = LaunchPointType.ANDROID_APP
    override val pinned: Boolean = false
    override val hidden: Boolean = false
    override val lastLaunchedAtEpochMs: Long? = null
}

sealed class AndroidLaunchException(message: String) : RuntimeException(message) {
    data class Unresolvable(val id: String) : AndroidLaunchException("Unresolvable launch intent: $id")
}

private fun drawableToBitmap(drawable: Drawable, widthPx: Int, heightPx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val src = drawable.bitmap
        if (src.width == widthPx && src.height == heightPx) return src
        return Bitmap.createScaledBitmap(src, widthPx, heightPx, true)
    }

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, widthPx, heightPx)
    drawable.draw(canvas)
    return bitmap
}
