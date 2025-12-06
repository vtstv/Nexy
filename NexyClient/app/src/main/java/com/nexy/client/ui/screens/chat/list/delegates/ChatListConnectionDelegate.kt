package com.nexy.client.ui.screens.chat.list.delegates

import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.websocket.ConnectionState
import com.nexy.client.data.websocket.NexyWebSocketClient
import com.nexy.client.data.websocket.WebSocketMessageHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatListConnectionDelegate @Inject constructor(
    private val webSocketClient: NexyWebSocketClient,
    private val messageHandler: WebSocketMessageHandler,
    private val tokenManager: AuthTokenManager
) {
    private lateinit var scope: CoroutineScope
    private lateinit var refreshTrigger: MutableStateFlow<Long>
    private var onRefreshChats: (() -> Unit)? = null

    fun initialize(
        scope: CoroutineScope,
        refreshTrigger: MutableStateFlow<Long>,
        onRefreshChats: () -> Unit
    ) {
        this.scope = scope
        this.refreshTrigger = refreshTrigger
        this.onRefreshChats = onRefreshChats
    }

    fun setupWebSocket() {
        connectWebSocket()
        setupMessageCallback()
        setupPreviewCallback()
        observeIncomingMessages()
        observeConnectionState()
    }

    private fun connectWebSocket() {
        scope.launch {
            val token = tokenManager.getAccessToken()
            if (token != null) {
                webSocketClient.connect(token)
            }
        }
    }

    private fun setupMessageCallback() {
        webSocketClient.setMessageCallback { message ->
            messageHandler.handleIncomingMessage(message)
        }
    }

    private fun setupPreviewCallback() {
        webSocketClient.setMessagePreviewCallback {
            android.util.Log.d("ChatListConnectionDelegate", "WebSocket callback triggered, updating refresh trigger")
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    private fun observeIncomingMessages() {
        scope.launch {
            webSocketClient.incomingMessages.collect { message ->
                if (message.header.type == "chat_message") {
                    android.util.Log.d("ChatListConnectionDelegate", "Chat message received, updating refresh trigger")
                    refreshTrigger.value = System.currentTimeMillis()
                    delay(100)
                    refreshTrigger.value = System.currentTimeMillis()
                }
            }
        }
    }

    private fun observeConnectionState() {
        scope.launch {
            webSocketClient.connectionState.collect { state ->
                android.util.Log.d("ChatListConnectionDelegate", "WebSocket state changed: $state")
                if (state == ConnectionState.CONNECTED) {
                    android.util.Log.d("ChatListConnectionDelegate", "WebSocket connected, refreshing chats from server")
                    onRefreshChats?.invoke()
                }
            }
        }
    }
}
