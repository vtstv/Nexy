package com.nexy.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: Int,
    val type: String,
    val name: String?,
    val avatarUrl: String?,
    val participantIds: String,
    val lastMessageId: String?,
    val unreadCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val muted: Boolean,
    val lastReadMessageId: Int = 0,
    val firstUnreadMessageId: String? = null,
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0,
    val isHidden: Boolean = false
)
