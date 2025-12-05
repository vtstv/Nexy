package com.nexy.client.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.User
import com.nexy.client.data.models.ChatFolder
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.FolderRepository
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
    val unreadCount: Int = 0,
    val isSelfChat: Boolean = false
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
    private val folderRepository: FolderRepository,
    private val tokenManager: AuthTokenManager,
    private val webSocketClient: NexyWebSocketClient,
    private val messageHandler: com.nexy.client.data.websocket.WebSocketMessageHandler,
    private val pinManager: PinManager,
    private val apiService: NexyApiService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    
    val folders: StateFlow<List<ChatFolder>> = folderRepository.folders
    
    private val _refreshTrigger = MutableStateFlow(0L)
    
    init {
        connectWebSocket()
        loadChats()
        loadCurrentUser()
        loadFolders()
        
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
                if (message.header.type == "chat_message") {
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
                // Initial load with force refresh to ensure we have latest data
                launch {
                    userRepository.getUserById(userId, forceRefresh = true)
                }
                
                // Observe changes from DB
                userRepository.getUserByIdFlow(userId).collect { user ->
                    if (user != null) {
                        _uiState.update { it.copy(currentUser = user) }
                    }
                }
            }
        }
    }
    
    private fun loadFolders() {
        viewModelScope.launch {
            folderRepository.loadFolders()
        }
    }
    
    fun refreshFolders() {
        loadFolders()
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
                        val (displayName, avatarUrl, isSelfChat) = when (chat.type) {
                            ChatType.PRIVATE -> {
                                val currentUserId = tokenManager.getUserId()
                                val otherUserId = chat.participantIds?.firstOrNull { it != currentUserId }
                                
                                if (otherUserId != null) {
                                    val userResult = userRepository.getUserById(otherUserId)
                                    val user = userResult.getOrNull()
                                    val name = user?.displayName?.takeIf { it.isNotBlank() } 
                                        ?: user?.username?.takeIf { it.isNotBlank() } 
                                        ?: "User $otherUserId"
                                    Triple(name, user?.avatarUrl, false)
                                } else {
                                    Triple("Notepad", null, true)
                                }
                            }
                            else -> Triple(chat.name?.takeIf { it.isNotBlank() } ?: "Unknown", chat.avatarUrl, false)
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
                            unreadCount = chat.unreadCount,
                            isSelfChat = isSelfChat
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
                    
                    // Smart Sync: Only fetch messages if we are missing the last one
                    chats.forEach { chat ->
                        launch {
                            try {
                                if (chat.lastMessage != null) {
                                    // Check if we have this message locally
                                    val lastMsg = chatRepository.getLastMessageForChat(chat.id)
                                    
                                    // If local last message is different from server's, or we don't have one, fetch!
                                    if (lastMsg == null || lastMsg.id != chat.lastMessage.id) {
                                        android.util.Log.d("ChatListViewModel", "Syncing chat ${chat.id}: Local=${lastMsg?.id}, Server=${chat.lastMessage.id}")
                                        chatRepository.loadMessages(chat.id, limit = 20, offset = 0)
                                        _refreshTrigger.value = System.currentTimeMillis()
                                    } else {
                                        android.util.Log.d("ChatListViewModel", "Chat ${chat.id} is up to date")
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatListViewModel", "Failed to sync chat ${chat.id}", e)
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

    fun addChatToFolder(chatId: Int, folderId: Int) {
        viewModelScope.launch {
            val folder = folderRepository.getFolder(folderId) ?: return@launch
            val currentChats = folder.includedChatIds?.toMutableList() ?: mutableListOf()
            if (!currentChats.contains(chatId)) {
                currentChats.add(chatId)
                folderRepository.updateFolderChats(folderId, currentChats)
            }
        }
    }

    fun removeChatFromFolder(chatId: Int, folderId: Int) {
        viewModelScope.launch {
            folderRepository.removeChatFromFolder(folderId, chatId)
        }
    }

    fun moveFolderLocally(fromIndex: Int, toIndex: Int) {
        folderRepository.moveFolderLocally(fromIndex, toIndex)
    }

    fun saveFolderOrder() {
        viewModelScope.launch {
            val folderIds = folderRepository.folders.value.map { it.id }
            folderRepository.reorderFolders(folderIds)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Do not disconnect WebSocket here as it is a singleton used by the entire app
        // webSocketClient.disconnect()
    }
}
