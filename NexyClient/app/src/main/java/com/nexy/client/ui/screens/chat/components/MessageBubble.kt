package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.nexy.client.ui.screens.chat.components.invite.InviteLinkCard
import com.nexy.client.ui.screens.chat.components.message.MessageLinkCard
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    highlight: Boolean = false,
    isOwnMessage: Boolean,
    isGroupChat: Boolean = false,
    canDeleteMessage: Boolean = false,
    canPinMessage: Boolean = false,
    repliedMessage: Message? = null,
    fontScale: Float = 1.0f,
    textColor: Long = 0L,
    avatarSize: Float = 32f,
    currentUserId: Int = 0,
    onDelete: () -> Unit = {},
    onReply: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCopy: () -> Unit = {},
    onPin: () -> Unit = {},
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onStartSelection: () -> Unit = {},
    onDownloadFile: (String, String) -> Unit = { _, _ -> },
    onOpenFile: (String) -> Unit = {},
    onSaveFile: (String) -> Unit = {},
    onInviteLinkClick: (String) -> Unit = {},
    onUserLinkClick: (String) -> Unit = {},
    onReactionClick: (String, String) -> Unit = { _, _ -> },
    invitePreviewProvider: (String) -> InvitePreviewResponse? = { null },
    isLoadingInvitePreview: (String) -> Boolean = { false },
    messageLinkPreviewProvider: (String, String) -> Message? = { _, _ -> null },
    isLoadingMessagePreview: (String, String) -> Boolean = { _, _ -> false },
    onLoadMessagePreview: (String, String) -> Unit = { _, _ -> },
    onNavigateToMessage: (String, String) -> Unit = { _, _ -> },
    participants: Map<Int, com.nexy.client.data.models.User> = emptyMap()
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPanel by remember { mutableStateOf(false) }

    val hasAttachment = message.mediaUrl != null
    val file = if (hasAttachment) File(context.getExternalFilesDir(null), message.content ?: "") else null
    val isDownloaded = file?.exists() == true

    val inviteCode = remember(message.content) {
        LinkParser.extractInviteCode(message.content ?: "")
    }

    val messageLink = remember(message.content) {
        LinkParser.extractMessageLink(message.content ?: "")
    }

    LaunchedEffect(messageLink) {
        if (messageLink != null) {
            val preview = messageLinkPreviewProvider(messageLink.first, messageLink.second)
            if (preview == null && !isLoadingMessagePreview(messageLink.first, messageLink.second)) {
                onLoadMessagePreview(messageLink.first, messageLink.second)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwnMessage && isGroupChat) {
            MessageAvatar(sender = message.sender, size = avatarSize)
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column {
            Box {
                Surface(
                    shape = if (isOwnMessage) {
                        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
                    } else {
                        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
                    },
                    color = when {
                        highlight -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        isOwnMessage -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.85f).dp)
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    onToggleSelection()
                                } else {
                                    showReactionPanel = true
                                }
                            },
                            onLongClick = {
                                if (selectionMode) {
                                    onToggleSelection()
                                } else {
                                    onStartSelection()
                                    onToggleSelection()
                                }
                            }
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
                                    ?: "User ${'$'}{message.senderId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        Column {
                            if (message.mediaUrl != null) {
                                FileAttachment(
                                    message = message,
                                    isOwnMessage = isOwnMessage,
                                    onDownloadFile = onDownloadFile,
                                    onOpenFile = onOpenFile,
                                    onSaveFile = onSaveFile,
                                    onLongClick = { showMenu = true }
                                )
                            } else if (inviteCode != null) {
                                InviteLinkCard(
                                    inviteCode = inviteCode,
                                    preview = invitePreviewProvider(inviteCode),
                                    isLoading = isLoadingInvitePreview(inviteCode),
                                    onJoinClick = { onInviteLinkClick(inviteCode) }
                                )
                            } else if (messageLink != null) {
                                MessageLinkCard(
                                    linkedMessage = messageLinkPreviewProvider(messageLink.first, messageLink.second),
                                    isLoading = isLoadingMessagePreview(messageLink.first, messageLink.second),
                                    onNavigateClick = {
                                        val targetChatId = messageLink.first.toIntOrNull()
                                            ?: messageLinkPreviewProvider(messageLink.first, messageLink.second)?.chatId
                                            ?: message.chatId
                                        onNavigateToMessage(targetChatId.toString(), messageLink.second)
                                    }
                                )
                            } else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    LinkedText(
                                        text = message.content ?: "",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                                            color = if (textColor != 0L) Color(textColor) else Color.Unspecified
                                        ),
                                        onInviteLinkClick = onInviteLinkClick,
                                        onUserLinkClick = onUserLinkClick,
                                        onMessageLinkClick = { chatId, msgId ->
                                            val targetChatId = chatId.ifBlank { message.chatId.toString() }
                                            onNavigateToMessage(targetChatId, msgId)
                                        },
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

                            if (message.mediaUrl != null || inviteCode != null || messageLink != null) {
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

                        if (message.reactions?.isNotEmpty() == true) {
                            MessageReactions(
                                reactions = message.reactions,
                                currentUserId = currentUserId,
                                onReactionClick = { emoji ->
                                    onReactionClick(message.id, emoji)
                                },
                                modifier = Modifier.padding(top = 4.dp),
                                participants = participants
                            )
                        }
                    }
                }

                MessageContextMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    messageContent = message.content ?: "",
                    messageId = message.id,
                    chatId = message.chatId,
                    serverMessageId = message.serverId,
                    isOwnMessage = isOwnMessage,
                    canDeleteMessage = canDeleteMessage,
                    canPinMessage = canPinMessage,
                    hasAttachment = hasAttachment,
                    isDownloaded = isDownloaded,
                    onReply = onReply,
                    onCopy = onCopy,
                    onEdit = onEdit,
                    onPin = onPin,
                    onDelete = { showDeleteDialog = true },
                    onOpenFile = { onOpenFile(message.content ?: "") },
                    onSaveFile = { onSaveFile(message.content ?: "") },
                    onDownloadFile = {
                        val fileId = message.mediaUrl?.substringAfterLast("/") ?: ""
                        if (fileId.isNotEmpty()) {
                            onDownloadFile(fileId, message.content ?: "")
                        }
                    }
                )
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

    if (showReactionPanel) {
        ReactionFloatingPanel(
            onDismiss = { showReactionPanel = false },
            onReactionSelected = { emoji ->
                onReactionClick(message.id, emoji)
                showReactionPanel = false
            },
            contextMenuContent = {
                MessageMenuItems(
                    onDismiss = { showReactionPanel = false },
                    messageContent = message.content ?: "",
                    messageId = message.id,
                    chatId = message.chatId,
                    serverMessageId = message.serverId,
                    isOwnMessage = isOwnMessage,
                    canDeleteMessage = canDeleteMessage,
                    canPinMessage = canPinMessage,
                    hasAttachment = hasAttachment,
                    isDownloaded = isDownloaded,
                    onReply = onReply,
                    onCopy = onCopy,
                    onEdit = onEdit,
                    onPin = onPin,
                    onDelete = { showDeleteDialog = true },
                    onOpenFile = { onOpenFile(message.content ?: "") },
                    onSaveFile = { onSaveFile(message.content ?: "") },
                    onDownloadFile = {
                        val fileId = message.mediaUrl?.substringAfterLast("/") ?: ""
                        if (fileId.isNotEmpty()) {
                            onDownloadFile(fileId, message.content ?: "")
                        }
                    }
                )
            }
        )
    }
}
