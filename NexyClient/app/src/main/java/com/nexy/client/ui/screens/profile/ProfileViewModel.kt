/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.repository.file.FileOperations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val fileOperations: FileOperations,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userRepository.getCurrentUser().fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(
                        user = user,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }

    fun updateProfile(displayName: String, bio: String, avatarUri: Uri?, email: String?, password: String?) {
        viewModelScope.launch {
            android.util.Log.d("ProfileViewModel", "updateProfile: starting. displayName=$displayName, hasAvatar=${avatarUri != null}")
            val currentUser = _uiState.value.user
            if (currentUser == null) {
                _uiState.value = _uiState.value.copy(error = "User not loaded")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            
            var avatarUrl = currentUser.avatarUrl

            if (avatarUri != null) {
                // Upload new avatar
                android.util.Log.d("ProfileViewModel", "updateProfile: uploading avatar...")
                fileOperations.uploadFile(context, avatarUri).fold(
                    onSuccess = { url ->
                        android.util.Log.d("ProfileViewModel", "updateProfile: avatar uploaded. url=$url")
                        avatarUrl = url
                    },
                    onFailure = { error ->
                        android.util.Log.e("ProfileViewModel", "updateProfile: avatar upload failed", error)
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            error = "Failed to upload avatar: ${error.message}"
                        )
                        return@launch
                    }
                )
            }

            // Update profile with new data
            userRepository.updateProfile(displayName, bio, avatarUrl, email, password).fold(
                onSuccess = { updatedUser ->
                    _uiState.value = _uiState.value.copy(
                        user = updatedUser,
                        isSaving = false,
                        message = "Profile updated successfully"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = error.message
                    )
                }
            )
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
