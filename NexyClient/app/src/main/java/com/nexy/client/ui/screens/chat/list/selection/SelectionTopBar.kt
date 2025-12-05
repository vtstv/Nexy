/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.selection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Telegram-style selection action bar that replaces the search field
 * when in selection mode
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onMute: (MuteDuration) -> Unit,
    onAddToFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMuteMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Close button and count
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            // Right side: Action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pin
                IconButton(onClick = onPin) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pin",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // Mute with dropdown
                Box {
                    IconButton(onClick = { showMuteMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.VolumeOff,
                            contentDescription = "Mute",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMuteMenu,
                        onDismissRequest = { showMuteMenu = false }
                    ) {
                        MuteDuration.entries.forEach { duration ->
                            DropdownMenuItem(
                                text = { Text(duration.displayName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMuteMenu = false
                                    onMute(duration)
                                }
                            )
                        }
                    }
                }
                
                // Add to folder
                IconButton(onClick = onAddToFolder) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Add to folder",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // Delete
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
