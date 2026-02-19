package com.lunasysman.launcher.ui.home

import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.model.JustTypeUiState
import com.lunasysman.launcher.core.model.DeckCard
import com.lunasysman.launcher.core.model.DeckWidget
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.ui.home.theme.GlassSurface
import com.lunasysman.launcher.ui.home.theme.LauncherTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * Full-screen overlay for the Widget Deck.
 *
 * Displays a horizontal pager of widget cards. Only the foreground card renders live widgets;
 * background cards show cached bitmaps for performance.
 *
 * @param cards List of (card, widgets) pairs.
 * @param currentPage The currently focused page index.
 * @param onPageChanged Called when the user swipes to a new page.
 * @param createHostView Factory to create a live AppWidgetHostView for a widget ID with size.
 * @param getCachedBitmap Retrieves a cached bitmap for a background card.
 * @param onCreateCard Creates a new empty card in the deck.
 * @param onRemoveWidget Removes a widget by its appWidgetId. Card is auto-deleted if empty.
 * @param onOpenWidgets Opens the widget picker to add widgets to the current card.
 * @param onDismiss Closes the deck overlay.
 * @param initialPage The page to start on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetDeckOverlay(
    cards: List<Pair<DeckCard, List<DeckWidget>>>,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    onCaptureCard: (cardId: Long) -> Unit,
    createHostView: (appWidgetId: Int, widthDp: Int, heightDp: Int) -> AppWidgetHostView?,
    getCachedBitmap: (cardId: Long) -> Bitmap?,
    onCreateCard: () -> Unit,
    onRemoveWidget: (appWidgetId: Int) -> Unit,
    onOpenWidgets: () -> Unit,
    onDismiss: () -> Unit,
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
    initialPage: Int = 0,
) {
    val bottomInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)

    // Scrim background that dismisses on tap.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )

    // ── Just Type state ──
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(bottomInsets),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Just Type search bar (fixed height, stays in column flow) ──
        val hasResults = searchOpen && justTypeState.sections.any { it.items.isNotEmpty() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 6.dp)
                .then(
                    if (searchOpen) Modifier
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = if (hasResults) 0.dp else 26.dp, bottomEnd = if (hasResults) 0.dp else 26.dp))
                        .background(Color(0xFF1E2226))
                    else Modifier
                ),
        ) {
            TopSearchBar(
                modifier = Modifier,
                squareBottom = searchOpen && justTypeState.sections.any { it.items.isNotEmpty() },
                placeholderAlpha = if (searchOpen && searchQuery.isNotBlank()) 0f else 0.92f,
                onClick = { if (!searchOpen) onOpenSearch() },
            )

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
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = LauncherTheme.colors.homeEditBarPrimaryText,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                    )
                    Spacer(modifier = Modifier.width(26.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (cards.isEmpty()) {
            // ── Empty state ──
            EmptyDeckContent(
                onAddWidgets = onOpenWidgets,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            // ── Card pager ──
            val pageCount = cards.size
            val pagerState = rememberPagerState(
                initialPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                pageCount = { pageCount },
            )

            var previousPage by remember { mutableStateOf(pagerState.currentPage) }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collectLatest { page ->
                    // Capture the card we're swiping away from.
                    if (page != previousPage && previousPage in cards.indices) {
                        val prevCardId = cards[previousPage].first.cardId
                        onCaptureCard(prevCardId)
                    }
                    previousPage = page
                    onPageChanged(page)
                }
            }

            // ── Stacked card deck ──
            // Background card peeks are rendered OUTSIDE the pager as
            // independent composables. The pager only holds the foreground
            // card for swipe gestures. This avoids HorizontalPager's
            // content clipping.
            val peekStepDp = 12.dp   // vertical offset between stacked cards
            val maxPeek = 2          // max visible cards behind the foreground
            val currentIdx = pagerState.currentPage

            // Build ordered list of cards to peek (those not currently shown).
            // Prefer cards after currentIdx, then wrap to cards before it.
            val peekIndices = buildList {
                for (i in 1..maxPeek) {
                    val idx = currentIdx + i
                    if (idx in cards.indices) add(idx)
                }
                // If we don't have enough from the right, fill from the left.
                if (size < maxPeek) {
                    for (i in 1..maxPeek) {
                        val idx = currentIdx - i
                        if (idx in cards.indices && idx !in this) add(idx)
                        if (size >= maxPeek) break
                    }
                }
            }
            val peekCount = peekIndices.size

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // ── Background card peeks (rendered behind foreground) ──
                // depth 1 = closest to foreground (slightly scaled/dimmed)
                // depth 2 = furthest back (more scaled/dimmed, peeking above)
                peekIndices.forEachIndexed { index, peekIdx ->
                    val depth = index + 1  // 1-based depth
                    val (peekCard, peekWidgets) = cards[peekIdx]
                    val peekScale = 1f - (depth * 0.04f)
                    val peekAlpha = (1f - (depth * 0.18f)).coerceAtLeast(0.3f)

                    DeckCardContent(
                        card = peekCard,
                        widgets = peekWidgets,
                        isForeground = false,
                        createHostView = createHostView,
                        onRemoveWidget = onRemoveWidget,
                        cachedBitmap = getCachedBitmap(peekCard.cardId),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            // Deeper cards are higher up (less top padding)
                            // so they peek from behind the foreground card.
                            .padding(top = peekStepDp * (peekCount - depth))
                            // Closer peeks draw on top of deeper peeks.
                            .zIndex((maxPeek - depth + 1).toFloat())
                            .graphicsLayer {
                                scaleX = peekScale
                                scaleY = peekScale
                                alpha = peekAlpha
                            },
                    )
                }

                // ── Foreground card (on top, inside pager for swipe) ──
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Push foreground down so background peeks are visible above.
                        .padding(top = peekStepDp * peekCount)
                        .zIndex(100f),
                    beyondViewportPageCount = 0,
                    pageSpacing = 16.dp,
                ) { page ->
                    val (card, widgets) = cards[page]
                    val isForeground = pagerState.currentPage == page

                    DeckCardContent(
                        card = card,
                        widgets = widgets,
                        isForeground = isForeground,
                        createHostView = createHostView,
                        onRemoveWidget = onRemoveWidget,
                        cachedBitmap = if (!isForeground) getCachedBitmap(card.cardId) else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                    )
                }
            }

            // ── Page indicator: "2 of 5" ──
            if (pageCount > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} of $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = LauncherTheme.colors.homeEditBarSecondaryText,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        // ── Bottom action bar ──
        val actionBarShape = RoundedCornerShape(18.dp)
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            shape = actionBarShape,
            backgroundColor = LauncherTheme.colors.homeEditBarBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Widget Deck",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = LauncherTheme.colors.homeEditBarPrimaryText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                // ── New card button ──
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = LauncherTheme.colors.homeEditBarPrimaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LauncherTheme.colors.justTypeChipBackground)
                        .clickable(onClick = onCreateCard)
                        .padding(2.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Widgets",
                    style = MaterialTheme.typography.titleMedium,
                    color = LauncherTheme.colors.homeEditBarPrimaryText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenWidgets)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    // ── Search results overlay (floats above the cards, fully opaque) ──
    if (searchOpen) {
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 58.dp)
                .padding(horizontal = 18.dp)
                .fillMaxHeight(0.55f)
                .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(Color(0xFF1E2226)),
        ) {
            SearchResultsPanel(
                modifier = Modifier.fillMaxSize(),
                state = justTypeState,
                launchPointsById = launchPointsById,
                iconContent = searchResultIconContent,
                onItemClick = onSearchItemClick,
                onNotificationActionClick = onNotificationActionClick,
            )
        }
    }
}

/**
 * Renders the content of a single deck card.
 *
 * When [isForeground], widgets are rendered live via [AndroidView].
 * When background, displays a [cachedBitmap] if available.
 */
@Composable
private fun DeckCardContent(
    card: DeckCard,
    widgets: List<DeckWidget>,
    isForeground: Boolean,
    createHostView: (appWidgetId: Int, widthDp: Int, heightDp: Int) -> AppWidgetHostView?,
    onRemoveWidget: (appWidgetId: Int) -> Unit,
    cachedBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    // Foreground card uses near-opaque background so peeks don't bleed through.
    val bgColor = if (isForeground) LauncherTheme.colors.deckForegroundCardBackground
        else LauncherTheme.colors.justTypeSectionCardBackground

    GlassSurface(
        modifier = modifier.clip(cardShape),
        shape = cardShape,
        backgroundColor = bgColor,
        contentAlignment = Alignment.Center,
    ) {
        if (widgets.isEmpty()) {
            Text(
                text = "Empty card\nTap \"Widgets\" to add",
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherTheme.colors.homeEditBarSecondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        } else if (isForeground) {
            // Live widget rendering.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                widgets.forEach { widget ->
                    key(widget.appWidgetId) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(widget.heightDp.dp),
                        ) {
                            LiveWidgetView(
                                appWidgetId = widget.appWidgetId,
                                widthDp = widget.widthDp,
                                heightDp = widget.heightDp,
                                createHostView = createHostView,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // ── Remove button ──
                            Text(
                                text = "✕",
                                fontSize = 12.sp,
                                color = LauncherTheme.colors.homeEditBarPrimaryText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(LauncherTheme.colors.dockBackground)
                                    .clickable { onRemoveWidget(widget.appWidgetId) }
                                    .padding(2.dp),
                            )
                        }
                    }
                }
            }
        } else if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            // Background card: show cached bitmap.
            Image(
                bitmap = cachedBitmap.asImageBitmap(),
                contentDescription = "Widget card preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alpha = 0.85f,
            )
        } else {
            // No cached bitmap yet (first time seeing this card).
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${widgets.size} widget${if (widgets.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LauncherTheme.colors.homeEditBarSecondaryText,
                )
            }
        }

        // Starred indicator.
        if (card.starred) {
            Text(
                text = "★",
                fontSize = 16.sp,
                color = Color(0xFFFFD54F).copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * Renders a single live widget via AndroidView.
 */
@Composable
private fun LiveWidgetView(
    appWidgetId: Int,
    widthDp: Int,
    heightDp: Int,
    createHostView: (appWidgetId: Int, widthDp: Int, heightDp: Int) -> AppWidgetHostView?,
    modifier: Modifier = Modifier,
) {
    var hostView by remember(appWidgetId) { mutableStateOf<AppWidgetHostView?>(null) }

    // Create the host view once, passing size so the provider gets correct options.
    DisposableEffect(appWidgetId) {
        hostView = createHostView(appWidgetId, widthDp, heightDp)
        onDispose {
            hostView = null
        }
    }

    val view = hostView
    val density = LocalDensity.current
    if (view != null) {
        AndroidView(
            factory = { view },
            update = { hostView ->
                // Set layout params so the widget knows its actual pixel size.
                val widthPx = with(density) { widthDp.dp.roundToPx() }
                val heightPx = with(density) { heightDp.dp.roundToPx() }
                hostView.layoutParams = android.widget.FrameLayout.LayoutParams(widthPx, heightPx)
            },
            modifier = modifier
                .clip(RoundedCornerShape(14.dp)),
        )
    } else {
        // Placeholder while widget loads or if it fails.
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(LauncherTheme.colors.justTypeChipBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Widget loading…",
                style = MaterialTheme.typography.bodySmall,
                color = LauncherTheme.colors.homeEditBarSecondaryText,
            )
        }
    }
}

/**
 * Empty deck onboarding content.
 */
@Composable
private fun EmptyDeckContent(
    onAddWidgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Your widget deck is empty",
            style = MaterialTheme.typography.headlineSmall,
            color = LauncherTheme.colors.homeEditBarPrimaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Add widgets to create your first card.\nSwipe left and right to shuffle between cards.",
            style = MaterialTheme.typography.bodyMedium,
            color = LauncherTheme.colors.homeEditBarSecondaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        val buttonShape = RoundedCornerShape(16.dp)
        GlassSurface(
            shape = buttonShape,
            backgroundColor = LauncherTheme.colors.justTypePillBackground,
        ) {
            Text(
                text = "Add Widgets",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LauncherTheme.colors.homeEditBarPrimaryText,
                modifier = Modifier
                    .clip(buttonShape)
                    .clickable(onClick = onAddWidgets)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}
