package com.nexy.client.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.ContactStatus
import com.nexy.client.data.models.ContactWithUser
import com.nexy.client.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()
    
    init {
        loadContacts()
    }
    
    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = ContactsUiState.Loading
            
            contactRepository.getContacts()
                .onSuccess { contacts ->
                    _uiState.value = if (contacts.isEmpty()) {
                        ContactsUiState.Empty
                    } else {
                        ContactsUiState.Success(contacts)
                    }
                }
                .onFailure { error ->
                    _uiState.value = ContactsUiState.Error(error.message ?: "Failed to load contacts")
                }
        }
    }
    
    fun deleteContact(contactUserId: Int) {
        viewModelScope.launch {
            contactRepository.deleteContact(contactUserId)
                .onSuccess {
                    loadContacts() // Reload after delete
                }
                .onFailure { error ->
                    _uiState.value = ContactsUiState.Error(error.message ?: "Failed to delete contact")
                }
        }
    }
    
    fun blockContact(contactId: Int) {
        viewModelScope.launch {
            contactRepository.updateContactStatus(contactId, ContactStatus.BLOCKED)
                .onSuccess {
                    loadContacts()
                }
                .onFailure { error ->
                    _uiState.value = ContactsUiState.Error(error.message ?: "Failed to block contact")
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
                    _uiState.value = ContactsUiState.Error(error.message ?: "Failed to create chat")
                }
        }
    }
}

sealed class ContactsUiState {
    object Loading : ContactsUiState()
    object Empty : ContactsUiState()
    data class Success(val contacts: List<ContactWithUser>) : ContactsUiState()
    data class Error(val message: String) : ContactsUiState()
}
