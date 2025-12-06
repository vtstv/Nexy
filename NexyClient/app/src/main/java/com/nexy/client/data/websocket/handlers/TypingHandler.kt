package com.nexy.client.data.websocket.handlers

import android.util.Log
import com.nexy.client.data.models.nexy.NexyMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TypingHandler @Inject constructor() {
    companion object {
        private const val TAG = "TypingHandler"
    }

    private val _typingEvents = MutableSharedFlow<Triple<Int, Boolean, Int?>>()
    val typingEvents: SharedFlow<Triple<Int, Boolean, Int?>> = _typingEvents.asSharedFlow()

    suspend fun handle(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        
        val chatId = when (val id = body["chat_id"]) {
            is Double -> id.toInt()
            is Int -> id
            is String -> id.toIntOrNull()
            else -> id.toString().toDoubleOrNull()?.toInt()
        }
        
        if (chatId == null) {
            Log.w(TAG, "Invalid chat_id in typing message: ${body["chat_id"]}")
            return
        }

        val isTyping = body["is_typing"] as? Boolean ?: return
        val senderId = nexyMessage.header.senderId
        
        Log.d(TAG, "Received typing event: chatId=$chatId, isTyping=$isTyping, senderId=$senderId")
        _typingEvents.emit(Triple(chatId, isTyping, senderId))
    }
}
