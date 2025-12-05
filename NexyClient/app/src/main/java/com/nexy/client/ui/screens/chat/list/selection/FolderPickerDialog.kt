/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexy.client.R
import com.nexy.client.data.models.ChatFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    folders: List<ChatFolder>,
    onDismiss: () -> Unit,
    onFolderSelected: (ChatFolder) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "Choose a folder",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            
            HorizontalDivider()
            
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No folders created yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create folders in Settings → Folders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                folders.forEach { folder ->
                    FolderItem(
                        folder = folder,
                        onClick = {
                            onFolderSelected(folder)
                            onDismiss()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FolderItem(
    folder: ChatFolder,
    onClick: () -> Unit
) {
    val iconColor = parseColorOrDefault(folder.color)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            folder.includeGroups && !folder.includeContacts -> Icons.Default.Group
            folder.includeContacts && !folder.includeGroups -> Icons.Default.Person
            else -> Icons.Default.Folder
        }
        
        if (folder.icon.isNotBlank()) {
            Text(
                text = folder.icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(20.dp))
        
        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun parseColorOrDefault(colorString: String?): Color {
    if (colorString.isNullOrBlank()) return Color.Gray
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        Color.Gray
    }
}
