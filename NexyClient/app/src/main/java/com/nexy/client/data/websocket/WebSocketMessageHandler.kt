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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketMessageHandler @Inject constructor(
    private val chatMessageHandler: ChatMessageHandler,
    private val groupEventHandler: GroupEventHandler,
    private val messageStateHandler: MessageStateHandler,
    private val typingHandler: TypingHandler,
    private val reactionHandler: com.nexy.client.data.websocket.handlers.ReactionHandler
) {
    companion object {
        private const val TAG = "WSMessageHandler"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val typingEvents: SharedFlow<Triple<Int, Boolean, Int?>>
        get() = typingHandler.typingEvents
    
    val reactionEvents: SharedFlow<com.nexy.client.data.websocket.handlers.ReactionEvent>
        get() = reactionHandler.reactionEvents

    private val _sessionTerminatedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sessionTerminatedEvents: SharedFlow<String> = _sessionTerminatedEvents

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
                    "reaction_add" -> reactionHandler.handleReactionAdd(nexyMessage)
                    "reaction_remove" -> reactionHandler.handleReactionRemove(nexyMessage)
                    "typing" -> typingHandler.handle(nexyMessage)
                    "session_terminated" -> handleSessionTerminated(nexyMessage)
                    else -> Log.d(TAG, "Ignoring message type: ${nexyMessage.header.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming message", e)
            }
        }
    }

    private fun handleSessionTerminated(nexyMessage: NexyMessage) {
        val body = nexyMessage.body as? Map<*, *>
        val reason = body?.get("reason") as? String ?: "session_terminated"
        Log.w(TAG, "Session terminated by server: $reason")
        _sessionTerminatedEvents.tryEmit(reason)
    }
}
