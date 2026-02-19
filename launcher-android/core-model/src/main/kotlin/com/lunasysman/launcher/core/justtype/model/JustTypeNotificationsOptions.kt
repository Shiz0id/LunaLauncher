package com.lunasysman.launcher.core.justtype.model

/**
 * User-configurable options for Just Type notification results.
 */
data class JustTypeNotificationsOptions(
    val maxResults: Int = 5,
    val matchText: Boolean = true,
    val matchNames: Boolean = true,
    val showActions: Boolean = true,
)
