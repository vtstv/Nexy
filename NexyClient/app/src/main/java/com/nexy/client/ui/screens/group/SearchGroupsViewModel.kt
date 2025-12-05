package com.nexy.client.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.Chat
import com.nexy.client.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchGroupsUiState {
    object Idle : SearchGroupsUiState()
    object Loading : SearchGroupsUiState()
    data class Success(val groups: List<Chat>) : SearchGroupsUiState()
    data class Error(val message: String) : SearchGroupsUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchGroupsViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SearchGroupsUiState>(SearchGroupsUiState.Idle)
    val uiState: StateFlow<SearchGroupsUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Int>()
    val navigationEvent: SharedFlow<Int> = _navigationEvent.asSharedFlow()
    
    init {
        viewModelScope.launch {
            searchQuery
                .debounce(500)
                .collect { query ->
                    if (query.isNotBlank()) {
                        searchGroups(query)
                    } else {
                        _uiState.value = SearchGroupsUiState.Idle
                    }
                }
        }
    }
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    private fun searchGroups(query: String) {
        viewModelScope.launch {
            _uiState.value = SearchGroupsUiState.Loading
            chatRepository.searchPublicGroups(query)
                .onSuccess { groups ->
                    _uiState.value = SearchGroupsUiState.Success(groups)
                }
                .onFailure { e ->
                    _uiState.value = SearchGroupsUiState.Error(e.message ?: "Unknown error")
                }
        }
    }
    
    fun joinGroup(groupId: Int) {
        viewModelScope.launch {
            chatRepository.joinPublicGroup(groupId)
                .onSuccess {
                    val currentQuery = _searchQuery.value
                    if (currentQuery.isNotBlank()) {
                        searchGroups(currentQuery)
                    }
                    _navigationEvent.emit(groupId)
                }
                .onFailure {
                    // TODO: Show error
                }
        }
    }
}
