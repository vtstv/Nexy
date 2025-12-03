package com.nexy.client.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.models.ContactWithUser
import com.nexy.client.data.models.CreateGroupRequest
import com.nexy.client.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val apiService: NexyApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateGroupUiState>(CreateGroupUiState.Loading)
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName.asStateFlow()
    
    private val _groupDescription = MutableStateFlow("")
    val groupDescription: StateFlow<String> = _groupDescription.asStateFlow()
    
    private val _groupUsername = MutableStateFlow("")
    val groupUsername: StateFlow<String> = _groupUsername.asStateFlow()
    
    private val _isPublic = MutableStateFlow(false)
    val isPublic: StateFlow<Boolean> = _isPublic.asStateFlow()

    private val _selectedMembers = MutableStateFlow<Set<Int>>(emptySet())
    val selectedMembers: StateFlow<Set<Int>> = _selectedMembers.asStateFlow()

    init {
        loadContacts()
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

    fun toggleMemberSelection(userId: Int) {
        val current = _selectedMembers.value.toMutableSet()
        if (current.contains(userId)) {
            current.remove(userId)
        } else {
            current.add(userId)
        }
        _selectedMembers.value = current
    }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = CreateGroupUiState.Loading
            contactRepository.getContacts().fold(
                onSuccess = { contacts ->
                    _uiState.value = if (contacts.isEmpty()) {
                        CreateGroupUiState.Empty
                    } else {
                        CreateGroupUiState.Success(contacts)
                    }
                },
                onFailure = { error ->
                    _uiState.value = CreateGroupUiState.Error(
                        error.message ?: "Failed to load contacts"
                    )
                }
            )
        }
    }

    fun createGroup(onSuccess: (Int) -> Unit) {
        val name = _groupName.value.trim()
        val memberIds = _selectedMembers.value.toList()

        if (name.isEmpty()) {
            _uiState.value = CreateGroupUiState.Error("Group name is required")
            return
        }
        
        if (_isPublic.value && _groupUsername.value.isBlank()) {
            _uiState.value = CreateGroupUiState.Error("Username is required for public groups")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateGroupUiState.Creating
            try {
                val response = apiService.createGroup(
                    CreateGroupRequest(
                        name = name,
                        description = _groupDescription.value.ifBlank { null },
                        type = if (_isPublic.value) "public_group" else "private_group",
                        username = if (_isPublic.value) _groupUsername.value else null,
                        members = memberIds.ifEmpty { null }
                    )
                )
                
                if (response.isSuccessful) {
                    response.body()?.let { chat ->
                        onSuccess(chat.id)
                    }
                } else {
                    val errorMsg = when {
                        response.code() == 400 -> "Invalid group data"
                        response.code() == 409 -> "Username already taken"
                        response.errorBody()?.string()?.contains("duplicate key") == true -> "Username already exists"
                        else -> "Failed to create group: ${response.message()}"
                    }
                    contactRepository.getContacts().fold(
                        onSuccess = { contacts ->
                            _uiState.value = CreateGroupUiState.Error(errorMsg)
                        },
                        onFailure = {
                            _uiState.value = CreateGroupUiState.Error(errorMsg)
                        }
                    )
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("duplicate", ignoreCase = true) == true -> "Username already exists"
                    e.message?.contains("network", ignoreCase = true) == true -> "Network error. Please check your connection"
                    else -> e.message ?: "Failed to create group"
                }
                contactRepository.getContacts().fold(
                    onSuccess = { contacts ->
                        _uiState.value = CreateGroupUiState.Error(errorMsg)
                    },
                    onFailure = {
                        _uiState.value = CreateGroupUiState.Error(errorMsg)
                    }
                )
            }
        }
    }
}

sealed class CreateGroupUiState {
    object Loading : CreateGroupUiState()
    object Creating : CreateGroupUiState()
    object Empty : CreateGroupUiState()
    data class Success(val contacts: List<ContactWithUser>) : CreateGroupUiState()
    data class Error(val message: String) : CreateGroupUiState()
}
