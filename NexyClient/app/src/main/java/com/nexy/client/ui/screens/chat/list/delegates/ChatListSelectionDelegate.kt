package com.nexy.client.ui.screens.chat.list.delegates

import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.FolderRepository
import com.nexy.client.ui.screens.chat.list.selection.ChatSelectionState
import com.nexy.client.ui.screens.chat.list.selection.MuteDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatListSelectionDelegate @Inject constructor(
    private val chatRepository: ChatRepository,
    private val folderRepository: FolderRepository
) {
    private lateinit var scope: CoroutineScope
    private lateinit var selectionState: MutableStateFlow<ChatSelectionState>
    private lateinit var refreshTrigger: MutableStateFlow<Long>
    private var onRefreshChats: (() -> Unit)? = null

    fun initialize(
        scope: CoroutineScope,
        selectionState: MutableStateFlow<ChatSelectionState>,
        refreshTrigger: MutableStateFlow<Long>,
        onRefreshChats: () -> Unit
    ) {
        this.scope = scope
        this.selectionState = selectionState
        this.refreshTrigger = refreshTrigger
        this.onRefreshChats = onRefreshChats
    }

    fun enterSelectionMode(chatId: Int) {
        selectionState.value = selectionState.value.enterSelectionMode(chatId)
    }

    fun toggleChatSelection(chatId: Int) {
        selectionState.value = selectionState.value.toggleSelection(chatId)
    }

    fun clearSelection() {
        selectionState.value = selectionState.value.clearSelection()
    }

    fun muteSelectedChats(duration: MuteDuration) {
        scope.launch {
            val selectedIds = selectionState.value.selectedChatIds.toList()
            selectedIds.forEach { chatId ->
                chatRepository.muteChat(chatId, duration.apiValue, null)
            }
            clearSelection()
            onRefreshChats?.invoke()
        }
    }

    fun unmuteSelectedChats() {
        scope.launch {
            val selectedIds = selectionState.value.selectedChatIds.toList()
            selectedIds.forEach { chatId ->
                chatRepository.unmuteChat(chatId)
            }
            clearSelection()
            onRefreshChats?.invoke()
        }
    }

    fun pinSelectedChats() {
        scope.launch {
            val selectedIds = selectionState.value.selectedChatIds.toList()
            selectedIds.forEach { chatId ->
                chatRepository.pinChat(chatId)
            }
            clearSelection()
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun unpinSelectedChats() {
        scope.launch {
            val selectedIds = selectionState.value.selectedChatIds.toList()
            selectedIds.forEach { chatId ->
                chatRepository.unpinChat(chatId)
            }
            clearSelection()
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun hideSelectedChats() {
        scope.launch {
            val selectedIds = selectionState.value.selectedChatIds.toList()
            selectedIds.forEach { chatId ->
                chatRepository.hideChat(chatId)
            }
            clearSelection()
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun addSelectedChatsToFolder(folderId: Int) {
        scope.launch {
            val selectedIds = selectionState.value.selectedChatIds.toList()
            val folder = folderRepository.getFolder(folderId) ?: return@launch
            val currentChats = folder.includedChatIds?.toMutableList() ?: mutableListOf()

            selectedIds.forEach { chatId ->
                if (!currentChats.contains(chatId)) {
                    currentChats.add(chatId)
                }
            }

            folderRepository.updateFolderChats(folderId, currentChats)
            clearSelection()
        }
    }
}
