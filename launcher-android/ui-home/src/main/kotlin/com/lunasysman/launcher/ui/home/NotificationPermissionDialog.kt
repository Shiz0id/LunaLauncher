package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.background
import com.lunasysman.launcher.ui.home.theme.LauncherTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Onboarding dialog explaining notification access and requesting permission.
 *
 * Shown on first boot to introduce the notification search feature.
 * Uses clear, friendly language to explain the value proposition.
 */
@Composable
fun NotificationPermissionDialog(
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
        containerColor = Color(0xFF1C1C1E),
        title = {
            Text(
                text = "Search Your Notifications",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Luna can make your notifications searchable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )

                // Feature list
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp),
                ) {
                    FeatureBullet(
                        emoji = "🔍",
                        text = "Type a name to find their messages"
                    )
                    FeatureBullet(
                        emoji = "💬",
                        text = "Reply directly from search results"
                    )
                    FeatureBullet(
                        emoji = "⚡",
                        text = "Actions appear only while notification is active"
                    )
                }

                Text(
                    text = "Notification content stays private and never leaves your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onEnableClick,
                modifier = Modifier
                    .background(
                        color = LauncherTheme.colors.notificationLiveBadgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = "Enable Access",
                    color = LauncherTheme.colors.notificationLiveBadgeColor,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(
                    text = "Not Now",
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        },
    )
}

@Composable
private fun FeatureBullet(
    emoji: String,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
