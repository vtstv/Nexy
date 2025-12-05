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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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
                        val existingEntity = existingChatsMap[chat.id]
                        // Pass existingEntity to preserve local-only fields (isPinned, pinnedAt, isHidden)
                        val newEntity = chatMappers.modelToEntity(chat, existingEntity)
                        
                        if (existingEntity != null) {
                            val mergedLastMessageId = chat.lastMessage?.id ?: existingEntity.lastMessageId
                            // Use server's values - they are the source of truth
                            Log.d(TAG, "refreshChats: chat ${chat.id} server unreadCount=${chat.unreadCount}, firstUnreadMessageId=${chat.firstUnreadMessageId}")
                            
                            updates.add(newEntity.copy(
                                lastMessageId = mergedLastMessageId,
                                unreadCount = chat.unreadCount,
                                lastReadMessageId = chat.lastReadMessageId,
                                firstUnreadMessageId = chat.firstUnreadMessageId,
                                muted = newEntity.muted,
                                // Preserve local-only fields from existing entity
                                isPinned = existingEntity.isPinned,
                                pinnedAt = existingEntity.pinnedAt,
                                isHidden = existingEntity.isHidden
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
                    
                    // Pre-fetch participants for private chats to ensure names are available
                    // We do this after updating chats so the UI can show the list immediately, 
                    // even if names are temporarily missing (though they will update as users are fetched)
                    try {
                        val participantIds = chats
                            .filter { it.type == com.nexy.client.data.models.ChatType.PRIVATE }
                            .flatMap { it.participantIds ?: emptyList() }
                            .toSet()
                        
                        // Use supervisorScope to fetch users in parallel
                        supervisorScope {
                            participantIds.map { userId ->
                                async {
                                    try {
                                        userRepository.getUserById(userId)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to fetch participant $userId", e)
                                    }
                                }
                            }.awaitAll()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pre-fetching participants", e)
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
                Log.d(TAG, "getChatById: chatId=$chatId")
                
                // Try API first to get fresh data (especially participants and first_unread_message_id)
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
                    
                    // Use server values - they are the source of truth
                    Log.d(TAG, "getChatById: serverUnreadCount=${chat.unreadCount}, firstUnreadMessageId=${chat.firstUnreadMessageId}")
                    
                    val existingChat = chatDao.getChatById(chatId)
                    val updatedEntity = chatMappers.modelToEntity(chat).copy(
                        lastMessageId = existingChat?.lastMessageId ?: chat.lastMessage?.id,
                        // Preserve local-only fields
                        isPinned = existingChat?.isPinned ?: false,
                        pinnedAt = existingChat?.pinnedAt ?: 0L,
                        isHidden = existingChat?.isHidden ?: false
                    )
                    
                    if (existingChat != null) {
                        chatDao.updateChat(updatedEntity)
                    } else {
                        chatDao.insertChat(updatedEntity)
                    }
                    
                    Log.d(TAG, "getChatById: returning chat with unreadCount=${chat.unreadCount}, firstUnreadMessageId=${chat.firstUnreadMessageId}")
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
