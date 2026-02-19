package com.lunasysman.launcher.core.justtype.model

data class JustTypeUiState(
    val query: String,
    val sections: List<JustTypeSectionUi>,
    /** Active category filter from @-prefix, or null if showing all categories */
    val categoryFilter: JustTypeCategory? = null,
)

data class JustTypeSectionUi(
    val providerId: String,
    val title: String,
    val category: JustTypeCategory,
    val items: List<JustTypeItemUi>,
)

