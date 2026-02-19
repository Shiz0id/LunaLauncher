package com.lunasysman.launcher.core.justtype.model

enum class JustTypeCategory {
    APPS,
    NOTIFICATIONS,  // Live notification actions (first-class verbs)
    ACTION,
    DBSEARCH,
    SEARCH,
    ;

    companion object {
        private val aliasMap: Map<String, JustTypeCategory> = buildMap {
            put("apps", APPS)
            put("app", APPS)
            put("notifications", NOTIFICATIONS)
            put("notification", NOTIFICATIONS)
            put("notif", NOTIFICATIONS)
            put("actions", ACTION)
            put("action", ACTION)
            put("contacts", DBSEARCH)
            put("contact", DBSEARCH)
            put("search", SEARCH)
            put("searches", SEARCH)
        }

        /**
         * Parses a query for @Category prefix.
         * Returns a pair of (category filter or null, remaining query).
         * Example: "@Apps chrome" -> (APPS, "chrome")
         * Example: "chrome" -> (null, "chrome")
         */
        fun parseCategoryFilter(query: String): Pair<JustTypeCategory?, String> {
            val trimmed = query.trimStart()
            if (!trimmed.startsWith("@")) {
                return null to query
            }
            val afterAt = trimmed.substring(1)
            val spaceIndex = afterAt.indexOf(' ')
            val categoryPart: String
            val remainder: String
            if (spaceIndex == -1) {
                categoryPart = afterAt
                remainder = ""
            } else {
                categoryPart = afterAt.substring(0, spaceIndex)
                remainder = afterAt.substring(spaceIndex + 1)
            }
            val category = aliasMap[categoryPart.lowercase()]
            return if (category != null) {
                category to remainder
            } else {
                // Not a valid category, treat as normal query
                null to query
            }
        }
    }
}

