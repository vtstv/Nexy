package com.nexy.client.data.websocket.handlers

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.SettingsManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.repository.chat.ChatMappers
import com.nexy.client.data.repository.message.MessageMappers
import com.nexy.client.utils.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMessageHandler @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val messageMappers: MessageMappers,
    private val chatMappers: ChatMappers,
    private val apiService: NexyApiService,
    private val notificationHelper: NotificationHelper,
    private val settingsManager: SettingsManager,
    private val userRepository: UserRepository,
    private val tokenManager: AuthTokenManager
) {
    companion object {
        private const val TAG = "ChatMessageHandler"
    }

    suspend fun handle(nexyMessage: NexyMessage) {
        val header = nexyMessage.header
        val body = nexyMessage.body ?: return
        
        val content = body["content"] as? String ?: ""
        val messageType = when (body["message_type"] as? String) {
            "text" -> MessageType.TEXT
            "media" -> MessageType.MEDIA
            "file" -> MessageType.FILE
            "system" -> MessageType.SYSTEM
            "voice" -> MessageType.VOICE
            else -> MessageType.TEXT
        }
        
        val duration = (body["duration"] as? Number)?.toInt()
        
        val message = Message(
            id = header.messageId,
            chatId = header.chatId ?: 0,
            senderId = header.senderId ?: 0,
            content = content,
            type = messageType,
            status = MessageStatus.DELIVERED,
            timestamp = convertTimestamp(header.timestamp),
            mediaUrl = body["media_url"] as? String,
            mediaType = body["media_type"] as? String,
            duration = duration
        )
        
        Log.d(TAG, "Saving incoming message to DB: chatId=${message.chatId}, messageId=${message.id}, content=${message.content}")
        
        if (message.senderId != 0) {
            try {
                userRepository.getUserById(message.senderId, forceRefresh = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch sender ${message.senderId}", e)
            }
        }
        
        val existingChat = chatDao.getChatById(message.chatId)
        var isChatMuted = existingChat?.muted == true
        
        if (existingChat == null) {
            isChatMuted = ensureChatExists(message)
        }

        val existingMessage = messageDao.getMessageById(message.id)
        if (existingMessage == null) {
            saveNewMessage(message, isChatMuted)
        } else {
            updateExistingMessage(message, existingMessage)
        }
    }

    private suspend fun ensureChatExists(message: Message): Boolean {
        Log.d(TAG, "Chat ${message.chatId} missing locally, fetching from API")
        var isChatMuted = false
        var chatInserted = false
        
        try {
            val response = apiService.getChatById(message.chatId)
            if (response.isSuccessful && response.body() != null) {
                val chat = response.body()!!
                val chatEntity = chatMappers.modelToEntity(chat)
                chatDao.insertChat(chatEntity)
                isChatMuted = chatEntity.muted
                Log.d(TAG, "Chat ${message.chatId} fetched and inserted, muted=$isChatMuted")
                chatInserted = true
            } else {
                Log.e(TAG, "Failed to fetch chat ${message.chatId}: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chat ${message.chatId}", e)
        }
        
        if (!chatInserted) {
            Log.w(TAG, "Inserting placeholder chat for ${message.chatId} to save message")
            val placeholderChat = ChatEntity(
                id = message.chatId,
                type = "private",
                name = "New Chat",
                avatarUrl = null,
                participantIds = message.senderId.toString(),
                lastMessageId = null,
                unreadCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                muted = false
            )
            try {
                chatDao.insertChat(placeholderChat)
                Log.d(TAG, "Placeholder chat inserted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert placeholder chat", e)
            }
        }
        
        return isChatMuted
    }

    private suspend fun saveNewMessage(message: Message, isChatMuted: Boolean) {
        messageDao.insertMessage(messageMappers.modelToEntity(message))
        Log.d(TAG, "Message saved successfully")
        
        try {
            chatDao.updateLastMessage(
                chatId = message.chatId,
                messageId = message.id,
                timestamp = System.currentTimeMillis()
            )
            
            val currentUserId = tokenManager.getUserId()
            if (currentUserId != null && message.senderId != currentUserId) {
                chatDao.incrementUnreadCount(message.chatId)
                Log.d(TAG, "Incremented unread count for chat ${message.chatId}")
            }
            
            Log.d(TAG, "Updated chat ${message.chatId} with last message ${message.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update chat last message", e)
        }
        
        if (settingsManager.isPushNotificationsEnabled() && !isChatMuted) {
            notificationHelper.showNotification("New Message", message.content, message.chatId)
        }
    }

    private suspend fun updateExistingMessage(message: Message, existingMessage: com.nexy.client.data.local.entity.MessageEntity) {
        Log.d(TAG, "Message already exists, updating status")
        val updatedStatus = if (message.senderId == existingMessage.senderId) {
            MessageStatus.SENT
        } else {
            MessageStatus.DELIVERED
        }
        messageDao.updateMessageStatus(message.id, updatedStatus.name)
    }

    private fun convertTimestamp(timestamp: Long): String {
        val instant = java.time.Instant.ofEpochSecond(timestamp)
        val formatter = java.time.format.DateTimeFormatter.ISO_INSTANT
        return formatter.format(instant)
    }
}
