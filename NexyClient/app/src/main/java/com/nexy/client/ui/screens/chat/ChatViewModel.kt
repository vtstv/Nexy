package com.nexy.client.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.GroupType
import com.nexy.client.ui.screens.chat.handlers.CallHandler
import com.nexy.client.ui.screens.chat.handlers.ChatStateManager
import com.nexy.client.ui.screens.chat.handlers.FileOperationsHandler
import com.nexy.client.ui.screens.chat.handlers.MessageOperationsHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val messageText: TextFieldValue = TextFieldValue(""),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUserId: Int? = null,
    val chatName: String = "Chat",
    val chatAvatarUrl: String? = null,
    val chatType: ChatType = ChatType.PRIVATE,
    val groupType: GroupType? = null,
    val participantIds: List<Int> = emptyList(),
    val isSelfChat: Boolean = false,
    val isCreator: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val stateManager: ChatStateManager,
    private val messageOps: MessageOperationsHandler,
    private val fileOps: FileOperationsHandler,
    private val callHandler: CallHandler,
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
            val userId = stateManager.loadCurrentUserId()
            _uiState.value = _uiState.value.copy(currentUserId = userId)
        }
    }
    
    private fun loadChatName() {
        viewModelScope.launch {
            try {
                val currentUserId = stateManager.loadCurrentUserId()
                val chatInfo = stateManager.loadChatInfo(chatId, currentUserId)
                
                if (chatInfo != null) {
                    _uiState.value = _uiState.value.copy(
                        chatName = chatInfo.chatName,
                        chatAvatarUrl = chatInfo.chatAvatarUrl,
                        chatType = chatInfo.chatType,
                        groupType = chatInfo.groupType,
                        participantIds = chatInfo.participantIds,
                        isSelfChat = chatInfo.isSelfChat,
                        isCreator = chatInfo.isCreator
                    )
                }
            } catch (e: Exception) {
                // Ignore error for now
            }
        }
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // First, start observing local DB
            messageOps.observeMessages(chatId)
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        isLoading = false
                    )
                }
        }
        
        // Also trigger a fetch from server to ensure we're up to date
        viewModelScope.launch {
            try {
                messageOps.loadMessages(chatId)
            } catch (e: Exception) {
                // Error handled in repository/operations
            }
        }
    }
    
    private fun refreshMessages() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                messageOps.loadMessages(chatId).fold(
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
    
    fun onMessageTextChange(text: TextFieldValue) {
        _uiState.value = _uiState.value.copy(messageText = text)
    }
    
    fun sendMessage(replyToId: Int? = null) {
        val text = _uiState.value.messageText.text.trim()
        if (text.isEmpty()) return
        
        viewModelScope.launch {
            try {
                val userId = _uiState.value.currentUserId ?: return@launch
                
                _uiState.value = _uiState.value.copy(messageText = TextFieldValue(""))
                
                messageOps.sendMessage(
                    chatId = chatId,
                    userId = userId,
                    text = text,
                    chatType = _uiState.value.chatType,
                    isSelfChat = _uiState.value.isSelfChat,
                    participantIds = _uiState.value.participantIds,
                    replyToId = replyToId
                ).fold(
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
                
                fileOps.sendFileMessage(chatId, userId, context, fileUri, fileName).fold(
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
            messageOps.markAsRead(chatId)
        }
    }
    
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                messageOps.deleteMessage(messageId).fold(
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
                messageOps.clearChat(chatId).fold(
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
                messageOps.deleteChat(chatId).fold(
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
                
                fileOps.downloadFile(fileId, context, fileName).fold(
                    onSuccess = { fileUri ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                        android.util.Log.d("ChatViewModel", "File downloaded successfully to: $fileUri")
                        
                        // Trigger a recomposition
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
        val error = fileOps.openFile(context, fileName)
        if (error != null) {
            _uiState.value = _uiState.value.copy(error = error)
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun startCall() {
        val error = callHandler.startCall(_uiState.value.participantIds, _uiState.value.currentUserId)
        if (error != null) {
            _uiState.value = _uiState.value.copy(error = error)
        }
    }
}
