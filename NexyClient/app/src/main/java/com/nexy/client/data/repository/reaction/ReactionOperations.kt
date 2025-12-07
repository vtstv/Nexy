package com.nexy.client.data.repository.reaction

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.AddReactionRequest
import com.nexy.client.data.models.ReactionCount
import com.nexy.client.data.models.RemoveReactionRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionOperations @Inject constructor(
    private val api: NexyApiService,
    private val messageDao: MessageDao,
    private val tokenManager: AuthTokenManager
) {
    companion object {
        private const val TAG = "ReactionOps"
    }
    
    suspend fun addReaction(messageId: Int, emoji: String): Result<Unit> {
        return try {
            Log.d(TAG, "Adding reaction: messageId=$messageId, emoji=$emoji")
            val request = AddReactionRequest(messageId, emoji)
            val response = api.addReaction(request)
            Log.d(TAG, "Response code: ${response.code()}")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to add reaction: ${response.code()}, body=$errorBody")
                Result.failure(Exception("Failed to add reaction: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception adding reaction", e)
            Result.failure(e)
        }
    }
    
    suspend fun addReactionOptimistic(clientMessageId: String, emoji: String): Result<Unit> {
        val currentUserId = tokenManager.getUserId() ?: return Result.failure(Exception("Not logged in"))
        val message = messageDao.getMessageById(clientMessageId) 
            ?: return Result.failure(Exception("Message not found"))
        
        val currentReactions = message.reactions?.toMutableList() ?: mutableListOf()
        
        // Check if user already has THIS EXACT reaction (same emoji)
        val existingEmojiIndex = currentReactions.indexOfFirst { it.emoji == emoji }
        
        if (existingEmojiIndex != -1) {
            val existing = currentReactions[existingEmojiIndex]
            if (existing.userIds.contains(currentUserId)) {
                // User already has this reaction - toggle off (remove it)
                val newUserIds = existing.userIds - currentUserId
                if (newUserIds.isEmpty()) {
                    currentReactions.removeAt(existingEmojiIndex)
                } else {
                    currentReactions[existingEmojiIndex] = existing.copy(
                        count = existing.count - 1,
                        userIds = newUserIds,
                        reactedBy = false
                    )
                }
                
                messageDao.updateReactionsByClientId(clientMessageId, currentReactions)
                Log.d(TAG, "Optimistic reaction toggled off for message $clientMessageId")
                
                val serverId = message.serverId
                if (serverId != null && serverId > 0) {
                    return addReaction(serverId, emoji)
                }
                return Result.success(Unit)
            } else {
                // Emoji exists from other users - user is JOINING this reaction (not creating new)
                // Just add user to this reaction counter
                currentReactions[existingEmojiIndex] = existing.copy(
                    count = existing.count + 1,
                    userIds = existing.userIds + currentUserId,
                    reactedBy = true
                )
            }
        } else {
            // NEW emoji that doesn't exist yet - this is user's OWN reaction
            // First, remove user's previous "own" reaction (first one they created)
            val userOwnReactionIndex = currentReactions.indexOfFirst { reaction ->
                reaction.userIds.contains(currentUserId)
            }
            
            if (userOwnReactionIndex != -1) {
                val oldReaction = currentReactions[userOwnReactionIndex]
                val newUserIds = oldReaction.userIds - currentUserId
                if (newUserIds.isEmpty()) {
                    currentReactions.removeAt(userOwnReactionIndex)
                } else {
                    currentReactions[userOwnReactionIndex] = oldReaction.copy(
                        count = oldReaction.count - 1,
                        userIds = newUserIds,
                        reactedBy = false
                    )
                }
            }
            
            // Add new own reaction
            currentReactions.add(
                ReactionCount(
                    emoji = emoji,
                    count = 1,
                    userIds = listOf(currentUserId),
                    reactedBy = true
                )
            )
        }
        
        messageDao.updateReactionsByClientId(clientMessageId, currentReactions)
        Log.d(TAG, "Optimistic reaction added for message $clientMessageId")
        
        val serverId = message.serverId
        if (serverId != null && serverId > 0) {
            return addReaction(serverId, emoji)
        }
        
        return Result.success(Unit)
    }
    
    suspend fun removeReaction(messageId: Int, emoji: String): Result<Unit> {
        return try {
            val response = api.removeReaction(RemoveReactionRequest(messageId, emoji))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove reaction: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun removeReactionOptimistic(clientMessageId: String, emoji: String): Result<Unit> {
        val currentUserId = tokenManager.getUserId() ?: return Result.failure(Exception("Not logged in"))
        val message = messageDao.getMessageById(clientMessageId) 
            ?: return Result.failure(Exception("Message not found"))
        
        val currentReactions = message.reactions?.toMutableList() ?: mutableListOf()
        val existingIndex = currentReactions.indexOfFirst { it.emoji == emoji }
        
        if (existingIndex != -1) {
            val existing = currentReactions[existingIndex]
            if (existing.userIds.contains(currentUserId)) {
                val newUserIds = existing.userIds - currentUserId
                if (newUserIds.isEmpty()) {
                    currentReactions.removeAt(existingIndex)
                } else {
                    currentReactions[existingIndex] = existing.copy(
                        count = existing.count - 1,
                        userIds = newUserIds,
                        reactedBy = false
                    )
                }
            }
        }
        
        messageDao.updateReactionsByClientId(clientMessageId, currentReactions)
        Log.d(TAG, "Optimistic reaction removed for message $clientMessageId")
        
        val serverId = message.serverId
        if (serverId != null && serverId > 0) {
            return removeReaction(serverId, emoji)
        }
        
        return Result.success(Unit)
    }
    
    suspend fun getReactions(messageId: Int): Result<List<ReactionCount>> {
        return try {
            val response = api.getReactions(messageId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to get reactions: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}