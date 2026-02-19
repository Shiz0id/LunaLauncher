package com.lunasysman.launcher

import com.lunasysman.launcher.apps.android.AndroidAppScanner
import com.lunasysman.launcher.data.LaunchPointRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class PackageChangeHandler(
    scope: CoroutineScope,
    private val scanner: AndroidAppScanner,
    private val repository: LaunchPointRepository,
) {
    private val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        scope.launch {
            signals
                .debounce(350)
                .collectLatest {
                    val apps = scanner.scanLaunchableActivities()
                    repository.syncAndroidApps(apps)
                }
        }
    }

    fun signalChanged() {
        signals.tryEmit(Unit)
    }
}
