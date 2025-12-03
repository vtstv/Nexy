package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.Message

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
    onCopyMessage: () -> Unit = {},
    onDownloadFile: (String, String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        reverseLayout = true
    ) {
        items(messages.reversed()) { message ->
            MessageBubble(
                message = message,
                isOwnMessage = message.senderId == currentUserId,
                isGroupChat = chatType == ChatType.GROUP,
                fontScale = fontScale,
                textColor = if (message.senderId == currentUserId) outgoingTextColor else incomingTextColor,
                onDelete = { onDeleteMessage(message.id) },
                onReply = { onReplyMessage(message) },
                onCopy = onCopyMessage,
                onDownloadFile = onDownloadFile,
                onOpenFile = onOpenFile
            )
        }
    }
}
