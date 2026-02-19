package com.lunasysman.launcher.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.model.JustTypeUiState
import com.lunasysman.launcher.core.justtype.model.stableKey
import com.lunasysman.launcher.core.model.LaunchPoint

@Composable
fun SearchOverlay(
    state: JustTypeUiState,
    launchPointsById: Map<String, LaunchPoint>,
    iconContent: @Composable (LaunchPoint) -> Unit,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onItemClick: (JustTypeItemUi) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Keep the background effectively transparent so this feels like it "extends" from Home's search bar,
                // not like a separate modal sheet.
                .background(Color.Transparent)
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
        ) {
            val sections = remember(state) { state.sections.filter { it.items.isNotEmpty() } }
            val hasResults = sections.isNotEmpty()
            val topShape =
                RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = 22.dp,
                    bottomStart = if (hasResults) 0.dp else 22.dp,
                    bottomEnd = if (hasResults) 0.dp else 22.dp,
                )
            val bottomShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 22.dp, bottomEnd = 22.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 38.dp, start = 18.dp, end = 18.dp),
            ) {
                // Search bar with top rounded corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(topShape)
                        .background(Color(0xFF1E2226).copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    SearchBar(
                        value = state.query,
                        onValueChange = onQueryChange,
                        focusRequester = focusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }

                if (hasResults) {
                    val isCategoryFilterActive = state.categoryFilter != null

                    if (isCategoryFilterActive) {
                        // Category filter mode: vertical scrollable list
                        val allItems = sections.flatMap { it.items }
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 480.dp)
                                .clip(bottomShape)
                                .background(Color(0xFF1E2226).copy(alpha = 0.92f)),
                        ) {
                            itemsIndexed(allItems, key = { _, item -> item.stableKey() }) { itemIndex, item ->
                                CategoryFilterResultRow(
                                    item = item,
                                    launchPointsById = launchPointsById,
                                    iconContent = iconContent,
                                    highlight = itemIndex == 0,
                                    onClick = { onItemClick(item) },
                                )
                            }
                        }
                    } else {
                        // Normal mode: horizontal rows per section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(bottomShape)
                                .background(Color(0xFF1E2226).copy(alpha = 0.92f))
                                .padding(vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                                sections.forEachIndexed { sectionIndex, section ->
                                    if (section.title.isNotBlank()) {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                        )
                                    }
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        itemsIndexed(section.items.take(8), key = { _, item -> item.stableKey() }) { itemIndex, item ->
                                            when (item) {
                                                is JustTypeItemUi.LaunchPointItem -> {
                                                    val lp = launchPointsById[item.lpId] ?: return@itemsIndexed
                                                    SearchResultTile(
                                                        launchPoint = lp,
                                                        highlight = sectionIndex == 0 && itemIndex == 0,
                                                        iconContent = { iconContent(lp) },
                                                        onClick = { onItemClick(item) },
                                                    )
                                                }
                                                is JustTypeItemUi.ActionItem -> {
                                                    ActionResultTile(
                                                        title = item.title,
                                                        subtitle = item.subtitle,
                                                        icon = actionIconFor(item.actionId),
                                                        highlight = sectionIndex == 0 && itemIndex == 0,
                                                        onClick = { onItemClick(item) },
                                                    )
                                                }
                                                is JustTypeItemUi.SearchTemplateItem -> {
                                                    SearchTemplateResultTile(
                                                        title = item.title,
                                                        icon = searchIconFor(item.providerId),
                                                        highlight = sectionIndex == 0 && itemIndex == 0,
                                                        onClick = { onItemClick(item) },
                                                    )
                                                }
                                                is JustTypeItemUi.DbRowItem -> {
                                                    DbRowResultTile(
                                                        title = item.title,
                                                        subtitle = item.subtitle,
                                                        highlight = sectionIndex == 0 && itemIndex == 0,
                                                        onClick = { onItemClick(item) },
                                                    )
                                                }
                                                is JustTypeItemUi.NotificationItem -> {
                                                    NotificationResultTile(
                                                        title = item.title,
                                                        subtitle = item.subtitle,
                                                        highlight = sectionIndex == 0 && itemIndex == 0,
                                                        onClick = { onItemClick(item) },
                                                    )
                                                }
                                                else -> Unit
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Just type…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.92f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
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
            Color(0xFF6FAEDB).copy(alpha = 0.35f)
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
    icon: ImageVector?,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg =
        if (highlight) {
            Color(0xFF6FAEDB).copy(alpha = 0.35f)
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
                .width(56.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(14.dp),
                )
            } else {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(14.dp),
                )
            }
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
    icon: ImageVector?,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg =
        if (highlight) {
            Color(0xFF6FAEDB).copy(alpha = 0.35f)
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
                .width(56.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(14.dp),
                )
            } else {
                Text(
                    text = "S",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(14.dp),
                )
            }
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
            Color(0xFF6FAEDB).copy(alpha = 0.35f)
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
                .width(56.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(14.dp),
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
private fun NotificationResultTile(
    title: String,
    subtitle: String?,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg =
        if (highlight) {
            Color(0xFF6FAEDB).copy(alpha = 0.35f)
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
                .width(56.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
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

/**
 * Row-style result item for category filter mode (vertical list).
 */
@Composable
private fun CategoryFilterResultRow(
    item: JustTypeItemUi,
    launchPointsById: Map<String, LaunchPoint>,
    iconContent: @Composable (LaunchPoint) -> Unit,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val bg =
        if (highlight) {
            Color(0xFF6FAEDB).copy(alpha = 0.35f)
        } else {
            Color.Transparent
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                is JustTypeItemUi.LaunchPointItem -> {
                    val lp = launchPointsById[item.lpId]
                    if (lp != null) iconContent(lp)
                }
                is JustTypeItemUi.NotificationItem -> {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                }
                is JustTypeItemUi.ActionItem -> {
                    val icon = actionIconFor(item.actionId)
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.92f),
                        )
                    } else {
                        Text(
                            text = item.title.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }
                is JustTypeItemUi.SearchTemplateItem -> {
                    Icon(
                        imageVector = searchIconFor(item.providerId) ?: Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                }
                is JustTypeItemUi.DbRowItem -> {
                    Text(
                        text = item.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }

        // Title and subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val title = when (item) {
                is JustTypeItemUi.LaunchPointItem -> launchPointsById[item.lpId]?.title ?: ""
                is JustTypeItemUi.NotificationItem -> item.title
                is JustTypeItemUi.ActionItem -> item.title
                is JustTypeItemUi.SearchTemplateItem -> item.title
                is JustTypeItemUi.DbRowItem -> item.title
            }
            val subtitle = when (item) {
                is JustTypeItemUi.NotificationItem -> item.subtitle
                is JustTypeItemUi.ActionItem -> item.subtitle
                is JustTypeItemUi.DbRowItem -> item.subtitle
                else -> null
            }

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
