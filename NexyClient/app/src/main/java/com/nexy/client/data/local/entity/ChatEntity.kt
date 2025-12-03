package com.nexy.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: Int,  // Changed from String to Int
    val type: String,
    val name: String?,
    val avatarUrl: String?,
    val participantIds: String,  // Comma-separated Int IDs stored as String
    val lastMessageId: String?,
    val unreadCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val muted: Boolean
)
