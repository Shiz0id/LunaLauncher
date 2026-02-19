package com.lunasysman.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lunasysman.launcher.core.justtype.model.JustTypeCategory
import com.lunasysman.launcher.core.justtype.model.JustTypeNotificationsOptions
import com.lunasysman.launcher.core.justtype.notifications.NotificationPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JustTypeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as LauncherApplication).container
        setContent {
            MaterialTheme {
                JustTypeSettingsScreen(
                    container = container,
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun JustTypeSettingsScreen(
    container: LauncherContainer,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val providers by container.justTypeRegistry.providers.collectAsState(initial = emptyList())
    val defaultSearchProviderId by container.justTypeRegistry.defaultSearchProviderId.collectAsState(initial = null)
    val notificationsOptions by container.justTypeRegistry.notificationsOptions.collectAsState(initial = JustTypeNotificationsOptions())

    val bgBrush =
        remember {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2C3136).copy(alpha = 0.93f),
                    Color(0xFF1E2226).copy(alpha = 0.93f),
                ),
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Just Type Settings",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val grouped = providers.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "notifications_options") {
                NotificationsOptionsCard(
                    notificationsOptions = notificationsOptions,
                    notificationAccessGranted = rememberNotificationAccessGranted(),
                    onOpenNotificationAccessSettings = {
                        NotificationPermissionHelper.openNotificationAccessSettings(context)
                    },
                    onSetMaxResults = { value ->
                        scope.launch(Dispatchers.IO) {
                            container.justTypeRegistry.setNotificationsMaxResults(value)
                        }
                    },
                    onSetMatchText = { enabled ->
                        scope.launch(Dispatchers.IO) {
                            container.justTypeRegistry.setNotificationsMatchText(enabled)
                        }
                    },
                    onSetMatchNames = { enabled ->
                        scope.launch(Dispatchers.IO) {
                            container.justTypeRegistry.setNotificationsMatchNames(enabled)
                        }
                    },
                    onSetShowActions = { enabled ->
                        scope.launch(Dispatchers.IO) {
                            container.justTypeRegistry.setNotificationsShowActions(enabled)
                        }
                    },
                )
            }

            grouped.forEach { (category, list) ->
                item(key = "header_${category.name}") {
                    Text(
                        text = categoryTitle(category),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }

                val sorted = list.sortedWith(compareBy({ it.orderIndex }, { it.id }))
                item(key = "card_${category.name}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.22f)),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            sorted.forEachIndexed { idx, provider ->
                                if (idx != 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color.Black.copy(alpha = 0.18f)),
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = provider.displayName ?: provider.id,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.92f),
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = provider.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.62f),
                                            maxLines = 1,
                                        )
                                    }

                                    if (category == JustTypeCategory.SEARCH) {
                                        RadioButton(
                                            selected = provider.id == defaultSearchProviderId,
                                            onClick = {
                                                if (!provider.enabled) return@RadioButton
                                                scope.launch(Dispatchers.IO) {
                                                    container.justTypeRegistry.setDefaultSearchProviderId(provider.id)
                                                }
                                            },
                                        )
                                    }

                                    Switch(
                                        checked = provider.enabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch(Dispatchers.IO) {
                                                container.justTypeRegistry.setProviderEnabled(provider.id, enabled)
                                            }
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
}

@Composable
private fun rememberNotificationAccessGranted(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val grantedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        if (lifecycleOwner == null) {
            grantedState.value = NotificationPermissionHelper.isNotificationAccessGranted(context)
            return@DisposableEffect onDispose { }
        }
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    grantedState.value = NotificationPermissionHelper.isNotificationAccessGranted(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        grantedState.value = NotificationPermissionHelper.isNotificationAccessGranted(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return grantedState.value
}

@Composable
private fun NotificationsOptionsCard(
    notificationsOptions: JustTypeNotificationsOptions,
    notificationAccessGranted: Boolean,
    onOpenNotificationAccessSettings: () -> Unit,
    onSetMaxResults: (Int) -> Unit,
    onSetMatchText: (Boolean) -> Unit,
    onSetMatchNames: (Boolean) -> Unit,
    onSetShowActions: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.92f),
            )

            Text(
                text = if (notificationAccessGranted) "Access: Granted" else "Access: Not granted",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenNotificationAccessSettings) {
                    Text(
                        text = "Open notification access settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Max results",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSetMaxResults((notificationsOptions.maxResults - 1).coerceAtLeast(1)) },
                ) {
                    Text(text = "−", color = Color.White.copy(alpha = 0.92f))
                }
                Text(
                    text = notificationsOptions.maxResults.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
                TextButton(
                    onClick = { onSetMaxResults((notificationsOptions.maxResults + 1).coerceAtMost(25)) },
                ) {
                    Text(text = "+", color = Color.White.copy(alpha = 0.92f))
                }
            }

            SettingsSwitchRow(
                title = "Search message text",
                checked = notificationsOptions.matchText,
                onCheckedChange = onSetMatchText,
            )
            SettingsSwitchRow(
                title = "Search people names",
                checked = notificationsOptions.matchNames,
                onCheckedChange = onSetMatchNames,
            )
            SettingsSwitchRow(
                title = "Show action pills",
                checked = notificationsOptions.showActions,
                onCheckedChange = onSetShowActions,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun categoryTitle(category: JustTypeCategory): String =
    when (category) {
        JustTypeCategory.APPS -> "Apps"
        JustTypeCategory.NOTIFICATIONS -> "Notifications"
        JustTypeCategory.ACTION -> "Quick Actions"
        JustTypeCategory.DBSEARCH -> "DB Search"
        JustTypeCategory.SEARCH -> "Search"
    }
