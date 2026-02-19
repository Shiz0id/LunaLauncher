package com.lunasysman.launcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "just_type_provider")
data class JustTypeProviderEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    val displayName: String?,
    val enabled: Boolean,
    val orderIndex: Int,
    val version: Int,
    val source: String,
    val urlTemplate: String?,
    val suggestUrlTemplate: String?,
    val canPromotePrimaryResult: Boolean,
)
