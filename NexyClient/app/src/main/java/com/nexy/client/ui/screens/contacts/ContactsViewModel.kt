package com.nexy.client.ui.screens.contacts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.contacts.ContactsSyncManager
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
    private val contactRepository: ContactRepository,
    private val contactsSyncManager: ContactsSyncManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "ContactsViewModel"
    }
    
    private val _uiState = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()
    
    private val _needsContactsPermission = MutableStateFlow(!contactsSyncManager.hasContactsPermission())
    val needsContactsPermission: StateFlow<Boolean> = _needsContactsPermission.asStateFlow()
    
    init {
        loadContacts()
        if (contactsSyncManager.hasContactsPermission()) {
            syncDeviceContacts()
        }
        
        // Listen for contact updates
        viewModelScope.launch {
            contactRepository.contactsUpdateTrigger.collect {
                loadContacts()
            }
        }
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
    
    fun onContactsPermissionGranted() {
        Log.d(TAG, "Contacts permission granted, starting sync")
        _needsContactsPermission.value = false
        syncDeviceContacts()
    }
    
    fun onContactsPermissionDenied() {
        Log.d(TAG, "Contacts permission denied")
        _needsContactsPermission.value = false
    }
    
    private fun syncDeviceContacts() {
        viewModelScope.launch {
            Log.d(TAG, "Starting device contacts sync")
            val result = contactsSyncManager.syncDeviceContactsWithNexy()
            Log.d(TAG, "Sync result: $result")
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
