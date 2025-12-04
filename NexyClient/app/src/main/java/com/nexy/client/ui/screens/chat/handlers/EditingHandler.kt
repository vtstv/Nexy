package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.models.Message
import com.nexy.client.data.repository.ChatRepository
import javax.inject.Inject

class EditingHandler @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend fun editMessage(messageId: String, content: String): Result<Unit> {
        return chatRepository.editMessage(messageId, content)
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return chatRepository.deleteMessage(messageId)
    }
}
