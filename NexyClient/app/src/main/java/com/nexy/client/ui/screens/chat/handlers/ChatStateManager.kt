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
        var recipientVoiceMessagesEnabled = true
        var userRole: String? = null
        val participantsMap = mutableMapOf<Int, com.nexy.client.data.models.User>()
        
        val name = if (chat.type == ChatType.PRIVATE) {
            if (chat.participantIds != null && currentUserId != null) {
                val otherUserId = chat.participantIds.firstOrNull { it != currentUserId } ?: currentUserId
                // Use cache-first for instant display
                val userResult = userRepository.getUserById(otherUserId, forceRefresh = false)
                val otherUser = userResult.getOrNull()
                if (otherUser != null) {
                    participantsMap[otherUser.id] = otherUser
                }
                otherUserOnlineStatus = otherUser?.onlineStatus
                recipientVoiceMessagesEnabled = otherUser?.voiceMessagesEnabled ?: true
                otherUser?.displayName?.takeIf { it.isNotBlank() } ?: otherUser?.username ?: "Chat"
            } else {
                "Chat"
            }
        } else {
            // Load user role for group chats
            if (currentUserId != null && chat.isMember) {
                val membersResult = chatRepository.getGroupMembers(chatId)
                val members = membersResult.getOrNull()
                
                if (members != null) {
                    members.forEach { member ->
                        member.user?.let { user ->
                            participantsMap[user.id] = user
                        }
                    }
                    members.find { it.userId == currentUserId }?.let { member ->
                        userRole = member.role.name.lowercase()
                    }
                } else {
                    // Fallback: Load participants from local DB if network call fails
                    chat.participantIds?.forEach { userId ->
                        val userResult = userRepository.getUserById(userId, forceRefresh = false)
                        userResult.getOrNull()?.let { user ->
                            participantsMap[user.id] = user
                        }
                    }
                }
            }
            chat.name ?: "Group Chat"
        }
        
        return ChatInfo(
            chatName = name,
            chatAvatarUrl = chat.avatarUrl,
            chatType = chat.type,
            groupType = chat.groupType,
            participantIds = chat.participantIds ?: emptyList(),
            participants = participantsMap,
            isSelfChat = chat.type == ChatType.PRIVATE && chat.participantIds?.size == 1 && chat.participantIds.contains(currentUserId),
            isCreator = chat.createdBy == currentUserId,
            isMember = chat.isMember || (chat.participantIds?.contains(currentUserId) == true),
            mutedUntil = chat.mutedUntil,
            otherUserOnlineStatus = otherUserOnlineStatus,
            unreadCount = chat.unreadCount,
            firstUnreadMessageId = chat.firstUnreadMessageId,
            recipientVoiceMessagesEnabled = recipientVoiceMessagesEnabled,
            userRole = userRole
        )
    }

    suspend fun getUserName(userId: Int): String? {
        val userResult = userRepository.getUserById(userId)
        return userResult.getOrNull()?.displayName ?: userResult.getOrNull()?.username
    }

    suspend fun joinGroup(chatId: Int): Result<Unit> {
        return chatRepository.joinPublicGroup(chatId).map { }
    }

    suspend fun getCurrentUser(): com.nexy.client.data.models.User? {
        val userId = tokenManager.getUserId() ?: return null
        return userRepository.getUserById(userId).getOrNull()
    }

    suspend fun getCurrentUserFlow(): kotlinx.coroutines.flow.Flow<com.nexy.client.data.models.User?> {
        val userId = tokenManager.getUserId() ?: return kotlinx.coroutines.flow.emptyFlow()
        return userRepository.getUserByIdFlow(userId)
    }
}

data class ChatInfo(
    val chatName: String,
    val chatAvatarUrl: String?,
    val chatType: ChatType,
    val groupType: GroupType?,
    val participantIds: List<Int>,
    val participants: Map<Int, com.nexy.client.data.models.User> = emptyMap(),
    val isSelfChat: Boolean,
    val isCreator: Boolean,
    val isMember: Boolean,
    val mutedUntil: String? = null,
    val otherUserOnlineStatus: String? = null,
    val unreadCount: Int = 0,
    val firstUnreadMessageId: String? = null,
    val recipientVoiceMessagesEnabled: Boolean = true,
    val userRole: String? = null
)
