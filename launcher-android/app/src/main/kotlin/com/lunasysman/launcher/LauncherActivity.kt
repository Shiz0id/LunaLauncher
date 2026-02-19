package com.lunasysman.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasysman.launcher.apps.android.AndroidLaunchException
import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.notifications.NotificationActionExecutor
import com.lunasysman.launcher.core.justtype.notifications.NotificationPermissionHelper
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.core.model.LaunchPointAction
import com.lunasysman.launcher.core.model.LauncherColorTheme
import com.lunasysman.launcher.core.model.LauncherThemeStyle
import com.lunasysman.launcher.deck.DeckViewModel
import com.lunasysman.launcher.ui.appmenu.LaunchPointMenuSheet
import com.lunasysman.launcher.ui.home.NotificationPermissionDialog
import com.lunasysman.launcher.ui.home.AllAppsScreen
import com.lunasysman.launcher.ui.home.AllAppsTab
import com.lunasysman.launcher.ui.home.HomeScreen
import com.lunasysman.launcher.ui.home.WidgetDeckOverlay
import com.lunasysman.launcher.ui.home.theme.DEFAULT_HOME_TINT_STRENGTH
import com.lunasysman.launcher.ui.home.theme.LunaLauncherTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class MenuSource {
    AllApps,
    Dock,
    HomeGrid,
}

private data class MenuTarget(
    val id: String,
    val source: MenuSource,
)

private fun slotIndexForDrop(
    positionInRoot: Offset,
    gridBoundsInRoot: Rect,
    columns: Int,
    rows: Int,
): Int? {
    if (columns <= 0 || rows <= 0) return null
    val relX = (positionInRoot.x - gridBoundsInRoot.left).coerceIn(0f, gridBoundsInRoot.width)
    val relY = (positionInRoot.y - gridBoundsInRoot.top).coerceIn(0f, gridBoundsInRoot.height)
    if (gridBoundsInRoot.width <= 0f || gridBoundsInRoot.height <= 0f) return null
    val col = ((relX / gridBoundsInRoot.width) * columns).toInt().coerceIn(0, columns - 1)
    val row = ((relY / gridBoundsInRoot.height) * rows).toInt().coerceIn(0, rows - 1)
    return row * columns + col
}

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind system bars so wallpaper shows under the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val container = (application as LauncherApplication).container
        setContent {
            MaterialTheme {
                LauncherRoot(
                    container = container,
                    startActivitySafely = { intent ->
                        try {
                            startActivity(intent)
                            true
                        } catch (e: ActivityNotFoundException) {
                            Log.e("LunaLauncher", "Activity not found: ${intent.toDebugString()}", e)
                            Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
                            false
                        } catch (e: SecurityException) {
                            Log.e("LunaLauncher", "Security exception launching: ${intent.toDebugString()}", e)
                            Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
                            false
                        } catch (e: Exception) {
                            Log.e("LunaLauncher", "Failed to launch intent: ${intent.toDebugString()}", e)
                            Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
                            false
                        }
                    },
                )
            }
        }
    }
}

private fun Intent.toDebugString(): String =
    buildString {
        append("action=").append(action)
        append(", component=").append(component?.flattenToShortString())
        append(", data=").append(dataString)
        append(", categories=").append(categories?.joinToString(prefix = "[", postfix = "]"))
        append(", flags=0x").append(flags.toString(16))
    }

@Composable
private fun LauncherRoot(
    container: LauncherContainer,
    startActivitySafely: (Intent) -> Boolean,
) {
    val vm: LauncherViewModel = viewModel(factory = LauncherViewModel.factory(container))
    val deckVm: DeckViewModel = viewModel(
        factory = DeckViewModel.factory(
            repository = container.deckRepository,
            widgetHost = container.deckWidgetHost,
            bitmapCache = container.deckBitmapCache,
        ),
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val notificationExecutor =
        remember(container.notificationIndexer, context) {
            NotificationActionExecutor(
                context = context.applicationContext,
                indexer = container.notificationIndexer,
            )
        }

    val requestContactsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.setContactsPermissionGranted(granted)
        }

    // Deck state (declared here so widgetPickerLauncher can reference it).
    var deckOpen by rememberSaveable { mutableStateOf(false) }
    val deckCards by deckVm.cardsWithWidgets.collectAsStateWithLifecycle()
    val deckCurrentPage by deckVm.currentPage.collectAsStateWithLifecycle()

    // Deck-local search state (independent from HomeScreen's searchOpen).
    var deckSearchOpen by rememberSaveable { mutableStateOf(false) }
    var deckSearchQuery by rememberSaveable { mutableStateOf("") }

    var searchOpen by rememberSaveable { mutableStateOf(false) }

    // Compute Just Type results from the deck's own query.
    LaunchedEffect(deckSearchOpen, deckSearchQuery) {
        if (deckSearchOpen) {
            vm.setSearchQuery(deckSearchQuery)
        }
    }
    // Clear deck search when deck closes.
    LaunchedEffect(deckOpen) {
        if (!deckOpen) {
            deckSearchOpen = false
            deckSearchQuery = ""
            vm.setSearchActive(searchOpen) // keep active if home search is still open
        }
    }

    // Keep AppWidgetHost in sync with Activity lifecycle while the deck is open.
    // If the system pauses/stops the Activity (screen off, incoming call, etc.)
    // and then resumes it, we need to re-call startListening() so widgets refresh.
    // We call the host directly rather than onDeckOpened/onDeckClosed to avoid
    // clearing the bitmap cache on a simple lifecycle pause.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, deckOpen) {
        if (!deckOpen) return@DisposableEffect onDispose {}

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> deckVm.widgetHost.startListening()
                Lifecycle.Event.ON_STOP -> deckVm.widgetHost.stopListening()
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Widget picker result → add widget to the current (or new) deck card.
    val widgetPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val data = result.data ?: return@rememberLauncherForActivityResult
            val appWidgetId = data.getIntExtra(WidgetsPickerActivity.EXTRA_APP_WIDGET_ID, -1)
            val provider = data.getStringExtra(WidgetsPickerActivity.EXTRA_PROVIDER) ?: return@rememberLauncherForActivityResult
            val widthDp = data.getIntExtra(WidgetsPickerActivity.EXTRA_WIDTH_DP, 160)
            val heightDp = data.getIntExtra(WidgetsPickerActivity.EXTRA_HEIGHT_DP, 120)
            if (appWidgetId < 0) return@rememberLauncherForActivityResult

            val cards = deckVm.cardsWithWidgets.value
            if (cards.isNotEmpty()) {
                // Add to the current foreground card.
                val page = deckVm.currentPage.value.coerceIn(cards.indices)
                val cardId = cards[page].first.cardId
                deckVm.addWidget(appWidgetId, cardId, provider, widthDp, heightDp)
            } else {
                // No cards yet — create one, then add the widget.
                deckVm.createCard { newCardId ->
                    if (newCardId > 0) {
                        deckVm.addWidget(appWidgetId, newCardId, provider, widthDp, heightDp)
                    }
                }
            }

            // Make sure the deck is open so the user sees the result.
            if (!deckOpen) {
                deckVm.onDeckOpened()
                deckOpen = true
            }
        }

    // Notification permission onboarding
    val prefs = remember { context.getSharedPreferences("luna_prefs", Context.MODE_PRIVATE) }
    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var themeStyleName by rememberSaveable {
        mutableStateOf(prefs.getString("theme_style", LauncherThemeStyle.SMOKY_GLASS.name) ?: LauncherThemeStyle.SMOKY_GLASS.name)
    }
    val themeStyle =
        remember(themeStyleName) {
            runCatching { LauncherThemeStyle.valueOf(themeStyleName) }.getOrDefault(LauncherThemeStyle.SMOKY_GLASS)
        }
    var colorThemeName by rememberSaveable {
        mutableStateOf(prefs.getString("color_theme", LauncherColorTheme.SMOKE.name) ?: LauncherColorTheme.SMOKE.name)
    }
    val colorTheme =
        remember(colorThemeName) {
            runCatching { LauncherColorTheme.valueOf(colorThemeName) }.getOrDefault(LauncherColorTheme.SMOKE)
        }
    var homeTintStrength by rememberSaveable {
        mutableStateOf(prefs.getFloat("home_tint_strength", DEFAULT_HOME_TINT_STRENGTH))
    }

    LaunchedEffect(Unit) {
        // Check if we should show the notification permission dialog
        val hasAsked = prefs.getBoolean(NotificationPermissionHelper.PREF_NOTIFICATION_PERMISSION_REQUESTED, false)
        val hasPermission = NotificationPermissionHelper.isNotificationAccessGranted(context)

        if (!hasAsked && !hasPermission) {
            showNotificationPermissionDialog = true
            prefs.edit().putBoolean(NotificationPermissionHelper.PREF_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        }
    }

    val appsItems by vm.appsItems.collectAsStateWithLifecycle()
    val favoritesItems by vm.favoritesItems.collectAsStateWithLifecycle()
    val dockItems by vm.dockItems.collectAsStateWithLifecycle()
    val homeIcons by vm.homeIcons.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val justTypeState by vm.justTypeState.collectAsStateWithLifecycle()
    val launchPointsById by vm.launchPointsById.collectAsStateWithLifecycle()

    var allAppsOpen by rememberSaveable { mutableStateOf(false) }

    // Drive window-level backdrop blur whenever Just Type search is open (API 31+).
    // setBackgroundBlurRadius() blurs the wallpaper layer behind the entire window,
    // which gives the Just Type panel a genuine frosted-glass backdrop at zero
    // per-surface cost. On API 30 the iOS-stack layers in GlassSurface carry the load.
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (searchOpen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(52)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(0)
            }
        }
    }

    var allAppsInitialTab by rememberSaveable { mutableStateOf(AllAppsTab.Apps) }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }

    var pendingReply by remember { mutableStateOf<Pair<String, JustTypeItemUi.NotificationActionUi>?>(null) }
    var pendingReplyText by rememberSaveable { mutableStateOf("") }

    var dragPayload by remember { mutableStateOf<LaunchPoint?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    val dragging = dragPayload != null && dragPosition != null

    var homeBounds by remember { mutableStateOf<Rect?>(null) }
    var dockBounds by remember { mutableStateOf<Rect?>(null) }
    var homeGridBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) {
        vm.refreshInstalledApps()
    }

    fun openSearch() {
        vm.setSearchQuery("")
        vm.setSearchActive(true)
        searchOpen = true
    }

    fun dismissSearch() {
        searchOpen = false
        vm.setSearchActive(deckSearchOpen) // keep active if deck search is still open
        vm.setSearchQuery("")
    }

    fun showNotificationResult(result: NotificationActionExecutor.ExecutionResult) {
        val message =
            when (result) {
                NotificationActionExecutor.ExecutionResult.Success -> "Done"
                is NotificationActionExecutor.ExecutionResult.NotificationDismissed -> result.message
                is NotificationActionExecutor.ExecutionResult.IntentCancelled -> result.message
                is NotificationActionExecutor.ExecutionResult.Error -> result.message
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                LauncherEvent.RequestContactsPermission -> requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    LunaLauncherTheme(style = themeStyle, colorTheme = colorTheme, homeTintStrength = homeTintStrength) {
        Box(modifier = Modifier) {
        LaunchedEffect(appsItems) {
            container.iconRepository.prefetch(appsItems.mapNotNull { it.iconKey }, maxCount = 36)
        }

        BackHandler(enabled = deckOpen && deckSearchOpen) {
            deckSearchOpen = false
            deckSearchQuery = ""
            vm.setSearchQuery("")
            vm.setSearchActive(searchOpen) // keep active if home search is still open
        }

        BackHandler(enabled = deckOpen && !deckSearchOpen) {
            deckVm.onDeckClosed()
            deckOpen = false
        }

        BackHandler(enabled = allAppsOpen && !searchOpen && menuTarget == null) {
            allAppsOpen = false
        }

        BackHandler(enabled = searchOpen) {
            dismissSearch()
        }

        BackHandler(enabled = dragging) {
            dragPayload = null
            dragPosition = null
        }

        fun launchById(id: String) {
            val intent =
                try {
                    vm.intentFor(id)
                } catch (e: Exception) {
                    Log.e("LunaLauncher", "intentFor failed for id=$id", e)
                    Toast.makeText(context, "App unavailable", Toast.LENGTH_SHORT).show()
                    if (e is AndroidLaunchException.Unresolvable) {
                        vm.hide(id, true)
                    }
                    return
                }

            val ok = startActivitySafely(intent)
            if (ok) vm.recordLaunched(id)
        }

        // Shared Just Type search handlers (used by both HomeScreen and WidgetDeckOverlay).
        val handleSearchItemClick: (JustTypeItemUi) -> Unit = { item ->
            when (item) {
                is JustTypeItemUi.LaunchPointItem -> {
                    dismissSearch()
                    launchById(item.lpId)
                }
                is JustTypeItemUi.NotificationItem -> {
                    dismissSearch()
                    scope.launch {
                        val result = notificationExecutor.executeContentIntent(item.notificationKey)
                        showNotificationResult(result)
                    }
                }
                else -> {
                    val intent = vm.onJustTypeItemClick(item)
                    if (intent != null) {
                        dismissSearch()
                        startActivitySafely(intent)
                    }
                }
            }
        }

        val handleNotificationActionClick: (String, JustTypeItemUi.NotificationActionUi) -> Unit = { notificationKey, action ->
            dismissSearch()
            if (action.requiresText) {
                pendingReply = notificationKey to action
                pendingReplyText = ""
            } else {
                scope.launch {
                    val result = notificationExecutor.executeAction(notificationKey, action.index, replyText = null)
                    showNotificationResult(result)
                }
            }
        }

        HomeScreen(
            homeIcons = homeIcons,
            onUpdateHomeIcon = { id, xNorm, yNorm, rot ->
                vm.updateHomeIconPosition(id, xNorm, yNorm, rot)
            },
            homeGridIconContent = { lp -> LaunchPointIcon(lp, container.iconRepository, size = 72.dp) },
            onHomeSlotClick = { lp -> launchById(lp.id) },
            onHomeSlotLongPress = { lp -> menuTarget = MenuTarget(lp.id, MenuSource.HomeGrid) },
            dockItems = dockItems,
            dockIconContent = { lp -> LaunchPointIcon(lp, container.iconRepository, size = 56.dp) },
            onDockItemClick = { lp -> launchById(lp.id) },
            onDockItemLongPress = { lp -> menuTarget = MenuTarget(lp.id, MenuSource.Dock) },
            onOpenAllApps = {
                allAppsInitialTab = AllAppsTab.Apps
                allAppsOpen = true
            },
            searchOpen = searchOpen,
            searchQuery = searchQuery,
            justTypeState = justTypeState,
            launchPointsById = launchPointsById,
            searchResultIconContent = { lp -> LaunchPointIcon(lp, container.iconRepository) },
            onOpenSearch = { openSearch() },
            onDismissSearch = { dismissSearch() },
            onSearchQueryChange = vm::setSearchQuery,
            onSearchItemClick = handleSearchItemClick,
            onNotificationActionClick = handleNotificationActionClick,
            onSwipeDownSearch = { openSearch() },
            onOpenWidgets = {
                widgetPickerLauncher.launch(Intent(context, WidgetsPickerActivity::class.java))
            },
            onOpenLauncherSettings = {
                dismissSearch()
                allAppsInitialTab = AllAppsTab.Settings
                allAppsOpen = true
            },
            onOpenDeck = {
                deckVm.onDeckOpened()
                deckOpen = true
            },
            modifier = Modifier.onGloballyPositioned { coords -> homeBounds = coords.boundsInRoot() },
            dockTargetModifier = Modifier.onGloballyPositioned { coords -> dockBounds = coords.boundsInRoot() },
            gridTargetModifier = Modifier.onGloballyPositioned { coords -> homeGridBounds = coords.boundsInRoot() },
        )

        // ── Widget Deck overlay ──
        if (deckOpen) {
            WidgetDeckOverlay(
                cards = deckCards,
                currentPage = deckCurrentPage,
                onPageChanged = { deckVm.setCurrentPage(it) },
                onCaptureCard = { cardId -> deckVm.captureCard(cardId) },
                createHostView = { appWidgetId, widthDp, heightDp ->
                    container.deckWidgetHost.createHostView(appWidgetId, widthDp, heightDp)
                },
                getCachedBitmap = { cardId ->
                    container.deckBitmapCache.get(cardId)
                },
                onCreateCard = { deckVm.createCard() },
                onRemoveWidget = { appWidgetId -> deckVm.removeWidget(appWidgetId) },
                onOpenWidgets = {
                    widgetPickerLauncher.launch(Intent(context, WidgetsPickerActivity::class.java))
                },
                onDismiss = {
                    deckVm.onDeckClosed()
                    deckOpen = false
                },
                searchOpen = deckSearchOpen,
                searchQuery = deckSearchQuery,
                justTypeState = justTypeState,
                launchPointsById = launchPointsById,
                searchResultIconContent = { lp -> LaunchPointIcon(lp, container.iconRepository) },
                onOpenSearch = {
                    deckSearchQuery = ""
                    deckSearchOpen = true
                    vm.setSearchActive(true)
                },
                onDismissSearch = {
                    deckSearchOpen = false
                    deckSearchQuery = ""
                    vm.setSearchQuery("")
                    vm.setSearchActive(searchOpen) // keep active if home search is still open
                },
                onSearchQueryChange = { deckSearchQuery = it },
                onSearchItemClick = handleSearchItemClick,
                onNotificationActionClick = handleNotificationActionClick,
                initialPage = deckVm.resolveInitialPage(),
            )
        }

        val reply = pendingReply
        if (reply != null) {
            val (notificationKey, action) = reply
            AlertDialog(
                onDismissRequest = { pendingReply = null },
                title = { Text(text = action.title) },
                text = {
                    TextField(
                        value = pendingReplyText,
                        onValueChange = { pendingReplyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(text = "Type a reply") },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = pendingReplyText.trim().isNotEmpty(),
                        onClick = {
                            val text = pendingReplyText.trim()
                            pendingReply = null
                            scope.launch {
                                val result = notificationExecutor.executeAction(notificationKey, action.index, replyText = text)
                                showNotificationResult(result)
                            }
                        },
                    ) {
                        Text("Send")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingReply = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (allAppsOpen || dragging) {
            AllAppsScreen(
                initialTab = allAppsInitialTab,
                apps = appsItems,
                favorites = favoritesItems,
                iconContent = { lp -> LaunchPointIcon(lp, container.iconRepository) },
                onItemClick = { lp -> launchById(lp.id) },
                onItemLongPress = { lp -> menuTarget = MenuTarget(lp.id, MenuSource.AllApps) },
                onSetAsHome = {
                    startActivitySafely(Intent(Settings.ACTION_HOME_SETTINGS))
                },
                onSetWallpaper = {
                    startActivitySafely(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SET_WALLPAPER),
                            "Set wallpaper",
                        ),
                    )
                },
                onOpenNotificationAccessSettings = {
                    NotificationPermissionHelper.openNotificationAccessSettings(context)
                },
                onShowNotificationPermissionDialog = {
                    showNotificationPermissionDialog = true
                },
                themeStyle = themeStyle,
                onSetThemeStyle = { style ->
                    themeStyleName = style.name
                    prefs.edit().putString("theme_style", style.name).apply()
                },
                colorTheme = colorTheme,
                onSetColorTheme = { theme ->
                    colorThemeName = theme.name
                    prefs.edit().putString("color_theme", theme.name).apply()
                },
                homeTintStrength = homeTintStrength,
                onSetHomeTintStrength = { strength ->
                    homeTintStrength = strength
                    prefs.edit().putFloat("home_tint_strength", strength).apply()
                },
                onOpenJustTypeSettings = {
                    startActivitySafely(Intent(context, JustTypeSettingsActivity::class.java))
                },
                onDismiss = { allAppsOpen = false },
                dragInProgress = dragging,
                onItemDragStart = { lp, startPos ->
                    menuTarget = null
                    searchOpen = false
                    vm.setSearchActive(deckSearchOpen)
                    dragPayload = lp
                    dragPosition = startPos
                },
                onItemDragMove = { pos ->
                    dragPosition = pos
                },
                onItemDragEnd = { lp, pos ->
                    val withinDock = dockBounds?.contains(pos) == true
                    val withinGrid = homeGridBounds?.contains(pos) == true
                    if (withinDock) {
                        if (dockItems.size >= 5) {
                            Toast.makeText(context, "Dock is full (5 apps)", Toast.LENGTH_SHORT).show()
                        } else {
                            vm.addToDock(lp.id)
                            allAppsOpen = false
                        }
                    } else if (withinGrid) {
                        val bounds = homeGridBounds
                        if (bounds != null) {
                            val iconHalfPx = with(density) { 72.dp.toPx() / 2f }
                            val width = bounds.width
                            val height = bounds.height
                            val maxX = (width - iconHalfPx * 2f).coerceAtLeast(1f)
                            val maxY = (height - iconHalfPx * 2f).coerceAtLeast(1f)
                            val xNorm = ((pos.x - bounds.left - iconHalfPx) / maxX).toDouble().coerceIn(0.0, 1.0)
                            val yNorm = ((pos.y - bounds.top - iconHalfPx) / maxY).toDouble().coerceIn(0.0, 1.0)
                            vm.placeInHomeAbsolute(lp.id, xNorm, yNorm)
                            allAppsOpen = false
                        }
                    }
                    dragPayload = null
                    dragPosition = null
                },
                onItemDragCancel = {
                    dragPayload = null
                    dragPosition = null
                },
                // Keep the same node alive during drag so the gesture doesn't get cancelled by recomposition.
                // Fade instead of swapping modifiers/nodes.
                modifier = Modifier.graphicsLayer {
                    alpha = if (dragging) 0.12f else 1f
                },
            )
        }

        if (dragging) {
            val lp = dragPayload!!
            val pos = dragPosition!!
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (pos.x - 32f).roundToInt(),
                            y = (pos.y - 32f).roundToInt(),
                        )
                    }
                    .graphicsLayer(alpha = 0.95f),
            ) {
                LaunchPointIcon(lp, container.iconRepository, size = 64.dp)
            }
        }

        // Notification permission onboarding dialog (and manual trigger from Settings)
        if (showNotificationPermissionDialog) {
            NotificationPermissionDialog(
                onEnableClick = {
                    showNotificationPermissionDialog = false
                    NotificationPermissionHelper.openNotificationAccessSettings(context)
                },
                onDismiss = {
                    showNotificationPermissionDialog = false
                },
            )
        }

        val current = menuTarget?.id?.let(vm::findById)
        if (current != null) {
            val actions = buildList {
                when (menuTarget?.source) {
                    MenuSource.Dock -> add(LaunchPointAction.RemoveFromDock)
                    MenuSource.HomeGrid -> add(LaunchPointAction.RemoveFromHome)
                    else -> Unit
                }
                addAll(vm.actionsFor(current.id))
            }

            LaunchPointMenuSheet(
                launchPoint = current,
                actions = actions,
                onDismiss = { menuTarget = null },
                onAction = { action ->
                    when (action) {
                        LaunchPointAction.Pin -> vm.pin(current.id, true)
                        LaunchPointAction.Unpin -> vm.pin(current.id, false)
                        LaunchPointAction.RemoveFromDock -> vm.removeFromDock(current.id)
                        LaunchPointAction.RemoveFromHome -> vm.removeFromHome(current.id)
                        LaunchPointAction.Hide -> {
                            vm.hide(current.id, true)
                            menuTarget = null
                        }
                        LaunchPointAction.Unhide -> {
                            vm.hide(current.id, false)
                            menuTarget = null
                        }
                        LaunchPointAction.AppInfo -> {
                            startActivitySafely(vm.appInfoIntent(current.id))
                            menuTarget = null
                        }
                        LaunchPointAction.Uninstall -> {
                            startActivitySafely(vm.uninstallIntent(current.id))
                            menuTarget = null
                        }
                    }
                    if (action == LaunchPointAction.Pin ||
                        action == LaunchPointAction.Unpin ||
                        action == LaunchPointAction.RemoveFromDock ||
                        action == LaunchPointAction.RemoveFromHome
                    ) {
                        menuTarget = null
                    }
                },
            )
        }
        }
    }
}

@Composable
private fun LaunchPointIcon(
    launchPoint: LaunchPoint,
    iconRepository: IconRepository,
    size: Dp = 56.dp,
) {
    val key = launchPoint.iconKey
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = key) {
        value = if (key == null) null else iconRepository.icon(key)
    }

    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                text = launchPoint.title.take(1).uppercase(),
                modifier = Modifier,
            )
        }
    }
}
