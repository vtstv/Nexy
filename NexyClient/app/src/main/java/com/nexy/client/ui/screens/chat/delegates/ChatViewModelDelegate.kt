package com.nexy.client.ui.screens.chat.delegates

import com.nexy.client.ui.screens.chat.state.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

interface ChatViewModelDelegate {
    fun initialize(
        scope: CoroutineScope,
        uiState: MutableStateFlow<ChatUiState>,
        getChatId: () -> Int
    )
}
