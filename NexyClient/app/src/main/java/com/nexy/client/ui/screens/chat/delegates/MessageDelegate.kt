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
    private val readReceiptHandler: ReadReceiptHandler,
    private val chatRepository: com.nexy.client.data.repository.ChatRepository,
    private val webSocketMessageHandler: com.nexy.client.data.websocket.WebSocketMessageHandler
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
        observeReactionEvents()
    }

    private suspend fun refreshReactions(messages: List<Message>) {
        val ids = messages.mapNotNull { it.serverId }
        if (ids.isEmpty()) return

        val reactionsMap = mutableMapOf<Int, List<com.nexy.client.data.models.ReactionCount>>()
        for (id in ids) {
            chatRepository.getReactions(id).onSuccess { reactions ->
                reactionsMap[id] = reactions
            }
        }

        uiState.value = uiState.value.copy(
            messages = messages.map { msg ->
                val sid = msg.serverId
                if (sid != null && reactionsMap.containsKey(sid)) {
                    msg.copy(reactions = reactionsMap[sid])
                } else msg
            }
        )
    }
    
    private fun observeReactionEvents() {
        scope.launch {
            webSocketMessageHandler.reactionEvents.collect { event ->
                android.util.Log.d("MessageDelegate", "Reaction event: messageId=${event.messageId}, emoji=${event.emoji}, isAdd=${event.isAdd}")
                
                // Reload messages to get updated reactions
                val currentUserId = uiState.value.currentUserId ?: return@collect
                val result = chatRepository.getReactions(event.messageId)
                
                result.onSuccess { reactions ->
                    // Update the message in the current list only if reactions changed to prevent flicker
                    val updatedMessages = uiState.value.messages.map { message ->
                        if (message.serverId == event.messageId) {
                            if (message.reactions == reactions) message else message.copy(reactions = reactions)
                        } else {
                            message
                        }
                    }
                    if (updatedMessages !== uiState.value.messages) {
                        uiState.value = uiState.value.copy(messages = updatedMessages)
                    }
                }
            }
        }
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
            messageText = TextFieldValue(message.content ?: "")
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

                val previousMessages = uiState.value.messages
                val mergedMessages = messages.map { msg ->
                    val previous = previousMessages.find {
                        (it.serverId != null && it.serverId == msg.serverId) || it.id == msg.id
                    }
                    when {
                        msg.reactions != null -> msg // server provided reactions
                        previous?.reactions != null -> msg.copy(reactions = previous.reactions) // preserve existing reactions when absent in new payload
                        else -> msg
                    }
                }

                uiState.value = uiState.value.copy(
                    messages = mergedMessages,
                    isLoading = false
                )

                // Refresh reactions only if some merged messages are missing them to avoid unnecessary recomposition flicker
                val needsRefresh = mergedMessages.any { it.serverId != null && it.reactions == null }
                if (needsRefresh) {
                    scope.launch {
                        refreshReactions(mergedMessages)
                    }
                }

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
    
    fun toggleReaction(messageId: Int, emoji: String) {
        scope.launch {
            // Find if current user already reacted with this emoji
            val currentMessages = uiState.value.messages
            val message = currentMessages.find { it.serverId == messageId }
            val currentUserReacted = message?.reactions?.find { it.emoji == emoji }?.reactedBy == true
            
            val result = if (currentUserReacted) {
                chatRepository.removeReaction(messageId, emoji)
            } else {
                chatRepository.addReaction(messageId, emoji)
            }
            
            result.onFailure { error ->
                uiState.value = uiState.value.copy(
                    error = error.message ?: "Failed to update reaction"
                )
            }.onSuccess {
                // Pull fresh reactions for this message to keep UI in sync
                chatRepository.getReactions(messageId).onSuccess { reactions ->
                    val updated = uiState.value.messages.map { msg ->
                        if (msg.serverId == messageId) msg.copy(reactions = reactions) else msg
                    }
                    uiState.value = uiState.value.copy(messages = updated)
                }
            }
        }
    }
}
