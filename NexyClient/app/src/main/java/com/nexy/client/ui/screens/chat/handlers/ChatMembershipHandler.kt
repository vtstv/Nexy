package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.models.InvitePreviewResponse
import com.nexy.client.data.models.JoinChatResponse
import com.nexy.client.data.repository.ChatRepository
import javax.inject.Inject

class ChatMembershipHandler @Inject constructor(
    private val chatRepository: ChatRepository,
    private val stateManager: ChatStateManager
) {
    suspend fun joinGroup(chatId: Int): Result<Unit> {
        return stateManager.joinGroup(chatId)
    }

    suspend fun validateGroupInvite(code: String): Result<InvitePreviewResponse> {
        return chatRepository.validateGroupInvite(code)
    }

    suspend fun joinByInviteCode(code: String): Result<JoinChatResponse> {
        return chatRepository.joinByInviteCode(code)
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
