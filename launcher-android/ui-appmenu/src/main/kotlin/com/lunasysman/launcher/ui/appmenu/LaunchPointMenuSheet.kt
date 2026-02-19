package com.lunasysman.launcher.ui.appmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.core.model.LaunchPointAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchPointMenuSheet(
    launchPoint: LaunchPoint,
    actions: List<LaunchPointAction>,
    onDismiss: () -> Unit,
    onAction: (LaunchPointAction) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = launchPoint.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            actions.forEach { action ->
                MenuItem(labelFor(action)) { onAction(action) }
            }
        }
    }
}

private fun labelFor(action: LaunchPointAction): String =
    when (action) {
        LaunchPointAction.Pin -> "Add to Favorites"
        LaunchPointAction.Unpin -> "Remove from Favorites"
        LaunchPointAction.Hide -> "Hide"
        LaunchPointAction.Unhide -> "Unhide"
        LaunchPointAction.RemoveFromDock -> "Remove from dock"
        LaunchPointAction.RemoveFromHome -> "Remove from home"
        LaunchPointAction.AppInfo -> "App info"
        LaunchPointAction.Uninstall -> "Uninstall"
    }

@Composable
private fun MenuItem(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label)
    }
}
