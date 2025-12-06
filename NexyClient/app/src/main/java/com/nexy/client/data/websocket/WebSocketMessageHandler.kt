package com.nexy.client.data.websocket

import android.util.Log
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.PendingMessageDao
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.repository.chat.ChatMappers
import com.nexy.client.data.repository.message.MessageMappers
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.SettingsManager
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketMessageHandler @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val pendingMessageDao: PendingMessageDao,
    private val messageMappers: MessageMappers,
    private val chatMappers: ChatMappers,
    private val apiService: NexyApiService,
    private val notificationHelper: NotificationHelper,
    private val settingsManager: SettingsManager,
    private val userRepository: UserRepository,
    private val tokenManager: AuthTokenManager
) {
    companion object {
        private const val TAG = "WSMessageHandler"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _typingEvents = kotlinx.coroutines.flow.MutableSharedFlow<Triple<Int, Boolean, Int?>>()
    val typingEvents = _typingEvents.asSharedFlow()

    fun handleIncomingMessage(nexyMessage: NexyMessage) {
        scope.launch {
            try {
                when (nexyMessage.header.type) {
                    "chat_message" -> handleChatMessage(nexyMessage)
                    "chat_created" -> handleChatCreated(nexyMessage)
                    "added_to_group" -> handleAddedToGroup(nexyMessage)
                    "ack" -> handleAck(nexyMessage)
                    "read" -> handleReadReceipt(nexyMessage)
                    "edit" -> handleEditMessage(nexyMessage)
                    "delete" -> handleDeleteMessage(nexyMessage)
                    "typing" -> handleTyping(nexyMessage)
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
        
        // Ensure sender exists locally for avatar display (force refresh to get latest avatar)
        if (message.senderId != 0) {
            try {
                userRepository.getUserById(message.senderId, forceRefresh = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch sender ${message.senderId}", e)
            }
        }
        
        // Ensure chat exists locally
        val existingChat = chatDao.getChatById(message.chatId)
        var isChatMuted = existingChat?.muted == true
        
        if (existingChat == null) {
            Log.d(TAG, "Chat ${message.chatId} missing locally, fetching from API")
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
                
                val currentUserId = tokenManager.getUserId()
                if (currentUserId != null && message.senderId != currentUserId) {
                    chatDao.incrementUnreadCount(message.chatId)
                    Log.d(TAG, "Incremented unread count for chat ${message.chatId}")
                }
                
                Log.d(TAG, "Updated chat ${message.chatId} with last message ${message.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat last message", e)
            }
            
            // Show notification for new messages (if chat is not muted)
            if (settingsManager.isPushNotificationsEnabled() && !isChatMuted) {
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
        
        Log.d(TAG, "Received chat_created: chatId=$chatId")
        
        // Fetch full chat details from API to ensure we have name, avatar, etc.
        try {
            val response = apiService.getChatById(chatId)
            if (response.isSuccessful && response.body() != null) {
                val chat = response.body()!!
                chatDao.insertChat(chatMappers.modelToEntity(chat))
                
                // Also fetch participants for avatars
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
    
    private suspend fun handleAddedToGroup(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        
        val chatId = (body["chat_id"] as? Double)?.toInt() ?: return
        val chatName = body["chat_name"] as? String ?: "Group"
        
        Log.d(TAG, "Received added_to_group: chatId=$chatId, chatName=$chatName")
        
        // Fetch full chat details from API
        try {
            val response = apiService.getChatById(chatId)
            if (response.isSuccessful && response.body() != null) {
                val chat = response.body()!!
                chatDao.insertChat(chatMappers.modelToEntity(chat))
                
                // Also fetch participants for avatars
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
    
    private suspend fun handleAck(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["message_id"] as? String ?: return
        val status = body["status"] as? String
        
        if (status == "ok") {
            Log.d(TAG, "Received ACK for message: $messageId")
            // Remove from pending queue
            pendingMessageDao.delete(messageId)
            // Update message status to SENT
            messageDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
            Log.d(TAG, "Message $messageId marked as SENT and removed from queue")
        }
    }
    
    private suspend fun handleReadReceipt(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        // Server sends "message_id" in the body for read receipts
        val messageId = (body["message_id"] as? String) ?: (body["read_message_id"] as? String) ?: return
        
        Log.d(TAG, "Received read receipt for message: $messageId")
        
        // Find the message to get its timestamp and chatId
        val message = messageDao.getMessageById(messageId)
        if (message != null) {
            val currentUserId = tokenManager.getUserId()
            Log.d(TAG, "Processing Read Receipt: msgId=$messageId, chatId=${message.chatId}, timestamp=${message.timestamp}, msgSender=${message.senderId}, currentUserId=$currentUserId")
            
            if (currentUserId != null) {
                if (message.senderId == currentUserId) {
                    Log.d(TAG, "Executing markMessagesAsReadUpTo for user $currentUserId")
                    val updatedCount = messageDao.markMessagesAsReadUpTo(message.chatId, message.timestamp, currentUserId)
                    Log.d(TAG, "Marked $updatedCount messages as READ")
                    
                    // Fallback: If bulk update failed (e.g. due to timestamp issues), ensure at least this message is updated
                    if (updatedCount == 0) {
                        Log.w(TAG, "markMessagesAsReadUpTo updated 0 rows! Force updating current message $messageId")
                        messageDao.updateMessageStatus(messageId, MessageStatus.READ.name)
                    }
                } else {
                    Log.d(TAG, "Skipping markMessagesAsReadUpTo: Message sender ${message.senderId} != current user $currentUserId")
                }
            } else {
                Log.e(TAG, "Cannot mark as read: currentUserId is null")
            }
        } else {
            Log.w(TAG, "Message $messageId not found for read receipt, updating status only if exists")
            messageDao.updateMessageStatus(messageId, MessageStatus.READ.name)
        }
    }
    
    private suspend fun handleEditMessage(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["message_id"] as? String ?: return
        val content = body["content"] as? String ?: return
        
        Log.d(TAG, "Handling edit message: messageId=$messageId, newContent=$content")
        
        messageDao.updateMessageContent(messageId, content, true)
    }
    
    private suspend fun handleDeleteMessage(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["message_id"] as? String ?: return

        Log.d(TAG, "Handling delete message: messageId=$messageId")

        // Delete from local database
        messageDao.deleteMessage(messageId)
    }
    
    private suspend fun handleTyping(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        // Handle both Double (Gson default) and other number types
        val chatId = when (val id = body["chat_id"]) {
            is Double -> id.toInt()
            is Int -> id
            is String -> id.toIntOrNull()
            else -> id.toString().toDoubleOrNull()?.toInt()
        }
        
        if (chatId == null) {
            Log.w(TAG, "Invalid chat_id in typing message: ${body["chat_id"]}")
            return
        }

        val isTyping = body["is_typing"] as? Boolean ?: return
        val senderId = nexyMessage.header.senderId
        
        Log.d(TAG, "Received typing event: chatId=$chatId, isTyping=$isTyping, senderId=$senderId")
        _typingEvents.emit(Triple(chatId, isTyping, senderId))
    }
    
    private fun convertTimestamp(timestamp: Long): String {
        val instant = java.time.Instant.ofEpochSecond(timestamp)
        val formatter = java.time.format.DateTimeFormatter.ISO_INSTANT
        return formatter.format(instant)
    }
}
