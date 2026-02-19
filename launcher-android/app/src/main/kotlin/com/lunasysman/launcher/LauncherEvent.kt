package com.lunasysman.launcher

sealed interface LauncherEvent {
    data object RequestContactsPermission : LauncherEvent
}

