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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isCreator: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val editingMessage: Message? = null,
    val isTyping: Boolean = false,
    val typingUser: String? = null
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
    
    private var typingDebounceJob: Job? = null
    private var lastTypingSent = 0L
    private val TYPING_DEBOUNCE = 2000L // 2 seconds
    
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
        
        // Observe typing events
        viewModelScope.launch {
            messageOps.observeTypingEvents().collect { (eventChatId, isTyping, senderId) ->
                if (eventChatId == chatId) {
                    var typingUserName: String? = null
                    if (isTyping && senderId != null && _uiState.value.chatType == ChatType.GROUP) {
                        // Try to find user name
                        // Ideally we should have a user cache or repository call here
                        // For now, we'll try to fetch it or just use "Someone" if not found immediately
                        // In a real app, we'd look up the user in the local DB
                        typingUserName = stateManager.getUserName(senderId) ?: "Someone"
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        isTyping = isTyping,
                        typingUser = typingUserName
                    )
                }
            }
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
    
    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(
            isSearching = !_uiState.value.isSearching,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.length > 2) {
            searchMessages(query)
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    private fun searchMessages(query: String) {
        viewModelScope.launch {
            val result = messageOps.searchMessages(chatId, query)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(searchResults = result.getOrDefault(emptyList()))
            }
        }
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
                    // Only mark as read if the chat is actually visible/active
                    // We rely on the UI calling markAsRead() in LaunchedEffect or onResume
                    // Removing the automatic markAsRead here prevents background updates from triggering read receipts
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
    
    fun onMessageTextChanged(text: TextFieldValue) {
        _uiState.value = _uiState.value.copy(messageText = text)
        
        // Handle typing indicator
        if (text.text.isNotEmpty()) {
            // Send start typing if not already typing (or if debounce expired)
            // We don't track "isTyping" state for *self* in UI state usually, 
            // but we can use the debounce job existence as a proxy or just send it.
            // Better: Send it if we haven't sent it recently.
            
            if (typingDebounceJob == null || !typingDebounceJob!!.isActive) {
                 messageOps.sendTyping(chatId, true)
            }
            
            // Reset debounce timer
            typingDebounceJob?.cancel()
            typingDebounceJob = viewModelScope.launch {
                delay(2000) // 2 seconds debounce
                messageOps.sendTyping(chatId, false)
            }
        } else {
            // Text cleared, send stop typing immediately
            typingDebounceJob?.cancel()
            messageOps.sendTyping(chatId, false)
        }
    }
    
    fun sendMessage(replyToId: Int? = null) {
        if (_uiState.value.editingMessage != null) {
            saveEditedMessage()
            return
        }

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
            messageOps.deleteMessage(messageId)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun startEditing(message: Message) {
        _uiState.value = _uiState.value.copy(
            editingMessage = message,
            messageText = TextFieldValue(message.content)
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            editingMessage = null,
            messageText = TextFieldValue("")
        )
    }

    fun saveEditedMessage() {
        val state = uiState.value
        val message = state.editingMessage ?: return
        val newContent = state.messageText.text.trim()

        if (newContent.isEmpty()) return
        if (newContent == message.content) {
            cancelEditing()
            return
        }

        viewModelScope.launch {
            messageOps.editMessage(message.id, newContent)
                .onSuccess {
                    cancelEditing()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
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
    
    fun saveFile(context: Context, fileName: String) {
        viewModelScope.launch {
            try {
                fileOps.saveFileToDownloads(context, fileName).fold(
                    onSuccess = {
                        android.widget.Toast.makeText(context, "File saved to Downloads", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: "Failed to save file"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error saving file: ${e.message}"
                )
            }
        }
    }

    fun onMessageTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(messageText = TextFieldValue(text))
        
        // Handle typing indicator
        if (text.isNotEmpty()) {
            if (!_uiState.value.isTyping) {
                // Send start typing
                messageOps.sendTyping(chatId, true)
            }
            
            // Reset debounce timer
            typingDebounceJob?.cancel()
            typingDebounceJob = viewModelScope.launch {
                delay(2000) // 2 seconds debounce
                messageOps.sendTyping(chatId, false)
            }
        } else {
            // Text cleared, send stop typing immediately
            typingDebounceJob?.cancel()
            messageOps.sendTyping(chatId, false)
        }
    }
}
