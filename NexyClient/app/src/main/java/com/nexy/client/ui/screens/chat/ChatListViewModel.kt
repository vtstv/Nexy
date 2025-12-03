package com.nexy.client.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.websocket.NexyWebSocketClient
import com.nexy.client.utils.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatWithInfo(
    val chat: Chat,
    val displayName: String,
    val lastMessagePreview: String = "No messages",
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0
)

data class ChatListUiState(
    val chats: List<ChatWithInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUser: User? = null
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val tokenManager: AuthTokenManager,
    private val webSocketClient: NexyWebSocketClient,
    private val messageHandler: com.nexy.client.data.websocket.WebSocketMessageHandler,
    private val pinManager: PinManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    private val _refreshTrigger = MutableStateFlow(0L)
    
    init {
        connectWebSocket()
        loadChats()
        loadCurrentUser()
        
        // Setup message handler to save incoming messages to DB
        webSocketClient.setMessageCallback { message ->
            messageHandler.handleIncomingMessage(message)
        }
        
        // Setup WebSocket callback to auto-refresh previews
        webSocketClient.setMessagePreviewCallback {
            android.util.Log.d("ChatListViewModel", "WebSocket callback triggered, updating refresh trigger")
            _refreshTrigger.value = System.currentTimeMillis()
        }
        
        // Also listen to incoming messages directly for immediate updates
        viewModelScope.launch {
            webSocketClient.incomingMessages.collect { message ->
                if (message != null && message.header.type == "chat_message") {
                    android.util.Log.d("ChatListViewModel", "Chat message received, updating refresh trigger")
                    _refreshTrigger.value = System.currentTimeMillis()
                    // Also refresh last message from DB for immediate preview update
                    kotlinx.coroutines.delay(100) // Small delay to ensure message is saved to DB
                    _refreshTrigger.value = System.currentTimeMillis()
                }
            }
        }
        
        // Monitor WebSocket connection state and refresh previews on reconnect
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                android.util.Log.d("ChatListViewModel", "WebSocket state changed: $state")
                if (state == com.nexy.client.data.websocket.ConnectionState.CONNECTED) {
                    android.util.Log.d("ChatListViewModel", "WebSocket connected, refreshing chats from server")
                    // Force refresh from server on reconnect
                    refreshChats()
                }
            }
        }
    }
    
    private fun loadCurrentUser() {
        viewModelScope.launch {
            tokenManager.getUserId()?.let { userId ->
                userRepository.getUserById(userId, forceRefresh = true).onSuccess { user ->
                    _uiState.value = _uiState.value.copy(currentUser = user)
                }
            }
        }
    }
    
    private fun connectWebSocket() {
        viewModelScope.launch {
            val token = tokenManager.getAccessToken()
            if (token != null) {
                webSocketClient.connect(token)
            }
        }
    }
    
    private fun loadChats() {
        viewModelScope.launch {
            combine(
                chatRepository.getAllChats(),
                _refreshTrigger
            ) { chats, _ -> chats }
                .collect { chats ->
                    val chatsWithInfo = ArrayList<ChatWithInfo>()
                    for (chat in chats) {
                        val (displayName, avatarUrl) = when (chat.type) {
                            ChatType.PRIVATE -> {
                                val chatInfo = chatRepository.getChatInfo(chat.id)
                                Pair(chatInfo?.name ?: "Unknown", chatInfo?.avatarUrl)
                            }
                            else -> Pair(chat.name ?: "Unknown", chat.avatarUrl)
                        }
                        
                        val lastMessage = chatRepository.getLastMessageForChat(chat.id)
                        val preview = when {
                            lastMessage == null -> "No messages"
                            lastMessage.mediaUrl != null -> "📎 ${lastMessage.content}"
                            else -> lastMessage.content
                        }
                        val timeStr = lastMessage?.timestamp?.let { formatMessageTime(it) }
                        
                        chatsWithInfo.add(ChatWithInfo(
                            chat = chat.copy(avatarUrl = avatarUrl),
                            displayName = displayName,
                            lastMessagePreview = preview,
                            lastMessageTime = timeStr,
                            unreadCount = chat.unreadCount
                        ))
                    }
                    _uiState.value = _uiState.value.copy(chats = chatsWithInfo)
                }
        }
        
        refreshChats()
    }
    
    private fun formatMessageTime(timestamp: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val date = sdf.parse(timestamp.substringBefore('.')) ?: return ""
            val now = java.util.Calendar.getInstance()
            val msgCal = java.util.Calendar.getInstance()
            msgCal.time = date
            
            when {
                now.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR) &&
                now.get(java.util.Calendar.DAY_OF_YEAR) == msgCal.get(java.util.Calendar.DAY_OF_YEAR) -> {
                    // Today - show time
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
                }
                now.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR) &&
                now.get(java.util.Calendar.DAY_OF_YEAR) - msgCal.get(java.util.Calendar.DAY_OF_YEAR) == 1 -> {
                    // Yesterday
                    "Yesterday"
                }
                now.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR) -> {
                    // This year - show date without year
                    java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(date)
                }
                else -> {
                    // Different year - show full date
                    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(date)
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    fun refreshChats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            chatRepository.refreshChats().fold(
                onSuccess = { chats ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _refreshTrigger.value = System.currentTimeMillis()
                    
                    // Fetch latest message for each chat to ensure previews are up to date
                    // This ensures we have previews even if the user hasn't opened the chat yet
                    chats.forEach { chat ->
                        launch {
                            try {
                                chatRepository.loadMessages(chat.id, limit = 1, offset = 0)
                                _refreshTrigger.value = System.currentTimeMillis()
                            } catch (e: Exception) {
                                android.util.Log.e("ChatListViewModel", "Failed to load preview for chat ${chat.id}", e)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }
    
    fun openSavedMessages(onChatClick: (Int) -> Unit) {
        viewModelScope.launch {
            chatRepository.getOrCreateSavedMessages().fold(
                onSuccess = { chat ->
                    onChatClick(chat.id)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to open Notepad"
                    )
                }
            )
        }
    }

    fun lockApp() {
        pinManager.lockImmediately()
    }
    
    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
    }
}
