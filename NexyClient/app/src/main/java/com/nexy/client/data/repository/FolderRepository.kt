package com.nexy.client.data.repository

import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.models.AddChatsToFolderRequest
import com.nexy.client.data.models.ChatFolder
import com.nexy.client.data.models.CreateFolderRequest
import com.nexy.client.data.models.UpdateFolderRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val apiService: NexyApiService
) {
    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Scope for background operations if needed, though usually we suspend
    // But here we want to keep the state updated
    
    suspend fun loadFolders() {
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

    suspend fun createFolder(name: String, icon: String, color: String): Result<ChatFolder> {
        _isLoading.value = true
        return try {
            val request = CreateFolderRequest(
                name = name,
                icon = icon,
                color = color
            )
            val response = apiService.createFolder(request)
            if (response.isSuccessful) {
                val folder = response.body()
                if (folder != null) {
                    // Update local state immediately
                    val currentList = _folders.value.toMutableList()
                    currentList.add(folder)
                    _folders.value = currentList
                    Result.success(folder)
                } else {
                    Result.failure(Exception("Response body is null"))
                }
            } else {
                Result.failure(Exception("Failed to create folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun updateFolder(folderId: Int, name: String, icon: String, color: String): Result<Unit> {
        _isLoading.value = true
        return try {
            val request = UpdateFolderRequest(
                name = name,
                icon = icon,
                color = color
            )
            val response = apiService.updateFolder(folderId, request)
            if (response.isSuccessful) {
                // Update local state immediately
                val currentList = _folders.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == folderId }
                if (index != -1) {
                    // We don't get the full updated object back usually, or maybe we do?
                    // Assuming we need to reload or patch locally.
                    // Ideally the server returns the updated object.
                    // If not, we might need to reload.
                    // Let's reload to be safe for now, but we can also optimistically update if we knew the structure.
                    // But wait, the user wants "immediate" update.
                    // If I reload, it takes a round trip.
                    // Let's try to update locally with the data we sent, preserving other fields if possible.
                    val oldFolder = currentList[index]
                    val newFolder = oldFolder.copy(name = name, icon = icon, color = color)
                    currentList[index] = newFolder
                    _folders.value = currentList
                }
                // Also reload to ensure consistency
                loadFolders() 
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun deleteFolder(folderId: Int): Result<Unit> {
        return try {
            val response = apiService.deleteFolder(folderId)
            if (response.isSuccessful) {
                val currentList = _folders.value.toMutableList()
                currentList.removeAll { it.id == folderId }
                _folders.value = currentList
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFolderChats(folderId: Int, chatIds: List<Int>): Result<Unit> {
        _isLoading.value = true
        return try {
            val request = AddChatsToFolderRequest(chatIds = chatIds)
            val response = apiService.addChatsToFolder(folderId, request)
            if (response.isSuccessful) {
                // Update local state immediately
                val currentList = _folders.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == folderId }
                if (index != -1) {
                    val oldFolder = currentList[index]
                    val currentChats = oldFolder.includedChatIds?.toMutableList() ?: mutableListOf()
                    // Add new chats that are not already there
                    val newChats = chatIds.filter { !currentChats.contains(it) }
                    currentChats.addAll(newChats)
                    
                    val newFolder = oldFolder.copy(includedChatIds = currentChats)
                    currentList[index] = newFolder
                    _folders.value = currentList
                }

                // Reload to get updated chats list in folder
                loadFolders()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update folder chats"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun removeChatFromFolder(folderId: Int, chatId: Int): Result<Unit> {
        return try {
            val response = apiService.removeChatFromFolder(folderId, chatId)
            if (response.isSuccessful) {
                // Update local state immediately
                val currentList = _folders.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == folderId }
                if (index != -1) {
                    val oldFolder = currentList[index]
                    val currentChats = oldFolder.includedChatIds?.toMutableList() ?: mutableListOf()
                    currentChats.remove(chatId)
                    
                    val newFolder = oldFolder.copy(includedChatIds = currentChats)
                    currentList[index] = newFolder
                    _folders.value = currentList
                }

                // Reload to get updated chats list in folder
                loadFolders()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove chat from folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getFolder(folderId: Int): ChatFolder? {
        return _folders.value.find { it.id == folderId }
    }
}
