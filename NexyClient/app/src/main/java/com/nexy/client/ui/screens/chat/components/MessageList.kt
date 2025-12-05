package com.nexy.client.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.ChatType
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
    chatType: ChatType = ChatType.PRIVATE,
    fontScale: Float = 1.0f,
    incomingTextColor: Long = 0L,
    outgoingTextColor: Long = 0L,
    firstUnreadMessageId: String? = null,
    onDeleteMessage: (String) -> Unit,
    onReplyMessage: (Message) -> Unit = {},
    onEditMessage: (Message) -> Unit = {},
    onCopyMessage: () -> Unit = {},
    onDownloadFile: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onSaveFile: (String) -> Unit,
    onInviteLinkClick: (String) -> Unit = {},
    onUserLinkClick: (String) -> Unit = {}
) {
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
                // In reverseLayout, the item at the top of the screen has the highest index
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
                            // Find the first message of the selected day
                            // Since reversedMessages is [Newest ... Oldest], we want the message with the highest index
                            // that matches the date (which is the oldest message of that day)
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

                    MessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == currentUserId,
                        isGroupChat = chatType == ChatType.GROUP,
                        repliedMessage = repliedMessage,
                        fontScale = fontScale,
                        textColor = if (message.senderId == currentUserId) outgoingTextColor else incomingTextColor,
                        onDelete = { onDeleteMessage(message.id) },
                        onReply = { onReplyMessage(message) },
                        onEdit = { onEditMessage(message) },
                        onCopy = onCopyMessage,
                        onDownloadFile = onDownloadFile,
                        onOpenFile = onOpenFile,
                        onSaveFile = onSaveFile,
                        onInviteLinkClick = onInviteLinkClick,
                        onUserLinkClick = onUserLinkClick
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
