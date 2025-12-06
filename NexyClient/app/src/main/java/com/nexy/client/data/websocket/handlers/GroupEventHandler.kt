package com.nexy.client.data.websocket.handlers

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.SettingsManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.repository.chat.ChatMappers
import com.nexy.client.utils.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupEventHandler @Inject constructor(
    private val chatDao: ChatDao,
    private val chatMappers: ChatMappers,
    private val apiService: NexyApiService,
    private val notificationHelper: NotificationHelper,
    private val settingsManager: SettingsManager,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "GroupEventHandler"
    }

    suspend fun handleChatCreated(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val chatId = (body["chat_id"] as? Double)?.toInt() ?: return
        
        Log.d(TAG, "Received chat_created: chatId=$chatId")
        
        try {
            val response = apiService.getChatById(chatId)
            if (response.isSuccessful && response.body() != null) {
                val chat = response.body()!!
                chatDao.insertChat(chatMappers.modelToEntity(chat))
                
                chat.participantIds?.forEach { userId ->
                    try {
                        userRepository.getUserById(userId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch participant $userId", e)
                    }
                }
                
                Log.d(TAG, "New chat fetched and inserted: $chatId")
                
                if (settingsManager.isPushNotificationsEnabled()) {
                    val title = if (chat.type == ChatType.GROUP) "New Group" else "New Chat"
                    val content = if (!chat.name.isNullOrEmpty()) "You were added to ${chat.name}" else "You have a new chat"
                    notificationHelper.showNotification(title, content, chatId)
                }
            } else {
                Log.e(TAG, "Failed to fetch chat details for $chatId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chat details for $chatId", e)
        }
    }

    suspend fun handleAddedToGroup(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val chatId = (body["chat_id"] as? Double)?.toInt() ?: return
        val chatName = body["chat_name"] as? String ?: "Group"
        
        Log.d(TAG, "Received added_to_group: chatId=$chatId, chatName=$chatName")
        
        try {
            val response = apiService.getChatById(chatId)
            if (response.isSuccessful && response.body() != null) {
                val chat = response.body()!!
                chatDao.insertChat(chatMappers.modelToEntity(chat))
                
                chat.participantIds?.forEach { userId ->
                    try {
                        userRepository.getUserById(userId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch participant $userId", e)
                    }
                }
                
                Log.d(TAG, "Added to group fetched and inserted: $chatId")
                
                if (settingsManager.isPushNotificationsEnabled()) {
                    notificationHelper.showNotification("Added to Group", "You were added to $chatName", chatId)
                }
            } else {
                Log.e(TAG, "Failed to fetch group details for $chatId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching group details for $chatId", e)
        }
    }

    suspend fun handleKickedFromGroup(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val chatId = (body["chat_id"] as? Double)?.toInt() ?: return
        
        Log.d(TAG, "Received kicked_from_group: chatId=$chatId")
        
        chatDao.deleteChatById(chatId)
        
        if (settingsManager.isPushNotificationsEnabled()) {
            notificationHelper.showNotification("Removed from Group", "You were kicked from the group", chatId)
        }
    }

    suspend fun handleBannedFromGroup(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val chatId = (body["chat_id"] as? Double)?.toInt() ?: return
        val reason = body["reason"] as? String
        
        Log.d(TAG, "Received banned_from_group: chatId=$chatId, reason=$reason")
        
        chatDao.deleteChatById(chatId)
        
        if (settingsManager.isPushNotificationsEnabled()) {
            val message = if (!reason.isNullOrEmpty()) "Reason: $reason" else "You were banned from the group"
            notificationHelper.showNotification("Banned from Group", message, chatId)
        }
    }
}
