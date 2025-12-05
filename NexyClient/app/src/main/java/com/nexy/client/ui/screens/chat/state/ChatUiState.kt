package com.nexy.client.ui.screens.chat.state

import androidx.compose.ui.text.input.TextFieldValue
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.models.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val messageText: TextFieldValue = TextFieldValue(""),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUserId: Int? = null,
    val chatName: String = "Chat",
    val chatAvatarUrl: String? = null,
    val chatType: ChatType = ChatType.PRIVATE,
    val groupType: GroupType? = null,
    val participantIds: List<Int> = emptyList(),
    val isSelfChat: Boolean = false,
    val isCreator: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val editingMessage: Message? = null,
    val isTyping: Boolean = false,
    val typingUser: String? = null,
    val isMember: Boolean = true
)
