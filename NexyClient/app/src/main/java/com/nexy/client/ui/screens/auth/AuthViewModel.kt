package com.nexy.client.ui.screens.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.repository.AuthRepository
import com.nexy.client.data.websocket.WebSocketMessageHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
    val sessionTerminatedReason: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val webSocketMessageHandler: WebSocketMessageHandler
) : ViewModel() {
    
    companion object {
        private const val TAG = "AuthViewModel"
    }
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    private val _forceLogoutEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val forceLogoutEvent: SharedFlow<String> = _forceLogoutEvent
    
    init {
        checkAuthStatus()
        loadSavedCredentials()
        observeSessionTermination()
    }
    
    private fun observeSessionTermination() {
        viewModelScope.launch {
            webSocketMessageHandler.sessionTerminatedEvents.collect { reason ->
                Log.w(TAG, "Session terminated by server: $reason")
                forceLogout(reason)
            }
        }
    }
    
    private fun forceLogout(reason: String) {
        viewModelScope.launch {
            authRepository.logout(clearCredentials = false)
            _uiState.value = AuthUiState(sessionTerminatedReason = reason)
            _forceLogoutEvent.emit(reason)
        }
    }
    
    fun clearSessionTerminatedReason() {
        _uiState.value = _uiState.value.copy(sessionTerminatedReason = null)
    }
    
    private fun checkAuthStatus() {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isLoggedIn()
            _uiState.value = _uiState.value.copy(isAuthenticated = isLoggedIn)
        }
    }
    
    private fun loadSavedCredentials() {
        viewModelScope.launch {
            val rememberMe = authRepository.isRememberMeEnabled()
            if (rememberMe) {
                val (email, password) = authRepository.getSavedCredentials()
                _uiState.value = _uiState.value.copy(
                    email = email ?: "",
                    password = password ?: "",
                    rememberMe = true
                )
            }
        }
    }
    
    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }
    
    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }
    
    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }
    
    fun onDisplayNameChange(displayName: String) {
        _uiState.value = _uiState.value.copy(displayName = displayName, error = null)
    }
    
    fun onRememberMeChange(rememberMe: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = rememberMe, error = null)
    }
    
    fun login() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = authRepository.login(
                _uiState.value.email, 
                _uiState.value.password,
                _uiState.value.rememberMe
            )
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Login failed"
                    )
                }
            )
        }
    }
    
    fun logout(clearCredentials: Boolean = false) {
        viewModelScope.launch {
            authRepository.logout(clearCredentials)
            _uiState.value = AuthUiState()
        }
    }
    
    fun register() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = authRepository.register(
                _uiState.value.username,
                _uiState.value.email,
                _uiState.value.password,
                _uiState.value.displayName.ifBlank { _uiState.value.username }
            )
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Registration failed"
                    )
                }
            )
        }
    }
}
