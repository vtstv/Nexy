package com.nexy.client.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatInviteLink
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.models.CreateInviteLinkRequest
import com.nexy.client.data.models.MemberRole
import com.nexy.client.data.models.UpdateMemberRoleRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GroupSettingsUiState {
    object Loading : GroupSettingsUiState()
    data class Success(
        val chat: Chat,
        val members: List<ChatMember>,
        val canManageMembers: Boolean,
        val canCreateInvite: Boolean,
        val inviteLink: ChatInviteLink? = null
    ) : GroupSettingsUiState()
    data class Error(val message: String) : GroupSettingsUiState()
}

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    private val apiService: NexyApiService,
    private val tokenManager: AuthTokenManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<GroupSettingsUiState>(GroupSettingsUiState.Loading)
    val uiState: StateFlow<GroupSettingsUiState> = _uiState.asStateFlow()
    
    fun loadGroupSettings(groupId: Int) {
        viewModelScope.launch {
            _uiState.value = GroupSettingsUiState.Loading
            try {
                val chatResponse = apiService.getGroup(groupId)
                val membersResponse = apiService.getGroupMembers(groupId)
                
                if (chatResponse.isSuccessful && membersResponse.isSuccessful) {
                    val chat = chatResponse.body()!!
                    val members = membersResponse.body()!!
                    
                    // Load user data for each member
                    val membersWithUsers = members.map { member ->
                        try {
                            val userResponse = apiService.getUserById(member.userId)
                            if (userResponse.isSuccessful) {
                                member.copy(user = userResponse.body())
                            } else {
                                member
                            }
                        } catch (e: Exception) {
                            member
                        }
                    }
                    
                    val currentUserId = getCurrentUserId()
                    val currentUserMember = membersWithUsers.find { it.userId == currentUserId }
                    val canManage = currentUserMember?.role == MemberRole.OWNER || 
                                  currentUserMember?.role == MemberRole.ADMIN
                    val canCreateInvite = canManage || 
                                        currentUserMember?.permissions?.addUsers == true
                    
                    _uiState.value = GroupSettingsUiState.Success(
                        chat = chat,
                        members = membersWithUsers,
                        canManageMembers = canManage,
                        canCreateInvite = canCreateInvite
                    )
                } else {
                    _uiState.value = GroupSettingsUiState.Error("Failed to load group settings")
                }
            } catch (e: Exception) {
                _uiState.value = GroupSettingsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun removeMember(groupId: Int, userId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.removeMember(groupId, userId)
                if (response.isSuccessful) {
                    loadGroupSettings(groupId)
                }
            } catch (e: Exception) {
            }
        }
    }
    
    fun updateMemberRole(groupId: Int, userId: Int, role: String) {
        viewModelScope.launch {
            try {
                val response = apiService.updateMemberRole(
                    groupId,
                    userId,
                    UpdateMemberRoleRequest(role)
                )
                if (response.isSuccessful) {
                    loadGroupSettings(groupId)
                }
            } catch (e: Exception) {
            }
        }
    }
    
    fun transferOwnership(groupId: Int, newOwnerId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.transferOwnership(groupId, com.nexy.client.data.api.TransferOwnershipRequest(newOwnerId))
                if (response.isSuccessful) {
                    loadGroupSettings(groupId)
                }
            } catch (e: Exception) {
            }
        }
    }
    
    fun createInviteLink(groupId: Int, usageLimit: Int? = null, expiresInSeconds: Int? = null) {
        viewModelScope.launch {
            try {
                val response = apiService.createGroupInviteLink(
                    groupId,
                    CreateInviteLinkRequest(usageLimit, expiresInSeconds)
                )
                if (response.isSuccessful) {
                    val state = _uiState.value as? GroupSettingsUiState.Success
                    state?.let {
                        _uiState.value = it.copy(inviteLink = response.body())
                    }
                }
            } catch (e: Exception) {
            }
        }
    }
    
    fun clearInviteLink() {
        val state = _uiState.value as? GroupSettingsUiState.Success
        state?.let {
            _uiState.value = it.copy(inviteLink = null)
        }
    }
    
    private suspend fun getCurrentUserId(): Int {
        return tokenManager.getUserId() ?: 0
    }
}
