package com.lunasysman.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lunasysman.launcher.core.model.LauncherThemeStyle
import com.lunasysman.launcher.ui.home.theme.GlassSurface
import com.lunasysman.launcher.ui.home.theme.LauncherTheme
import com.lunasysman.launcher.ui.home.theme.LunaLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Widget picker that lists installed AppWidget providers, grouped by app.
 *
 * When the user selects a widget, this activity handles:
 * 1. Allocating an appWidgetId via DeckWidgetHost
 * 2. Binding the provider (requesting permission if needed)
 * 3. Launching the optional widget configure activity
 * 4. Returning the result (appWidgetId, provider, size) via setResult()
 *
 * The caller (LauncherActivity) receives the result and persists the widget to the deck.
 */
class WidgetsPickerActivity : ComponentActivity() {
    private val container by lazy { (application as LauncherApplication).container }

    private var pendingAppWidgetId: Int? = null
    private var pendingProviderInfo: AppWidgetProviderInfo? = null

    private val bindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingAppWidgetId
            if (result.resultCode != RESULT_OK || id == null) {
                cleanupPending()
                return@registerForActivityResult
            }
            // Verify binding actually happened.
            val provider = pendingProviderInfo?.provider
            if (container.deckWidgetHost.appWidgetManager.getAppWidgetInfo(id) == null && provider != null) {
                container.deckWidgetHost.bindWidgetIfAllowed(id, provider)
            }
            if (container.deckWidgetHost.appWidgetManager.getAppWidgetInfo(id) == null) {
                cleanupPending()
                return@registerForActivityResult
            }
            maybeConfigureOrFinish(id)
        }

    private val configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingAppWidgetId
            if (result.resultCode != RESULT_OK || id == null) {
                cleanupPending()
                return@registerForActivityResult
            }
            finishWithResult(id)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind system bars so wallpaper/scrim shows through.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val widgetHost = container.deckWidgetHost
        val pm = packageManager

        // Build grouped widget data.
        val groups = widgetHost.appWidgetManager.installedProviders
            .groupBy { it.provider.packageName }
            .map { (pkg, widgets) ->
                val appLabel = try {
                    @Suppress("DEPRECATION")
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg
                }
                WidgetGroup(
                    packageName = pkg,
                    appLabel = appLabel,
                    widgets = widgets.sortedBy {
                        it.loadLabel(pm).toString().lowercase()
                    },
                )
            }
            .sortedBy { it.appLabel.lowercase() }

        // Read theme style from SharedPreferences (same pattern as LauncherActivity).
        val prefs = getSharedPreferences("luna_prefs", Context.MODE_PRIVATE)
        val themeStyleName = prefs.getString("theme_style", LauncherThemeStyle.SMOKY_GLASS.name)
            ?: LauncherThemeStyle.SMOKY_GLASS.name
        val themeStyle = runCatching { LauncherThemeStyle.valueOf(themeStyleName) }
            .getOrDefault(LauncherThemeStyle.SMOKY_GLASS)

        setContent {
            MaterialTheme {
                LunaLauncherTheme(style = themeStyle) {
                    WidgetsPickerContent(
                        groups = groups,
                        pm = pm,
                        onSelectWidget = { info -> beginAddWidget(info) },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    /**
     * Begin the widget add flow: allocate ID → bind → (optional) configure → return result.
     */
    private fun beginAddWidget(info: AppWidgetProviderInfo) {
        cleanupPending()

        val widgetHost = container.deckWidgetHost
        val appWidgetId = widgetHost.allocateWidgetId()
        if (appWidgetId < 0) {
            Toast.makeText(this, "Unable to add widget", Toast.LENGTH_SHORT).show()
            return
        }
        pendingAppWidgetId = appWidgetId
        pendingProviderInfo = info

        val bound = widgetHost.bindWidgetIfAllowed(appWidgetId, info.provider)
        if (!bound) {
            // Need user permission to bind — launch the system bind UI.
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            bindLauncher.launch(intent)
            return
        }

        maybeConfigureOrFinish(appWidgetId)
    }

    /**
     * If the widget has a configure activity, launch it. Otherwise finish directly.
     */
    private fun maybeConfigureOrFinish(appWidgetId: Int) {
        val info = container.deckWidgetHost.getWidgetInfo(appWidgetId) ?: pendingProviderInfo
        val configure = info?.configure
        if (configure != null && isExportedActivity(configure)) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                configureLauncher.launch(intent)
                return
            } catch (_: SecurityException) {
                // Fall through to finish without config.
            } catch (_: Exception) {
                // Fall through.
            }
        }

        finishWithResult(appWidgetId)
    }

    /**
     * Pack the result and finish. The caller receives appWidgetId, provider, and default size.
     */
    private fun finishWithResult(appWidgetId: Int) {
        val info = container.deckWidgetHost.getWidgetInfo(appWidgetId) ?: pendingProviderInfo
        if (info == null) {
            cleanupPending()
            Toast.makeText(this, "Unable to add widget", Toast.LENGTH_SHORT).show()
            return
        }

        val (widthDp, heightDp) = defaultSizeFor(info)

        val data = Intent().apply {
            putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
            putExtra(EXTRA_PROVIDER, info.provider.flattenToString())
            putExtra(EXTRA_WIDTH_DP, widthDp)
            putExtra(EXTRA_HEIGHT_DP, heightDp)
        }
        setResult(RESULT_OK, data)
        pendingAppWidgetId = null
        pendingProviderInfo = null
        finish()
    }

    private fun cleanupPending() {
        val id = pendingAppWidgetId
        pendingAppWidgetId = null
        pendingProviderInfo = null
        if (id != null) {
            container.deckWidgetHost.deleteWidgetId(id)
        }
    }

    private fun isExportedActivity(component: ComponentName): Boolean =
        try {
            @Suppress("DEPRECATION")
            packageManager.getActivityInfo(component, 0).exported
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

    companion object {
        const val EXTRA_APP_WIDGET_ID = "extra_app_widget_id"
        const val EXTRA_PROVIDER = "extra_provider"
        const val EXTRA_WIDTH_DP = "extra_width_dp"
        const val EXTRA_HEIGHT_DP = "extra_height_dp"

        /**
         * Heuristic default size based on the widget's min dimensions.
         * Returns (widthDp, heightDp).
         */
        fun defaultSizeFor(info: AppWidgetProviderInfo): Pair<Int, Int> {
            val widthDp = when {
                info.minWidth >= 280 -> 320
                info.minWidth >= 200 -> 240
                info.minWidth >= 110 -> 160
                else -> 160
            }
            val heightDp = when {
                info.minHeight >= 280 -> 320
                info.minHeight >= 200 -> 240
                info.minHeight >= 110 -> 160
                else -> 120
            }
            return widthDp to heightDp
        }
    }
}

// ---------------------------------------------------------------------------
// Data structures
// ---------------------------------------------------------------------------

private data class WidgetGroup(
    val packageName: String,
    val appLabel: String,
    val widgets: List<AppWidgetProviderInfo>,
)

private data class WidgetDisplayInfo(
    val info: AppWidgetProviderInfo,
    val label: String,
    val description: String?,
    val gridWidth: Int,
    val gridHeight: Int,
    val sizeLabel: String,
)

/**
 * Convert a widget's min dimension (dp) to approximate home-screen grid cells.
 * Standard Android formula: cells = floor((minDp - 30) / 70) + 1, clamped to 1..4.
 */
private fun gridCells(minDimensionDp: Int): Int =
    ((minDimensionDp - 30) / 70 + 1).coerceIn(1, 4)

/**
 * Best-effort grid span for display. Uses targetCellWidth/Height on API 31+,
 * falls back to the standard heuristic.
 */
private fun widgetSpanCells(info: AppWidgetProviderInfo): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val tw = info.targetCellWidth
        val th = info.targetCellHeight
        if (tw > 0 && th > 0) return tw to th
    }
    return gridCells(info.minWidth) to gridCells(info.minHeight)
}

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

@Composable
private fun WidgetsPickerContent(
    groups: List<WidgetGroup>,
    pm: PackageManager,
    onSelectWidget: (AppWidgetProviderInfo) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var expandedPackages by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }

    val filteredGroups = remember(groups, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            groups
        } else {
            groups.mapNotNull { group ->
                if (group.appLabel.contains(q, ignoreCase = true)) {
                    group
                } else {
                    val matching = group.widgets.filter { info ->
                        val label = info.loadLabel(pm)?.toString().orEmpty()
                        label.contains(q, ignoreCase = true)
                    }
                    if (matching.isNotEmpty()) group.copy(widgets = matching) else null
                }
            }
        }
    }

    // Auto-expand all groups when searching.
    LaunchedEffect(query, filteredGroups) {
        if (query.trim().isNotEmpty()) {
            expandedPackages = filteredGroups.map { it.packageName }.toSet()
        }
    }

    val bgTop = LauncherTheme.colors.allAppsBackgroundTop
    val bgBottom = LauncherTheme.colors.allAppsBackgroundBottom
    val bgBrush = remember(bgTop, bgBottom) {
        Brush.verticalGradient(colors = listOf(bgTop, bgBottom))
    }

    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBarHeightPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val gutterTint = LauncherTheme.colors.homeGutterScrim
    val cornerRadius = 26.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Widgets",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.92f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (busy) "ADDING\u2026" else "CLOSE",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !busy) { onClose() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            // Search bar
            WidgetsPickerSearchBar(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )

            // Widget list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredGroups.isEmpty() && query.trim().isNotEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "No widgets found for \u201C$query\u201D",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.50f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        )
                    }
                }

                filteredGroups.forEach { group ->
                    item(key = "group_${group.packageName}") {
                        val expanded = group.packageName in expandedPackages

                        Column {
                            WidgetGroupHeader(
                                group = group,
                                expanded = expanded,
                                onToggle = {
                                    expandedPackages = if (group.packageName in expandedPackages) {
                                        expandedPackages - group.packageName
                                    } else {
                                        expandedPackages + group.packageName
                                    }
                                },
                                pm = pm,
                            )

                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(animationSpec = tween(250)),
                                exit = shrinkVertically(animationSpec = tween(200)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    group.widgets.forEach { info ->
                                        val displayInfo = remember(info.provider.flattenToString()) {
                                            val label = info.loadLabel(pm)?.toString().orEmpty()
                                            val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                info.loadDescription(context)?.toString()
                                            } else {
                                                null
                                            }
                                            val (gw, gh) = widgetSpanCells(info)
                                            WidgetDisplayInfo(
                                                info = info,
                                                label = label.ifBlank { info.provider.shortClassName ?: "Widget" },
                                                description = desc,
                                                gridWidth = gw,
                                                gridHeight = gh,
                                                sizeLabel = "$gw \u00D7 $gh",
                                            )
                                        }

                                        WidgetItemRow(
                                            displayInfo = displayInfo,
                                            busy = busy,
                                            onSelect = {
                                                busy = true
                                                onSelectWidget(info)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gutter scrim — dark overlay behind system bars with rounded cutout,
        // matching the Home screen gutter style.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .drawWithCache {
                    val cutout = Rect(
                        left = 0f,
                        top = statusBarHeightPx,
                        right = size.width,
                        bottom = size.height - navBarHeightPx,
                    )
                    val cr = cornerRadius.toPx()
                    val cutoutPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = cutout,
                                topLeft = CornerRadius(cr, cr),
                                topRight = CornerRadius(cr, cr),
                                bottomLeft = CornerRadius(cr, cr),
                                bottomRight = CornerRadius(cr, cr),
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
private fun WidgetsPickerSearchBar(
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
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "Just type\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.50f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WidgetGroupHeader(
    group: WidgetGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    pm: PackageManager,
    modifier: Modifier = Modifier,
) {
    val appIcon = rememberAppIcon(group.packageName, pm)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevron",
    )
    val widgetCountText = if (group.widgets.size == 1) "1 widget" else "${group.widgets.size} widgets"

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = LauncherTheme.colors.dockBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App icon
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = group.appLabel.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.70f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // App name + widget count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = widgetCountText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.60f),
                )
            }

            // Chevron
            Text(
                text = "\u276F",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}

@Composable
private fun WidgetItemRow(
    displayInfo: WidgetDisplayInfo,
    busy: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preview = rememberWidgetPreview(displayInfo.info, context)

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !busy) { onSelect() },
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color.Black.copy(alpha = 0.16f), // TODO: add widgetItemRowBackground token
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // Preview image (if available)
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = displayInfo.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Widget name
            Text(
                text = displayInfo.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Size label
            Text(
                text = displayInfo.sizeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.60f),
            )

            // Description (if available)
            if (!displayInfo.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.50f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Async image loading helpers
// ---------------------------------------------------------------------------

@Composable
private fun rememberAppIcon(packageName: String, pm: PackageManager): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                val drawable = pm.getApplicationIcon(packageName)
                val sizePx = 96
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    return icon
}

@Composable
private fun rememberWidgetPreview(
    info: AppWidgetProviderInfo,
    context: Context,
): ImageBitmap? {
    // loadPreviewImage expects DisplayMetrics.densityDpi (e.g. 480), not the float ratio.
    val densityDpi = context.resources.displayMetrics.densityDpi
    val preview by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = info.provider.flattenToString(),
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                // 1. Try loadPreviewImage (handles both previewLayout and previewImage).
                // 2. Fall back to the legacy previewImage resource directly.
                // 3. Last resort: widget icon.
                val drawable = info.loadPreviewImage(context, densityDpi)
                    ?: loadPreviewImageLegacy(info, context)
                    ?: info.loadIcon(context, densityDpi)
                    ?: return@withContext null
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                // Scale down large previews to save memory.
                val maxDim = 480
                val scale = (maxDim.toFloat() / maxOf(width, height)).coerceAtMost(1f)
                val scaledW = (width * scale).toInt().coerceAtLeast(1)
                val scaledH = (height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, scaledW, scaledH)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    return preview
}

/**
 * Load the legacy previewImage resource directly from the widget's package.
 * Some OEM widgets (Samsung, etc.) only declare android:previewImage in XML
 * and loadPreviewImage() can miss them if the resource isn't in the expected
 * density bucket. Loading via the provider's package resources is more reliable.
 */
private fun loadPreviewImageLegacy(
    info: AppWidgetProviderInfo,
    context: Context,
): android.graphics.drawable.Drawable? {
    @Suppress("DEPRECATION")
    val resId = info.previewImage
    if (resId == 0) return null
    return try {
        val pm = context.packageManager
        val res = pm.getResourcesForApplication(info.provider.packageName)
        res.getDrawable(resId, null)
    } catch (_: Exception) {
        null
    }
}
