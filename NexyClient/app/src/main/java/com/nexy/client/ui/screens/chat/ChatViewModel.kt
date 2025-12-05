package com.nexy.client.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.handlers.CallHandler
import com.nexy.client.ui.screens.chat.handlers.ChatStateManager
import com.nexy.client.ui.screens.chat.handlers.EditingHandler
import com.nexy.client.ui.screens.chat.handlers.FileOperationsHandler
import com.nexy.client.ui.screens.chat.handlers.MessageOperationsHandler
import com.nexy.client.ui.screens.chat.handlers.SearchHandler
import com.nexy.client.ui.screens.chat.handlers.TypingHandler
import com.nexy.client.ui.screens.chat.state.ChatUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: com.nexy.client.data.repository.ChatRepository,
    private val stateManager: ChatStateManager,
    private val messageOps: MessageOperationsHandler,
    private val fileOps: FileOperationsHandler,
    private val callHandler: CallHandler,
    private val searchHandler: SearchHandler,
    private val typingHandler: TypingHandler,
    private val editingHandler: EditingHandler,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private var chatId: Int = savedStateHandle.get<Int>("chatId") 
        ?: savedStateHandle.get<String>("chatId")?.toIntOrNull() 
        ?: 0
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    init {
        android.util.Log.d("ChatViewModel", "ViewModel init: chatId=$chatId")
        if (chatId <= 0) {
            _uiState.value = _uiState.value.copy(error = "Invalid chat ID", isLoading = false)
        } else {
            initializeChat()
        }
        observeTypingEvents()
    }
    
    fun initializeChatId(newChatId: Int) {
        android.util.Log.d("ChatViewModel", "initializeChatId: newChatId=$newChatId, current=$chatId")
        if (chatId <= 0 && newChatId > 0) {
            chatId = newChatId
            savedStateHandle["chatId"] = newChatId
            _uiState.value = ChatUiState()
            initializeChat()
        }
    }
    
    // Flag to track if this is the first load (don't mark as read until messages loaded)
    private var firstLoading = true
    // Store firstUnreadMessageId before marking as read so divider persists until user sees it
    private var savedFirstUnreadMessageId: String? = null
    
    fun onChatOpened() {
        // Re-fetch chat info from server to get current firstUnreadMessageId
        android.util.Log.d("ChatViewModel", "onChatOpened: fetching fresh chat info from server")
        firstLoading = true
        savedFirstUnreadMessageId = null
        
        viewModelScope.launch {
            loadCurrentUserAndChatInfo()
            loadMessages()
        }
    }
    
    fun setChatId(newChatId: Int, savedStateHandle: SavedStateHandle) {
        savedStateHandle["chatId"] = newChatId
    }

    private fun initializeChat() {
        viewModelScope.launch {
            loadCurrentUserAndChatInfo()
            // DON'T mark as read yet - wait for messages to load first (like Telegram)
            loadMessages()
        }
    }
    
    // Called when user leaves the chat (like Telegram's onPause)
    fun onChatClosed() {
        android.util.Log.d("ChatViewModel", "onChatClosed: marking as read now")
        viewModelScope.launch {
            markAsReadInternal()
        }
    }
    
    private suspend fun loadCurrentUserAndChatInfo() {
        val userId = stateManager.loadCurrentUserId()
        _uiState.value = _uiState.value.copy(currentUserId = userId)
        
        try {
            stateManager.loadChatInfo(chatId, userId)?.let { chatInfo ->
                // Save firstUnreadMessageId BEFORE any marking as read
                // This ensures divider shows correctly until user scrolls/leaves
                if (savedFirstUnreadMessageId == null && chatInfo.firstUnreadMessageId != null) {
                    savedFirstUnreadMessageId = chatInfo.firstUnreadMessageId
                    android.util.Log.d("ChatViewModel", "Saved firstUnreadMessageId: $savedFirstUnreadMessageId")
                }
                
                android.util.Log.d("ChatViewModel", "Chat info loaded: unreadCount=${chatInfo.unreadCount}, firstUnreadMessageId=${chatInfo.firstUnreadMessageId}")
                _uiState.value = _uiState.value.copy(
                    chatName = chatInfo.chatName,
                    chatAvatarUrl = chatInfo.chatAvatarUrl,
                    chatType = chatInfo.chatType,
                    groupType = chatInfo.groupType,
                    participantIds = chatInfo.participantIds,
                    isSelfChat = chatInfo.isSelfChat,
                    isCreator = chatInfo.isCreator,
                    isMember = chatInfo.isMember,
                    mutedUntil = chatInfo.mutedUntil,
                    otherUserOnlineStatus = chatInfo.otherUserOnlineStatus,
                    // Use saved value to keep divider visible
                    firstUnreadMessageId = savedFirstUnreadMessageId ?: chatInfo.firstUnreadMessageId
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to load chat info", e)
        }
    }

    private fun observeTypingEvents() {
        viewModelScope.launch {
            typingHandler.observeTypingEvents().collect { (eventChatId, isTyping, senderId) ->
                if (eventChatId == chatId) {
                    val typingUserName = if (isTyping && senderId != null && _uiState.value.chatType == com.nexy.client.data.models.ChatType.GROUP) {
                        stateManager.getUserName(senderId) ?: "Someone"
                    } else null
                    
                    _uiState.value = _uiState.value.copy(isTyping = isTyping, typingUser = typingUserName)
                }
            }
        }
    }

    // region Search
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
            viewModelScope.launch {
                searchHandler.searchMessages(chatId, query)
                    .onSuccess { results ->
                        _uiState.value = _uiState.value.copy(searchResults = results)
                    }
            }
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }
    // endregion

    // region Load Data
    
    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            messageOps.observeMessages(chatId).collect { messages ->
                _uiState.value = _uiState.value.copy(
                    messages = messages,
                    isLoading = false
                )
                
                // Mark as read AFTER messages are loaded (like Telegram's !firstLoading check)
                if (firstLoading && messages.isNotEmpty()) {
                    firstLoading = false
                    android.util.Log.d("ChatViewModel", "Messages loaded, marking as read now")
                    markAsReadInternal()
                }
            }
        }
        
        viewModelScope.launch {
            try {
                messageOps.loadMessages(chatId)
            } catch (e: Exception) {
                // Error handled in repository
            }
        }
    }
    // endregion

    // region Message Operations
    fun onMessageTextChanged(text: TextFieldValue) {
        _uiState.value = _uiState.value.copy(messageText = text)
        typingHandler.handleTextChanged(viewModelScope, chatId, text.text.isNotEmpty())
    }
    
    fun onMessageTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(messageText = TextFieldValue(text))
        typingHandler.handleTextChanged(viewModelScope, chatId, text.isNotEmpty())
    }
    
    fun sendMessage(replyToId: Int? = null) {
        if (_uiState.value.editingMessage != null) {
            saveEditedMessage()
            return
        }

        val text = _uiState.value.messageText.text.trim()
        if (text.isEmpty()) return
        
        viewModelScope.launch {
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
            ).onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to send message")
            }
        }
    }
    
    private suspend fun markAsReadInternal() {
        android.util.Log.d("ChatViewModel", "markAsRead called")
        messageOps.markAsRead(chatId)
    }
    // endregion

    // region Editing
    fun startEditing(message: Message) {
        _uiState.value = _uiState.value.copy(
            editingMessage = message,
            messageText = TextFieldValue(message.content)
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(editingMessage = null, messageText = TextFieldValue(""))
    }

    fun saveEditedMessage() {
        val message = _uiState.value.editingMessage ?: return
        val newContent = _uiState.value.messageText.text.trim()

        if (newContent.isEmpty() || newContent == message.content) {
            cancelEditing()
            return
        }

        viewModelScope.launch {
            editingHandler.editMessage(message.id, newContent)
                .onSuccess { cancelEditing() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            editingHandler.deleteMessage(messageId)
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }
    // endregion

    // region Chat Operations
    fun clearChat() {
        viewModelScope.launch {
            messageOps.clearChat(chatId).onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to clear chat")
            }
        }
    }
    
    fun deleteChat() {
        viewModelScope.launch {
            messageOps.deleteChat(chatId).onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to delete chat")
            }
        }
    }
    // endregion

    // region File Operations
    fun sendFileMessage(context: Context, fileUri: Uri, fileName: String) {
        viewModelScope.launch {
            val userId = _uiState.value.currentUserId ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            fileOps.sendFileMessage(chatId, userId, context, fileUri, fileName)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(error = null, isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to send file", isLoading = false)
                }
        }
    }
    
    fun downloadFile(context: Context, fileId: String, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            fileOps.downloadFile(fileId, context, fileName)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null, messages = _uiState.value.messages)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to download file", isLoading = false)
                }
        }
    }
    
    fun openFile(context: Context, fileName: String) {
        fileOps.openFile(context, fileName)?.let { error ->
            _uiState.value = _uiState.value.copy(error = error)
        }
    }
    
    fun saveFile(context: Context, fileName: String) {
        viewModelScope.launch {
            fileOps.saveFileToDownloads(context, fileName)
                .onSuccess {
                    android.widget.Toast.makeText(context, "File saved to Downloads", android.widget.Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to save file")
                }
        }
    }
    // endregion

    // region Call
    fun startCall() {
        callHandler.startCall(_uiState.value.participantIds, _uiState.value.currentUserId)?.let { error ->
            _uiState.value = _uiState.value.copy(error = error)
        }
    }
    // endregion

    fun joinGroup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            stateManager.joinGroup(chatId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isMember = true)
                    initializeChat() // Reload chat info
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Failed to join group")
                }
        }
    }

    fun muteChat(duration: String?, until: String?) {
        viewModelScope.launch {
            chatRepository.muteChat(chatId, duration, until)
                .onSuccess {
                    refreshChatInfo()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }

    fun unmuteChat() {
        viewModelScope.launch {
            chatRepository.unmuteChat(chatId)
                .onSuccess {
                    refreshChatInfo()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
        }
    }
    
    private fun refreshChatInfo() {
        viewModelScope.launch {
            val currentUserId = stateManager.loadCurrentUserId()
            stateManager.loadChatInfo(chatId, currentUserId)?.let { chatInfo ->
                _uiState.value = _uiState.value.copy(
                    chatName = chatInfo.chatName,
                    chatAvatarUrl = chatInfo.chatAvatarUrl,
                    chatType = chatInfo.chatType,
                    groupType = chatInfo.groupType,
                    participantIds = chatInfo.participantIds,
                    isSelfChat = chatInfo.isSelfChat,
                    isCreator = chatInfo.isCreator,
                    isMember = chatInfo.isMember,
                    mutedUntil = chatInfo.mutedUntil,
                    otherUserOnlineStatus = chatInfo.otherUserOnlineStatus
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
