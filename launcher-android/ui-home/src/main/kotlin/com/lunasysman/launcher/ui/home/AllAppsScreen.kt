package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.core.model.LauncherColorTheme
import com.lunasysman.launcher.core.model.LauncherThemeStyle
import com.lunasysman.launcher.ui.home.theme.LauncherTheme
import com.lunasysman.launcher.ui.home.theme.GlassSurface
import kotlinx.coroutines.CancellationException

enum class AllAppsTab {
    Apps,
    Favorites,
    Settings,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(
    modifier: Modifier = Modifier,
    initialTab: AllAppsTab = AllAppsTab.Apps,
    apps: List<LaunchPoint>,
    favorites: List<LaunchPoint>,
    iconContent: @Composable (LaunchPoint) -> Unit,
    onItemClick: (LaunchPoint) -> Unit,
    onItemLongPress: (LaunchPoint) -> Unit,
    onSetAsHome: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onShowNotificationPermissionDialog: () -> Unit,
    themeStyle: LauncherThemeStyle,
    onSetThemeStyle: (LauncherThemeStyle) -> Unit,
    colorTheme: LauncherColorTheme,
    onSetColorTheme: (LauncherColorTheme) -> Unit,
    homeTintStrength: Float,
    onSetHomeTintStrength: (Float) -> Unit,
    onOpenJustTypeSettings: () -> Unit,
    onDismiss: () -> Unit,
    dragInProgress: Boolean = false,
    onItemDragStart: (LaunchPoint, Offset) -> Unit = { _, _ -> },
    onItemDragMove: (Offset) -> Unit = {},
    onItemDragEnd: (LaunchPoint, Offset) -> Unit = { _, _ -> },
    onItemDragCancel: () -> Unit = {},
) {
    // Keeping this state allows easy future tweaks like dimming UI during drags.
    var localDragActive by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(AllAppsTab.Apps) }
    var query by rememberSaveable { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(initialTab) {
        tab = initialTab
    }

    val bgTop = LauncherTheme.colors.allAppsBackgroundTop
    val bgBottom = LauncherTheme.colors.allAppsBackgroundBottom
    val bgBrush =
        remember(bgTop, bgBottom) {
            Brush.verticalGradient(
                colors = listOf(bgTop, bgBottom),
            )
        }

    val itemsForTab =
        when (tab) {
            AllAppsTab.Apps -> apps
            AllAppsTab.Favorites -> favorites
            AllAppsTab.Settings -> emptyList()
        }

    val filtered =
        remember(itemsForTab, query) {
            val q = query.trim()
            if (q.isEmpty()) itemsForTab
            else itemsForTab.filter { it.title.contains(q, ignoreCase = true) }
        }

    val gridState = rememberLazyGridState()
    val atTop by remember(tab, gridState) {
        derivedStateOf {
            when (tab) {
                AllAppsTab.Settings -> true
                else -> gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            }
        }
    }

    // Use centralized gesture thresholds
    val thresholds = rememberGestureThresholds()
    val swipeThresholdPx = thresholds.allAppsSwipeThresholdPx
    Column(
        modifier = modifier
            .background(bgBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(tab, atTop, dragInProgress) {
                if (dragInProgress) {
                    GestureDebug.log("AllApps", "Drag in progress, ignoring swipe")
                    return@pointerInput
                }
                if (!atTop) {
                    GestureDebug.log("AllApps", "Not at top, ignoring swipe")
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    GestureDebug.log("AllApps", "Down at ${down.position}")
                    var totalDy = 0f
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                        totalDy += overSlop.y
                        change.consumeAllChanges()
                    } ?: return@awaitEachGesture

                    if (totalDy > swipeThresholdPx) {
                        GestureDebug.log("AllApps", "Swipe down detected, dismissing")
                        onDismiss()
                        return@awaitEachGesture
                    }

                    drag(slop.id) { change ->
                        totalDy += (change.position.y - change.previousPosition.y)
                        if (totalDy > swipeThresholdPx) {
                            GestureDebug.log("AllApps", "Swipe down during drag, dismissing")
                            change.consumeAllChanges()
                            onDismiss()
                            return@drag
                        }
                        change.consumeAllChanges()
                    }
                }
            },
    ) {
        TopTabBar(
            selected = tab,
            onSelect = { tab = it },
        )

        if (tab != AllAppsTab.Settings) {
            AllAppsSearchBar(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (tab) {
            AllAppsTab.Settings -> {
                SettingsPane(
                    onSetAsHome = onSetAsHome,
                    onSetWallpaper = onSetWallpaper,
                    onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                    onShowNotificationPermissionDialog = onShowNotificationPermissionDialog,
                    themeStyle = themeStyle,
                    onSetThemeStyle = onSetThemeStyle,
                    colorTheme = colorTheme,
                    onSetColorTheme = onSetColorTheme,
                    homeTintStrength = homeTintStrength,
                    onSetHomeTintStrength = onSetHomeTintStrength,
                    onOpenJustTypeSettings = onOpenJustTypeSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(horizontal = 12.dp),
                )
            }
            else -> {
                AllAppsGrid(
                    items = filtered,
                    iconContent = iconContent,
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    onLocalDragActiveChange = { localDragActive = it },
                    onItemDragStart = onItemDragStart,
                    onItemDragMove = onItemDragMove,
                    onItemDragEnd = onItemDragEnd,
                    onItemDragCancel = onItemDragCancel,
                    gridState = gridState,
                    modifier = Modifier.weight(1f, fill = true),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllAppsGrid(
    items: List<LaunchPoint>,
    iconContent: @Composable (LaunchPoint) -> Unit,
    onItemClick: (LaunchPoint) -> Unit,
    onItemLongPress: (LaunchPoint) -> Unit,
    onLocalDragActiveChange: (Boolean) -> Unit,
    onItemDragStart: (LaunchPoint, Offset) -> Unit,
    onItemDragMove: (Offset) -> Unit,
    onItemDragEnd: (LaunchPoint, Offset) -> Unit,
    onItemDragCancel: () -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 92.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { lp ->
            var topLeftInRoot by remember(lp.id) { mutableStateOf(Offset.Zero) }
            LaunchPointTile(
                launchPoint = lp,
                iconContent = { iconContent(lp) },
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        topLeftInRoot = coords.positionInRoot()
                    }
                    .pointerInput(lp.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            GestureDebug.log("AllAppsTile", "Down on ${lp.id}")

                            val longPress = awaitLongPressOrCancellation(down.id)
                            if (longPress == null) {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    GestureDebug.log("AllAppsTile", "Tap detected on ${lp.id}")
                                    onItemClick(lp)
                                    up.consumeAllChanges()
                                }
                                return@awaitEachGesture
                            }

                            GestureDebug.log("AllAppsTile", "Long-press detected on ${lp.id}")
                            onLocalDragActiveChange(true)
                            var lastInRoot = topLeftInRoot + longPress.position
                            var dragStarted = false

                            try {
                                drag(longPress.id) { change ->
                                    change.consumeAllChanges()
                                    lastInRoot = topLeftInRoot + change.position
                                    if (!dragStarted) {
                                        dragStarted = true
                                        GestureDebug.log("AllAppsTile", "Drag started on ${lp.id}")
                                        onItemDragStart(lp, lastInRoot)
                                    }
                                    onItemDragMove(lastInRoot)
                                }

                                onLocalDragActiveChange(false)
                                if (dragStarted) {
                                    GestureDebug.log("AllAppsTile", "Drag ended on ${lp.id}")
                                    onItemDragEnd(lp, lastInRoot)
                                } else {
                                    GestureDebug.log("AllAppsTile", "Long-press menu on ${lp.id}")
                                    onItemLongPress(lp)
                                }
                            } catch (_: CancellationException) {
                                GestureDebug.log("AllAppsTile", "Drag cancelled on ${lp.id}")
                                onLocalDragActiveChange(false)
                                onItemDragCancel()
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun TopTabBar(
    selected: AllAppsTab,
    onSelect: (AllAppsTab) -> Unit,
) {
    val barBrush =
        remember {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF4B5157).copy(alpha = 0.9f),
                    Color(0xFF2F3439).copy(alpha = 0.9f),
                ),
            )
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(barBrush),
    ) {
        TabButton(
            label = "Apps",
            selected = selected == AllAppsTab.Apps,
            onClick = { onSelect(AllAppsTab.Apps) },
            modifier = Modifier.weight(1f),
        )
        TabButton(
            label = "Favorites",
            selected = selected == AllAppsTab.Favorites,
            onClick = { onSelect(AllAppsTab.Favorites) },
            modifier = Modifier.weight(1f),
        )
        TabButton(
            label = "Settings",
            selected = selected == AllAppsTab.Settings,
            onClick = { onSelect(AllAppsTab.Settings) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(0.dp)
    val bg =
        if (selected) {
            Color.Black.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        }

    TextButton(
        onClick = onClick,
        modifier = modifier
            .clip(shape)
            .background(bg),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.75f),
        )
    }
}

@Composable
private fun AllAppsSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = shape,
        backgroundColor = LauncherTheme.colors.allAppsSearchBarBackground,
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
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
            textStyle =
                TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPane(
    onSetAsHome: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onShowNotificationPermissionDialog: () -> Unit,
    themeStyle: LauncherThemeStyle,
    onSetThemeStyle: (LauncherThemeStyle) -> Unit,
    colorTheme: LauncherColorTheme,
    onSetColorTheme: (LauncherColorTheme) -> Unit,
    homeTintStrength: Float,
    onSetHomeTintStrength: (Float) -> Unit,
    onOpenJustTypeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        Text(
            text = "Launcher Settings",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.padding(vertical = 10.dp),
        )
        TextButton(onClick = onSetAsHome) {
            Text(
                text = "Set as home",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        TextButton(onClick = onSetWallpaper) {
            Text(
                text = "Set wallpaper",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        TextButton(onClick = onOpenNotificationAccessSettings) {
            Text(
                text = "Notification access",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        TextButton(onClick = onShowNotificationPermissionDialog) {
            Text(
                text = "Show notification permission dialog",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }

        // ── Glass Type ──────────────────────────────────────────────────
        Text(
            text = "Glass Type",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            GlassTypeChip(
                label = "Smoky",
                selected = themeStyle == LauncherThemeStyle.SMOKY_GLASS,
                glassStyle = LauncherThemeStyle.SMOKY_GLASS,
                tintColor = Color(colorTheme.primaryArgb),
                onClick = { onSetThemeStyle(LauncherThemeStyle.SMOKY_GLASS) },
            )
            GlassTypeChip(
                label = "Crystal",
                selected = themeStyle == LauncherThemeStyle.CRYSTAL_GLASS,
                glassStyle = LauncherThemeStyle.CRYSTAL_GLASS,
                tintColor = Color(colorTheme.primaryArgb),
                onClick = { onSetThemeStyle(LauncherThemeStyle.CRYSTAL_GLASS) },
            )
        }

        // ── Color Theme ─────────────────────────────────────────────────
        Text(
            text = "Color Theme",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        )
        val themes = remember { LauncherColorTheme.entries }
        // Flow layout: wrap chips in rows
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            themes.forEach { theme ->
                ColorThemeChip(
                    theme = theme,
                    selected = colorTheme == theme,
                    onClick = { onSetColorTheme(theme) },
                )
            }
        }

        // ── Home Screen Tint Strength ───────────────────────────────────
        Text(
            text = "Home screen tint strength",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        )
        androidx.compose.material3.Slider(
            value = homeTintStrength,
            onValueChange = onSetHomeTintStrength,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.60f),
                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
            ),
        )

        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onOpenJustTypeSettings) {
            Text(
                text = "Just Type settings",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * A small glass-effect chip previewing the given glass type with the current tint color.
 */
@Composable
private fun GlassTypeChip(
    label: String,
    selected: Boolean,
    glassStyle: LauncherThemeStyle,
    tintColor: Color,
    onClick: () -> Unit,
) {
    val smoky = glassStyle == LauncherThemeStyle.SMOKY_GLASS
    val chipAlpha = if (smoky) 0.30f else 0.22f
    val strokeColor = if (smoky) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.35f)
    val strokeWidth = if (smoky) 0.5.dp else 1.dp
    val shape = RoundedCornerShape(12.dp)

    val borderMod = if (selected) {
        Modifier.border(
            width = 2.dp,
            color = Color.White.copy(alpha = 0.85f),
            shape = shape,
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .then(borderMod)
            .clip(shape)
            .background(tintColor.copy(alpha = chipAlpha))
            .then(
                if (strokeWidth > 0.dp) Modifier.border(strokeWidth, strokeColor, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.80f),
        )
    }
}

/**
 * A small colored chip showing the theme's primary color with its name.
 */
@Composable
private fun ColorThemeChip(
    theme: LauncherColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = Color(theme.primaryArgb)
    val shape = RoundedCornerShape(10.dp)

    val borderMod = if (selected) {
        Modifier.border(
            width = 2.dp,
            color = Color.White.copy(alpha = 0.85f),
            shape = shape,
        )
    } else {
        Modifier
    }

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .then(borderMod)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // Color swatch
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(primary)
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(5.dp),
                ),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.78f),
        )
    }
}
