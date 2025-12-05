package com.nexy.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SendState {
    QUEUED,
    SENDING,
    SENT,
    ERROR
}

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey
    val messageId: String,
    val chatId: Int,
    val senderId: Int,
    val content: String,
    val messageType: String = "text",
    val recipientId: Int? = null,
    val replyToId: Int? = null,
    val encrypted: Boolean = false,
    val encryptionAlgorithm: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val sendState: String = SendState.QUEUED.name,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null
)
