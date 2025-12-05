package com.nexy.client.ui.screens.group

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.models.UpdateGroupRequest
import com.nexy.client.data.repository.file.FileOperations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EditGroupUiState {
    object Loading : EditGroupUiState()
    data class Success(val chat: Chat) : EditGroupUiState()
    data class Error(val message: String) : EditGroupUiState()
}

@HiltViewModel
class EditGroupViewModel @Inject constructor(
    private val apiService: NexyApiService,
    private val tokenManager: AuthTokenManager,
    private val fileOperations: FileOperations,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<EditGroupUiState>(EditGroupUiState.Loading)
    val uiState: StateFlow<EditGroupUiState> = _uiState.asStateFlow()
    
    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName.asStateFlow()
    
    private val _groupDescription = MutableStateFlow("")
    val groupDescription: StateFlow<String> = _groupDescription.asStateFlow()
    
    private val _groupUsername = MutableStateFlow("")
    val groupUsername: StateFlow<String> = _groupUsername.asStateFlow()
    
    private val _isPublic = MutableStateFlow(false)
    val isPublic: StateFlow<Boolean> = _isPublic.asStateFlow()
    
    private val _isOwner = MutableStateFlow(false)
    val isOwner: StateFlow<Boolean> = _isOwner.asStateFlow()
    
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    fun loadGroup(groupId: Int) {
        viewModelScope.launch {
            _uiState.value = EditGroupUiState.Loading
            try {
                val currentUserId = tokenManager.getUserId()
                val response = apiService.getGroup(groupId)
                if (response.isSuccessful) {
                    val chat = response.body()!!
                    _groupName.value = chat.name ?: ""
                    _groupDescription.value = chat.description ?: ""
                    _groupUsername.value = chat.username ?: ""
                    _isPublic.value = chat.groupType == GroupType.PUBLIC_GROUP
                    _isOwner.value = chat.createdBy == currentUserId
                    _avatarUrl.value = chat.avatarUrl
                    _uiState.value = EditGroupUiState.Success(chat)
                } else {
                    _uiState.value = EditGroupUiState.Error("Failed to load group")
                }
            } catch (e: Exception) {
                _uiState.value = EditGroupUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun setGroupName(name: String) {
        _groupName.value = name
    }
    
    fun setGroupDescription(description: String) {
        _groupDescription.value = description
    }
    
    fun setGroupUsername(username: String) {
        _groupUsername.value = username
    }
    
    fun setIsPublic(isPublic: Boolean) {
        _isPublic.value = isPublic
    }
    
    fun updateGroup(groupId: Int, selectedAvatarUri: Uri?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                var finalAvatarUrl = _avatarUrl.value
                
                // Upload new avatar if selected
                if (selectedAvatarUri != null) {
                    fileOperations.uploadFile(context, selectedAvatarUri, "avatar").fold(
                        onSuccess = { url ->
                            finalAvatarUrl = url
                        },
                        onFailure = { error ->
                            _isSaving.value = false
                            _uiState.value = EditGroupUiState.Error("Failed to upload avatar: ${error.message}")
                            return@launch
                        }
                    )
                }
                
                val response = apiService.updateGroup(
                    groupId,
                    UpdateGroupRequest(
                        name = _groupName.value,
                        description = _groupDescription.value.ifBlank { null },
                        username = if (_isPublic.value) _groupUsername.value else null,
                        avatarUrl = finalAvatarUrl
                    )
                )
                if (response.isSuccessful) {
                    _isSaving.value = false
                    onSuccess()
                } else {
                    _isSaving.value = false
                    _uiState.value = EditGroupUiState.Error("Failed to update group")
                }
            } catch (e: Exception) {
                _isSaving.value = false
                _uiState.value = EditGroupUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun deleteGroup(groupId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteChat(groupId)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    _uiState.value = EditGroupUiState.Error("Failed to delete group")
                }
            } catch (e: Exception) {
                _uiState.value = EditGroupUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
