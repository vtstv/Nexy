package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.Message
import com.nexy.client.data.network.NetworkMonitor
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.websocket.ConnectionState
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MessageOperationsHandler @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkMonitor,
    private val webSocketClient: NexyWebSocketClient
) {
    fun observeMessages(chatId: Int): Flow<List<Message>> {
        return chatRepository.getMessagesByChatId(chatId)
    }

    suspend fun loadMessages(chatId: Int): Result<List<Message>> {
        return chatRepository.loadMessages(chatId)
    }

    suspend fun searchMessages(chatId: Int, query: String): Result<List<Message>> {
        return chatRepository.searchMessages(chatId, query)
    }

    suspend fun sendMessage(
        chatId: Int,
        userId: Int,
        text: String,
        chatType: ChatType,
        isSelfChat: Boolean,
        participantIds: List<Int>,
        replyToId: Int? = null
    ): Result<Message> {
        val recipientUserId = if (chatType == ChatType.PRIVATE && !isSelfChat) {
            participantIds.firstOrNull { it != userId }
        } else {
            null
        }
        
        return chatRepository.sendMessage(chatId, userId, text, recipientUserId = recipientUserId, replyToId = replyToId)
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return chatRepository.deleteMessage(messageId)
    }

    suspend fun editMessage(messageId: String, content: String): Result<Unit> {
        return chatRepository.editMessage(messageId, content)
    }

    suspend fun clearChat(chatId: Int): Result<Unit> {
        return chatRepository.clearChatMessages(chatId)
    }

    suspend fun deleteChat(chatId: Int): Result<Unit> {
        return chatRepository.deleteChat(chatId)
    }

    suspend fun markAsRead(chatId: Int) {
        chatRepository.markChatAsRead(chatId)
    }

    fun sendTyping(chatId: Int, isTyping: Boolean) {
        chatRepository.sendTyping(chatId, isTyping)
    }

    fun observeTypingEvents(): kotlinx.coroutines.flow.Flow<Triple<Int, Boolean, Int?>> = chatRepository.observeTypingEvents()
    
    fun observeConnectionStatus(): Flow<Boolean> {
        return combine(
            networkMonitor.isConnected,
            webSocketClient.connectionState
        ) { networkConnected, wsState ->
            networkConnected && wsState == ConnectionState.CONNECTED
        }
    }
    
    fun getPendingMessageCount(): Flow<Int> = chatRepository.getPendingMessageCount()
    
    suspend fun retryMessage(messageId: String): Result<Boolean> = chatRepository.retryMessage(messageId)
    
    suspend fun cancelMessage(messageId: String): Result<Boolean> = chatRepository.cancelMessage(messageId)
}
