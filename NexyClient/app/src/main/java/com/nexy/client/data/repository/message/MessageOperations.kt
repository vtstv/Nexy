package com.nexy.client.data.repository.message

import android.util.Log
import com.nexy.client.BuildConfig
import com.nexy.client.data.models.DeleteMessageRequest
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.network.NetworkMonitor
import com.nexy.client.data.sync.MessageQueueManager
import com.nexy.client.data.websocket.ConnectionState
import com.nexy.client.data.websocket.NexyWebSocketClient
import com.nexy.client.data.websocket.WebSocketMessageHandler
import com.nexy.client.e2e.E2EManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.local.entity.UserEntity
import com.nexy.client.data.models.UserStatus

@Singleton
class MessageOperations @Inject constructor(
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val apiService: NexyApiService,
    private val webSocketClient: NexyWebSocketClient,
    private val webSocketMessageHandler: WebSocketMessageHandler,
    private val messageMappers: MessageMappers,
    private val e2eManager: E2EManager,
    private val messageQueueManager: MessageQueueManager,
    private val networkMonitor: NetworkMonitor
) {
    
    companion object {
        private const val TAG = "MessageOperations"
    }
    
    fun getMessagesByChatId(chatId: Int): Flow<List<Message>> {
        return messageDao.getMessagesByChatId(chatId).map { entities ->
            if (entities.isNotEmpty()) {
                val lastMsg = entities.last()
                Log.d(TAG, "Flow emitted ${entities.size} messages for chat $chatId. Last msg: ${lastMsg.message.id}, status: ${lastMsg.message.status}")
            }
            entities.map { entity -> messageMappers.messageWithSenderToModel(entity) }
        }
    }
    
    suspend fun getLastMessageForChat(chatId: Int): Message? {
        return withContext(Dispatchers.IO) {
            val messageWithSender = messageDao.getLastMessageWithSender(chatId)
            messageWithSender?.let { messageMappers.messageWithSenderToModel(it) }
        }
    }
    
    suspend fun loadMessages(chatId: Int, limit: Int = 50, offset: Int = 0): Result<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getMessages(chatId, limit, offset)
                if (response.isSuccessful && response.body() != null) {
                    val messages = response.body()!!
                    Log.d(TAG, "loadMessages: chatId=$chatId, loaded ${messages.size} messages")
                    
                    // Decrypt messages if needed (only in production)
                    val decryptedMessages = if (!BuildConfig.DEBUG) {
                        messages.map { message ->
                            if (message.encrypted && message.content?.startsWith("{\"ciphertext\"") == true) {
                                decryptMessage(message)
                            } else {
                                message
                            }
                        }
                    } else {
                        messages
                    }
                    
                    // Insert to local database
                    decryptedMessages.forEach { message ->
                        // Handle deleted messages
                        if (message.isDeleted) {
                            messageDao.deleteMessage(message.id)
                            return@forEach
                        }

                        // Insert sender if available
                        message.sender?.let { sender ->
                            userDao.insertUser(UserEntity(
                                id = sender.id,
                                username = sender.username,
                                email = sender.email,
                                displayName = sender.displayName,
                                avatarUrl = sender.avatarUrl,
                                status = sender.status?.name ?: UserStatus.OFFLINE.name,
                                bio = sender.bio,
                                publicKey = sender.publicKey,
                                createdAt = sender.createdAt
                            ))
                        }
                        
                        val entity = messageMappers.modelToEntity(message)
                        val existing = messageDao.getMessageById(message.id)
                        
                        if (existing != null) {
                            // Smart Merge: Preserve READ status if local is READ but server is SENT/DELIVERED
                            // This handles cases where server sync is lagging or buggy
                            val existingStatus = try { MessageStatus.valueOf(existing.status) } catch (e: Exception) { MessageStatus.SENT }
                            val newStatus = try { MessageStatus.valueOf(entity.status) } catch (e: Exception) { MessageStatus.SENT }
                            
                            val finalStatus = if (existingStatus.ordinal > newStatus.ordinal) {
                                existing.status
                            } else {
                                entity.status
                            }

                            // Preserve reactions if server didn't return them (null)
                            // If server returns empty list, it means reactions were cleared, so we use it.
                            val finalReactions = if (entity.reactions == null) {
                                existing.reactions
                            } else {
                                entity.reactions
                            }
                            
                            messageDao.updateMessage(entity.copy(
                                status = finalStatus,
                                reactions = finalReactions
                            ))
                        } else {
                            messageDao.insertMessage(entity)
                        }
                    }
                    
                    Result.success(decryptedMessages)
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
    
    /**
     * Decrypt a single message
     */
    private fun decryptMessage(message: Message): Message {
        if (!message.encrypted || message.senderId == 0 || message.content == null) {
            return message
        }
        
        try {
            // Parse encrypted content from JSON
            val encryptedData = parseEncryptedContent(message.content)
            if (encryptedData != null) {
                val decrypted = e2eManager.decryptMessage(encryptedData, message.senderId)
                if (decrypted != null) {
                    Log.d(TAG, "Message ${message.id} decrypted successfully")
                    return message.copy(
                        content = decrypted,
                        encrypted = false // Mark as decrypted for UI
                    )
                } else {
                    Log.w(TAG, "Failed to decrypt message ${message.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting message ${message.id}", e)
        }
        
        return message // Return original if decryption fails
    }
    
    /**
     * Parse encrypted content from JSON string
     */
    private fun parseEncryptedContent(content: String): com.nexy.client.e2e.EncryptedMessage? {
        return try {
            val json = com.google.gson.JsonParser.parseString(content).asJsonObject
            com.nexy.client.e2e.EncryptedMessage(
                ciphertext = json.get("ciphertext").asString,
                iv = json.get("iv").asString,
                algorithm = json.get("algorithm").asString
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse encrypted content", e)
            null
        }
    }
    
    private fun canSendImmediately(): Boolean {
        return networkMonitor.isConnected.value && 
               webSocketClient.connectionState.value == ConnectionState.CONNECTED
    }
    
    suspend fun sendMessage(
        chatId: Int, 
        senderId: Int, 
        content: String, 
        type: MessageType = MessageType.TEXT,
        recipientUserId: Int? = null,
        replyToId: Int? = null
    ): Result<Message> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending message: chatId=$chatId, senderId=$senderId, content='$content', replyToId=$replyToId")
                val messageId = generateMessageId()
                
                val finalContent: String
                val isEncrypted: Boolean
                val encryptionAlgorithm: String?
                
                if (!BuildConfig.DEBUG && recipientUserId != null && e2eManager.isE2EReady()) {
                    Log.d(TAG, "Production build - encrypting message for user $recipientUserId")
                    val encrypted = e2eManager.encryptMessage(recipientUserId, content)
                    if (encrypted != null) {
                        finalContent = """{"ciphertext":"${encrypted.ciphertext}","iv":"${encrypted.iv}","algorithm":"${encrypted.algorithm}"}"""
                        isEncrypted = true
                        encryptionAlgorithm = encrypted.algorithm
                        Log.d(TAG, "Message encrypted successfully")
                    } else {
                        Log.w(TAG, "E2E encryption failed - sending plain text")
                        finalContent = content
                        isEncrypted = false
                        encryptionAlgorithm = null
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Debug build - sending plain text message")
                    }
                    finalContent = content
                    isEncrypted = false
                    encryptionAlgorithm = null
                }
                
                val message = Message(
                    id = messageId,
                    chatId = chatId,
                    senderId = senderId,
                    content = finalContent,
                    type = type,
                    status = MessageStatus.SENDING,
                    encrypted = isEncrypted,
                    encryptionAlgorithm = encryptionAlgorithm,
                    replyToId = replyToId
                )
                
                Log.d(TAG, "Inserting message to local DB: ${message.id}, encrypted=$isEncrypted")
                messageDao.insertMessage(messageMappers.modelToEntity(message))
                
                // Always queue message for tracking - will be removed on ACK
                Log.d(TAG, "Queueing message for tracking: $messageId")
                messageQueueManager.queueMessage(
                    messageId = messageId,
                    chatId = chatId,
                    senderId = senderId,
                    content = finalContent,
                    messageType = type.name.lowercase(),
                    recipientId = recipientUserId,
                    replyToId = replyToId,
                    encrypted = isEncrypted,
                    encryptionAlgorithm = encryptionAlgorithm
                )
                
                // Try to send immediately if connected
                if (canSendImmediately()) {
                    Log.d(TAG, "Sending message via WebSocket with messageId: $messageId")
                    webSocketClient.sendTextMessage(chatId, senderId, finalContent, messageId, recipientUserId, replyToId)
                } else {
                    Log.d(TAG, "Offline or disconnected, message queued: $messageId")
                }
                
                Result.success(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun retryMessage(messageId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val success = messageQueueManager.retryMessage(messageId)
                if (success) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Message not found in queue"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun cancelMessage(messageId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val success = messageQueueManager.cancelMessage(messageId)
                Result.success(success)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    fun getPendingMessageCount(): Flow<Int> = messageQueueManager.pendingCount
    
    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteMessage(DeleteMessageRequest(messageId))
                // If successful or if message not found (already deleted/never existed), delete locally
                if (response.isSuccessful || response.code() == 404) {
                    messageDao.deleteMessage(messageId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete message: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun editMessage(messageId: String, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateMessage(messageId, com.nexy.client.data.models.UpdateMessageRequest(content))
                if (response.isSuccessful) {
                    // Update local DB
                    messageDao.updateMessageContent(messageId, content, true)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to edit message: ${response.code()}"))
                }
            } catch (e: Exception) {
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

    suspend fun searchMessages(chatId: Int, query: String): Result<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.searchMessages(chatId, query)
                if (response.isSuccessful && response.body() != null) {
                    val messages = response.body()!!
                    // Decrypt messages if needed (only in production)
                    val decryptedMessages = if (!BuildConfig.DEBUG) {
                        messages.map { message ->
                            if (message.encrypted && message.content?.startsWith("{\"ciphertext\"") == true) {
                                decryptMessage(message)
                            } else {
                                message
                            }
                        }
                    } else {
                        messages
                    }
                    Result.success(decryptedMessages)
                } else {
                    Result.failure(Exception("Failed to search messages"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun sendTyping(chatId: Int, isTyping: Boolean) {
        webSocketClient.sendTyping(chatId, isTyping)
    }

    fun observeTypingEvents(): kotlinx.coroutines.flow.SharedFlow<Triple<Int, Boolean, Int?>> = webSocketMessageHandler.typingEvents
}
