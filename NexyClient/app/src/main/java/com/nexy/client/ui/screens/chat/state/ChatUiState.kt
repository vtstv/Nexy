package com.nexy.client.ui.screens.chat.state

import androidx.compose.ui.text.input.TextFieldValue
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.models.InvitePreviewResponse
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
    val participants: Map<Int, com.nexy.client.data.models.User> = emptyMap(),
    val isSelfChat: Boolean = false,
    val isCreator: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val editingMessage: Message? = null,
    val isTyping: Boolean = false,
    val typingUser: String? = null,
    val isMember: Boolean = true,
    val mutedUntil: String? = null,
    val otherUserOnlineStatus: String? = null,
    val firstUnreadMessageId: String? = null,
    val isConnected: Boolean = true,
    val pendingMessageCount: Int = 0,
    val invitePreviews: Map<String, InvitePreviewResponse> = emptyMap(),
    val loadingInviteCodes: Set<String> = emptySet(),
    val messageLinkPreviews: Map<String, Message> = emptyMap(),
    val loadingMessageLinks: Set<String> = emptySet(),
    val voiceMessagesEnabled: Boolean = true,
    val recipientVoiceMessagesEnabled: Boolean = true,
    val userRole: String? = null
)
