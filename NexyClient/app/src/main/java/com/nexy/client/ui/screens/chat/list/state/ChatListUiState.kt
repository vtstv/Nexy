package com.nexy.client.ui.screens.chat.list.state

import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.User

data class ChatWithInfo(
    val chat: Chat,
    val displayName: String,
    val lastMessagePreview: String = "No messages",
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0,
    val isSelfChat: Boolean = false
)

data class ChatListUiState(
    val chats: List<ChatWithInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUser: User? = null
)
