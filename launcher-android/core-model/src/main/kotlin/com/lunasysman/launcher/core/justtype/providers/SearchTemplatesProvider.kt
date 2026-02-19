package com.lunasysman.launcher.core.justtype.providers

import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi

object SearchTemplatesProvider {
    data class TemplateDef(
        val id: String,
        val displayName: String,
        val urlTemplate: String,
    )

    private val templates: List<TemplateDef> =
        listOf(
            TemplateDef(
                id = "google",
                displayName = "Google",
                urlTemplate = "https://www.google.com/search?q={searchTerms}",
            ),
            TemplateDef(
                id = "wikipedia",
                displayName = "Wikipedia",
                urlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search={searchTerms}",
            ),
        )

    fun query(
        query: String,
        defaultProviderId: String = "google",
    ): List<JustTypeItemUi.SearchTemplateItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val byId = templates.associateBy { it.id }
        val ordered = buildList {
            val default = byId[defaultProviderId]
            if (default != null) add(default)
            templates.forEach { t -> if (t.id != defaultProviderId) add(t) }
        }

        return ordered.map { def ->
            JustTypeItemUi.SearchTemplateItem(
                providerId = def.id,
                title = "Search with ${def.displayName}",
                query = q,
            )
        }
    }

    fun urlTemplateFor(providerId: String): String? = templates.firstOrNull { it.id == providerId }?.urlTemplate
}

