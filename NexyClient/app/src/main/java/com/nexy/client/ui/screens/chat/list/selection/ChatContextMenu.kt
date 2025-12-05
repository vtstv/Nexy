/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContextMenu(
    chatName: String,
    isMuted: Boolean,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onAddToFolder: () -> Unit,
    onMarkAsRead: () -> Unit,
    onMute: (MuteDuration) -> Unit,
    onUnmute: () -> Unit,
    onDelete: () -> Unit
) {
    var showMuteOptions by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = chatName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            if (showMuteOptions) {
                // Mute duration options
                Text(
                    text = "Mute for...",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                MuteDuration.entries.forEach { duration ->
                    ContextMenuItem(
                        icon = Icons.Outlined.Schedule,
                        title = duration.displayName,
                        onClick = {
                            onMute(duration)
                            onDismiss()
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = { showMuteOptions = false },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("Back")
                }
            } else {
                // Main menu
                if (isPinned) {
                    ContextMenuItem(
                        icon = Icons.Filled.PushPin,
                        title = "Unpin",
                        onClick = {
                            onUnpin()
                            onDismiss()
                        }
                    )
                } else {
                    ContextMenuItem(
                        icon = Icons.Outlined.PushPin,
                        title = "Pin",
                        onClick = {
                            onPin()
                            onDismiss()
                        }
                    )
                }
                
                ContextMenuItem(
                    icon = Icons.Outlined.Folder,
                    title = "Add to folder",
                    onClick = {
                        onAddToFolder()
                        // Don't dismiss - let the folder picker show
                    }
                )
                
                ContextMenuItem(
                    icon = Icons.Outlined.DoneAll,
                    title = "Mark as read",
                    onClick = {
                        onMarkAsRead()
                        onDismiss()
                    }
                )
                
                if (isMuted) {
                    ContextMenuItem(
                        icon = Icons.Outlined.VolumeUp,
                        title = "Unmute",
                        onClick = {
                            onUnmute()
                            onDismiss()
                        }
                    )
                } else {
                    ContextMenuItem(
                        icon = Icons.Outlined.VolumeOff,
                        title = "Mute",
                        onClick = {
                            showMuteOptions = true
                        }
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                ContextMenuItem(
                    icon = Icons.Outlined.Delete,
                    title = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}
