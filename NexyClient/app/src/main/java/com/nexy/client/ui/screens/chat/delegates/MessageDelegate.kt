package com.nexy.client.ui.screens.chat.delegates

import androidx.compose.ui.text.input.TextFieldValue
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.handlers.EditingHandler
import com.nexy.client.ui.screens.chat.handlers.MessageOperationsHandler
import com.nexy.client.ui.screens.chat.handlers.ReadReceiptHandler
import com.nexy.client.ui.screens.chat.handlers.TypingHandler
import com.nexy.client.ui.screens.chat.state.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class MessageDelegate @Inject constructor(
    private val messageOps: MessageOperationsHandler,
    private val typingHandler: TypingHandler,
    private val editingHandler: EditingHandler,
    private val readReceiptHandler: ReadReceiptHandler
) : ChatViewModelDelegate {

    private lateinit var scope: CoroutineScope
    private lateinit var uiState: MutableStateFlow<ChatUiState>
    private lateinit var getChatId: () -> Int

    override fun initialize(
        scope: CoroutineScope,
        uiState: MutableStateFlow<ChatUiState>,
        getChatId: () -> Int
    ) {
        this.scope = scope
        this.uiState = uiState
        this.getChatId = getChatId
    }

    fun onMessageTextChanged(text: TextFieldValue) {
        uiState.value = uiState.value.copy(messageText = text)
        typingHandler.handleTextChanged(scope, getChatId(), text.text.isNotEmpty())
    }

    fun onMessageTextChanged(text: String) {
        uiState.value = uiState.value.copy(messageText = TextFieldValue(text))
        typingHandler.handleTextChanged(scope, getChatId(), text.isNotEmpty())
    }

    fun sendMessage(replyToId: Int? = null) {
        if (uiState.value.editingMessage != null) {
            saveEditedMessage()
            return
        }

        val text = uiState.value.messageText.text.trim()
        if (text.isEmpty()) return

        scope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            uiState.value = uiState.value.copy(messageText = TextFieldValue(""))

            messageOps.sendMessage(
                chatId = getChatId(),
                userId = userId,
                text = text,
                chatType = uiState.value.chatType,
                isSelfChat = uiState.value.isSelfChat,
                participantIds = uiState.value.participantIds,
                replyToId = replyToId
            ).onFailure { error ->
                uiState.value = uiState.value.copy(
                    error = error.message ?: "Failed to send message"
                )
            }
        }
    }

    fun onUserSawNewMessages() {
        if (!readReceiptHandler.isChatActive()) return

        val job = scope.launch {
            delay(readReceiptHandler.getDebounceMs())
            android.util.Log.d("MessageDelegate", "User saw new messages (debounced), marking as read")
            readReceiptHandler.markAsRead(getChatId())
        }
        readReceiptHandler.setDebounceJob(job)
    }

    fun startEditing(message: Message) {
        uiState.value = uiState.value.copy(
            editingMessage = message,
            messageText = TextFieldValue(message.content)
        )
    }

    fun cancelEditing() {
        uiState.value = uiState.value.copy(
            editingMessage = null,
            messageText = TextFieldValue("")
        )
    }

    fun saveEditedMessage() {
        val message = uiState.value.editingMessage ?: return
        val newContent = uiState.value.messageText.text.trim()

        if (newContent.isEmpty() || newContent == message.content) {
            cancelEditing()
            return
        }

        scope.launch {
            editingHandler.editMessage(message.id, newContent)
                .onSuccess { cancelEditing() }
                .onFailure { e -> uiState.value = uiState.value.copy(error = e.message) }
        }
    }

    fun deleteMessage(messageId: String) {
        scope.launch {
            editingHandler.deleteMessage(messageId)
                .onFailure { e -> uiState.value = uiState.value.copy(error = e.message) }
        }
    }

    fun loadMessages() {
        scope.launch {
            uiState.value = uiState.value.copy(isLoading = true)
            messageOps.observeMessages(getChatId()).collect { messages ->
                val newestMessage = messages.filter { it.timestamp != null }
                    .maxByOrNull { it.timestamp!! }
                readReceiptHandler.updateLastKnownMessageId(newestMessage?.id)

                uiState.value = uiState.value.copy(
                    messages = messages,
                    isLoading = false
                )

                if (!readReceiptHandler.isChatActive()) {
                    android.util.Log.d("MessageDelegate", "Chat not active, skipping markAsRead")
                } else if (readReceiptHandler.isFirstLoading() && messages.isNotEmpty()) {
                    readReceiptHandler.setFirstLoadingComplete()
                    android.util.Log.d("MessageDelegate", "Messages loaded, marking as read now")
                    readReceiptHandler.markAsRead(getChatId())
                }
            }
        }

        scope.launch {
            try {
                messageOps.loadMessages(getChatId())
            } catch (_: Exception) {
                // Error handled in repository
            }
        }
    }

    fun observeConnectionStatus() {
        scope.launch {
            messageOps.observeConnectionStatus().collect { isConnected ->
                uiState.value = uiState.value.copy(isConnected = isConnected)
            }
        }
        scope.launch {
            messageOps.getPendingMessageCount().collect { count ->
                uiState.value = uiState.value.copy(pendingMessageCount = count)
            }
        }
    }

    fun observeTypingEvents() {
        scope.launch {
            typingHandler.observeTypingEvents().collect { (eventChatId, isTyping, senderId) ->
                if (eventChatId == getChatId()) {
                    val typingUserName = if (isTyping && senderId != null &&
                        uiState.value.chatType == com.nexy.client.data.models.ChatType.GROUP) {
                        null // Would need stateManager to get user name
                    } else null

                    uiState.value = uiState.value.copy(
                        isTyping = isTyping,
                        typingUser = typingUserName
                    )
                }
            }
        }
    }
}
