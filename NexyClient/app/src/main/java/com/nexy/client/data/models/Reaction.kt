package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

data class MessageReaction(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("message_id")
    val messageId: Int,
    
    @SerializedName("user_id")
    val userId: Int,
    
    @SerializedName("emoji")
    val emoji: String,
    
    @SerializedName("created_at")
    val createdAt: String
)

data class ReactionCount(
    @SerializedName("emoji")
    val emoji: String,
    
    @SerializedName("count")
    val count: Int,
    
    @SerializedName("user_ids")
    val userIds: List<Int>,
    
    @SerializedName("reacted_by")
    val reactedBy: Boolean
)

data class AddReactionRequest(
    @SerializedName("message_id")
    val messageId: Int,
    
    @SerializedName("emoji")
    val emoji: String
)

data class RemoveReactionRequest(
    @SerializedName("message_id")
    val messageId: Int,
    
    @SerializedName("emoji")
    val emoji: String
)
