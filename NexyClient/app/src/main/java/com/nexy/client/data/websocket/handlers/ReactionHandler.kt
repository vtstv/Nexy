package com.nexy.client.data.websocket.handlers

import android.util.Log
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.ReactionCount
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.local.AuthTokenManager
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
    private val messageDao: MessageDao,
    private val tokenManager: AuthTokenManager,
    private val apiService: com.nexy.client.data.api.NexyApiService,
    private val userDao: com.nexy.client.data.local.dao.UserDao
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
            
            ensureUserCached(userId)
            updateReactionsInDb(messageId, emoji, userId, true)
            
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
            
            updateReactionsInDb(messageId, emoji, userId, false)
            
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

    private suspend fun updateReactionsInDb(messageId: Int, emoji: String, userId: Int, isAdd: Boolean) {
        val message = messageDao.getMessageByServerId(messageId) ?: return
        val currentReactions = message.reactions?.toMutableList() ?: mutableListOf()
        
        val existingReactionIndex = currentReactions.indexOfFirst { it.emoji == emoji }
        
        if (isAdd) {
            if (existingReactionIndex != -1) {
                val existing = currentReactions[existingReactionIndex]
                if (!existing.userIds.contains(userId)) {
                    val newUserIds = existing.userIds + userId
                    currentReactions[existingReactionIndex] = existing.copy(
                        count = existing.count + 1,
                        userIds = newUserIds
                    )
                }
            } else {
                currentReactions.add(
                    ReactionCount(
                        emoji = emoji,
                        count = 1,
                        userIds = listOf(userId),
                        reactedBy = false
                    )
                )
            }
        } else {
            if (existingReactionIndex != -1) {
                val existing = currentReactions[existingReactionIndex]
                if (existing.userIds.contains(userId)) {
                    val newUserIds = existing.userIds - userId
                    if (newUserIds.isEmpty()) {
                        currentReactions.removeAt(existingReactionIndex)
                    } else {
                        currentReactions[existingReactionIndex] = existing.copy(
                            count = existing.count - 1,
                            userIds = newUserIds
                        )
                    }
                }
            }
        }
        
        val currentUserId = tokenManager.getUserId() ?: -1
        val finalReactions = currentReactions.map { reaction ->
             reaction.copy(reactedBy = reaction.userIds.contains(currentUserId))
        }

        messageDao.updateReactions(messageId, finalReactions)
    }

    private suspend fun ensureUserCached(userId: Int) {
        if (userDao.getUserById(userId) == null) {
             try {
                 val response = apiService.getUserById(userId)
                 if (response.isSuccessful && response.body() != null) {
                     val user = response.body()!!
                     userDao.insertUser(com.nexy.client.data.local.entity.UserEntity(
                         id = user.id,
                         username = user.username,
                         email = user.email,
                         displayName = user.displayName,
                         avatarUrl = user.avatarUrl,
                         status = user.status?.name ?: com.nexy.client.data.models.UserStatus.OFFLINE.name,
                         bio = user.bio,
                         publicKey = user.publicKey,
                         createdAt = user.createdAt
                     ))
                 }
             } catch (e: Exception) {
                 Log.e(TAG, "Failed to fetch user $userId for reaction", e)
             }
        }
    }
}
