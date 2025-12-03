package com.nexy.client.data.repository.message

import android.util.Log
import com.nexy.client.data.api.DeleteMessageRequest
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageOperations @Inject constructor(
    private val messageDao: MessageDao,
    private val apiService: NexyApiService,
    private val webSocketClient: NexyWebSocketClient,
    private val messageMappers: MessageMappers
) {
    
    companion object {
        private const val TAG = "MessageOperations"
    }
    
    fun getMessagesByChatId(chatId: Int): Flow<List<Message>> {
        return messageDao.getMessagesByChatId(chatId).map { entities ->
            entities.map { entity -> messageMappers.entityToModel(entity) }
        }
    }
    
    suspend fun getLastMessageForChat(chatId: Int): Message? {
        return withContext(Dispatchers.IO) {
            val entity = messageDao.getLastMessage(chatId)
            entity?.let { messageMappers.entityToModel(it) }
        }
    }
    
    suspend fun loadMessages(chatId: Int, limit: Int = 50, offset: Int = 0): Result<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getMessages(chatId, limit, offset)
                if (response.isSuccessful && response.body() != null) {
                    val messages = response.body()!!
                    Log.d(TAG, "loadMessages: chatId=$chatId, loaded ${messages.size} messages")
                    
                    // Insert to local database
                    messages.forEach { message ->
                        messageDao.insertMessage(messageMappers.modelToEntity(message))
                    }
                    
                    Result.success(messages)
                } else {
                    Log.e(TAG, "loadMessages failed: code=${response.code()}, message=${response.message()}")
                    Result.failure(Exception("Failed to load messages"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMessages exception", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun sendMessage(
        chatId: Int, 
        senderId: Int, 
        content: String, 
        type: MessageType = MessageType.TEXT
    ): Result<Message> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending message: chatId=$chatId, senderId=$senderId, content='$content'")
                val messageId = generateMessageId()
                val message = Message(
                    id = messageId,
                    chatId = chatId,
                    senderId = senderId,
                    content = content,
                    type = type,
                    status = MessageStatus.SENDING
                )
                
                Log.d(TAG, "Inserting message to local DB: ${message.id}")
                messageDao.insertMessage(messageMappers.modelToEntity(message))
                
                Log.d(TAG, "Sending message via WebSocket with messageId: $messageId")
                webSocketClient.sendTextMessage(chatId, senderId, content, messageId)
                
                Log.d(TAG, "Message sent successfully")
                Result.success(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Deleting message: messageId=$messageId")
                // Delete from server first
                val response = apiService.deleteMessage(DeleteMessageRequest(messageId))
                if (response.isSuccessful) {
                    // Then delete from local database
                    messageDao.deleteMessage(messageId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete message on server"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete message", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun deleteMessagesByChatId(chatId: Int) {
        withContext(Dispatchers.IO) {
            messageDao.deleteMessagesByChatId(chatId)
        }
    }
    
    private fun generateMessageId(): String {
        return "${System.currentTimeMillis()}-${(0..999999).random()}"
    }
}
