package com.nexy.client.ui.screens.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.ContactRepository
import com.nexy.client.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val users: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    fun onQueryChange(query: String) {
        Log.d("SearchViewModel", "Query changed: '$query'")
        _uiState.value = _uiState.value.copy(query = query, error = null, successMessage = null)
        if (query.length >= 2) {
            Log.d("SearchViewModel", "Query length >= 2, starting search")
            searchUsers(query)
        } else {
            Log.d("SearchViewModel", "Query too short, clearing results")
            _uiState.value = _uiState.value.copy(users = emptyList())
        }
    }
    
    private fun searchUsers(query: String) {
        viewModelScope.launch {
            Log.d("SearchViewModel", "Searching for users with query: '$query'")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            userRepository.searchUsers(query).fold(
                onSuccess = { users ->
                    Log.d("SearchViewModel", "Search successful, found ${users.size} users")
                    _uiState.value = _uiState.value.copy(
                        users = users,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    Log.e("SearchViewModel", "Search failed: ${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }
    
    fun addContact(userId: Int) {
        viewModelScope.launch {
            contactRepository.addContact(userId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Contact added successfully"
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to add contact"
                    )
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
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to create chat"
                    )
                }
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
