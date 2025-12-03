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
import com.nexy.client.ServerConfig

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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar for incoming messages 
        if (isGroupChat && !isOwnMessage) {
            val avatarUrl = ServerConfig.getFileUrl(message.sender?.avatarUrl)
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Sender avatar",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (message.sender?.displayName?.firstOrNull()?.uppercaseChar()
                                ?: message.sender?.username?.firstOrNull()?.uppercaseChar()
                                ?: '?').toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = if (isOwnMessage) {
                RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
            } else {
                RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
            },
            color = if (isOwnMessage) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // Sender Name inside bubble (only for groups)
                if (isGroupChat && !isOwnMessage) {
                    Text(
                        text = message.sender?.displayName ?: message.sender?.username ?: "User ${message.senderId}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
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
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    // Timestamp and Delete button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        
                        if (isOwnMessage) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showDeleteDialog = true },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
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
