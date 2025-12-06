package com.nexy.client.data.repository.reaction

import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.models.AddReactionRequest
import com.nexy.client.data.models.ReactionCount
import com.nexy.client.data.models.RemoveReactionRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionOperations @Inject constructor(
    private val api: NexyApiService
) {
    
    suspend fun addReaction(messageId: Int, emoji: String): Result<Unit> {
        return try {
            android.util.Log.d("ReactionOps", "Adding reaction: messageId=$messageId, emoji=$emoji")
            val request = AddReactionRequest(messageId, emoji)
            android.util.Log.d("ReactionOps", "Request: $request")
            val response = api.addReaction(request)
            android.util.Log.d("ReactionOps", "Response code: ${response.code()}, body: ${response.errorBody()?.string()}")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add reaction: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ReactionOps", "Exception adding reaction", e)
            Result.failure(e)
        }
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
