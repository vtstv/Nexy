/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatType

private val FOLDER_ICONS = listOf(
    "📁", "📂", "📌", "⭐", "❤️", "💼", "🎯", "🎮",
    "🎵", "📷", "🎬", "📚", "✈️", "🏠", "💰", "🛒",
    "👥", "👤", "💬", "📱", "💻", "🔒", "🔑", "⚙️",
    "🎁", "🎉", "🌟", "🔥", "💡", "📝", "✅", "❌"
)

private val FOLDER_COLORS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7",
    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722"
)

@Composable
fun IconPickerDialog(
    selectedIcon: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.height(200.dp)
            ) {
                items(FOLDER_ICONS) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (emoji == selectedIcon) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else Color.Transparent
                            )
                            .clickable { onIconSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ColorPickerDialog(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FOLDER_COLORS) { color ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(parseColor(color))
                            .then(
                                if (color == selectedColor) {
                                    Modifier.border(3.dp, Color.White, CircleShape)
                                } else Modifier
                            )
                            .clickable { onColorSelected(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (color == selectedColor) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun IncludedChatItem(
    chat: Chat,
    onRemove: () -> Unit
) {
    ListItem(
        headlineContent = { Text(chat.name ?: chat.username ?: "Chat") },
        supportingContent = {
            val typeLabel = when (chat.type) {
                ChatType.GROUP -> "Group"
                ChatType.PRIVATE -> "Contact"
            }
            Text(typeLabel, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (chat.name ?: chat.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove")
            }
        }
    )
}

@Composable
fun DeleteFolderDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Folder") },
        text = { Text("Are you sure you want to delete this folder? This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun parseColor(hexColor: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexColor))
    } catch (e: Exception) {
        Color(0xFF2196F3)
    }
}
