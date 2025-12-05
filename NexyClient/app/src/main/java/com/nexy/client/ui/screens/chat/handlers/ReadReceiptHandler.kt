package com.nexy.client.ui.screens.chat.handlers

import android.util.Log
import com.nexy.client.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject

class ReadReceiptHandler @Inject constructor(
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "ReadReceiptHandler"
        private const val DEBOUNCE_MS = 500L
    }

    private var debounceJob: Job? = null
    private var firstLoading = true
    private var savedFirstUnreadMessageId: String? = null
    private var lastKnownMessageId: String? = null
    private var isChatActive = false

    fun reset() {
        firstLoading = true
        savedFirstUnreadMessageId = null
        lastKnownMessageId = null
        isChatActive = false
        debounceJob?.cancel()
        debounceJob = null
    }

    fun setChatActive(active: Boolean) {
        isChatActive = active
    }

    fun isChatActive(): Boolean = isChatActive

    fun isFirstLoading(): Boolean = firstLoading

    fun setFirstLoadingComplete() {
        firstLoading = false
    }

    fun getSavedFirstUnreadMessageId(): String? = savedFirstUnreadMessageId

    fun saveFirstUnreadMessageId(id: String?) {
        if (savedFirstUnreadMessageId == null && id != null) {
            savedFirstUnreadMessageId = id
            Log.d(TAG, "Saved firstUnreadMessageId: $savedFirstUnreadMessageId")
        }
    }

    fun updateLastKnownMessageId(messageId: String?) {
        lastKnownMessageId = messageId
    }

    fun getLastKnownMessageId(): String? = lastKnownMessageId

    suspend fun markAsRead(chatId: Int) {
        Log.d(TAG, "markAsRead called for chatId=$chatId")
        chatRepository.markChatAsRead(chatId)
    }

    fun cancelPendingReceipt() {
        debounceJob?.cancel()
        debounceJob = null
    }

    fun setDebounceJob(job: Job) {
        debounceJob?.cancel()
        debounceJob = job
    }

    fun getDebounceMs(): Long = DEBOUNCE_MS
}
