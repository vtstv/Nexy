package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.utils.formatTimestamp

import androidx.compose.ui.graphics.Color

@Composable
fun MessageBubble(
    message: Message, 
    isOwnMessage: Boolean,
    isGroupChat: Boolean = false,
    fontScale: Float = 1.0f,
    textColor: Long = 0L,
    onDelete: () -> Unit = {},
    onDownloadFile: (String, String) -> Unit = { _, _ -> },
    onOpenFile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
    ) {
        // Show sender info for incoming messages in group chats
        if (isGroupChat && !isOwnMessage && message.sender != null) {
            Row(
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Small avatar
                if (message.sender.avatarUrl != null) {
                    AsyncImage(
                        model = message.sender.avatarUrl,
                        contentDescription = "Sender avatar",
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (message.sender.displayName?.firstOrNull()?.uppercaseChar()
                                    ?: message.sender.username.firstOrNull()?.uppercaseChar()
                                    ?: '?').toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(6.dp))
                
                Text(
                    text = message.sender.displayName ?: message.sender.username,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isOwnMessage) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (message.mediaUrl != null) {
                        FileAttachment(
                            message = message,
                            onDownloadFile = onDownloadFile,
                            onOpenFile = onOpenFile
                        )
                    } else {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale
                            ),
                            color = if (textColor != 0L) Color(textColor) else Color.Unspecified
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isOwnMessage) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        DeleteMessageDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }
}

@Composable
private fun FileAttachment(
    message: Message,
    onDownloadFile: (String, String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    val isDownloaded = remember(message.content) {
        val file = java.io.File(context.getExternalFilesDir(null), message.content)
        file.exists()
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .clickable {
                if (isDownloaded) {
                    onOpenFile(message.content)
                } else {
                    val fileId = message.mediaUrl?.substringAfterLast("/") ?: ""
                    if (fileId.isNotEmpty()) {
                        onDownloadFile(fileId, message.content)
                    }
                }
            }
    ) {
        Icon(
            Icons.Default.AttachFile,
            contentDescription = "File",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        
        if (isDownloaded) {
            // Open button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "OPEN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            // Download icon
            Icon(
                Icons.Default.Download,
                contentDescription = "Download",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    if (message.mediaType?.startsWith("image/") == true) {
        Text(
            text = "📷 Image",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            text = message.mediaType ?: "File",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeleteMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Message") },
        text = { Text("Are you sure you want to delete this message?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
