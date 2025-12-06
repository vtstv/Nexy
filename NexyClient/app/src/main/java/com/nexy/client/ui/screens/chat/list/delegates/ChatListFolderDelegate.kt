package com.nexy.client.ui.screens.chat.list.delegates

import com.nexy.client.data.models.ChatFolder
import com.nexy.client.data.repository.FolderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatListFolderDelegate @Inject constructor(
    private val folderRepository: FolderRepository
) {
    private lateinit var scope: CoroutineScope

    val folders: StateFlow<List<ChatFolder>> = folderRepository.folders

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    fun loadFolders() {
        scope.launch {
            folderRepository.loadFolders()
        }
    }

    fun refreshFolders() {
        loadFolders()
    }

    fun addChatToFolder(chatId: Int, folderId: Int) {
        scope.launch {
            val folder = folderRepository.getFolder(folderId) ?: return@launch
            val currentChats = folder.includedChatIds?.toMutableList() ?: mutableListOf()
            if (!currentChats.contains(chatId)) {
                currentChats.add(chatId)
                folderRepository.updateFolderChats(folderId, currentChats)
            }
        }
    }

    fun removeChatFromFolder(chatId: Int, folderId: Int) {
        scope.launch {
            folderRepository.removeChatFromFolder(folderId, chatId)
        }
    }

    fun moveFolderLocally(fromIndex: Int, toIndex: Int) {
        folderRepository.moveFolderLocally(fromIndex, toIndex)
    }

    fun saveFolderOrder() {
        scope.launch {
            val folderIds = folderRepository.folders.value.map { it.id }
            folderRepository.reorderFolders(folderIds)
        }
    }
}
