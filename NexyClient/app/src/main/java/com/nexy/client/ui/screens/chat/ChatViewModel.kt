package com.nexy.client.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val messageText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUserId: Int? = null,
    val chatName: String = "Chat",
    val chatAvatarUrl: String? = null,
    val chatType: ChatType = ChatType.PRIVATE,
    val groupType: GroupType? = null,
    val participantIds: List<Int> = emptyList(),
    val isSelfChat: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val tokenManager: AuthTokenManager,
    private val webRTCClient: WebRTCClient,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    // Get chatId from SavedStateHandle or use provided value
    private var chatId: Int = savedStateHandle.get<Int>("chatId") 
        ?: savedStateHandle.get<String>("chatId")?.toIntOrNull() 
        ?: 0
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    init {
        android.util.Log.d("ChatViewModel", "ViewModel init: chatId=$chatId (from SavedStateHandle)")
        if (chatId <= 0) {
            android.util.Log.d("ChatViewModel", "Invalid chatId, setting error state")
            _uiState.value = _uiState.value.copy(
                error = "Invalid chat ID",
                isLoading = false
            )
        } else {
            android.util.Log.d("ChatViewModel", "Valid chatId, loading data")
            loadCurrentUser()
            loadChatName()
            loadMessages()
        }
    }
    
    // Method to initialize chatId after ViewModel creation
    fun initializeChatId(newChatId: Int) {
        android.util.Log.d("ChatViewModel", "initializeChatId called: newChatId=$newChatId, current chatId=$chatId")
        if (chatId <= 0 && newChatId > 0) {
            android.util.Log.d("ChatViewModel", "Initializing chatId from $chatId to $newChatId")
            chatId = newChatId
            savedStateHandle["chatId"] = newChatId
            // Clear error and start loading
            _uiState.value = ChatUiState()
            loadCurrentUser()
            loadChatName()
            loadMessages()
        } else {
            android.util.Log.d("ChatViewModel", "Skipping initialization: chatId=$chatId, newChatId=$newChatId")
        }
    }
    
    // Method to set chatId programmatically
    fun setChatId(newChatId: Int, savedStateHandle: SavedStateHandle) {
        savedStateHandle["chatId"] = newChatId
    }
    
    private fun loadCurrentUser() {
        viewModelScope.launch {
            val userId = tokenManager.getUserId()
            _uiState.value = _uiState.value.copy(currentUserId = userId)
        }
    }
    
    private fun loadChatName() {
        viewModelScope.launch {
            try {
                // Ensure we have the current user ID
                val currentUserId = _uiState.value.currentUserId ?: tokenManager.getUserId()
                if (_uiState.value.currentUserId == null) {
                    _uiState.value = _uiState.value.copy(currentUserId = currentUserId)
                }

                // First, fetch fresh data from server
                val chatResult = chatRepository.getChatById(chatId)
                if (chatResult.isSuccess) {
                    val chat = chatResult.getOrNull()
                    if (chat != null) {
                        // Check if this is a self-chat (Notepad)
                        val isSelfChat = chat.type == ChatType.PRIVATE && 
                                        chat.participantIds?.size == 1 && 
                                        chat.participantIds?.contains(currentUserId) == true
                        
                        _uiState.value = _uiState.value.copy(
                            chatName = chat.name ?: "Chat",
                            chatAvatarUrl = chat.avatarUrl,
                            chatType = chat.type,
                            groupType = chat.groupType,
                            participantIds = chat.participantIds ?: emptyList(),
                            isSelfChat = isSelfChat
                        )
                    }
                } else {
                    // Fallback to local data
                    val chatInfo = chatRepository.getChatInfo(chatId)
                    if (chatInfo != null) {
                        val isSelfChat = chatInfo.type == ChatType.PRIVATE && 
                                        chatInfo.participantIds?.size == 1 && 
                                        chatInfo.participantIds?.contains(currentUserId) == true
                        
                        _uiState.value = _uiState.value.copy(
                            chatName = chatInfo.name,
                            chatAvatarUrl = chatInfo.avatarUrl,
                            chatType = chatInfo.type,
                            participantIds = chatInfo.participantIds ?: emptyList(),
                            isSelfChat = isSelfChat
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(chatName = "Chat")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error loading chat name", e)
                _uiState.value = _uiState.value.copy(chatName = "Chat")
            }
        }
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            try {
                // First ensure chat exists in DB before loading messages
                // This prevents FOREIGN KEY constraint errors
                ensureChatExists()
                
                chatRepository.getMessagesByChatId(chatId).collect { messages ->
                    _uiState.value = _uiState.value.copy(messages = messages)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load messages", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load messages: ${e.message}"
                )
            }
        }
        
        refreshMessages()
    }
    
    private suspend fun ensureChatExists() {
        try {
            // Try to get chat from repository, which will fetch from server if not in DB
            chatRepository.getChatById(chatId)
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "Chat not found, will be created on first message", e)
        }
    }
    
    private fun refreshMessages() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Ensure chat exists before loading messages
                ensureChatExists()
                
                chatRepository.loadMessages(chatId).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatViewModel", "Failed to refresh messages: ${error.message}", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Exception in refreshMessages", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to refresh messages: ${e.message}"
                )
            }
        }
    }
    
    fun onMessageTextChange(text: String) {
        _uiState.value = _uiState.value.copy(messageText = text)
    }
    
    fun sendMessage() {
        val text = _uiState.value.messageText.trim()
        if (text.isEmpty()) return
        
        viewModelScope.launch {
            try {
                val userId = _uiState.value.currentUserId ?: return@launch
                
                _uiState.value = _uiState.value.copy(messageText = "")
                
                chatRepository.sendMessage(chatId, userId, text).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(error = null)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to send message")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error sending message: ${e.message}")
            }
        }
    }
    
    fun sendFileMessage(context: Context, fileUri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                val userId = _uiState.value.currentUserId ?: return@launch
                
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                chatRepository.sendFileMessage(chatId, userId, context, fileUri, fileName).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(error = null, isLoading = false)
                        android.util.Log.d("ChatViewModel", "File message sent successfully")
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: "Failed to send file", 
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error sending file: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun markAsRead() {
        viewModelScope.launch {
            chatRepository.markChatAsRead(chatId)
        }
    }
    
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessage(messageId).fold(
                    onSuccess = {
                        android.util.Log.d("ChatViewModel", "Message deleted successfully: $messageId")
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: "Failed to delete message"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting message: ${e.message}"
                )
            }
        }
    }
    
    fun clearChat() {
        viewModelScope.launch {
            try {
                chatRepository.clearChatMessages(chatId).fold(
                    onSuccess = {
                        android.util.Log.d("ChatViewModel", "Chat cleared successfully")
                        _uiState.value = _uiState.value.copy(error = null)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: "Failed to clear chat"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error clearing chat: ${e.message}"
                )
            }
        }
    }
    
    fun deleteChat() {
        viewModelScope.launch {
            try {
                chatRepository.deleteChat(chatId).fold(
                    onSuccess = {
                        android.util.Log.d("ChatViewModel", "Chat deleted successfully")
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: "Failed to delete chat"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting chat: ${e.message}"
                )
            }
        }
    }
    
    fun downloadFile(context: Context, fileId: String, fileName: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                chatRepository.downloadFile(fileId, context, fileName).fold(
                    onSuccess = { fileUri ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                        android.util.Log.d("ChatViewModel", "File downloaded successfully to: $fileUri")
                        
                        // Just refresh UI to show "Open" button, don't auto-open
                        // Trigger a recomposition by updating a dummy state or just let the file existence check handle it
                        // Since MessageBubble checks file existence, it should update automatically on next composition
                        // But we might need to force a refresh if it doesn't
                        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: "Failed to download file",
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error downloading file: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun openFile(context: Context, fileName: String) {
        try {
            val file = java.io.File(context.getExternalFilesDir(null), fileName)
            if (!file.exists()) {
                _uiState.value = _uiState.value.copy(error = "File not found")
                return
            }
            
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, context.contentResolver.getType(contentUri) ?: "*/*")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            try {
                context.startActivity(android.content.Intent.createChooser(intent, "Open file"))
            } catch (e: android.content.ActivityNotFoundException) {
                _uiState.value = _uiState.value.copy(error = "No app found to open this file")
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error opening file", e)
            _uiState.value = _uiState.value.copy(
                error = "Error opening file: ${e.message}"
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun startCall() {
        val currentState = _uiState.value
        val currentUserId = currentState.currentUserId
        val participantIds = currentState.participantIds
        
        if (currentUserId != null && participantIds.isNotEmpty()) {
            val recipientId = participantIds.firstOrNull { it != currentUserId }
            if (recipientId != null) {
                webRTCClient.startCall(recipientId, currentUserId)
            } else {
                _uiState.value = currentState.copy(error = "Cannot find recipient for call")
            }
        } else {
            _uiState.value = currentState.copy(error = "Chat info not loaded")
        }
    }
}
