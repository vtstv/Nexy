/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatFolder
import com.nexy.client.data.models.CreateFolderRequest
import com.nexy.client.data.models.UpdateFolderRequest
import com.nexy.client.data.models.AddChatsToFolderRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val apiService: NexyApiService
) : ViewModel() {

    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _allChats = MutableStateFlow<List<Chat>>(emptyList())
    val allChats: StateFlow<List<Chat>> = _allChats.asStateFlow()

    private val _currentFolder = MutableStateFlow<ChatFolder?>(null)
    val currentFolder: StateFlow<ChatFolder?> = _currentFolder.asStateFlow()

    private val _savedFolderId = MutableStateFlow<Int?>(null)
    val savedFolderId: StateFlow<Int?> = _savedFolderId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadFolders()
    }

    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getFolders()
                if (response.isSuccessful) {
                    _folders.value = response.body() ?: emptyList()
                } else {
                    _error.value = "Failed to load folders"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAllChats() {
        viewModelScope.launch {
            try {
                val response = apiService.getChats()
                if (response.isSuccessful) {
                    _allChats.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun loadFolderForEdit(folderId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // First try to get from cached folders
                val cached = _folders.value.find { it.id == folderId }
                if (cached != null) {
                    _currentFolder.value = cached
                } else {
                    // Reload folders and find
                    val response = apiService.getFolders()
                    if (response.isSuccessful) {
                        _folders.value = response.body() ?: emptyList()
                        _currentFolder.value = _folders.value.find { it.id == folderId }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearCurrentFolder() {
        _currentFolder.value = null
        _savedFolderId.value = null
    }

    fun createFolder(name: String, icon: String, color: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = CreateFolderRequest(
                    name = name,
                    icon = icon,
                    color = color
                )
                val response = apiService.createFolder(request)
                if (response.isSuccessful) {
                    val folder = response.body()
                    loadFolders()
                    _savedFolderId.value = folder?.id
                } else {
                    _error.value = "Failed to create folder"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateFolder(folderId: Int, name: String, icon: String, color: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = UpdateFolderRequest(
                    name = name,
                    icon = icon,
                    color = color
                )
                val response = apiService.updateFolder(folderId, request)
                if (response.isSuccessful) {
                    loadFolders()
                    _savedFolderId.value = folderId
                } else {
                    _error.value = "Failed to update folder"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFolder(folderId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteFolder(folderId)
                if (response.isSuccessful) {
                    loadFolders()
                } else {
                    _error.value = "Failed to delete folder"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateFolderChats(folderId: Int, chatIds: List<Int>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = AddChatsToFolderRequest(chatIds = chatIds)
                val response = apiService.addChatsToFolder(folderId, request)
                if (response.isSuccessful) {
                    loadFolders()
                    loadFolderForEdit(folderId)
                } else {
                    _error.value = "Failed to update folder chats"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeChatFromFolder(folderId: Int, chatId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.removeChatFromFolder(folderId, chatId)
                if (response.isSuccessful) {
                    loadFolders()
                    loadFolderForEdit(folderId)
                } else {
                    _error.value = "Failed to remove chat from folder"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
