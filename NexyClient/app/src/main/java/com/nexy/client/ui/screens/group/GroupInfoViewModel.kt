package com.nexy.client.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    fun loadGroupInfo(chatId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val chatResult = chatRepository.getChatById(chatId)
                if (chatResult.isSuccess) {
                    val chat = chatResult.getOrNull()!!
                    val currentUserId = getCurrentUserId()
                    val participantIds = chat.participantIds ?: emptyList()
                    val isMember = currentUserId != null && participantIds.contains(currentUserId)
                    
                    // Load members with roles and online status via API
                    val membersResult = chatRepository.getGroupMembers(chatId)
                    val members = if (membersResult.isSuccess) {
                        membersResult.getOrNull() ?: emptyList()
                    } else {
                        emptyList()
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        chat = chat,
                        members = members,
                        currentUserId = currentUserId,
                        isMember = isMember,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to load group info",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }

    fun leaveGroup() {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = chatRepository.leaveGroup(chat.id)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLeftGroup = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to leave group",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }

    fun joinGroup() {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = chatRepository.joinPublicGroup(chat.id)
                if (result.isSuccess) {
                    loadGroupInfo(chat.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to join group",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }
    
    fun transferOwnership(newOwnerId: Int) {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = chatRepository.transferOwnership(chat.id, newOwnerId)
                if (result.isSuccess) {
                    loadGroupInfo(chat.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to transfer ownership",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }
    
    fun kickMember(userId: Int) {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = chatRepository.kickMember(chat.id, userId)
                if (result.isSuccess) {
                    loadGroupInfo(chat.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to kick member",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }
    
    fun banMember(userId: Int, reason: String? = null) {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = chatRepository.banMember(chat.id, userId, reason)
                if (result.isSuccess) {
                    loadGroupInfo(chat.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to ban member",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }
    
    fun getCurrentUserRole(): com.nexy.client.data.models.MemberRole? {
        val currentUserId = _uiState.value.currentUserId ?: return null
        return _uiState.value.members.find { it.userId == currentUserId }?.role
    }

    private suspend fun getCurrentUserId(): Int? {
        return userRepository.getCurrentUser().getOrNull()?.id
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(
            isSearching = !_uiState.value.isSearching,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    fun clearLeftGroupState() {
        _uiState.value = _uiState.value.copy(isLeftGroup = false)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.length > 2) {
            searchMembers(query)
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    private fun searchMembers(query: String) {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            val result = chatRepository.getGroupMembers(chat.id, query)
            if (result.isSuccess) {
                val members = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(searchResults = members)
            }
        }
    }
    
    fun loadBannedMembers() {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBans = true)
            val result = chatRepository.getBannedMembers(chat.id)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    bannedMembers = result.getOrNull() ?: emptyList(),
                    isLoadingBans = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoadingBans = false,
                    error = "Failed to load banned members"
                )
            }
        }
    }
    
    fun unbanMember(userId: Int) {
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = chatRepository.unbanMember(chat.id, userId)
                if (result.isSuccess) {
                    loadBannedMembers()
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to unban member",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }
    
    fun toggleBannedMembersView() {
        val newShowBanned = !_uiState.value.showBannedMembers
        _uiState.value = _uiState.value.copy(showBannedMembers = newShowBanned)
        if (newShowBanned && _uiState.value.bannedMembers.isEmpty()) {
            loadBannedMembers()
        }
    }
}

data class GroupInfoUiState(
    val chat: Chat? = null,
    val members: List<ChatMember> = emptyList(),
    val currentUserId: Int? = null,
    val isMember: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLeftGroup: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ChatMember> = emptyList(),
    val bannedMembers: List<com.nexy.client.data.api.GroupBan> = emptyList(),
    val showBannedMembers: Boolean = false,
    val isLoadingBans: Boolean = false
)
