package com.lunasysman.launcher.core.justtype.registry

import com.lunasysman.launcher.core.justtype.model.JustTypeCategory

data class JustTypeProviderConfig(
    val id: String,
    val category: JustTypeCategory,
    val displayName: String? = null,
    val enabled: Boolean,
    val orderIndex: Int,
    val version: Int,
    val source: String,
    val urlTemplate: String? = null,
    val suggestUrlTemplate: String? = null,
    val canPromotePrimaryResult: Boolean = false,
)
