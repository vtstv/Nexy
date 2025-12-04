package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.models.Message
import com.nexy.client.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class SearchHandler @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend fun searchMessages(chatId: Int, query: String): Result<List<Message>> {
        return chatRepository.searchMessages(chatId, query)
    }
}
