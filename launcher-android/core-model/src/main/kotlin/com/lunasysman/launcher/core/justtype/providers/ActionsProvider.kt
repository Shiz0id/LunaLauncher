package com.lunasysman.launcher.core.justtype.providers

import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi

object ActionsProvider {
    enum class ActionKind {
        STATIC,
        QUERY_PARAM,
    }

    data class ActionInfo(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val keywords: Set<String>,
        val kind: ActionKind = ActionKind.STATIC,
        val showOnEmptyQuery: Boolean = false,
    )

    private val actions: List<ActionInfo> =
        listOf(
            ActionInfo(
                id = "new_email",
                title = "New Email",
                subtitle = "Compose a new message",
                keywords = setOf("email", "mail", "compose"),
                showOnEmptyQuery = true,
            ),
            ActionInfo(
                id = "new_event",
                title = "New Event",
                subtitle = "Create a calendar event",
                keywords = setOf("event", "calendar", "meeting"),
                showOnEmptyQuery = true,
            ),
            ActionInfo(
                id = "new_message",
                title = "New Message",
                subtitle = "Send a text message",
                keywords = setOf("message", "text", "sms"),
                showOnEmptyQuery = true,
            ),
            ActionInfo(
                id = "set_timer",
                title = "Set Timer",
                subtitle = "Open timer",
                keywords = setOf("timer"),
                showOnEmptyQuery = true,
            ),
            ActionInfo(
                id = "set_alarm",
                title = "Set Alarm",
                subtitle = "Open alarms",
                keywords = setOf("alarm"),
                showOnEmptyQuery = true,
            ),
            ActionInfo(
                id = "open_settings",
                title = "Open Settings",
                subtitle = "System settings",
                keywords = setOf("settings"),
                showOnEmptyQuery = true,
            ),
            ActionInfo(
                id = "wifi_settings",
                title = "Wi‑Fi Settings",
                subtitle = "Wi‑Fi settings",
                keywords = setOf("wifi", "wi-fi", "wireless"),
            ),
            ActionInfo(
                id = "bluetooth_settings",
                title = "Bluetooth Settings",
                subtitle = "Bluetooth settings",
                keywords = setOf("bluetooth", "bt"),
            ),
            ActionInfo(
                id = "manage_apps",
                title = "Manage Apps",
                subtitle = "App settings",
                keywords = setOf("apps", "applications", "manage"),
            ),
            ActionInfo(
                id = "call",
                title = "Call",
                subtitle = "Dial a number",
                keywords = setOf("call", "dial"),
                kind = ActionKind.QUERY_PARAM,
            ),
            ActionInfo(
                id = "text",
                title = "Text",
                subtitle = "Send a message",
                keywords = setOf("text", "sms", "message"),
                kind = ActionKind.QUERY_PARAM,
            ),
            ActionInfo(
                id = "search_web",
                title = "Search web",
                subtitle = "Search the web",
                keywords = setOf("search", "web"),
                kind = ActionKind.QUERY_PARAM,
            ),
        )

    fun itemsFor(query: String, enabledActionsInOrder: List<ActionInfo>): List<JustTypeItemUi.ActionItem> {
        val q = query.trim()
        val qLower = q.lowercase()
        if (qLower.isEmpty()) {
            return enabledActionsInOrder
                .asSequence()
                .filter { it.kind == ActionKind.STATIC && it.showOnEmptyQuery }
                .take(6)
                .map { def ->
                    JustTypeItemUi.ActionItem(
                        actionId = def.id,
                        title = def.title,
                        subtitle = def.subtitle,
                    )
                }
                .toList()
        }

        val (prefixVerb, prefixArg) = parseVerbPrefix(qLower)

        fun isNumericish(value: String): Boolean {
            val s = value.trim()
            if (s.length < 3) return false
            val digits = s.count { it.isDigit() }
            return digits >= 3 && s.all { it.isDigit() || it in setOf('+', '-', ' ', '(', ')') }
        }

        fun buildDynamic(def: ActionInfo): JustTypeItemUi.ActionItem? {
            if (def.kind != ActionKind.QUERY_PARAM) return null
            val arg =
                when (def.id) {
                    "call" -> {
                        when {
                            prefixVerb in setOf("call", "dial") -> prefixArg
                            isNumericish(qLower) -> q
                            else -> null
                        }
                    }
                    "text" -> {
                        when {
                            prefixVerb in setOf("text", "sms", "message") -> prefixArg
                            isNumericish(qLower) -> q
                            else -> null
                        }
                    }
                    "search_web" -> {
                        when {
                            prefixVerb == "search" -> prefixArg
                            else -> null
                        }
                    }
                    else -> null
                }?.trim()?.takeIf { it.isNotBlank() } ?: return null

            val title =
                when (def.id) {
                    "call" -> "Call \"$arg\""
                    "text" -> "Text \"$arg\""
                    "search_web" -> "Search web for \"$arg\""
                    else -> "${def.title} \"$arg\""
                }

            return JustTypeItemUi.ActionItem(
                actionId = def.id,
                title = title,
                subtitle = def.subtitle,
                argument = arg,
            )
        }

        val firstWord = qLower.substringBefore(' ')
        val verbTriggers =
            setOf(
                "new",
                "compose",
                "email",
                "mail",
                "text",
                "sms",
                "message",
                "call",
                "dial",
                "event",
                "alarm",
                "timer",
                "search",
                "settings",
                "wifi",
                "bluetooth",
                "apps",
            )

        return enabledActionsInOrder.mapNotNull { def ->
            when (def.kind) {
                ActionKind.QUERY_PARAM -> buildDynamic(def)
                ActionKind.STATIC -> {
                    val isVerbLike = firstWord in verbTriggers
                    if (!isVerbLike) return@mapNotNull null
                    val matched = def.keywords.any { k -> qLower.contains(k) } || firstWord == "new" || firstWord == "compose"
                    if (!matched) null
                    else {
                        JustTypeItemUi.ActionItem(
                            actionId = def.id,
                            title = def.title,
                            subtitle = def.subtitle,
                        )
                    }
                }
            }
        }
    }

    fun defaultActionInfoFor(id: String, titleOverride: String? = null): ActionInfo? {
        val def = actions.firstOrNull { it.id == id } ?: return null
        return def.copy(title = titleOverride ?: def.title)
    }

    private fun parseVerbPrefix(queryLower: String): Pair<String?, String?> {
        val parts = queryLower.trim().split(Regex("\\s+"), limit = 2)
        if (parts.isEmpty()) return null to null
        if (parts.size == 1) return parts[0] to null
        return parts[0] to parts[1]
    }
}
