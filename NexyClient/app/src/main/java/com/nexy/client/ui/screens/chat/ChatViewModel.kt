package com.nexy.client.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.delegates.FileDelegate
import com.nexy.client.ui.screens.chat.delegates.MembershipDelegate
import com.nexy.client.ui.screens.chat.delegates.MessageDelegate
import com.nexy.client.ui.screens.chat.delegates.SearchDelegate
import com.nexy.client.ui.screens.chat.handlers.CallHandler
import com.nexy.client.ui.screens.chat.handlers.ChatStateManager
import com.nexy.client.ui.screens.chat.handlers.ReadReceiptHandler
import com.nexy.client.ui.screens.chat.handlers.TypingHandler
import com.nexy.client.ui.screens.chat.state.ChatUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val stateManager: ChatStateManager,
    private val callHandler: CallHandler,
    private val typingHandler: TypingHandler,
    private val readReceiptHandler: ReadReceiptHandler,
    private val searchDelegate: SearchDelegate,
    private val fileDelegate: FileDelegate,
    private val membershipDelegate: MembershipDelegate,
    private val messageDelegate: MessageDelegate,
    private val savedStateHandle: SavedStateHandle,
    private val applicationScope: CoroutineScope
) : ViewModel() {

    private var chatId: Int = savedStateHandle.get<Int>("chatId")
        ?: savedStateHandle.get<String>("chatId")?.toIntOrNull()
        ?: 0

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        android.util.Log.d("ChatViewModel", "ViewModel init: chatId=$chatId")
        initializeDelegates()
        
        if (chatId <= 0) {
            _uiState.value = _uiState.value.copy(error = "Invalid chat ID", isLoading = false)
        } else {
            initializeChat()
        }
        observeTypingEvents()
        messageDelegate.observeConnectionStatus()
        observeCurrentUser()
    }

    private fun initializeDelegates() {
        val getChatId = { chatId }
        searchDelegate.initialize(viewModelScope, _uiState, getChatId)
        fileDelegate.initialize(viewModelScope, _uiState, getChatId)
        membershipDelegate.initialize(viewModelScope, _uiState, getChatId)
        membershipDelegate.setOnChatInitialized { initializeChat() }
        messageDelegate.initialize(viewModelScope, _uiState, getChatId)
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
        readReceiptHandler.setChatActive(true)
        readReceiptHandler.reset()

        viewModelScope.launch {
            loadCurrentUserAndChatInfo()
            messageDelegate.loadMessages()
        }
    }

    fun setChatId(newChatId: Int, savedStateHandle: SavedStateHandle) {
        savedStateHandle["chatId"] = newChatId
    }

    private fun initializeChat() {
        viewModelScope.launch {
            loadCurrentUserAndChatInfo()
            messageDelegate.loadMessages()
        }
    }

    fun onChatClosed() {
        android.util.Log.d("ChatViewModel", "onChatClosed: marking as read now")
        readReceiptHandler.setChatActive(false)
        readReceiptHandler.cancelPendingReceipt()
        applicationScope.launch {
            try {
                readReceiptHandler.markAsRead(chatId)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to mark as read in onChatClosed", e)
            }
        }
    }

    private suspend fun loadCurrentUserAndChatInfo() {
        val userId = stateManager.loadCurrentUserId()
        val currentUser = stateManager.getCurrentUser()
        _uiState.value = _uiState.value.copy(
            currentUserId = userId,
            voiceMessagesEnabled = currentUser?.voiceMessagesEnabled ?: true
        )

        try {
            stateManager.loadChatInfo(chatId, userId)?.let { chatInfo ->
                readReceiptHandler.saveFirstUnreadMessageId(chatInfo.firstUnreadMessageId)

                android.util.Log.d("ChatViewModel", "Chat info loaded: unreadCount=${chatInfo.unreadCount}, firstUnreadMessageId=${chatInfo.firstUnreadMessageId}, userRole=${chatInfo.userRole}")
                _uiState.value = _uiState.value.copy(
                    chatName = chatInfo.chatName,
                    chatAvatarUrl = chatInfo.chatAvatarUrl,
                    chatType = chatInfo.chatType,
                    groupType = chatInfo.groupType,
                    participantIds = chatInfo.participantIds,
                    participants = chatInfo.participants,
                    isSelfChat = chatInfo.isSelfChat,
                    isCreator = chatInfo.isCreator,
                    isMember = chatInfo.isMember,
                    mutedUntil = chatInfo.mutedUntil,
                    otherUserOnlineStatus = chatInfo.otherUserOnlineStatus,
                    firstUnreadMessageId = readReceiptHandler.getSavedFirstUnreadMessageId() ?: chatInfo.firstUnreadMessageId,
                    recipientVoiceMessagesEnabled = chatInfo.recipientVoiceMessagesEnabled,
                    userRole = chatInfo.userRole
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to load chat info", e)
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            stateManager.getCurrentUserFlow().collect { user ->
                android.util.Log.d("ChatViewModel", "Current user update: voiceMessagesEnabled=${user?.voiceMessagesEnabled}")
                _uiState.value = _uiState.value.copy(
                    voiceMessagesEnabled = user?.voiceMessagesEnabled ?: true
                )
            }
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

    // region Search - delegated
    fun toggleSearch() = searchDelegate.toggleSearch()
    fun updateSearchQuery(query: String) = searchDelegate.updateSearchQuery(query)
    // endregion

    // region Message Operations - delegated
    fun onMessageTextChanged(text: TextFieldValue) = messageDelegate.onMessageTextChanged(text)
    fun onMessageTextChanged(text: String) = messageDelegate.onMessageTextChanged(text)
    fun sendMessage(replyToId: Int? = null) = messageDelegate.sendMessage(replyToId)
    fun onUserSawNewMessages() = messageDelegate.onUserSawNewMessages()
    // endregion

    // region Editing - delegated
    fun startEditing(message: Message) = messageDelegate.startEditing(message)
    fun cancelEditing() = messageDelegate.cancelEditing()
    fun saveEditedMessage() = messageDelegate.saveEditedMessage()
    fun deleteMessage(messageId: String) = messageDelegate.deleteMessage(messageId)
    fun toggleReaction(messageId: Int, emoji: String) = messageDelegate.toggleReaction(messageId, emoji)
    // endregion

    // region Chat Operations - delegated
    fun clearChat() = membershipDelegate.clearChat()
    fun deleteChat() = membershipDelegate.deleteChat()
    // endregion

    // region File Operations - delegated
    fun sendFileMessage(context: Context, fileUri: Uri, fileName: String) =
        fileDelegate.sendFileMessage(context, fileUri, fileName)
    fun sendVoiceMessage(audioFile: java.io.File, durationMs: Long) =
        fileDelegate.sendVoiceMessage(audioFile, durationMs)
    fun downloadFile(context: Context, fileId: String, fileName: String) =
        fileDelegate.downloadFile(context, fileId, fileName)
    fun openFile(context: Context, fileName: String) = fileDelegate.openFile(context, fileName)
    fun saveFile(context: Context, fileName: String) = fileDelegate.saveFile(context, fileName)
    // endregion

    // region Call
    fun startCall() {
        callHandler.startCall(_uiState.value.participantIds, _uiState.value.currentUserId)?.let { error ->
            _uiState.value = _uiState.value.copy(error = error)
        }
    }
    // endregion

    // region Membership - delegated
    fun joinGroup() = membershipDelegate.joinGroup()
    fun muteChat(duration: String?, until: String?) = membershipDelegate.muteChat(duration, until)
    fun unmuteChat() = membershipDelegate.unmuteChat()
    suspend fun validateGroupInvite(code: String) = membershipDelegate.validateGroupInvite(code)
    suspend fun joinByInviteCode(code: String) = membershipDelegate.joinByInviteCode(code)
    fun loadInvitePreview(code: String) = membershipDelegate.loadInvitePreview(code)
    fun getInvitePreview(code: String) = membershipDelegate.getInvitePreview(code)
    fun isLoadingInvitePreview(code: String) = membershipDelegate.isLoadingInvitePreview(code)
    // endregion

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
