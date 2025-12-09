package com.nexy.client.ui.screens.chat.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.InvitePreviewResponse
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.utils.formatDateHeader
import com.nexy.client.ui.screens.chat.utils.isSameDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageList(
    messages: List<Message>,
    currentUserId: Int?,
    listState: LazyListState,
    highlightMessageId: String? = null,
    chatType: ChatType = ChatType.PRIVATE,
    fontScale: Float = 1.0f,
    incomingTextColor: Long = 0L,
    outgoingTextColor: Long = 0L,
    avatarSize: Float = 32f,
    firstUnreadMessageId: String? = null,
    userRole: String? = null,
    participants: Map<Int, com.nexy.client.data.models.User> = emptyMap(),
    onDeleteMessage: (String) -> Unit,
    onReplyMessage: (Message) -> Unit = {},
    onEditMessage: (Message) -> Unit = {},
    onCopyMessage: () -> Unit = {},
    onPinMessage: (Message) -> Unit = {},
    onDownloadFile: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onSaveFile: (String) -> Unit,
    onInviteLinkClick: (String) -> Unit = {},
    onUserLinkClick: (String) -> Unit = {},
    onReactionClick: (String, String) -> Unit = { _, _ -> },
    invitePreviewProvider: (String) -> InvitePreviewResponse? = { null },
    isLoadingInvitePreview: (String) -> Boolean = { false },
    messageLinkPreviewProvider: (String, String) -> Message? = { _, _ -> null },
    isLoadingMessagePreview: (String, String) -> Boolean = { _, _ -> false },
    onLoadMessagePreview: (String, String) -> Unit = { _, _ -> },
    onNavigateToMessage: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun toggleSelection(message: Message) {
        if (selectedIds.contains(message.id)) {
            selectedIds.remove(message.id)
        } else {
            selectedIds.add(message.id)
        }
        selectionMode = selectedIds.isNotEmpty()
    }

    fun forwardSelected() {
        val shareText = messages
            .filter { selectedIds.contains(it.id) }
            .joinToString(separator = "\n\n") { msg ->
                val content = msg.content.orEmpty()
                if (content.isNotBlank()) content else "https://nexy.app/chat/${msg.chatId}/message/${msg.serverId ?: msg.id}"
            }
        if (shareText.isNotBlank()) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Forward message"))
        }
        exitSelection()
    }

    val reversedMessages = remember(messages) { messages.reversed() }
    var isDateVisible by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isDateVisible = true
        } else {
            delay(2000)
            isDateVisible = false
        }
    }

    val topVisibleMessageDate by remember(reversedMessages, listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val topItem = visibleItems.maxByOrNull { it.index }
                val index = topItem?.index
                if (index != null && index in reversedMessages.indices) {
                    reversedMessages[index].timestamp
                } else null
            } else null
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        val selectedDateMillis = datePickerState.selectedDateMillis
                        if (selectedDateMillis != null) {
                            val indexToScroll = reversedMessages.indexOfLast { message ->
                                isSameDay(message.timestamp, selectedDateMillis)
                            }

                            if (indexToScroll != -1) {
                                scope.launch {
                                    listState.scrollToItem(indexToScroll)
                                }
                            }
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            reverseLayout = true
        ) {
            itemsIndexed(reversedMessages) { index, message ->
                val nextMessage = reversedMessages.getOrNull(index + 1)
                val showDateHeader = if (nextMessage == null) {
                    true
                } else {
                    !isSameDay(message.timestamp, nextMessage.timestamp)
                }

                val showUnreadDivider = firstUnreadMessageId != null && message.id == firstUnreadMessageId

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showUnreadDivider) {
                        UnreadMessagesDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (showDateHeader) {
                        DateHeader(
                            timestamp = message.timestamp,
                            onClick = { showDatePicker = true }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val repliedMessage = remember(message.replyToId, messages) {
                        message.replyToId?.let { replyId ->
                            messages.find { it.serverId == replyId }
                        }
                    }

                    val isOwnMessage = message.senderId == currentUserId
                    val canDeleteMessage = isOwnMessage ||
                        (chatType == ChatType.GROUP && (userRole == "admin" || userRole == "owner"))
                    val canPinMessage = chatType == ChatType.GROUP && (userRole == "admin" || userRole == "owner")

                    val isSelected = selectedIds.contains(message.id)

                    MessageBubble(
                        message = message,
                        highlight = message.id == highlightMessageId,
                        isOwnMessage = isOwnMessage,
                        isGroupChat = chatType == ChatType.GROUP,
                        canDeleteMessage = canDeleteMessage,
                        canPinMessage = canPinMessage,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onToggleSelection = { toggleSelection(message) },
                        onStartSelection = { selectionMode = true },
                        repliedMessage = repliedMessage,
                        fontScale = fontScale,
                        textColor = if (message.senderId == currentUserId) outgoingTextColor else incomingTextColor,
                        avatarSize = avatarSize,
                        currentUserId = currentUserId ?: 0,
                        onDelete = { onDeleteMessage(message.id) },
                        onReply = { onReplyMessage(message) },
                        onEdit = { onEditMessage(message) },
                        onCopy = onCopyMessage,
                        onPin = { onPinMessage(message) },
                        onDownloadFile = onDownloadFile,
                        onOpenFile = onOpenFile,
                        onSaveFile = onSaveFile,
                        onInviteLinkClick = onInviteLinkClick,
                        onUserLinkClick = onUserLinkClick,
                        onReactionClick = onReactionClick,
                        invitePreviewProvider = invitePreviewProvider,
                        isLoadingInvitePreview = isLoadingInvitePreview,
                        messageLinkPreviewProvider = messageLinkPreviewProvider,
                        isLoadingMessagePreview = isLoadingMessagePreview,
                        onLoadMessagePreview = onLoadMessagePreview,
                        onNavigateToMessage = onNavigateToMessage,
                        participants = participants
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isDateVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            DateHeader(
                timestamp = topVisibleMessageDate,
                onClick = { showDatePicker = true }
            )
        }

        if (selectionMode) {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close selection")
                        }
                        Text(text = "${selectedIds.size} selected")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            val selectedMessages = messages.filter { selectedIds.contains(it.id) }
                            val copyText = selectedMessages.joinToString("\n") { it.content.orEmpty() }
                            if (copyText.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(copyText))
                                exitSelection()
                            }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                        }

                        IconButton(onClick = { forwardSelected() }) {
                            Icon(Icons.Filled.Forward, contentDescription = "Forward")
                        }

                        if (userRole == "owner" || userRole == "admin") {
                            IconButton(onClick = {
                                selectedIds.toList().forEach { id -> onDeleteMessage(id) }
                                exitSelection()
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(timestamp: String?, onClick: () -> Unit = {}) {
    val dateStr = formatDateHeader(timestamp)
    if (dateStr.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun UnreadMessagesDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Text(
            text = "Unread Messages",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}
