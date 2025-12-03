package com.nexy.client.data.websocket

import android.util.Log
import com.google.gson.Gson
import com.nexy.client.data.models.nexy.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class NexyWebSocketClient(
    private val serverUrl: String,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "NexyWebSocket"
    }
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _incomingMessages = MutableStateFlow<NexyMessage?>(null)
    val incomingMessages: StateFlow<NexyMessage?> = _incomingMessages
    
    private var messageCallback: ((NexyMessage) -> Unit)? = null
    private var messagePreviewCallback: (() -> Unit)? = null
    private var authToken: String? = null
    
    fun connect(token: String) {
        if (_connectionState.value == ConnectionState.CONNECTED && authToken == token) {
            Log.d(TAG, "Already connected with same token, skipping connect")
            return
        }
        
        Log.d(TAG, "Connecting to WebSocket: $serverUrl")
        authToken = token
        disconnect()
        
        val request = Request.Builder()
            .url("$serverUrl?token=$token")
            .build()
        
        _connectionState.value = ConnectionState.CONNECTING
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully")
                _connectionState.value = ConnectionState.CONNECTED
                startHeartbeat()
                reconnectJob?.cancel()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
                handleIncomingMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failed: ${t.message}", t)
                _connectionState.value = ConnectionState.FAILED
                scheduleReconnect()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                heartbeatJob?.cancel()
                // Always try to reconnect if closed unexpectedly
                if (code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    fun disconnect() {
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    fun setMessagePreviewCallback(callback: () -> Unit) {
        messagePreviewCallback = callback
    }

    fun setMessageCallback(callback: (NexyMessage) -> Unit) {
        messageCallback = callback
    }
    
    fun sendTextMessage(chatId: Int, senderId: Int, content: String, messageId: String? = null, recipientId: Int? = null, replyToId: Int? = null) {
        Log.d(TAG, "Sending text message: chatId=$chatId, senderId=$senderId, content='$content', recipientId=$recipientId, replyToId=$replyToId")
        val msgId = messageId ?: generateMessageId()
        
        val body = mutableMapOf<String, Any>(
            "content" to content,
            "message_type" to "text"
        )
        
        if (replyToId != null) {
            body["reply_to_id"] = replyToId
        }
        
        val message = NexyMessage(
            header = NexyHeader(
                type = "chat_message",
                messageId = msgId,
                timestamp = System.currentTimeMillis(),
                senderId = senderId,
                chatId = chatId,
                recipientId = recipientId
            ),
            body = body
        )
        
        sendMessage(message)
    }
    
    fun sendMediaMessage(chatId: Int, senderId: Int, mediaType: String, mediaUrl: String, caption: String? = null, mimeType: String? = null) {
        val messageId = generateMessageId()
        val body = mutableMapOf<String, Any>(
            "message_type" to mediaType,
            "media_url" to mediaUrl,
            "content" to (caption ?: "")
        )
        
        if (mimeType != null) {
            body["media_type"] = mimeType
        }
        
        val message = NexyMessage(
            header = NexyHeader(
                type = "chat_message",
                messageId = messageId,
                timestamp = System.currentTimeMillis() / 1000,
                senderId = senderId,
                chatId = chatId
            ),
            body = body
        )
        sendMessage(message)
    }
    
    fun sendTypingIndicator(recipientId: Int, senderId: Int, isTyping: Boolean) {
        val message = NexyMessage(
            header = NexyHeader(
                type = "typing",
                messageId = generateMessageId(),
                timestamp = System.currentTimeMillis() / 1000,
                senderId = senderId,
                recipientId = recipientId
            ),
            body = mapOf("is_typing" to isTyping)
        )
        sendMessage(message)
    }
    
    fun sendReadReceipt(messageId: String, chatId: Int, senderId: Int) {
        val message = NexyMessage(
            header = NexyHeader(
                type = "read",
                messageId = generateMessageId(),
                timestamp = System.currentTimeMillis() / 1000,
                senderId = senderId,
                chatId = chatId
            ),
            body = mapOf("read_message_id" to messageId)
        )
        sendMessage(message)
    }
    
    fun updateOnlineStatus(userId: Int, status: String) {
        val message = NexyMessage(
            header = NexyHeader(
                type = if (status == "online") "online" else "offline",
                messageId = generateMessageId(),
                timestamp = System.currentTimeMillis() / 1000,
                senderId = userId
            ),
            body = null
        )
        sendMessage(message)
    }
    
    fun sendSignalingMessage(recipientId: Int, senderId: Int, type: String, body: Any) {
        Log.d(TAG, "Sending signaling message: type=$type, recipientId=$recipientId")
        val message = NexyMessage(
            header = NexyHeader(
                type = type,
                messageId = generateMessageId(),
                timestamp = System.currentTimeMillis(),
                senderId = senderId,
                recipientId = recipientId
            ),
            body = gson.fromJson(gson.toJson(body), Map::class.java) as Map<String, Any>
        )
        sendMessage(message)
    }
    
    private fun sendMessage(message: NexyMessage) {
        if (webSocket == null) {
            Log.e(TAG, "Cannot send message: WebSocket is null. Attempting to reconnect...")
            if (authToken != null) {
                connect(authToken!!)
                // Queue message? For now just drop and let user retry or rely on reconnect
            }
            return
        }
        
        val json = gson.toJson(message)
        try {
            val success = webSocket?.send(json) ?: false
            if (!success) {
                Log.e(TAG, "Failed to send message (send returned false)")
                scheduleReconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending message", e)
            scheduleReconnect()
        }
    }
    
    private fun sendHeartbeat() {
        val message = NexyMessage(
            header = NexyHeader(
                type = "heartbeat",
                messageId = generateMessageId(),
                timestamp = System.currentTimeMillis() / 1000
            ),
            body = null
        )
        sendMessage(message)
    }
    
    private fun handleIncomingMessage(text: String) {
        try {
            val message = gson.fromJson(text, NexyMessage::class.java)
            
            message?.let {
                _incomingMessages.value = it
                messageCallback?.invoke(it)
                
                // Trigger preview update only for chat messages
                if (it.header.type == "chat_message") {
                    Log.d(TAG, "Chat message received, triggering preview update")
                    messagePreviewCallback?.invoke()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                sendHeartbeat()
                delay(20_000)
            }
        }
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d(TAG, "Scheduling reconnect in 3 seconds...")
            delay(3_000)
            if (authToken != null) {
                Log.d(TAG, "Attempting to reconnect...")
                connect(authToken!!)
            } else {
                Log.e(TAG, "Cannot reconnect: Auth token is null")
            }
        }
    }
    
    private fun generateMessageId(): String {
        return "${System.currentTimeMillis()}-${(0..999999).random()}"
    }
    
    fun cleanup() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}
