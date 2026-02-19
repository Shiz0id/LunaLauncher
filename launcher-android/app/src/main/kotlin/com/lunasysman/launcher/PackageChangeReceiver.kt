package com.lunasysman.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? LauncherApplication ?: return
        val pendingResult = goAsync()
        try {
            app.container.packageChangeHandler.signalChanged()
        } finally {
            pendingResult.finish()
        }
    }
}
