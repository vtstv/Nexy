/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.repository.ChatInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatInfoProvider @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao,
    private val tokenManager: AuthTokenManager
) {
    companion object {
        private const val TAG = "ChatInfoProvider"
    }

    suspend fun getChatInfo(chatId: Int): ChatInfo? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "getChatInfo: chatId=$chatId")
                val chat = chatDao.getChatById(chatId) ?: return@withContext null
                
                Log.d(TAG, "getChatInfo: chat type=${chat.type}, name=${chat.name}, participantIds=${chat.participantIds}")
                
                val displayName = when (chat.type) {
                    "PRIVATE" -> getPrivateChatDisplayName(chat.participantIds)
                    "GROUP" -> chat.name ?: "Group Chat"
                    else -> chat.name ?: "Unknown"
                }
                
                Log.d(TAG, "getChatInfo: ${chat.type} chat, displayName=$displayName")
                
                val avatarUrl = if (chat.type == "PRIVATE") {
                    getPrivateChatAvatar(chat.participantIds)
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

    private suspend fun getPrivateChatDisplayName(participantIds: String): String {
        val currentUserId = tokenManager.getUserId()
        Log.d(TAG, "getPrivateChatDisplayName: currentUserId=$currentUserId")
        
        val participantIdList = participantIds.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
        
        Log.d(TAG, "getPrivateChatDisplayName: other participantIds=${participantIdList.filter { it != currentUserId }}")
        
        val otherUserId = participantIdList.firstOrNull { it != currentUserId }
        return if (otherUserId != null) {
            Log.d(TAG, "getPrivateChatDisplayName: fetching user info for participantId=$otherUserId")
            val response = apiService.getUserById(otherUserId)
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                val displayName = user.displayName?.takeIf { name -> name.isNotBlank() } ?: user.username
                Log.d(TAG, "getPrivateChatDisplayName: PRIVATE chat, displayName=$displayName")
                displayName
            } else {
                Log.e(TAG, "getPrivateChatDisplayName: API call failed - code=${response.code()}")
                "Unknown"
            }
        } else {
            if (participantIdList.contains(currentUserId) && participantIdList.size == 1) {
                "Notepad"
            } else {
                "Unknown"
            }
        }
    }

    private suspend fun getPrivateChatAvatar(participantIds: String): String? {
        val currentUserId = tokenManager.getUserId()
        val participantIdList = participantIds.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
        val otherUserId = participantIdList.firstOrNull { it != currentUserId }
        
        return if (otherUserId != null) {
            val response = apiService.getUserById(otherUserId)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.avatarUrl
            } else null
        } else {
            null
        }
    }
}
