package com.nexy.client.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.utils.formatDateHeader
import com.nexy.client.ui.screens.chat.utils.isSameDay
import kotlinx.coroutines.delay

@Composable
fun MessageList(
    messages: List<Message>,
    currentUserId: Int?,
    listState: LazyListState,
    chatType: ChatType = ChatType.PRIVATE,
    fontScale: Float = 1.0f,
    incomingTextColor: Long = 0L,
    outgoingTextColor: Long = 0L,
    onDeleteMessage: (String) -> Unit,
    onReplyMessage: (Message) -> Unit = {},
    onEditMessage: (Message) -> Unit = {},
    onCopyMessage: () -> Unit = {},
    onDownloadFile: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onSaveFile: (String) -> Unit
) {
    val reversedMessages = remember(messages) { messages.reversed() }
    var isDateVisible by remember { mutableStateOf(false) }
    
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

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showDateHeader) {
                        DateHeader(timestamp = message.timestamp)
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
                        onSaveFile = onSaveFile
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
            DateHeader(timestamp = topVisibleMessageDate)
        }
    }
}

@Composable
fun DateHeader(timestamp: String?) {
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
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
