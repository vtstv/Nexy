package com.nexy.client.data.repository

import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.FolderDao
import com.nexy.client.data.local.entity.ChatFolderEntity
import com.nexy.client.data.models.AddChatsToFolderRequest
import com.nexy.client.data.models.ChatFolder
import com.nexy.client.data.models.CreateFolderRequest
import com.nexy.client.data.models.ReorderFoldersRequest
import com.nexy.client.data.models.UpdateFolderRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val apiService: NexyApiService,
    private val folderDao: FolderDao,
    private val applicationScope: CoroutineScope
) {
    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        applicationScope.launch {
            folderDao.observeFolders().collectLatest { entities ->
                _folders.value = entities.map { it.toModel() }
            }
        }
    }
    
    suspend fun loadFolders() {
        _isLoading.value = true
        try {
            val response = apiService.getFolders()
            if (response.isSuccessful) {
                val remoteFolders = response.body() ?: emptyList()
                withContext(Dispatchers.IO) {
                    folderDao.replaceAll(remoteFolders.map { it.toEntity() })
                }
                _error.value = null
            } else {
                _error.value = "Failed to load folders"
            }
        } catch (e: Exception) {
            _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun createFolder(
        name: String, 
        icon: String, 
        color: String,
        includeContacts: Boolean = false,
        includeNonContacts: Boolean = false,
        includeGroups: Boolean = false
    ): Result<ChatFolder> {
        _isLoading.value = true
        return try {
            val request = CreateFolderRequest(
                name = name,
                icon = icon,
                color = color,
                includeContacts = includeContacts,
                includeNonContacts = includeNonContacts,
                includeGroups = includeGroups
            )
            val response = apiService.createFolder(request)
            if (response.isSuccessful) {
                val folder = response.body()
                folder?.let {
                    withContext(Dispatchers.IO) {
                        folderDao.upsertFolder(it.toEntity())
                    }
                }
                folder?.let { Result.success(it) } ?: Result.failure(Exception("Response body is null"))
            } else {
                Result.failure(Exception("Failed to create folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun updateFolder(
        folderId: Int, 
        name: String, 
        icon: String, 
        color: String,
        includeContacts: Boolean? = null,
        includeNonContacts: Boolean? = null,
        includeGroups: Boolean? = null
    ): Result<Unit> {
        _isLoading.value = true
        return try {
            val request = UpdateFolderRequest(
                name = name,
                icon = icon,
                color = color,
                includeContacts = includeContacts,
                includeNonContacts = includeNonContacts,
                includeGroups = includeGroups
            )
            val response = apiService.updateFolder(folderId, request)
            if (response.isSuccessful) {
                withContext(Dispatchers.IO) {
                    val existing = folderDao.getFolderById(folderId)
                    val updated = (existing ?: ChatFolderEntity(
                        id = folderId,
                        userId = 0,
                        name = name,
                        icon = icon,
                        color = color,
                        position = existing?.position ?: Int.MAX_VALUE,
                        includeContacts = includeContacts ?: existing?.includeContacts ?: false,
                        includeNonContacts = includeNonContacts ?: existing?.includeNonContacts ?: false,
                        includeGroups = includeGroups ?: existing?.includeGroups ?: false,
                        includeChannels = existing?.includeChannels ?: false,
                        includeBots = existing?.includeBots ?: false,
                        includedChatIds = existing?.includedChatIds,
                        excludedChatIds = existing?.excludedChatIds,
                        createdAt = existing?.createdAt,
                        updatedAt = existing?.updatedAt
                    )).copy(
                        name = name,
                        icon = icon,
                        color = color,
                        includeContacts = includeContacts ?: (existing?.includeContacts ?: false),
                        includeNonContacts = includeNonContacts ?: (existing?.includeNonContacts ?: false),
                        includeGroups = includeGroups ?: (existing?.includeGroups ?: false)
                    )
                    folderDao.upsertFolder(updated)
                }
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
                withContext(Dispatchers.IO) {
                    folderDao.deleteFolder(folderId)
                }
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
                withContext(Dispatchers.IO) {
                    val entity = folderDao.getFolderById(folderId)
                    if (entity != null) {
                        val currentChats = entity.includedChatIds?.toMutableList() ?: mutableListOf()
                        val newChats = chatIds.filter { !currentChats.contains(it) }
                        if (newChats.isNotEmpty()) {
                            currentChats.addAll(newChats)
                            folderDao.upsertFolder(entity.copy(includedChatIds = currentChats))
                        }
                    }
                }
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
                withContext(Dispatchers.IO) {
                    val entity = folderDao.getFolderById(folderId)
                    if (entity != null) {
                        val currentChats = entity.includedChatIds?.toMutableList() ?: mutableListOf()
                        if (currentChats.remove(chatId)) {
                            folderDao.upsertFolder(entity.copy(includedChatIds = currentChats))
                        }
                    }
                }
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

    suspend fun reorderFolders(folderIds: List<Int>): Result<Unit> {
        return try {
            android.util.Log.d("FolderRepository", "reorderFolders called with ids: $folderIds")
            
            if (folderIds.isEmpty()) {
                android.util.Log.w("FolderRepository", "reorderFolders: empty list, skipping")
                return Result.success(Unit)
            }
            
            // Create positions map: folderId -> new position
            val positions = folderIds.mapIndexed { index, folderId -> 
                folderId to index 
            }.toMap()
            
            android.util.Log.d("FolderRepository", "reorderFolders positions map: $positions")
            
            val request = ReorderFoldersRequest(positions = positions)
            val response = apiService.reorderFolders(request)
            
            android.util.Log.d("FolderRepository", "reorderFolders response: ${response.code()}")
            
            if (response.isSuccessful) {
                withContext(Dispatchers.IO) {
                    folderIds.forEachIndexed { index, folderId ->
                        folderDao.updateFolderPosition(folderId, index)
                    }
                }
                Result.success(Unit)
            } else {
                android.util.Log.e("FolderRepository", "reorderFolders failed: ${response.errorBody()?.string()}")
                Result.failure(Exception("Failed to reorder folders"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FolderRepository", "reorderFolders exception", e)
            Result.failure(e)
        }
    }

    // Move folder from one position to another (for drag-and-drop)
    fun moveFolderLocally(fromIndex: Int, toIndex: Int) {
        applicationScope.launch {
            val folders = folderDao.getFoldersOnce().toMutableList()
            if (fromIndex in folders.indices && toIndex in folders.indices) {
                val item = folders.removeAt(fromIndex)
                folders.add(toIndex, item)
                folders.forEachIndexed { index, entity ->
                    folderDao.updateFolderPosition(entity.id, index)
                }
            }
        }
    }

    private fun ChatFolderEntity.toModel() = ChatFolder(
        id = id,
        userId = userId,
        name = name,
        icon = icon,
        color = color,
        position = position,
        includeContacts = includeContacts,
        includeNonContacts = includeNonContacts,
        includeGroups = includeGroups,
        includeChannels = includeChannels,
        includeBots = includeBots,
        includedChatIds = includedChatIds,
        excludedChatIds = excludedChatIds,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ChatFolder.toEntity() = ChatFolderEntity(
        id = id,
        userId = userId,
        name = name,
        icon = icon,
        color = color,
        position = position,
        includeContacts = includeContacts,
        includeNonContacts = includeNonContacts,
        includeGroups = includeGroups,
        includeChannels = includeChannels,
        includeBots = includeBots,
        includedChatIds = includedChatIds,
        excludedChatIds = excludedChatIds,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
