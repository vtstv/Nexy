package com.nexy.client.ui.screens.profile.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.ContactRepository
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val webRTCClient: WebRTCClient,
    private val authTokenManager: AuthTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    fun loadUser(userId: Int) {
        viewModelScope.launch {
            _uiState.value = UserProfileUiState(isLoading = true)
            try {
                val userResult = userRepository.getUserById(userId, forceRefresh = true)
                val contactResult = contactRepository.checkContactStatus(userId)
                
                if (userResult.isSuccess) {
                    val isContact = contactResult.getOrNull()?.exists == true
                    val contactStatus = contactResult.getOrNull()?.status
                    _uiState.value = UserProfileUiState(
                        user = userResult.getOrNull(),
                        isContact = isContact,
                        isBlocked = contactStatus == "blocked"
                    )
                } else {
                    _uiState.value = UserProfileUiState(error = "Failed to load user")
                }
            } catch (e: Exception) {
                _uiState.value = UserProfileUiState(error = e.message ?: "Unknown error")
            }
        }
    }
    
    fun addContact(userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isContactActionLoading = true) }
            contactRepository.addContact(userId)
                .onSuccess {
                    _uiState.update { it.copy(isContact = true, isContactActionLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        error = error.message ?: "Failed to add contact",
                        isContactActionLoading = false
                    )}
                }
        }
    }
    
    fun removeContact(userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isContactActionLoading = true) }
            contactRepository.deleteContact(userId)
                .onSuccess {
                    _uiState.update { it.copy(isContact = false, isContactActionLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        error = error.message ?: "Failed to remove contact",
                        isContactActionLoading = false
                    )}
                }
        }
    }
    
    fun createChat(userId: Int, onChatCreated: (Int) -> Unit) {
        viewModelScope.launch {
            contactRepository.createPrivateChat(userId)
                .onSuccess { chat ->
                    onChatCreated(chat.id)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to create chat") }
                }
        }
    }
    
    fun startCall(userId: Int) {
        viewModelScope.launch {
            val currentUserId = authTokenManager.getUserId()
            if (currentUserId == null) {
                _uiState.update { it.copy(error = "Not authenticated") }
                return@launch
            }
            
            try {
                webRTCClient.startCall(userId, currentUserId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to start call: ${e.message}") }
            }
        }
    }
    
    fun showMessage(message: String) {
        _uiState.update { it.copy(error = message) }
    }
    
    fun toggleQrDialog() {
        _uiState.update { it.copy(showQrDialog = !it.showQrDialog) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class UserProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isContact: Boolean = false,
    val isBlocked: Boolean = false,
    val isContactActionLoading: Boolean = false,
    val showQrDialog: Boolean = false,
    val error: String? = null
)
