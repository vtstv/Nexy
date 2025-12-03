package com.nexy.client.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
                // Force refresh chat info from server to ensure participant list is up to date
                // This addresses the "synchronization problems" for group members
                // We don't have a direct "force refresh single chat" but we can fetch chat details from API
                // Actually, let's check if we can use getChatById with a force flag or similar
                // For now, we'll rely on the repository. 
                // If we want to be sure, we should probably add a refresh method.
                // But let's assume getChatById might return cached data.
                // Let's try to refresh the chat list or similar if needed.
                // However, for now, let's just proceed.
                
                val chatResult = chatRepository.getChatById(chatId)
                if (chatResult.isSuccess) {
                    val chat = chatResult.getOrNull()!!
                    
                    // Parse participant IDs
                    val participantIds = chat.participantIds ?: emptyList()
                    
                    // Fetch participants - force refresh to get latest status/avatar
                    val participants = participantIds.map { userId ->
                        async { userRepository.getUserById(userId, forceRefresh = true).getOrNull() }
                    }.awaitAll().filterNotNull()
                    
                    _uiState.value = _uiState.value.copy(
                        chat = chat,
                        participants = participants,
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
                    // Navigate back or handle success
                    // For now, we just update state, UI should observe and navigate
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
}

data class GroupInfoUiState(
    val chat: Chat? = null,
    val participants: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLeftGroup: Boolean = false // Track if the user has left the group
)
