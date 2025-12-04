package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.api.CreateChatRequest
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.repository.ChatInfo
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatOperations @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val tokenManager: AuthTokenManager,
    private val chatMappers: ChatMappers,
    private val chatSyncOperations: ChatSyncOperations,
    private val chatInviteOperations: ChatInviteOperations,
    private val chatInfoProvider: ChatInfoProvider,
    private val webSocketClient: NexyWebSocketClient
) {
    companion object {
        private const val TAG = "ChatOperations"
    }

    fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { chatMappers.entityToModel(it) }
        }
    }

    suspend fun refreshChats(): Result<List<Chat>> = chatSyncOperations.refreshChats()
    
    suspend fun getChatById(chatId: Int): Result<Chat> = chatSyncOperations.getChatById(chatId)

    suspend fun getChatInfo(chatId: Int): ChatInfo? = chatInfoProvider.getChatInfo(chatId)

    suspend fun markChatAsRead(chatId: Int) {
        withContext(Dispatchers.IO) {
            chatDao.markChatAsRead(chatId)
            
            // Send read receipt to server for the last message
            val lastMessage = messageDao.getLastMessage(chatId)
            val currentUserId = tokenManager.getUserId()
            if (lastMessage != null && currentUserId != null && lastMessage.senderId != currentUserId) {
                webSocketClient.sendReadReceipt(lastMessage.id, chatId, currentUserId)
            }
        }
    }

    suspend fun clearChatMessages(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Clearing chat messages: chatId=$chatId")
                val response = apiService.clearChatMessages(chatId)
                if (response.isSuccessful) {
                    messageDao.deleteMessagesByChatId(chatId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to clear chat on server"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear chat", e)
                Result.failure(e)
            }
        }
    }

    suspend fun deleteChat(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Deleting chat: chatId=$chatId")
                val response = apiService.deleteChat(chatId)
                if (response.isSuccessful) {
                    messageDao.deleteMessagesByChatId(chatId)
                    chatDao.deleteChatById(chatId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete chat on server"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete chat", e)
                Result.failure(e)
            }
        }
    }

    suspend fun leaveGroup(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUserId = tokenManager.getUserId()
                if (currentUserId != null) {
                    val response = apiService.removeMember(chatId, currentUserId)
                    if (response.isSuccessful) {
                        chatDao.deleteChatById(chatId)
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Failed to leave group"))
                    }
                } else {
                    Result.failure(Exception("User not logged in"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getGroupMembers(chatId: Int, query: String? = null): Result<List<ChatMember>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getGroupMembers(chatId, query)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get group members"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getOrCreateSavedMessages(): Result<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUserId = tokenManager.getUserId()
                if (currentUserId == null) {
                    return@withContext Result.failure(Exception("User not logged in"))
                }
                
                val request = CreateChatRequest(currentUserId)
                val response = apiService.createPrivateChat(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    
                    val existingChat = chatDao.getChatById(chat.id)
                    val newEntity = chatMappers.modelToEntity(chat)
                    val finalEntity = if (existingChat != null) {
                        newEntity.copy(
                            lastMessageId = existingChat.lastMessageId,
                            unreadCount = existingChat.unreadCount
                        )
                    } else {
                        newEntity
                    }
                    
                    chatDao.insertChat(finalEntity)
                    Result.success(chat)
                } else {
                    Result.failure(Exception("Failed to create Notepad chat"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Delegate invite operations
    suspend fun createInviteLink(chatId: Int, maxUses: Int = 1, expiresAt: Long? = null) =
        chatInviteOperations.createInviteLink(chatId, maxUses, expiresAt)
    
    suspend fun validateInviteCode(code: String) =
        chatInviteOperations.validateInviteCode(code)
    
    suspend fun joinByInviteCode(code: String) =
        chatInviteOperations.joinByInviteCode(code)
    
    suspend fun removeMember(groupId: Int, userId: Int) =
        chatInviteOperations.removeMember(groupId, userId)
    
    suspend fun addGroupMember(groupId: Int, userId: Int) =
        chatInviteOperations.addGroupMember(groupId, userId)
    
    suspend fun createGroupInviteLink(groupId: Int, usageLimit: Int? = null, expiresIn: Int? = null) =
        chatInviteOperations.createGroupInviteLink(groupId, usageLimit, expiresIn)
    
    suspend fun joinPublicGroup(groupId: Int) =
        chatInviteOperations.joinPublicGroup(groupId)
    
    suspend fun transferOwnership(groupId: Int, newOwnerId: Int) =
        chatInviteOperations.transferOwnership(groupId, newOwnerId)
}
