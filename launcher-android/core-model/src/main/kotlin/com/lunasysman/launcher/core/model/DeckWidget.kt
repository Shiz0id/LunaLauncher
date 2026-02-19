package com.lunasysman.launcher.core.model

/**
 * Represents a widget placed on a [DeckCard].
 *
 * Widgets are bound to Android's AppWidget system via [appWidgetId] and live
 * within a specific card. Position within the card is determined by [orderIndex].
 *
 * @property appWidgetId System-allocated widget ID from [android.appwidget.AppWidgetHost].
 * @property cardId The [DeckCard.cardId] this widget belongs to.
 * @property provider Flattened [android.content.ComponentName] of the widget provider.
 * @property orderIndex Ordering within the card (top-to-bottom layout).
 * @property widthDp Requested width in dp for this widget.
 * @property heightDp Requested height in dp for this widget.
 */
data class DeckWidget(
    val appWidgetId: Int,
    val cardId: Long,
    val provider: String,
    val orderIndex: Int,
    val widthDp: Int,
    val heightDp: Int,
)
