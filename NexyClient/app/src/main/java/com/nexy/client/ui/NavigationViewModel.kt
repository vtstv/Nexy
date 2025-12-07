package com.nexy.client.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor() : ViewModel() {
    private val _selectedChatId = MutableStateFlow<Int?>(null)
    val selectedChatId: StateFlow<Int?> = _selectedChatId.asStateFlow()

    // Pending deep link to a specific message in a chat (chatId, messageId)
    private val _pendingMessageLink = MutableStateFlow<Pair<Int, String>?>(null)
    val pendingMessageLink: StateFlow<Pair<Int, String>?> = _pendingMessageLink.asStateFlow()
    
    fun selectChat(chatId: Int) {
        _selectedChatId.value = chatId
    }

    fun setPendingMessageLink(chatId: Int, messageId: String) {
        _pendingMessageLink.value = chatId to messageId
    }

    fun clearPendingMessageLink() {
        _pendingMessageLink.value = null
    }
    
    fun clearSelection() {
        _selectedChatId.value = null
    }
}
