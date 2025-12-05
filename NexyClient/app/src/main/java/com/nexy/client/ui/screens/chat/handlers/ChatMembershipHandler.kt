package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.repository.ChatRepository
import javax.inject.Inject

class ChatMembershipHandler @Inject constructor(
    private val chatRepository: ChatRepository,
    private val stateManager: ChatStateManager
) {
    suspend fun joinGroup(chatId: Int): Result<Unit> {
        return stateManager.joinGroup(chatId)
    }

    suspend fun muteChat(chatId: Int, duration: String?, until: String?): Result<Unit> {
        return chatRepository.muteChat(chatId, duration, until)
    }

    suspend fun unmuteChat(chatId: Int): Result<Unit> {
        return chatRepository.unmuteChat(chatId)
    }

    suspend fun clearChat(chatId: Int): Result<Unit> {
        return chatRepository.clearChatMessages(chatId)
    }

    suspend fun deleteChat(chatId: Int): Result<Unit> {
        return chatRepository.deleteChat(chatId)
    }
}
