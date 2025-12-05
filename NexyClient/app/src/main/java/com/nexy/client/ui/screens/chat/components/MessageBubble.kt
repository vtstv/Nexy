package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.InvitePreviewResponse
import com.nexy.client.data.models.Message
import com.nexy.client.ui.components.LinkParser
import com.nexy.client.ui.components.LinkedText
import com.nexy.client.ui.screens.chat.components.bubble.*
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    isGroupChat: Boolean = false,
    repliedMessage: Message? = null,
    fontScale: Float = 1.0f,
    textColor: Long = 0L,
    onDelete: () -> Unit = {},
    onReply: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCopy: () -> Unit = {},
    onDownloadFile: (String, String) -> Unit = { _, _ -> },
    onOpenFile: (String) -> Unit = {},
    onSaveFile: (String) -> Unit = {},
    onInviteLinkClick: (String) -> Unit = {},
    onUserLinkClick: (String) -> Unit = {},
    invitePreviewProvider: (String) -> InvitePreviewResponse? = { null },
    isLoadingInvitePreview: (String) -> Boolean = { false }
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val hasAttachment = message.mediaUrl != null
    val file = if (hasAttachment) File(context.getExternalFilesDir(null), message.content) else null
    val isDownloaded = file?.exists() == true
    
    // Check if message contains an invite link
    val inviteCode = remember(message.content) {
        LinkParser.extractInviteCode(message.content)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwnMessage && isGroupChat) {
            MessageAvatar(sender = message.sender)
            Spacer(modifier = Modifier.width(4.dp))
        }

        Box {
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
                modifier = Modifier
                    .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.85f).dp)
                    .combinedClickable(
                        onClick = { showMenu = true },
                        onLongClick = { showMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (repliedMessage != null) {
                        ReplyPreview(repliedMessage = repliedMessage)
                    }

                    if (isGroupChat && !isOwnMessage) {
                        Text(
                            text = message.sender?.displayName?.takeIf { it.isNotBlank() }
                                ?: message.sender?.username 
                                ?: "User ${message.senderId}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Column {
                        if (message.mediaUrl != null) {
                            FileAttachment(
                                message = message,
                                onDownloadFile = onDownloadFile,
                                onOpenFile = onOpenFile,
                                onSaveFile = onSaveFile,
                                onLongClick = { showMenu = true }
                            )
                        } else if (inviteCode != null) {
                            // Show invite card for messages with invite links
                            InviteLinkCard(
                                inviteCode = inviteCode,
                                preview = invitePreviewProvider(inviteCode),
                                isLoading = isLoadingInvitePreview(inviteCode),
                                onJoinClick = { onInviteLinkClick(inviteCode) }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                LinkedText(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                                        color = if (textColor != 0L) Color(textColor) else Color.Unspecified
                                    ),
                                    onInviteLinkClick = onInviteLinkClick,
                                    onUserLinkClick = onUserLinkClick,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                MessageTimestamp(
                                    timestamp = message.timestamp,
                                    isEdited = message.isEdited,
                                    isOwnMessage = isOwnMessage,
                                    status = message.status
                                )
                            }
                        }
                        
                        // Show timestamp separately for invite cards and attachments
                        if (message.mediaUrl != null || inviteCode != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                MessageTimestamp(
                                    timestamp = message.timestamp,
                                    isEdited = message.isEdited,
                                    isOwnMessage = isOwnMessage,
                                    status = message.status
                                )
                            }
                        }
                    }
                }
            }

            MessageContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                messageContent = message.content,
                isOwnMessage = isOwnMessage,
                hasAttachment = hasAttachment,
                isDownloaded = isDownloaded,
                onReply = onReply,
                onCopy = onCopy,
                onEdit = onEdit,
                onDelete = { showDeleteDialog = true },
                onOpenFile = { onOpenFile(message.content) },
                onSaveFile = { onSaveFile(message.content) },
                onDownloadFile = {
                    val fileId = message.mediaUrl?.substringAfterLast("/") ?: ""
                    if (fileId.isNotEmpty()) {
                        onDownloadFile(fileId, message.content)
                    }
                }
            )
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
