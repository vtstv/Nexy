package com.nexy.client.ui.screens.chat.list.delegates

import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.ui.screens.chat.list.selection.MuteDuration
import com.nexy.client.utils.PinManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatListOperationsDelegate @Inject constructor(
    private val chatRepository: ChatRepository,
    private val pinManager: PinManager
) {
    private lateinit var scope: CoroutineScope
    private lateinit var refreshTrigger: MutableStateFlow<Long>
    private var onRefreshChats: (() -> Unit)? = null

    fun initialize(
        scope: CoroutineScope,
        refreshTrigger: MutableStateFlow<Long>,
        onRefreshChats: () -> Unit
    ) {
        this.scope = scope
        this.refreshTrigger = refreshTrigger
        this.onRefreshChats = onRefreshChats
    }

    fun deleteChat(chatId: Int) {
        scope.launch {
            chatRepository.hideChat(chatId)
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun muteChat(chatId: Int, duration: MuteDuration) {
        scope.launch {
            chatRepository.muteChat(chatId, duration.apiValue, null)
            onRefreshChats?.invoke()
        }
    }

    fun unmuteChat(chatId: Int) {
        scope.launch {
            chatRepository.unmuteChat(chatId)
            onRefreshChats?.invoke()
        }
    }

    fun pinChat(chatId: Int) {
        scope.launch {
            chatRepository.pinChat(chatId)
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun unpinChat(chatId: Int) {
        scope.launch {
            chatRepository.unpinChat(chatId)
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun lockApp() {
        pinManager.lockImmediately()
    }
}
