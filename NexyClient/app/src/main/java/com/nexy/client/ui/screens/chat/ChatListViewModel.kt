package com.nexy.client.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.ChatFolder
import com.nexy.client.ui.screens.chat.list.delegates.ChatListConnectionDelegate
import com.nexy.client.ui.screens.chat.list.delegates.ChatListFolderDelegate
import com.nexy.client.ui.screens.chat.list.delegates.ChatListLoadingDelegate
import com.nexy.client.ui.screens.chat.list.delegates.ChatListOperationsDelegate
import com.nexy.client.ui.screens.chat.list.delegates.ChatListSelectionDelegate
import com.nexy.client.ui.screens.chat.list.selection.ChatSelectionState
import com.nexy.client.ui.screens.chat.list.selection.MuteDuration
import com.nexy.client.ui.screens.chat.list.state.ChatListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val connectionDelegate: ChatListConnectionDelegate,
    private val loadingDelegate: ChatListLoadingDelegate,
    private val selectionDelegate: ChatListSelectionDelegate,
    private val folderDelegate: ChatListFolderDelegate,
    private val operationsDelegate: ChatListOperationsDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private val _selectionState = MutableStateFlow(ChatSelectionState())
    val selectionState: StateFlow<ChatSelectionState> = _selectionState.asStateFlow()

    val folders: StateFlow<List<ChatFolder>> = folderDelegate.folders

    private val _refreshTrigger = MutableStateFlow(0L)

    init {
        initializeDelegates()
        connectionDelegate.setupWebSocket()
        loadingDelegate.loadChats()
        loadingDelegate.loadCurrentUser()
        folderDelegate.loadFolders()
    }

    private fun initializeDelegates() {
        connectionDelegate.initialize(
            scope = viewModelScope,
            refreshTrigger = _refreshTrigger,
            onRefreshChats = { refreshChats() }
        )

        loadingDelegate.initialize(
            scope = viewModelScope,
            uiState = _uiState,
            refreshTrigger = _refreshTrigger
        )

        selectionDelegate.initialize(
            scope = viewModelScope,
            selectionState = _selectionState,
            refreshTrigger = _refreshTrigger,
            onRefreshChats = { refreshChats() }
        )

        folderDelegate.initialize(scope = viewModelScope)

        operationsDelegate.initialize(
            scope = viewModelScope,
            refreshTrigger = _refreshTrigger,
            onRefreshChats = { refreshChats() }
        )
    }

    // region Loading - delegated
    fun refreshChats() = loadingDelegate.refreshChats()
    fun openSavedMessages(onChatClick: (Int) -> Unit) = loadingDelegate.openSavedMessages(onChatClick)
    // endregion

    // region Selection - delegated
    fun enterSelectionMode(chatId: Int) = selectionDelegate.enterSelectionMode(chatId)
    fun toggleChatSelection(chatId: Int) = selectionDelegate.toggleChatSelection(chatId)
    fun clearSelection() = selectionDelegate.clearSelection()
    fun muteSelectedChats(duration: MuteDuration) = selectionDelegate.muteSelectedChats(duration)
    fun unmuteSelectedChats() = selectionDelegate.unmuteSelectedChats()
    fun pinSelectedChats() = selectionDelegate.pinSelectedChats()
    fun unpinSelectedChats() = selectionDelegate.unpinSelectedChats()
    fun hideSelectedChats() = selectionDelegate.hideSelectedChats()
    fun addSelectedChatsToFolder(folderId: Int) = selectionDelegate.addSelectedChatsToFolder(folderId)
    // endregion

    // region Folders - delegated
    fun refreshFolders() = folderDelegate.refreshFolders()
    fun addChatToFolder(chatId: Int, folderId: Int) = folderDelegate.addChatToFolder(chatId, folderId)
    fun removeChatFromFolder(chatId: Int, folderId: Int) = folderDelegate.removeChatFromFolder(chatId, folderId)
    fun moveFolderLocally(fromIndex: Int, toIndex: Int) = folderDelegate.moveFolderLocally(fromIndex, toIndex)
    fun saveFolderOrder() = folderDelegate.saveFolderOrder()
    // endregion

    // region Operations - delegated
    fun deleteChat(chatId: Int) = operationsDelegate.deleteChat(chatId)
    fun muteChat(chatId: Int, duration: MuteDuration) = operationsDelegate.muteChat(chatId, duration)
    fun unmuteChat(chatId: Int) = operationsDelegate.unmuteChat(chatId)
    fun pinChat(chatId: Int) = operationsDelegate.pinChat(chatId)
    fun unpinChat(chatId: Int) = operationsDelegate.unpinChat(chatId)
    fun lockApp() = operationsDelegate.lockApp()
    // endregion
}
