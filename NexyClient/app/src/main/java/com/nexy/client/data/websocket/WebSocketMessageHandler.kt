package com.nexy.client.data.websocket

import android.util.Log
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.websocket.handlers.ChatMessageHandler
import com.nexy.client.data.websocket.handlers.GroupEventHandler
import com.nexy.client.data.websocket.handlers.MessageStateHandler
import com.nexy.client.data.websocket.handlers.TypingHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketMessageHandler @Inject constructor(
    private val chatMessageHandler: ChatMessageHandler,
    private val groupEventHandler: GroupEventHandler,
    private val messageStateHandler: MessageStateHandler,
    private val typingHandler: TypingHandler
) {
    companion object {
        private const val TAG = "WSMessageHandler"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val typingEvents: SharedFlow<Triple<Int, Boolean, Int?>>
        get() = typingHandler.typingEvents

    fun handleIncomingMessage(nexyMessage: NexyMessage) {
        scope.launch {
            try {
                when (nexyMessage.header.type) {
                    "chat_message" -> chatMessageHandler.handle(nexyMessage)
                    "chat_created" -> groupEventHandler.handleChatCreated(nexyMessage)
                    "added_to_group" -> groupEventHandler.handleAddedToGroup(nexyMessage)
                    "kicked_from_group" -> groupEventHandler.handleKickedFromGroup(nexyMessage)
                    "banned_from_group" -> groupEventHandler.handleBannedFromGroup(nexyMessage)
                    "ack" -> messageStateHandler.handleAck(nexyMessage)
                    "read" -> messageStateHandler.handleReadReceipt(nexyMessage)
                    "edit" -> messageStateHandler.handleEditMessage(nexyMessage)
                    "delete" -> messageStateHandler.handleDeleteMessage(nexyMessage)
                    "typing" -> typingHandler.handle(nexyMessage)
                    else -> Log.d(TAG, "Ignoring message type: ${nexyMessage.header.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming message", e)
            }
        }
    }
}
