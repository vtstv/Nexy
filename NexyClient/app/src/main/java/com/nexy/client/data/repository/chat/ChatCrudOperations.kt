package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.api.CreateChatRequest
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatCrudOperations @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val tokenManager: AuthTokenManager,
    private val chatMappers: ChatMappers
) {
    companion object {
        private const val TAG = "ChatCrudOperations"
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

    suspend fun createPrivateChat(userId: Int): Result<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUserId = tokenManager.getUserId()
                if (currentUserId == null) {
                    return@withContext Result.failure(Exception("User not logged in"))
                }

                val request = CreateChatRequest(userId)
                val response = apiService.createPrivateChat(request)

                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    val entity = chatMappers.modelToEntity(chat)
                    chatDao.insertChat(entity)
                    Result.success(chat)
                } else {
                    Result.failure(Exception("Failed to create private chat"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
