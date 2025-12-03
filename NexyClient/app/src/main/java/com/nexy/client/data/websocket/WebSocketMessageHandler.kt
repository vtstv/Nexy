package com.nexy.client.data.websocket

import android.util.Log
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.repository.chat.ChatMappers
import com.nexy.client.data.repository.message.MessageMappers
import com.nexy.client.data.local.SettingsManager
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketMessageHandler @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val messageMappers: MessageMappers,
    private val chatMappers: ChatMappers,
    private val apiService: NexyApiService,
    private val notificationHelper: NotificationHelper,
    private val settingsManager: SettingsManager,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "WSMessageHandler"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun handleIncomingMessage(nexyMessage: NexyMessage) {
        scope.launch {
            try {
                when (nexyMessage.header.type) {
                    "chat_message" -> handleChatMessage(nexyMessage)
                    "chat_created" -> handleChatCreated(nexyMessage)
                    "ack" -> handleAck(nexyMessage)
                    "read" -> handleReadReceipt(nexyMessage)
                    else -> Log.d(TAG, "Ignoring message type: ${nexyMessage.header.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming message", e)
            }
        }
    }
    
    private suspend fun handleChatMessage(nexyMessage: NexyMessage) {
        val header = nexyMessage.header
        val body = nexyMessage.body ?: return
        
        val content = body["content"] as? String ?: ""
        val messageType = when (body["message_type"] as? String) {
            "text" -> MessageType.TEXT
            "media" -> MessageType.MEDIA
            "file" -> MessageType.FILE
            "system" -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }
        
        val message = Message(
            id = header.messageId,
            chatId = header.chatId ?: 0,
            senderId = header.senderId ?: 0,
            content = content,
            type = messageType,
            status = MessageStatus.DELIVERED,
            timestamp = convertTimestamp(header.timestamp),
            mediaUrl = body["media_url"] as? String,
            mediaType = body["media_type"] as? String
        )
        
        Log.d(TAG, "Saving incoming message to DB: chatId=${message.chatId}, messageId=${message.id}, content=${message.content}")
        
        // Ensure sender exists locally for avatar display
        if (message.senderId != 0) {
            try {
                userRepository.getUserById(message.senderId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch sender ${message.senderId}", e)
            }
        }
        
        // Ensure chat exists locally
        val existingChat = chatDao.getChatById(message.chatId)
        if (existingChat == null) {
            Log.d(TAG, "Chat ${message.chatId} missing locally, fetching from API")
            var chatInserted = false
            try {
                val response = apiService.getChatById(message.chatId)
                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    chatDao.insertChat(chatMappers.modelToEntity(chat))
                    Log.d(TAG, "Chat ${message.chatId} fetched and inserted")
                    chatInserted = true
                } else {
                    Log.e(TAG, "Failed to fetch chat ${message.chatId}: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching chat ${message.chatId}", e)
            }
            
            if (!chatInserted) {
                Log.w(TAG, "Inserting placeholder chat for ${message.chatId} to save message")
                // Insert placeholder so message can be saved
                val placeholderChat = com.nexy.client.data.local.entity.ChatEntity(
                    id = message.chatId,
                    type = "private", // Assume private
                    name = "New Chat",
                    avatarUrl = null,
                    participantIds = message.senderId.toString(), // At least we know the sender
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
        }

        val existingMessage = messageDao.getMessageById(message.id)
        if (existingMessage == null) {
            messageDao.insertMessage(messageMappers.modelToEntity(message))
            Log.d(TAG, "Message saved successfully")
            
            // Update chat's last message and timestamp
            try {
                chatDao.updateLastMessage(
                    chatId = message.chatId,
                    messageId = message.id,
                    timestamp = System.currentTimeMillis()
                )
                Log.d(TAG, "Updated chat ${message.chatId} with last message ${message.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat last message", e)
            }
            
            // Show notification for new messages
            // In a real app, we'd check if the chat is currently open/visible to avoid spamming
            // For now, we'll just show it.
            // We might want to fetch the sender name, but for now we'll use "New Message"
            if (settingsManager.isPushNotificationsEnabled()) {
                notificationHelper.showNotification("New Message", message.content, message.chatId)
            }
            
        } else {
            Log.d(TAG, "Message already exists, updating status")
            // If message exists (e.g. we sent it and it was in SENDING state), update it
            // If we are the sender, receiving it back means it's SENT/DELIVERED to server
            val updatedStatus = if (message.senderId == existingMessage.senderId) {
                MessageStatus.SENT // Confirmed by server
            } else {
                MessageStatus.DELIVERED // Received from someone else
            }
            
            messageDao.updateMessageStatus(message.id, updatedStatus.name)
        }
    }
    
    private suspend fun handleChatCreated(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        
        val chatId = (body["chat_id"] as? Double)?.toInt() ?: return
        val chatType = body["chat_type"] as? String ?: "private"
        val participantIds = (body["participant_ids"] as? List<*>)?.mapNotNull { 
            (it as? Double)?.toInt() 
        } ?: return
        val createdBy = (body["created_by"] as? Double)?.toInt() ?: return
        
        Log.d(TAG, "Received chat_created: chatId=$chatId, type=$chatType, participants=$participantIds")
        
        // Check if chat already exists
        val existingChat = chatDao.getChatById(chatId)
        if (existingChat == null) {
            // Create new chat in local database using ChatEntity
            val currentTime = System.currentTimeMillis()
            val chatEntity = com.nexy.client.data.local.entity.ChatEntity(
                id = chatId,
                type = chatType,
                name = "", // Will be populated when we fetch user details
                avatarUrl = null,
                participantIds = participantIds.joinToString(","),
                lastMessageId = null,
                unreadCount = 0,
                createdAt = currentTime,
                updatedAt = currentTime,
                muted = false
            )
            
            chatDao.insertChat(chatEntity)
            Log.d(TAG, "New chat created locally: $chatId")
            
            // Show notification
            if (settingsManager.isPushNotificationsEnabled()) {
                notificationHelper.showNotification("New Chat", "You have a new message", chatId)
            }
        } else {
            Log.d(TAG, "Chat already exists locally: $chatId")
        }
    }
    
    private suspend fun handleAck(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["message_id"] as? String ?: return
        val status = body["status"] as? String
        
        if (status == "ok") {
            Log.d(TAG, "Updating message status to SENT: $messageId")
            messageDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
        }
    }
    
    private suspend fun handleReadReceipt(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["read_message_id"] as? String ?: return
        
        Log.d(TAG, "Updating message status to READ: $messageId")
        messageDao.updateMessageStatus(messageId, MessageStatus.READ.name)
    }
    
    private fun convertTimestamp(timestamp: Long): String {
        val instant = java.time.Instant.ofEpochSecond(timestamp)
        val formatter = java.time.format.DateTimeFormatter.ISO_INSTANT
        return formatter.format(instant)
    }
}
