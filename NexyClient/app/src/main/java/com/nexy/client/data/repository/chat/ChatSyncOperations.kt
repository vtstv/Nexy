/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.models.Chat
import com.nexy.client.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSyncOperations @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao,
    private val chatMappers: ChatMappers,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "ChatSyncOperations"
    }

    suspend fun refreshChats(): Result<List<Chat>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getChats()
                if (response.isSuccessful && response.body() != null) {
                    val chats = response.body()!!
                    
                    val existingChatsMap = chatDao.getAllChatsSync().associateBy { it.id }
                    val serverChatIds = chats.map { it.id }.toSet()
                    
                    // Delete chats that exist locally but not on server
                    val chatsToDelete = existingChatsMap.keys.filter { !serverChatIds.contains(it) }
                    chatsToDelete.forEach { chatId ->
                        chatDao.deleteChatById(chatId)
                    }
                    
                    val updates = ArrayList<ChatEntity>()
                    val inserts = ArrayList<ChatEntity>()
                    
                    chats.forEach { chat ->
                        val newEntity = chatMappers.modelToEntity(chat)
                        val existingEntity = existingChatsMap[chat.id]
                        
                        if (existingEntity != null) {
                            val mergedLastMessageId = chat.lastMessage?.id ?: existingEntity.lastMessageId
                            val mergedUnreadCount = chat.unreadCount
                            
                            updates.add(newEntity.copy(
                                lastMessageId = mergedLastMessageId,
                                unreadCount = mergedUnreadCount,
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
                // Try API first to get fresh data (especially participants)
                val response = apiService.getChatById(chatId)
                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    
                    // Pre-fetch participants to ensure avatars are available
                    chat.participantIds?.forEach { userId ->
                        try {
                            userRepository.getUserById(userId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch participant $userId", e)
                        }
                    }
                    
                    val existingChat = chatDao.getChatById(chatId)
                    if (existingChat != null) {
                        val updatedEntity = chatMappers.modelToEntity(chat).copy(
                            lastMessageId = existingChat.lastMessageId,
                            unreadCount = existingChat.unreadCount,
                            muted = existingChat.muted
                        )
                        chatDao.updateChat(updatedEntity)
                    } else {
                        chatDao.insertChat(chatMappers.modelToEntity(chat))
                    }
                    Result.success(chat)
                } else {
                    // Fallback to local if API fails
                    val localChat = chatDao.getChatById(chatId)
                    if (localChat != null) {
                        val chat = chatMappers.entityToModel(localChat)
                        // Ensure participants are cached for avatars even if offline
                        chat.participantIds?.forEach { userId ->
                            try {
                                userRepository.getUserById(userId)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to fetch participant $userId", e)
                            }
                        }
                        Result.success(chat)
                    } else {
                        Result.failure(Exception("Chat not found"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting chat by ID: $chatId", e)
                // Fallback to local on error
                val localChat = chatDao.getChatById(chatId)
                if (localChat != null) {
                    val chat = chatMappers.entityToModel(localChat)
                    Result.success(chat)
                } else {
                    Result.failure(e)
                }
            }
        }
    }
}
