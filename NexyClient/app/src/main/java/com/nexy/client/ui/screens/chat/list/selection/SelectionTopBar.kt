/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.selection

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onMute: (MuteDuration) -> Unit,
    onAddToFolder: () -> Unit,
    onDelete: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMuteMenu by remember { mutableStateOf(false) }
    
    TopAppBar(
        title = {
            Text(text = selectedCount.toString())
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel selection"
                )
            }
        },
        actions = {
            IconButton(onClick = { showMuteMenu = true }) {
                Icon(
                    imageVector = Icons.Outlined.VolumeOff,
                    contentDescription = "Mute"
                )
            }
            
            DropdownMenu(
                expanded = showMuteMenu,
                onDismissRequest = { showMuteMenu = false }
            ) {
                Text(
                    text = "Mute for...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                )
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
            
            IconButton(onClick = onAddToFolder) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = "Add to folder"
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
            
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
            
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Mark as read") },
                    onClick = {
                        showMoreMenu = false
                        // TODO: Implement mark as read
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}
