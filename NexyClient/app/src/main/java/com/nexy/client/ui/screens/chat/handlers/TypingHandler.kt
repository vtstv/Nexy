package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

class TypingHandler @Inject constructor(
    private val chatRepository: ChatRepository
) {
    private var typingDebounceJob: Job? = null
    private val TYPING_DEBOUNCE_MS = 2000L

    fun observeTypingEvents(): Flow<Triple<Int, Boolean, Int?>> {
        return chatRepository.observeTypingEvents()
    }

    fun handleTextChanged(
        scope: CoroutineScope,
        chatId: Int,
        hasText: Boolean,
        onTypingChanged: () -> Unit = {}
    ) {
        if (hasText) {
            if (typingDebounceJob == null || !typingDebounceJob!!.isActive) {
                chatRepository.sendTyping(chatId, true)
            }
            
            typingDebounceJob?.cancel()
            typingDebounceJob = scope.launch {
                delay(TYPING_DEBOUNCE_MS)
                chatRepository.sendTyping(chatId, false)
                onTypingChanged()
            }
        } else {
            typingDebounceJob?.cancel()
            chatRepository.sendTyping(chatId, false)
        }
    }

    fun stopTyping(chatId: Int) {
        typingDebounceJob?.cancel()
        chatRepository.sendTyping(chatId, false)
    }
}
