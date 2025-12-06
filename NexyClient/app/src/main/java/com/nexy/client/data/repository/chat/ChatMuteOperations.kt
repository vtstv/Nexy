package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.models.MuteChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMuteOperations @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao,
    private val chatSyncOperations: ChatSyncOperations
) {
    companion object {
        private const val TAG = "ChatMuteOperations"
    }

    suspend fun muteChat(chatId: Int, duration: String?, until: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = MuteChatRequest(duration, until)
                val response = apiService.muteChat(chatId, request)
                if (response.isSuccessful) {
                    chatSyncOperations.getChatById(chatId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to mute chat: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun unmuteChat(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.unmuteChat(chatId)
                if (response.isSuccessful) {
                    chatSyncOperations.getChatById(chatId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to unmute chat: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun pinChat(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.pinChat(chatId)
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to pin chat on server: ${response.code()}")
                    return@withContext Result.failure(Exception("Server error: ${response.code()}"))
                }

                val pinnedAt = System.currentTimeMillis()
                chatDao.setPinned(chatId, true, pinnedAt)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pin chat", e)
                Result.failure(e)
            }
        }
    }

    suspend fun unpinChat(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.unpinChat(chatId)
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to unpin chat on server: ${response.code()}")
                    return@withContext Result.failure(Exception("Server error: ${response.code()}"))
                }

                chatDao.setPinned(chatId, false, 0L)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unpin chat", e)
                Result.failure(e)
            }
        }
    }

    suspend fun hideChat(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                chatDao.setHidden(chatId, true)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide chat", e)
                Result.failure(e)
            }
        }
    }

    suspend fun unhideChat(chatId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                chatDao.setHidden(chatId, false)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unhide chat", e)
                Result.failure(e)
            }
        }
    }
}
