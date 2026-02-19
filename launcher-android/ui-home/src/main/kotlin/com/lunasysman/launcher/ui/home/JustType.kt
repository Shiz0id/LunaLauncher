package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.model.JustTypeCategory
import com.lunasysman.launcher.core.justtype.model.JustTypeUiState
import com.lunasysman.launcher.core.justtype.model.stableKey
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.ui.home.theme.GlassSurface
import com.lunasysman.launcher.ui.home.theme.LauncherTheme

@Composable
internal fun TopSearchBar(
    modifier: Modifier,
    squareBottom: Boolean,
    placeholderAlpha: Float,
    onClick: () -> Unit,
) {
    val shape =
        RoundedCornerShape(
            topStart = 26.dp,
            topEnd = 26.dp,
            bottomStart = if (squareBottom) 0.dp else 26.dp,
            bottomEnd = if (squareBottom) 0.dp else 26.dp,
        )
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(LauncherTheme.colors.homeBarBacking)
            .clickable(onClick = onClick),
        shape = shape,
        backgroundColor = LauncherTheme.colors.homeSearchBarBackground,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Just type…",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = placeholderAlpha.coerceIn(0f, 1f)),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
internal fun SearchResultsPanel(
    modifier: Modifier,
    state: JustTypeUiState,
    launchPointsById: Map<String, LaunchPoint>,
    iconContent: @Composable (LaunchPoint) -> Unit,
    onItemClick: (JustTypeItemUi) -> Unit,
    onNotificationActionClick: (notificationKey: String, action: JustTypeItemUi.NotificationActionUi) -> Unit,
) {
    val sections = remember(state) { state.sections.filter { it.items.isNotEmpty() } }
    if (sections.isEmpty()) return

    val shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp, topStart = 0.dp, topEnd = 0.dp)

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        backgroundColor = LauncherTheme.colors.justTypeResultsPanelBackground,
        showDepthGradient = false,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sections.forEach { section ->
                when (section.providerId) {
                    "apps" -> {
                        item(key = "apps_header") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                        item(key = "apps_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                itemsIndexed(section.items.take(8), key = { _, item -> item.stableKey() }) { index, item ->
                                    if (item !is JustTypeItemUi.LaunchPointItem) return@itemsIndexed
                                    val lp = launchPointsById[item.lpId] ?: return@itemsIndexed
                                    SearchResultTile(
                                        launchPoint = lp,
                                        highlight = index == 0,
                                        iconContent = { iconContent(lp) },
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                    "related_notifications" -> {
                        // Related notifications appear right after apps (no header)
                        // E.g., searching "Snapchat" shows Snapchat notifications below the app
                        item(key = "related_notifications") {
                            val shown = section.items.filterIsInstance<JustTypeItemUi.NotificationItem>().take(5)
                            if (shown.isEmpty()) return@item
                            SectionCard(modifier = Modifier.padding(horizontal = 14.dp), showDepthGradient = false) {
                                // No header - appears as continuation of app results
                                shown.forEachIndexed { idx, item ->
                                    if (idx != 0) RowDivider()
                                    NotificationBarRow(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        actions = item.actions,
                                        isLive = item.isLive,
                                        onClick = { onItemClick(item) },
                                        onActionClick = { action ->
                                            onNotificationActionClick(item.notificationKey, action)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    "notifications" -> {
                        item(key = "notifications") {
                            val all = section.items.filterIsInstance<JustTypeItemUi.NotificationItem>()
                            val shown =
                                if (state.categoryFilter == JustTypeCategory.NOTIFICATIONS) {
                                    all
                                } else {
                                    all.take(5)
                                }
                            if (shown.isEmpty()) return@item
                            SectionCard(modifier = Modifier.padding(horizontal = 14.dp), showDepthGradient = false) {
                                SectionHeader(title = section.title)
                                val maxVisibleRows = 9
                                val estimatedRowHeight = 64.dp
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = estimatedRowHeight * maxVisibleRows),
                                    userScrollEnabled = shown.size > maxVisibleRows,
                                ) {
                                    itemsIndexed(shown, key = { _, item -> item.stableKey() }) { idx, item ->
                                        if (idx != 0) RowDivider()
                                        NotificationBarRow(
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            actions = item.actions,
                                            isLive = item.isLive,
                                            onClick = { onItemClick(item) },
                                            onActionClick = { action ->
                                                onNotificationActionClick(item.notificationKey, action)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "primary_search" -> {
                        item(key = "primary_search") {
                            val item = section.items.firstOrNull() as? JustTypeItemUi.SearchTemplateItem ?: return@item
                            SectionCard(modifier = Modifier.padding(horizontal = 14.dp)) {
                                BarRow(
                                    title = item.title,
                                    subtitle = null,
                                    leadingIcon = searchIconFor(providerId = item.providerId),
                                    leadingLabel = item.title.take(1),
                                    trailingLabel = "SUGGEST",
                                    onClick = { onItemClick(item) },
                                )
                            }
                        }
                    }
                    "contacts" -> {
                        item(key = "contacts") {
                            val shown = section.items.filterIsInstance<JustTypeItemUi.DbRowItem>().take(4)
                            if (shown.isEmpty()) return@item
                            SectionCard(modifier = Modifier.padding(horizontal = 14.dp)) {
                                SectionHeader(title = section.title)
                                shown.forEachIndexed { idx, item ->
                                    if (idx != 0) RowDivider()
                                    ContactBarRow(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                    "context_actions" -> {
                        item(key = "context_actions") {
                            val shown = section.items.filterIsInstance<JustTypeItemUi.ActionItem>().take(4)
                            if (shown.isEmpty()) return@item
                            SectionCard(modifier = Modifier.padding(horizontal = 14.dp)) {
                                SectionHeader(title = section.title)
                                shown.forEachIndexed { idx, item ->
                                    if (idx != 0) RowDivider()
                                    BarRow(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        leadingIcon = actionIconFor(actionId = item.actionId),
                                        leadingLabel = item.title.take(1),
                                        trailingLabel = null,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                    "more_searches" -> {
                        item(key = "more_searches") {
                            val shown = section.items.filterIsInstance<JustTypeItemUi.SearchTemplateItem>().take(6)
                            if (shown.isEmpty()) return@item
                            SectionCard(modifier = Modifier.padding(horizontal = 14.dp)) {
                                SectionHeader(title = section.title)
                                shown.forEachIndexed { idx, item ->
                                    if (idx != 0) RowDivider()
                                    BarRow(
                                        title = item.title,
                                        subtitle = null,
                                        leadingIcon = searchIconFor(providerId = item.providerId),
                                        leadingLabel = item.title.take(1),
                                        trailingLabel = null,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                    "quick_actions" -> {
                        item(key = "quick_actions") {
                            val shown = section.items.filterIsInstance<JustTypeItemUi.ActionItem>().take(6)
                            if (shown.isEmpty()) return@item
                            SectionCard(modifier = Modifier.padding(horizontal = 14.dp)) {
                                SectionHeader(title = section.title)
                                shown.forEachIndexed { idx, item ->
                                    if (idx != 0) RowDivider()
                                    BarRow(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        leadingIcon = actionIconFor(actionId = item.actionId),
                                        leadingLabel = item.title.take(1),
                                        trailingLabel = null,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    showDepthGradient: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        backgroundColor = LauncherTheme.colors.justTypeSectionCardBackground,
        showDepthGradient = showDepthGradient,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SectionHeader(title: String) {
    if (title.isBlank()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.75f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LauncherTheme.colors.justTypeRowDivider),
    )
}

@Composable
private fun BarRow(
    title: String,
    subtitle: String?,
    leadingIcon: ImageVector?,
    leadingLabel: String,
    trailingLabel: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LauncherTheme.colors.justTypeChipBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = leadingLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!trailingLabel.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(LauncherTheme.colors.justTypeChipBackground)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@Composable
private fun ContactBarRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun NotificationBarRow(
    title: String,
    subtitle: String?,
    actions: List<JustTypeItemUi.NotificationActionUi>,
    isLive: Boolean,
    onClick: () -> Unit,
    onActionClick: (JustTypeItemUi.NotificationActionUi) -> Unit,
) {
    // Colors for live vs historical
    val badgeColor = if (isLive) LauncherTheme.colors.notificationLiveBadgeColor
                     else LauncherTheme.colors.notificationHistoricalBadgeColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Badge indicator - different color for live vs historical
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = if (isLive) 0.92f else 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Only show tag for live notifications
                    if (isLive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor.copy(alpha = 0.9f),
                        )
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = if (isLive) 0.68f else 0.5f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Action pills - only shown for live notifications (actions list will be empty for historical)
        if (actions.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(actions.size) { index ->
                    val action = actions[index]
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LauncherTheme.colors.justTypePillBackground)
                            .clickable { onActionClick(action) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}

private fun actionIconFor(actionId: String): ImageVector? =
    when (actionId) {
        "new_email" -> Icons.Filled.Email
        "new_event" -> Icons.Filled.Event
        "new_message" -> Icons.Filled.Sms
        "set_timer" -> Icons.Filled.Timer
        "set_alarm" -> Icons.Filled.Alarm
        "open_settings" -> Icons.Filled.Settings
        "wifi_settings" -> Icons.Filled.Wifi
        "bluetooth_settings" -> Icons.Filled.Bluetooth
        "manage_apps" -> Icons.Filled.Apps
        "call" -> Icons.Filled.Phone
        "text" -> Icons.Filled.Message
        "search_web" -> Icons.Filled.Search
        "enable_contacts_search" -> Icons.Filled.Settings
        else -> null
    }

private fun searchIconFor(providerId: String): ImageVector? =
    when (providerId) {
        "google" -> Icons.Filled.Search
        "google_maps" -> Icons.Filled.Map
        "reddit" -> Icons.Filled.Public
        "wikipedia" -> Icons.Filled.Public
        else -> Icons.Filled.Search
    }

@Composable
private fun SearchResultTile(
    launchPoint: LaunchPoint,
    highlight: Boolean,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg =
        if (highlight) {
            LauncherTheme.colors.justTypeFirstItemHighlight
        } else {
            Color.Transparent
        }

    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        iconContent()
        Text(
            text = launchPoint.title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 2,
        )
    }
}

@Composable
private fun ActionResultTile(
    title: String,
    subtitle: String?,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg =
        if (highlight) {
            LauncherTheme.colors.justTypeFirstItemHighlight
        } else {
            Color.Transparent
        }

    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchTemplateResultTile(
    title: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg =
        if (highlight) {
            LauncherTheme.colors.justTypeFirstItemHighlight
        } else {
            Color.Transparent
        }

    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "S",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DbRowResultTile(
    title: String,
    subtitle: String?,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg =
        if (highlight) {
            LauncherTheme.colors.justTypeFirstItemHighlight
        } else {
            Color.Transparent
        }

    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
