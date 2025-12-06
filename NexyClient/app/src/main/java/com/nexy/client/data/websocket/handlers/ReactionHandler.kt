package com.nexy.client.data.websocket.handlers

import android.util.Log
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.ReactionCount
import com.nexy.client.data.models.nexy.NexyMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ReactionEvent(
    val messageId: Int,
    val emoji: String,
    val userId: Int,
    val isAdd: Boolean
)

@Singleton
class ReactionHandler @Inject constructor(
    private val messageDao: MessageDao
) {
    companion object {
        private const val TAG = "ReactionHandler"
    }
    
    private val _reactionEvents = MutableSharedFlow<ReactionEvent>(extraBufferCapacity = 10)
    val reactionEvents: SharedFlow<ReactionEvent> = _reactionEvents
    
    suspend fun handleReactionAdd(nexyMessage: NexyMessage) {
        try {
            val body = nexyMessage.body as? Map<*, *> ?: return
            val messageId = (body["message_id"] as? Double)?.toInt() ?: return
            val emoji = body["emoji"] as? String ?: return
            val userId = (body["user_id"] as? Double)?.toInt() ?: return
            
            Log.d(TAG, "Reaction added: messageId=$messageId, emoji=$emoji, userId=$userId")
            
            _reactionEvents.emit(
                ReactionEvent(
                    messageId = messageId,
                    emoji = emoji,
                    userId = userId,
                    isAdd = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling reaction add", e)
        }
    }
    
    suspend fun handleReactionRemove(nexyMessage: NexyMessage) {
        try {
            val body = nexyMessage.body as? Map<*, *> ?: return
            val messageId = (body["message_id"] as? Double)?.toInt() ?: return
            val emoji = body["emoji"] as? String ?: return
            val userId = (body["user_id"] as? Double)?.toInt() ?: return
            
            Log.d(TAG, "Reaction removed: messageId=$messageId, emoji=$emoji, userId=$userId")
            
            _reactionEvents.emit(
                ReactionEvent(
                    messageId = messageId,
                    emoji = emoji,
                    userId = userId,
                    isAdd = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling reaction remove", e)
        }
    }
}
