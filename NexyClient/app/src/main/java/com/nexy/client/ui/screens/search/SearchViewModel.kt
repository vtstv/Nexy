package com.nexy.client.ui.screens.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.local.dao.SearchHistoryDao
import com.nexy.client.data.local.entity.SearchHistoryEntity
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.ContactRepository
import com.nexy.client.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val users: List<User> = emptyList(),
    val groups: List<Chat> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    init {
        loadRecentSearches()
    }
    
    private fun loadRecentSearches() {
        viewModelScope.launch {
            searchHistoryDao.getRecentSearches().collect { history ->
                _uiState.value = _uiState.value.copy(
                    recentSearches = history.map { it.query }
                )
            }
        }
    }
    
    private fun normalizePhoneNumber(phone: String): String {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        if (cleaned.isEmpty()) return ""
        
        // Handle Russian format: 8XXXXXXXXXX -> +7XXXXXXXXXX
        if (cleaned.length == 11 && cleaned.startsWith("8")) {
            return "+7" + cleaned.substring(1)
        }
        
        // Add + if not present for international format
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }
    
    fun onQueryChange(query: String) {
        Log.d("SearchViewModel", "Query changed: '$query'")
        _uiState.value = _uiState.value.copy(query = query, error = null, successMessage = null)
        if (query.length >= 2) {
            Log.d("SearchViewModel", "Query length >= 2, starting search")
            search(query)
        } else {
            Log.d("SearchViewModel", "Query too short, clearing results")
            _uiState.value = _uiState.value.copy(users = emptyList(), groups = emptyList())
        }
    }
    
    private fun search(query: String) {
        viewModelScope.launch {
            Log.d("SearchViewModel", "Searching for users and groups with query: '$query'")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                coroutineScope {
                    val usersDeferred = async { userRepository.searchUsers(query) }
                    val groupsDeferred = async { chatRepository.searchPublicGroups(query) }
                    
                    // If query looks like a phone number, also search by phone with normalization
                    val cleanedQuery = query.replace(Regex("[^0-9+]"), "")
                    val phoneUserDeferred = if (cleanedQuery.matches(Regex("^\\+?[0-9]{7,15}$"))) {
                        val normalizedPhone = normalizePhoneNumber(query)
                        async { userRepository.searchUserByPhone(normalizedPhone) }
                    } else {
                        null
                    }
                    
                    val usersResult = usersDeferred.await()
                    val groupsResult = groupsDeferred.await()
                    val phoneUserResult = phoneUserDeferred?.await()
                    
                    val users = usersResult.getOrNull()?.toMutableList() ?: mutableListOf()
                    
                    // Add phone search result if found and not already in list
                    phoneUserResult?.getOrNull()?.let { phoneUser ->
                        if (users.none { it.id == phoneUser.id }) {
                            users.add(0, phoneUser) // Add to top of list
                        }
                    }
                    
                    val groups = groupsResult.getOrNull() ?: emptyList()
                    
                    Log.d("SearchViewModel", "Search complete. Users: ${users.size}, Groups: ${groups.size}")
                    
                    _uiState.value = _uiState.value.copy(
                        users = users,
                        groups = groups,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Search failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun saveCurrentQuery() {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            viewModelScope.launch {
                searchHistoryDao.insertSearch(SearchHistoryEntity(query))
            }
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearHistory()
        }
    }
    
    fun deleteHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryDao.deleteSearch(query)
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
