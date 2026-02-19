package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.model.JustTypeUiState
import com.lunasysman.launcher.core.model.HomeIconPlacement
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.ui.home.theme.LauncherTheme
import com.lunasysman.launcher.ui.home.theme.GlassSurface
import kotlin.math.roundToInt

/**
 * Centralized gesture lock to coordinate between multiple gesture handlers.
 *
 * Prevents simultaneous gesture handling across surface, icon, and widget interactions.
 * A handler must acquire the lock before processing gestures and release it when done.
 *
 * Example: When a user touches an icon, the icon gesture handler acquires the lock,
 * preventing the home surface swipe gesture from interfering.
 */
@Stable
internal class GestureLock {
    var owner by mutableStateOf<String?>(null)
        private set

    fun tryAcquire(tag: String): Boolean {
        if (owner == null || owner == tag) {
            owner = tag
            return true
        }
        return false
    }

    fun release(tag: String) {
        if (owner == tag) owner = null
    }

    fun isHeldByOther(tag: String): Boolean = owner != null && owner != tag
}

@Composable
fun HomeScreen(
    homeIcons: List<Pair<LaunchPoint, HomeIconPlacement>>,
    onUpdateHomeIcon: (launchPointId: String, xNorm: Double, yNorm: Double, rotationDeg: Float) -> Unit,
    homeGridIconContent: @Composable (LaunchPoint) -> Unit,
    onHomeSlotClick: (LaunchPoint) -> Unit,
    onHomeSlotLongPress: (LaunchPoint) -> Unit,
    dockItems: List<LaunchPoint>,
    dockIconContent: @Composable (LaunchPoint) -> Unit,
    onDockItemClick: (LaunchPoint) -> Unit,
    onDockItemLongPress: (LaunchPoint) -> Unit,
    onOpenAllApps: () -> Unit,
    searchOpen: Boolean,
    searchQuery: String,
    justTypeState: JustTypeUiState,
    launchPointsById: Map<String, LaunchPoint>,
    searchResultIconContent: @Composable (LaunchPoint) -> Unit,
    onOpenSearch: () -> Unit,
    onDismissSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchItemClick: (JustTypeItemUi) -> Unit,
    onNotificationActionClick: (notificationKey: String, action: JustTypeItemUi.NotificationActionUi) -> Unit,
    onSwipeDownSearch: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenDeck: () -> Unit = {},
    modifier: Modifier = Modifier,
    dockTargetModifier: Modifier = Modifier,
    gridTargetModifier: Modifier = Modifier,
) {
    val dockBarHeight = 128.dp
    val chevronGap = 10.dp
    val frameStroke = 2.dp
    val frameColor = Color.White.copy(alpha = 0.28f)
    val bottomInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
    val density = LocalDensity.current
    val statusBarHeightPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
    val navBarHeightPx = with(density) { WindowInsets.navigationBars.getBottom(this).toFloat() }
    val frameCornerRadius = 26.dp
    val frameShape = RoundedCornerShape(
        topStart = frameCornerRadius,
        topEnd = frameCornerRadius,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )

    // Wallpaper is shown by the Activity theme (`windowShowWallpaper=true`).
    var editMode by rememberSaveable { mutableStateOf(false) }
    var selectedIconId by rememberSaveable { mutableStateOf<String?>(null) }

    // Centralized gesture thresholds
    val thresholds = rememberGestureThresholds()

    val gestureLock = remember { GestureLock() }

    // Unified gesture modifier for Home surface using extracted gesture handler.
    // Handles: swipe up (All Apps), swipe down (Search), long-press background (Edit Mode)
    val homeSurfaceGestureModifier = Modifier.homeSurfaceGestures(
        gestureLock = gestureLock,
        thresholds = thresholds,
        enabled = !searchOpen && !editMode,
        onSwipeUp = onOpenAllApps,
        onSwipeDown = onSwipeDownSearch,
        onLongPress = {
            editMode = true
            selectedIconId = null
        },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(homeSurfaceGestureModifier),
    ) {
        LaunchedEffect(searchOpen) {
            if (searchOpen) {
                editMode = false
                selectedIconId = null
            }
        }
        // Thin border “frame” from dock top up to status bar, with rounded top corners.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = dockBarHeight)
                .windowInsetsPadding(bottomInsets)
                .windowInsetsPadding(WindowInsets.statusBars)
                .clip(frameShape)
                // Draw only 3 sides of the frame: top + left + right (no bottom border on the dock line).
                .drawBehind {
                    val strokePx = frameStroke.toPx()
                    if (strokePx <= 0f) return@drawBehind

                    val half = strokePx / 2f
                    val radiusPx = frameCornerRadius.toPx().coerceAtLeast(0f)
                    val cornerSize = (radiusPx * 2f - strokePx).coerceAtLeast(0f)

                    // Lines (inset by half stroke so they don't get clipped).
                    val leftX = half
                    val rightX = size.width - half
                    val topY = half
                    val bottomY = size.height
                    val topLeftX = radiusPx
                    val topRightX = size.width - radiusPx
                    val verticalStartY = radiusPx

                    drawLine(
                        color = frameColor,
                        start = androidx.compose.ui.geometry.Offset(topLeftX, topY),
                        end = androidx.compose.ui.geometry.Offset(topRightX, topY),
                        strokeWidth = strokePx,
                    )
                    drawLine(
                        color = frameColor,
                        start = androidx.compose.ui.geometry.Offset(leftX, verticalStartY),
                        end = androidx.compose.ui.geometry.Offset(leftX, bottomY),
                        strokeWidth = strokePx,
                    )
                    drawLine(
                        color = frameColor,
                        start = androidx.compose.ui.geometry.Offset(rightX, verticalStartY),
                        end = androidx.compose.ui.geometry.Offset(rightX, bottomY),
                        strokeWidth = strokePx,
                    )

                    // Rounded top corners.
                    if (cornerSize > 0f) {
                        val arcStroke = Stroke(width = strokePx)
                        val leftArc =
                            Rect(
                                left = half,
                                top = half,
                                right = half + cornerSize,
                                bottom = half + cornerSize,
                            )
                        val rightArc =
                            Rect(
                                left = size.width - half - cornerSize,
                                top = half,
                                right = size.width - half,
                                bottom = half + cornerSize,
                            )

                        drawArc(
                            color = frameColor,
                            startAngle = 180f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = leftArc.topLeft,
                            size = leftArc.size,
                            style = arcStroke,
                        )
                        drawArc(
                            color = frameColor,
                            startAngle = 270f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = rightArc.topLeft,
                            size = rightArc.size,
                            style = arcStroke,
                        )
                    }
                },
        )

        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(searchOpen) {
            if (searchOpen) searchFocusRequester.requestFocus()
        }

        val hasResults = searchOpen && justTypeState.sections.any { it.items.isNotEmpty() }

        // ── Search panel backing (search bar + results as a single unit) ──
        // When search is open, a solid backing sits behind the bar and results,
        // matching how the Widget Deck wraps its search UI.
        // ── Search panel backing (search bar + results as a single unit) ──
        // When search is open, a solid backing sits behind the bar and results,
        // matching how the Widget Deck wraps its search UI.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .padding(top = 38.dp, start = 18.dp, end = 18.dp)
                .then(
                    if (searchOpen) Modifier
                        .clip(RoundedCornerShape(
                            topStart = 26.dp, topEnd = 26.dp,
                            bottomStart = if (hasResults) 22.dp else 26.dp,
                            bottomEnd = if (hasResults) 22.dp else 26.dp,
                        ))
                        .background(LauncherTheme.colors.justTypePanelBacking)
                    else Modifier
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopSearchBar(
                    modifier = Modifier,
                    squareBottom = hasResults,
                    placeholderAlpha = if (searchOpen && searchQuery.isNotBlank()) 0f else 0.92f,
                    onClick = { if (!searchOpen) onOpenSearch() },
                )

                if (searchOpen && hasResults) {
                    SearchResultsPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp),
                        state = justTypeState,
                        launchPointsById = launchPointsById,
                        iconContent = searchResultIconContent,
                        onItemClick = onSearchItemClick,
                        onNotificationActionClick = onNotificationActionClick,
                    )
                }
            }

            if (searchOpen) {
                // Text input layered on top of the search bar.
                Row(
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.92f)),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                    )
                    Spacer(modifier = Modifier.width(26.dp))
                }
            }
        }

        if (searchOpen) {
            // Scrim behind Just Type — dismisses search when tapped.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(LauncherTheme.colors.justTypeScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissSearch,
                    ),
            )
        }

        HomeCanvas(
            icons = homeIcons,
            gestureLock = gestureLock,
            thresholds = thresholds,
            selectedIconId = selectedIconId,
            editMode = editMode,
            onSelectIcon = { selectedIconId = it },
            onUpdateIcon = onUpdateHomeIcon,
            iconContent = homeGridIconContent,
            modifier = gridTargetModifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(bottomInsets)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 110.dp,
                    bottom = dockBarHeight + chevronGap + 38.dp + 10.dp,
                ),
            onItemClick = onHomeSlotClick,
            onItemLongPress = onHomeSlotLongPress,
        )

        BottomAppBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .windowInsetsPadding(bottomInsets),
            dockTargetModifier = dockTargetModifier,
            dockItems = dockItems,
            dockIconContent = dockIconContent,
            onDockItemClick = onDockItemClick,
            onDockItemLongPress = onDockItemLongPress,
            onAllApps = onOpenAllApps,
            barHeight = dockBarHeight,
        )

        if (editMode && !searchOpen) {
            HomeEditBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(7f)
                    .windowInsetsPadding(bottomInsets)
                    .padding(bottom = dockBarHeight + chevronGap + 10.dp, start = 18.dp, end = 18.dp),
                onWidgets = onOpenWidgets,
                onLauncherSettings = onOpenLauncherSettings,
                onDismiss = { editMode = false },
            )
        }

        ChevronHandle(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(bottomInsets)
                .padding(bottom = dockBarHeight + chevronGap),
            onClick = onOpenAllApps,
            onLongPress = onOpenDeck,
        )

        // WebOS-style dimmed wallpaper outside the framed home area, matching the rounded frame corners.
        // Dock is part of the home area and should remain undimmed.
        val gutterTint = LauncherTheme.colors.homeGutterScrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(8f)
                .drawWithCache {
                    val cutout =
                        Rect(
                            left = 0f,
                            top = statusBarHeightPx,
                            right = size.width,
                            bottom = size.height - navBarHeightPx,
                        )
                    val topRadius = frameCornerRadius.toPx()
                    val bottomRadius = 28.dp.toPx()
                    val cutoutPath =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = cutout,
                                    topLeft = CornerRadius(topRadius, topRadius),
                                    topRight = CornerRadius(topRadius, topRadius),
                                    bottomLeft = CornerRadius(bottomRadius, bottomRadius),
                                    bottomRight = CornerRadius(bottomRadius, bottomRadius),
                                ),
                            )
                        }

                    onDrawBehind {
                        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                            drawRect(color = gutterTint)
                        }
                    }
                },
        )
    }
}

@Composable
private fun HomeEditBar(
    modifier: Modifier = Modifier,
    onWidgets: () -> Unit,
    onLauncherSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LauncherTheme.colors.homeBarBacking),
        shape = shape,
        backgroundColor = LauncherTheme.colors.homeEditBarBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Widgets",
                style = MaterialTheme.typography.titleMedium,
                color = LauncherTheme.colors.homeEditBarPrimaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onWidgets)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Launcher Settings",
                style = MaterialTheme.typography.titleMedium,
                color = LauncherTheme.colors.homeEditBarPrimaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onLauncherSettings)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "CLOSE",
                style = MaterialTheme.typography.labelLarge,
                color = LauncherTheme.colors.homeEditBarSecondaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun HomeCanvas(
    icons: List<Pair<LaunchPoint, HomeIconPlacement>>,
    gestureLock: GestureLock,
    thresholds: GestureThresholds,
    selectedIconId: String?,
    editMode: Boolean,
    onSelectIcon: (String?) -> Unit,
    onUpdateIcon: (launchPointId: String, xNorm: Double, yNorm: Double, rotationDeg: Float) -> Unit,
    iconContent: @Composable (LaunchPoint) -> Unit,
    modifier: Modifier,
    onItemClick: (LaunchPoint) -> Unit,
    onItemLongPress: (LaunchPoint) -> Unit,
) {
    val columns = 7
    val rows = 9
    val spacing = 10.dp
    val maxTile = 88.dp

    BoxWithConstraints(modifier = modifier) {
        val gridWidth = maxWidth
        val gridHeight = maxHeight
        val maxCellWidth = (gridWidth - spacing * (columns - 1)) / columns
        val maxCellHeight = (gridHeight - spacing * (rows - 1)) / rows
        val unconstrained = if (maxCellWidth < maxCellHeight) maxCellWidth else maxCellHeight
        val tileSize = if (unconstrained < maxTile) unconstrained else maxTile
        val verticalSpacing =
            if (rows <= 1) {
                0.dp
            } else {
                val remaining = gridHeight - (tileSize * rows)
                val candidate = remaining / (rows - 1)
                if (candidate < 0.dp) 0.dp else candidate
            }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
        ) {
            val density = LocalDensity.current
            val iconSizeDp = 72.dp
            val iconSizePx = with(density) { iconSizeDp.toPx() }
            val maxX = (with(density) { gridWidth.toPx() } - iconSizePx).coerceAtLeast(1f)
            val maxY = (with(density) { gridHeight.toPx() } - iconSizePx).coerceAtLeast(1f)

            val uniqueIcons =
                remember(icons) {
                    icons
                        .groupBy { (lp, _) -> lp.id }
                        .values
                        .map { group -> group.maxBy { (_, placement) -> placement.updatedAtEpochMs } }
                }

            // Shared icon state registry for surface-level rotation targeting
            val iconStates = remember { EditModeIconStates() }

            // Clear all drag states when exiting edit mode
            LaunchedEffect(editMode) {
                if (!editMode) iconStates.clearAll()
            }

            // Surface-level two-finger rotation (only active in edit mode).
            // Uses PointerEventPass.Initial so it sees events before per-icon handlers.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .editModeCanvasGestures(
                        editMode = editMode,
                        gestureLock = gestureLock,
                        iconStates = iconStates,
                        thresholds = thresholds,
                        onSelectIcon = onSelectIcon,
                        onUpdateIcon = onUpdateIcon,
                        maxX = maxX,
                        maxY = maxY,
                    ),
            )

            // Absolute-positioned icons (overlap allowed).
            // Single-finger drag on each icon; two-finger rotation handled at the surface.
            uniqueIcons.forEach { (lp, placement) ->
                key(lp.id) {
                    // Live drag state - null when not dragging, uses DB position
                    val dragState = remember(lp.id) { mutableStateOf<IconDragState?>(null) }

                    // Base position from DB (stable — only changes on persist)
                    val baseXPx = placement.xNorm.coerceIn(0.0, 1.0).toFloat() * maxX
                    val baseYPx = placement.yNorm.coerceIn(0.0, 1.0).toFloat() * maxY
                    val baseRotDeg = placement.rotationDeg

                    // Register this icon's state and base position for surface rotation
                    val baseCenterX = baseXPx + iconSizePx / 2f
                    val baseCenterY = baseYPx + iconSizePx / 2f
                    SideEffect {
                        iconStates.register(
                            iconId = lp.id,
                            dragState = dragState,
                            baseCenterPx = Offset(baseCenterX, baseCenterY),
                            initialRotationDeg = placement.rotationDeg,
                            initialXNorm = placement.xNorm,
                            initialYNorm = placement.yNorm,
                        )
                    }
                    DisposableEffect(lp.id) {
                        onDispose { iconStates.unregister(lp.id) }
                    }

                    Box(
                        modifier = Modifier
                            .zIndex(3f + (placement.zIndex.toFloat() / 10_000f))
                            // Base position from DB (recomposition only on persist)
                            .offset { IntOffset(baseXPx.roundToInt(), baseYPx.roundToInt()) }
                            .size(iconSizeDp)
                            // Read dragState in graphicsLayer — runs at draw phase,
                            // skipping recomposition and re-layout on every pointer event.
                            .graphicsLayer {
                                val ds = dragState.value
                                if (ds != null) {
                                    translationX = ds.xPx - baseXPx
                                    translationY = ds.yPx - baseYPx
                                    rotationZ = ds.rotationDeg
                                } else {
                                    translationX = 0f
                                    translationY = 0f
                                    rotationZ = baseRotDeg
                                }
                            }
                            .then(
                                if (editMode) {
                                    Modifier.iconDrag(
                                        iconId = lp.id,
                                        gestureLock = gestureLock,
                                        thresholds = thresholds,
                                        maxX = maxX,
                                        maxY = maxY,
                                        initialXNorm = placement.xNorm,
                                        initialYNorm = placement.yNorm,
                                        initialRotationDeg = placement.rotationDeg,
                                        dragState = dragState,
                                        onSelect = onSelectIcon,
                                        onUpdate = onUpdateIcon,
                                    )
                                } else {
                                    Modifier.combinedClickableCompat(
                                        onClick = { onItemClick(lp) },
                                        onLongPress = { onItemLongPress(lp) },
                                    )
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        iconContent(lp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomAppBar(
    modifier: Modifier,
    dockTargetModifier: Modifier,
    dockItems: List<LaunchPoint>,
    dockIconContent: @Composable (LaunchPoint) -> Unit,
    onDockItemClick: (LaunchPoint) -> Unit,
    onDockItemLongPress: (LaunchPoint) -> Unit,
    onAllApps: () -> Unit,
    barHeight: Dp,
) {
    val dockShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp,
    )
    val dockTileSize = 88.dp
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(dockShape)
            .background(LauncherTheme.colors.homeBarBacking),
        shape = dockShape,
        backgroundColor = LauncherTheme.colors.dockBackground,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = dockTargetModifier
                .fillMaxWidth()
                .height(dockTileSize)
                .padding(horizontal = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dockItems.isEmpty()) {
                // Keep it visually intentional even with no pinned apps yet.
                Text(
                    text = "Drag apps here",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    items(dockItems, key = { it.id }) { lp ->
                        Box(
                            modifier = Modifier
                                .size(dockTileSize)
                                .clip(RoundedCornerShape(26.dp))
                                .combinedClickableCompat(
                                    onClick = { onDockItemClick(lp) },
                                    onLongPress = { onDockItemLongPress(lp) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            dockIconContent(lp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChevronHandle(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(width = 65.dp, height = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0f))
            .combinedClickableCompat(
                onClick = onClick,
                onLongPress = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = "All apps",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(29.dp),
        )
    }
}
