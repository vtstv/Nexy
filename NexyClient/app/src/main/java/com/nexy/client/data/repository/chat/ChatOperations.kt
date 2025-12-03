package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.api.CreateChatRequest
import com.nexy.client.data.api.CreateGroupChatRequest
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.CreateInviteRequest
import com.nexy.client.data.models.InviteLink
import com.nexy.client.data.models.JoinChatResponse
import com.nexy.client.data.models.UseInviteRequest
import com.nexy.client.data.models.ValidateInviteRequest
import com.nexy.client.data.repository.ChatInfo
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
    private val chatMappers: ChatMappers
) {
    companion object {
        private const val TAG = "ChatOperations"
    }

    fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { chatMappers.entityToModel(it) }
        }
    }

    suspend fun refreshChats(): Result<List<Chat>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getChats()
                if (response.isSuccessful && response.body() != null) {
                    val chats = response.body()!!
                    
                    // Get existing chats to preserve local state
                    val existingChatsMap = chatDao.getAllChatsSync().associateBy { it.id }
                    
                    val updates = ArrayList<ChatEntity>()
                    val inserts = ArrayList<ChatEntity>()
                    
                    chats.forEach { chat ->
                        val newEntity = chatMappers.modelToEntity(chat)
                        val existingEntity = existingChatsMap[chat.id]
                        
                        if (existingEntity != null) {
                            updates.add(newEntity.copy(
                                lastMessageId = existingEntity.lastMessageId,
                                unreadCount = existingEntity.unreadCount,
                                muted = existingEntity.muted
                            ))
                        } else {
                            inserts.add(newEntity)
                        }
                    }
                    
                    if (updates.isNotEmpty()) {
                        chatDao.updateChats(updates)
                    }
                    if (inserts.isNotEmpty()) {
                        chatDao.insertChats(inserts)
                    }
                    
                    Result.success(chats)
                } else {
                    Result.failure(Exception("Failed to fetch chats"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getChatById(chatId: Int): Result<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                // First try to get from local DB
                val localChat = chatDao.getChatById(chatId)
                if (localChat != null) {
                    return@withContext Result.success(chatMappers.entityToModel(localChat))
                }
                
                // If not in DB, fetch from server
                val response = apiService.getChatById(chatId)
                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    
                    // Check again if chat was inserted by another thread
                    val existingChat = chatDao.getChatById(chatId)
                    if (existingChat != null) {
                        // Update existing chat but preserve local state
                        val updatedEntity = chatMappers.modelToEntity(chat).copy(
                            lastMessageId = existingChat.lastMessageId,
                            unreadCount = existingChat.unreadCount,
                            muted = existingChat.muted
                        )
                        chatDao.updateChat(updatedEntity)
                    } else {
                        // New chat, insert it
                        chatDao.insertChat(chatMappers.modelToEntity(chat))
                    }
                    
                    Result.success(chat)
                } else {
                    Result.failure(Exception("Chat not found"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting chat by ID: $chatId", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getChatInfo(chatId: Int): ChatInfo? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "getChatInfo: chatId=$chatId")
                val chat = chatDao.getChatById(chatId) ?: return@withContext null
                
                Log.d(TAG, "getChatInfo: chat type=${chat.type}, name=${chat.name}, participantIds=${chat.participantIds}")
                
                val displayName = when (chat.type) {
                    "PRIVATE" -> {
                        val currentUserId = tokenManager.getUserId()
                        Log.d(TAG, "getChatInfo: currentUserId=$currentUserId")
                        
                        val participantIds = chat.participantIds.split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                        
                        Log.d(TAG, "getChatInfo: other participantIds=${participantIds.filter { it != currentUserId }}")
                        
                        val otherUserId = participantIds.firstOrNull { it != currentUserId }
                        if (otherUserId != null) {
                            Log.d(TAG, "getChatInfo: fetching user info for participantId=$otherUserId")
                            val response = apiService.getUserById(otherUserId)
                            if (response.isSuccessful && response.body() != null) {
                                val user = response.body()!!
                                val displayName = user.displayName?.takeIf { name -> name.isNotBlank() } ?: user.username
                                Log.d(TAG, "getChatInfo: PRIVATE chat, displayName=$displayName")
                                displayName
                            } else {
                                Log.e(TAG, "getChatInfo: API call failed - code=${response.code()}")
                                "Unknown"
                            }
                        } else {
                            // If no other user, it might be Saved Messages (chat with self)
                            if (participantIds.contains(currentUserId) && participantIds.size == 1) {
                                "Notepad"
                            } else {
                                "Unknown"
                            }
                        }
                    }
                    "GROUP" -> chat.name ?: "Group Chat"
                    else -> chat.name ?: "Unknown"
                }
                
                Log.d(TAG, "getChatInfo: ${chat.type} chat, displayName=$displayName")
                
                // Get avatar URL
                val avatarUrl = if (chat.type == "PRIVATE") {
                    val currentUserId = tokenManager.getUserId()
                    val participantIds = chat.participantIds.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                    val otherUserId = participantIds.firstOrNull { it != currentUserId }
                    
                    if (otherUserId != null) {
                        val response = apiService.getUserById(otherUserId)
                        if (response.isSuccessful && response.body() != null) {
                            response.body()!!.avatarUrl
                        } else null
                    } else {
                        // Saved Messages avatar (could be user's own avatar or a special icon)
                        null 
                    }
                } else {
                    chat.avatarUrl
                }
                
                ChatInfo(
                    id = chat.id,
                    name = displayName,
                    type = ChatType.valueOf(chat.type.uppercase()),
                    avatarUrl = avatarUrl,
                    participantIds = chat.participantIds.split(",").mapNotNull { it.trim().toIntOrNull() }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get chat info", e)
                null
            }
        }
    }

    suspend fun markChatAsRead(chatId: Int) {
        withContext(Dispatchers.IO) {
            chatDao.markChatAsRead(chatId)
        }
    }

    suspend fun clearChatMessages(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Clearing chat messages: chatId=$chatId")
                val response = apiService.clearChatMessages(chatId)
                if (response.isSuccessful) {
                    // Delete messages directly using DAO
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
                    // Delete messages first, then chat
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
    
    suspend fun createInviteLink(chatId: Int, maxUses: Int = 1, expiresAt: Long? = null): Result<InviteLink> {
        return withContext(Dispatchers.IO) {
            try {
                val request = CreateInviteRequest(chatId, maxUses, expiresAt)
                val response = apiService.createInviteLink(request)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to create invite link"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun validateInviteCode(code: String): Result<InviteLink> {
        return withContext(Dispatchers.IO) {
            try {
                val request = ValidateInviteRequest(code)
                val response = apiService.validateInviteCode(request)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to validate invite code"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun joinByInviteCode(code: String): Result<JoinChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = UseInviteRequest(code)
                val response = apiService.useInviteCode(request)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    result.chat?.let { chat ->
                        val existingChat = chatDao.getChatById(chat.id)
                        val newEntity = chatMappers.modelToEntity(chat)
                        val finalEntity = if (existingChat != null) {
                            newEntity.copy(
                                lastMessageId = existingChat.lastMessageId,
                                unreadCount = existingChat.unreadCount,
                                muted = existingChat.muted
                            )
                        } else {
                            newEntity
                        }
                        chatDao.insertChat(finalEntity)
                    }
                    Result.success(result)
                } else {
                    Result.failure(Exception("Failed to join chat"))
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
                    
                    // Preserve local state (lastMessageId, unreadCount) if exists
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
                    Result.failure(Exception("Failed to create saved messages chat"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
