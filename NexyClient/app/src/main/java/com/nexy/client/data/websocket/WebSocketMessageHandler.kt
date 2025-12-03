package com.nexy.client.data.websocket

import android.util.Log
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.repository.message.MessageMappers
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
    private val messageMappers: MessageMappers,
    private val notificationHelper: NotificationHelper
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
        
        val existingMessage = messageDao.getMessageById(message.id)
        if (existingMessage == null) {
            messageDao.insertMessage(messageMappers.modelToEntity(message))
            Log.d(TAG, "Message saved successfully")
            
            // Show notification for new messages
            // In a real app, we'd check if the chat is currently open/visible to avoid spamming
            // For now, we'll just show it.
            // We might want to fetch the sender name, but for now we'll use "New Message"
            notificationHelper.showNotification("New Message", message.content, message.chatId)
            
        } else {
            Log.d(TAG, "Message already exists, skipping")
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
