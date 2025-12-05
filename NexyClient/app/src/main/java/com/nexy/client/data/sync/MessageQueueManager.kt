package com.nexy.client.data.sync

import android.util.Log
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.PendingMessageDao
import com.nexy.client.data.local.entity.PendingMessageEntity
import com.nexy.client.data.local.entity.SendState
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.network.NetworkMonitor
import com.nexy.client.data.websocket.ConnectionState
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageQueueManager @Inject constructor(
    private val pendingMessageDao: PendingMessageDao,
    private val messageDao: MessageDao,
    private val webSocketClient: NexyWebSocketClient,
    private val networkMonitor: NetworkMonitor
) : NetworkMonitor.NetworkStatusListener {
    
    companion object {
        private const val TAG = "MessageQueueManager"
        private const val RETRY_DELAY_BASE_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val FLUSH_DEBOUNCE_MS = 500L
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null
    private var monitoringJob: Job? = null
    
    private val _isFlushing = MutableStateFlow(false)
    val isFlushing: StateFlow<Boolean> = _isFlushing
    
    private val _pendingCount = pendingMessageDao.getPendingCount()
        .stateIn(scope, SharingStarted.Lazily, 0)
    val pendingCount: StateFlow<Int> = _pendingCount
    
    fun start() {
        networkMonitor.addListener(this)
        startConnectionMonitoring()
        
        scope.launch {
            delay(1000)
            checkAndFlushPendingMessages()
        }
    }
    
    fun stop() {
        networkMonitor.removeListener(this)
        monitoringJob?.cancel()
        flushJob?.cancel()
        scope.cancel()
    }
    
    private fun startConnectionMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            webSocketClient.connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        Log.d(TAG, "WebSocket connected, scheduling flush")
                        scheduleFlush()
                    }
                    ConnectionState.DISCONNECTED, ConnectionState.FAILED -> {
                        Log.d(TAG, "WebSocket disconnected")
                    }
                    else -> {}
                }
            }
        }
    }
    
    override fun onNetworkAvailable() {
        Log.d(TAG, "Network available, scheduling flush")
        scheduleFlush()
    }
    
    override fun onNetworkLost() {
        Log.d(TAG, "Network lost, cancelling flush")
        flushJob?.cancel()
    }
    
    suspend fun queueMessage(
        messageId: String,
        chatId: Int,
        senderId: Int,
        content: String,
        messageType: String = "text",
        recipientId: Int? = null,
        replyToId: Int? = null,
        encrypted: Boolean = false,
        encryptionAlgorithm: String? = null,
        mediaUrl: String? = null,
        mediaType: String? = null
    ): PendingMessageEntity {
        val pending = PendingMessageEntity(
            messageId = messageId,
            chatId = chatId,
            senderId = senderId,
            content = content,
            messageType = messageType,
            recipientId = recipientId,
            replyToId = replyToId,
            encrypted = encrypted,
            encryptionAlgorithm = encryptionAlgorithm,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            sendState = SendState.QUEUED.name,
            createdAt = System.currentTimeMillis()
        )
        
        pendingMessageDao.insert(pending)
        Log.d(TAG, "Message queued: $messageId")
        
        if (shouldSendImmediately()) {
            scheduleFlush()
        }
        
        return pending
    }
    
    private fun shouldSendImmediately(): Boolean {
        return networkMonitor.isConnected.value && 
               webSocketClient.connectionState.value == ConnectionState.CONNECTED
    }
    
    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            flushPendingMessages()
        }
    }
    
    suspend fun checkAndFlushPendingMessages() {
        val pending = pendingMessageDao.getReadyToSend()
        if (pending.isNotEmpty()) {
            Log.d(TAG, "Found ${pending.size} pending messages to send")
            flushPendingMessages()
        }
    }
    
    private suspend fun flushPendingMessages() {
        if (_isFlushing.value) {
            Log.d(TAG, "Already flushing, skipping")
            return
        }
        
        if (!networkMonitor.isConnected.value) {
            Log.d(TAG, "No network, skipping flush")
            return
        }
        
        if (webSocketClient.connectionState.value != ConnectionState.CONNECTED) {
            Log.d(TAG, "WebSocket not connected, skipping flush")
            return
        }
        
        _isFlushing.value = true
        
        try {
            val messages = pendingMessageDao.getReadyToSend()
            Log.d(TAG, "Flushing ${messages.size} pending messages")
            
            for (message in messages) {
                if (!networkMonitor.isConnected.value) {
                    Log.d(TAG, "Network lost during flush, stopping")
                    break
                }
                
                sendPendingMessage(message)
                delay(100)
            }
        } finally {
            _isFlushing.value = false
        }
    }
    
    private suspend fun sendPendingMessage(pending: PendingMessageEntity) {
        try {
            pendingMessageDao.updateState(pending.messageId, SendState.SENDING.name)
            messageDao.updateMessageStatus(pending.messageId, MessageStatus.SENDING.name)
            
            Log.d(TAG, "Sending pending message: ${pending.messageId}")
            
            when (pending.messageType) {
                "text" -> {
                    webSocketClient.sendTextMessage(
                        chatId = pending.chatId,
                        senderId = pending.senderId,
                        content = pending.content,
                        messageId = pending.messageId,
                        recipientId = pending.recipientId,
                        replyToId = pending.replyToId
                    )
                }
                "media", "file" -> {
                    if (pending.mediaUrl != null) {
                        webSocketClient.sendMediaMessage(
                            chatId = pending.chatId,
                            senderId = pending.senderId,
                            mediaType = pending.messageType,
                            mediaUrl = pending.mediaUrl,
                            caption = pending.content.takeIf { it.isNotEmpty() },
                            mimeType = pending.mediaType,
                            messageId = pending.messageId
                        )
                    } else {
                        Log.e(TAG, "Media message without URL: ${pending.messageId}")
                        markAsFailed(pending, "Missing media URL")
                        return
                    }
                }
                else -> {
                    webSocketClient.sendTextMessage(
                        chatId = pending.chatId,
                        senderId = pending.senderId,
                        content = pending.content,
                        messageId = pending.messageId,
                        recipientId = pending.recipientId,
                        replyToId = pending.replyToId
                    )
                }
            }
            
            // Don't remove from queue here - wait for ACK in handleMessageAck()
            Log.d(TAG, "Message sent, waiting for ACK: ${pending.messageId}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${pending.messageId}", e)
            markAsFailed(pending, e.message)
        }
    }
    
    suspend fun handleMessageAck(messageId: String) {
        Log.d(TAG, "Received ACK for message: $messageId")
        pendingMessageDao.delete(messageId)
        messageDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
    }
    
    private suspend fun markAsFailed(pending: PendingMessageEntity, error: String?) {
        val newRetryCount = pending.retryCount + 1
        
        if (newRetryCount >= pending.maxRetries) {
            Log.e(TAG, "Message ${pending.messageId} exceeded max retries, marking as failed")
            pendingMessageDao.markFailed(pending.messageId, SendState.ERROR.name, error = error)
            messageDao.updateMessageStatus(pending.messageId, MessageStatus.FAILED.name)
        } else {
            Log.d(TAG, "Message ${pending.messageId} retry $newRetryCount/${pending.maxRetries}")
            pendingMessageDao.markFailed(pending.messageId, SendState.QUEUED.name, error = error)
            
            val delay = calculateRetryDelay(newRetryCount)
            scope.launch {
                delay(delay)
                if (shouldSendImmediately()) {
                    scheduleFlush()
                }
            }
        }
    }
    
    private fun calculateRetryDelay(retryCount: Int): Long {
        val delay = RETRY_DELAY_BASE_MS * (1 shl minOf(retryCount, 5))
        return minOf(delay, MAX_RETRY_DELAY_MS)
    }
    
    suspend fun retryMessage(messageId: String): Boolean {
        val pending = pendingMessageDao.getByMessageId(messageId) ?: return false
        
        val updated = pending.copy(
            sendState = SendState.QUEUED.name,
            retryCount = 0,
            errorMessage = null
        )
        pendingMessageDao.update(updated)
        messageDao.updateMessageStatus(messageId, MessageStatus.SENDING.name)
        
        if (shouldSendImmediately()) {
            scheduleFlush()
        }
        
        return true
    }
    
    suspend fun cancelMessage(messageId: String): Boolean {
        pendingMessageDao.delete(messageId)
        messageDao.deleteMessage(messageId)
        return true
    }
    
    suspend fun getFailedMessages(): List<PendingMessageEntity> {
        return pendingMessageDao.getByState(SendState.ERROR.name)
    }
    
    suspend fun retryAllFailed() {
        val failed = getFailedMessages()
        for (message in failed) {
            retryMessage(message.messageId)
        }
    }
}
