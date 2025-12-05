package com.nexy.client.ui.screens.chat.handlers

import androidx.lifecycle.SavedStateHandle
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.UserRepository
import javax.inject.Inject

class ChatStateManager @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val tokenManager: AuthTokenManager
) {
    suspend fun loadCurrentUserId(): Int? {
        return tokenManager.getUserId()
    }

    suspend fun loadChatInfo(
        chatId: Int,
        currentUserId: Int?
    ): ChatInfo? {
        val chatResult = chatRepository.getChatById(chatId)
        if (chatResult.isFailure) return null
        
        val chat = chatResult.getOrNull() ?: return null
        
        var otherUserOnlineStatus: String? = null
        
        val name = if (chat.type == ChatType.PRIVATE) {
            if (chat.participantIds != null && currentUserId != null) {
                val otherUserId = chat.participantIds.firstOrNull { it != currentUserId } ?: currentUserId
                val userResult = userRepository.getUserById(otherUserId, forceRefresh = true)
                val otherUser = userResult.getOrNull()
                otherUserOnlineStatus = otherUser?.onlineStatus
                otherUser?.displayName ?: otherUser?.username ?: "Chat"
            } else {
                "Chat"
            }
        } else {
            chat.name ?: "Group Chat"
        }
        
        return ChatInfo(
            chatName = name,
            chatAvatarUrl = chat.avatarUrl,
            chatType = chat.type,
            groupType = chat.groupType,
            participantIds = chat.participantIds ?: emptyList(),
            isSelfChat = chat.participantIds?.size == 1 && chat.participantIds.contains(currentUserId),
            isCreator = chat.createdBy == currentUserId,
            isMember = chat.isMember || (chat.participantIds?.contains(currentUserId) == true),
            mutedUntil = chat.mutedUntil,
            otherUserOnlineStatus = otherUserOnlineStatus,
            unreadCount = chat.unreadCount
        )
    }

    suspend fun getUserName(userId: Int): String? {
        val userResult = userRepository.getUserById(userId)
        return userResult.getOrNull()?.displayName ?: userResult.getOrNull()?.username
    }

    suspend fun joinGroup(chatId: Int): Result<Unit> {
        return chatRepository.joinPublicGroup(chatId).map { }
    }
}

data class ChatInfo(
    val chatName: String,
    val chatAvatarUrl: String?,
    val chatType: ChatType,
    val groupType: GroupType?,
    val participantIds: List<Int>,
    val isSelfChat: Boolean,
    val isCreator: Boolean,
    val isMember: Boolean,
    val mutedUntil: String? = null,
    val otherUserOnlineStatus: String? = null,
    val unreadCount: Int = 0
)
