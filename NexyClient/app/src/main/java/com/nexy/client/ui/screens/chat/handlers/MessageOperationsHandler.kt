package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.Message
import com.nexy.client.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MessageOperationsHandler @Inject constructor(
    private val chatRepository: ChatRepository
) {
    fun observeMessages(chatId: Int): Flow<List<Message>> {
        return chatRepository.getMessagesByChatId(chatId)
    }

    suspend fun loadMessages(chatId: Int): Result<List<Message>> {
        return chatRepository.loadMessages(chatId)
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

    suspend fun clearChat(chatId: Int): Result<Unit> {
        return chatRepository.clearChatMessages(chatId)
    }

    suspend fun deleteChat(chatId: Int): Result<Unit> {
        return chatRepository.deleteChat(chatId)
    }

    suspend fun markAsRead(chatId: Int) {
        chatRepository.markChatAsRead(chatId)
    }
}
