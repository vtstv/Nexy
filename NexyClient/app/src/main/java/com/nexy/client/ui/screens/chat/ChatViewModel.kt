package com.nexy.client.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.handlers.CallHandler
import com.nexy.client.ui.screens.chat.handlers.ChatMembershipHandler
import com.nexy.client.ui.screens.chat.handlers.ChatStateManager
import com.nexy.client.ui.screens.chat.handlers.EditingHandler
import com.nexy.client.ui.screens.chat.handlers.FileOperationsHandler
import com.nexy.client.ui.screens.chat.handlers.MessageOperationsHandler
import com.nexy.client.ui.screens.chat.handlers.ReadReceiptHandler
import com.nexy.client.ui.screens.chat.handlers.SearchHandler
import com.nexy.client.ui.screens.chat.handlers.TypingHandler
import com.nexy.client.ui.screens.chat.state.ChatUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val stateManager: ChatStateManager,
    private val messageOps: MessageOperationsHandler,
    private val fileOps: FileOperationsHandler,
    private val callHandler: CallHandler,
    private val searchHandler: SearchHandler,
    private val typingHandler: TypingHandler,
    private val editingHandler: EditingHandler,
    private val membershipHandler: ChatMembershipHandler,
    private val readReceiptHandler: ReadReceiptHandler,
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
        observeConnectionStatus()
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
    
    fun onChatOpened() {
        android.util.Log.d("ChatViewModel", "onChatOpened: fetching fresh chat info from server")
        readReceiptHandler.reset()
        readReceiptHandler.setChatActive(true)
        
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
            loadMessages()
        }
    }
    
    fun onChatClosed() {
        android.util.Log.d("ChatViewModel", "onChatClosed: marking as read now")
        readReceiptHandler.setChatActive(false)
        readReceiptHandler.cancelPendingReceipt()
        viewModelScope.launch {
            readReceiptHandler.markAsRead(chatId)
        }
    }
    
    private suspend fun loadCurrentUserAndChatInfo() {
        val userId = stateManager.loadCurrentUserId()
        _uiState.value = _uiState.value.copy(currentUserId = userId)
        
        try {
            stateManager.loadChatInfo(chatId, userId)?.let { chatInfo ->
                readReceiptHandler.saveFirstUnreadMessageId(chatInfo.firstUnreadMessageId)
                
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
                    firstUnreadMessageId = readReceiptHandler.getSavedFirstUnreadMessageId() ?: chatInfo.firstUnreadMessageId
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
    
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            messageOps.observeConnectionStatus().collect { isConnected ->
                _uiState.value = _uiState.value.copy(isConnected = isConnected)
            }
        }
        viewModelScope.launch {
            messageOps.getPendingMessageCount().collect { count ->
                _uiState.value = _uiState.value.copy(pendingMessageCount = count)
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
                val newestMessage = messages.filter { it.timestamp != null }
                    .maxByOrNull { it.timestamp!! }
                readReceiptHandler.updateLastKnownMessageId(newestMessage?.id)
                
                _uiState.value = _uiState.value.copy(
                    messages = messages,
                    isLoading = false
                )
                
                if (!readReceiptHandler.isChatActive()) {
                    android.util.Log.d("ChatViewModel", "Chat not active, skipping markAsRead")
                } else if (readReceiptHandler.isFirstLoading() && messages.isNotEmpty()) {
                    readReceiptHandler.setFirstLoadingComplete()
                    android.util.Log.d("ChatViewModel", "Messages loaded, marking as read now")
                    readReceiptHandler.markAsRead(chatId)
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
    
    fun onUserSawNewMessages() {
        if (!readReceiptHandler.isChatActive()) return
        
        val job = viewModelScope.launch {
            delay(readReceiptHandler.getDebounceMs())
            android.util.Log.d("ChatViewModel", "User saw new messages (debounced), marking as read")
            readReceiptHandler.markAsRead(chatId)
        }
        readReceiptHandler.setDebounceJob(job)
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
    // region Chat Operations
    fun clearChat() {
        viewModelScope.launch {
            membershipHandler.clearChat(chatId).onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to clear chat")
            }
        }
    }
    
    fun deleteChat() {
        viewModelScope.launch {
            membershipHandler.deleteChat(chatId).onFailure { error ->
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

    // region Membership
    fun joinGroup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            membershipHandler.joinGroup(chatId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isMember = true)
                    initializeChat()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Failed to join group")
                }
        }
    }

    fun muteChat(duration: String?, until: String?) {
        viewModelScope.launch {
            membershipHandler.muteChat(chatId, duration, until)
                .onSuccess { refreshChatInfo() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun unmuteChat() {
        viewModelScope.launch {
            membershipHandler.unmuteChat(chatId)
                .onSuccess { refreshChatInfo() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    suspend fun validateGroupInvite(code: String) = membershipHandler.validateGroupInvite(code)

    suspend fun joinByInviteCode(code: String): Result<Int> {
        return membershipHandler.joinByInviteCode(code).map { response ->
            response.chat?.id ?: throw Exception("No chat returned")
        }
    }
    // endregion
    
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
