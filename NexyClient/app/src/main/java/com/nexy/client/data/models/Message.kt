package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

data class Message(
    @SerializedName("message_id")  // Server uses message_id field
    val id: String,
    
    @SerializedName("chat_id")  // Changed to snake_case and Int type
    val chatId: Int,
    
    @SerializedName("sender_id")  // Changed to snake_case and Int type
    val senderId: Int,
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("message_type")  // Server uses message_type field
    val type: MessageType,
    
    @SerializedName("created_at")  // Server sends as ISO timestamp string
    val timestamp: String? = null,
    
    @SerializedName("status")
    val status: MessageStatus? = null,
    
    @SerializedName("media_url")  // Changed to snake_case
    val mediaUrl: String? = null,
    
    @SerializedName("media_type")  // Server has media_type field
    val mediaType: String? = null,
    
    @SerializedName("reply_to_id")  // Changed to snake_case
    val replyToId: Int? = null,  // Changed from String to Int
    
    @SerializedName("is_edited")  // Server has is_edited field
    val isEdited: Boolean = false,
    
    @SerializedName("encrypted")  // Server E2E fields
    val encrypted: Boolean = false,
    
    @SerializedName("encryption_algorithm")
    val encryptionAlgorithm: String? = null,
    
    @SerializedName("sender")
    val sender: User? = null
)

// Server message types (from CHECK constraint: CHECK (message_type IN ('text', 'media', 'file', 'system')))
enum class MessageType {
    @SerializedName("text")
    TEXT,
    
    @SerializedName("media")  // Server uses "media" for image/video/audio
    MEDIA,
    
    @SerializedName("file")
    FILE,
    
    @SerializedName("system")
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
