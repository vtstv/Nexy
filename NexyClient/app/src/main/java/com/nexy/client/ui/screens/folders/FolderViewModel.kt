/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatFolder
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.FolderRepository
import com.nexy.client.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectableChat(
    val chat: Chat,
    val displayName: String,
    val avatarUrl: String? = null
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val tokenManager: AuthTokenManager
) : ViewModel() {

    val folders: StateFlow<List<ChatFolder>> = folderRepository.folders
    val isLoading: StateFlow<Boolean> = folderRepository.isLoading
    val error: StateFlow<String?> = folderRepository.error

    private val _allChats = MutableStateFlow<List<Chat>>(emptyList())
    val allChats: StateFlow<List<Chat>> = _allChats.asStateFlow()

    private val _selectableChats = MutableStateFlow<List<SelectableChat>>(emptyList())
    val selectableChats: StateFlow<List<SelectableChat>> = _selectableChats.asStateFlow()

    private val _currentFolderId = MutableStateFlow<Int?>(null)
    
    val currentFolder: StateFlow<ChatFolder?> = combine(folderRepository.folders, _currentFolderId) { folders, id ->
        folders.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _savedFolderId = MutableStateFlow<Int?>(null)
    val savedFolderId: StateFlow<Int?> = _savedFolderId.asStateFlow()

    init {
        loadFolders()
    }

    fun loadFolders() {
        viewModelScope.launch {
            folderRepository.loadFolders()
        }
    }

    fun loadAllChats() {
        viewModelScope.launch {
            val currentUserId = tokenManager.getUserId()
            chatRepository.getAllChats().collect { chats ->
                _allChats.value = chats
                
                val selectable = ArrayList<SelectableChat>()
                for (chat in chats) {
                    val (name, avatar) = if (chat.type == ChatType.PRIVATE) {
                        val otherId = chat.participantIds?.firstOrNull { it != currentUserId }
                        if (otherId != null) {
                            val userResult = userRepository.getUserById(otherId)
                            val user = userResult.getOrNull()
                            Pair(user?.displayName?.takeIf { it.isNotBlank() } ?: user?.username ?: "User $otherId", user?.avatarUrl)
                        } else {
                            Pair("Notepad", null)
                        }
                    } else {
                        Pair(chat.name ?: "Unknown", chat.avatarUrl)
                    }
                    selectable.add(SelectableChat(chat, name, avatar))
                }
                _selectableChats.value = selectable
            }
        }
    }

    fun loadFolderForEdit(folderId: Int) {
        _currentFolderId.value = folderId
        if (folderRepository.folders.value.isEmpty()) {
            loadFolders()
        }
    }

    fun clearCurrentFolder() {
        _currentFolderId.value = null
        _savedFolderId.value = null
    }

    fun createFolder(
        name: String, 
        icon: String, 
        color: String,
        includeContacts: Boolean = false,
        includeNonContacts: Boolean = false,
        includeGroups: Boolean = false
    ) {
        viewModelScope.launch {
            val result = folderRepository.createFolder(
                name, icon, color,
                includeContacts, includeNonContacts, includeGroups
            )
            result.onSuccess { folder ->
                _savedFolderId.value = folder.id
            }
        }
    }

    fun updateFolder(
        folderId: Int, 
        name: String, 
        icon: String, 
        color: String,
        includeContacts: Boolean? = null,
        includeNonContacts: Boolean? = null,
        includeGroups: Boolean? = null
    ) {
        viewModelScope.launch {
            val result = folderRepository.updateFolder(
                folderId, name, icon, color,
                includeContacts, includeNonContacts, includeGroups
            )
            result.onSuccess {
                _savedFolderId.value = folderId
            }
        }
    }

    fun deleteFolder(folderId: Int) {
        viewModelScope.launch {
            folderRepository.deleteFolder(folderId)
        }
    }

    fun updateFolderChats(folderId: Int, chatIds: List<Int>) {
        viewModelScope.launch {
            val result = folderRepository.updateFolderChats(folderId, chatIds)
            result.onSuccess {
                // No need to reload manually, repository updates state
            }
        }
    }

    fun removeChatFromFolder(folderId: Int, chatId: Int) {
        viewModelScope.launch {
            val result = folderRepository.removeChatFromFolder(folderId, chatId)
            result.onSuccess {
                // No need to reload manually, repository updates state
            }
        }
    }

    fun moveFolderLocally(fromIndex: Int, toIndex: Int) {
        folderRepository.moveFolderLocally(fromIndex, toIndex)
    }

    fun saveFolderOrder() {
        viewModelScope.launch {
            val folderIds = folderRepository.folders.value.map { it.id }
            android.util.Log.d("FolderViewModel", "saveFolderOrder called, folderIds: $folderIds")
            folderRepository.reorderFolders(folderIds)
        }
    }

    fun clearError() {
        // Error is now managed by repository
    }
}
