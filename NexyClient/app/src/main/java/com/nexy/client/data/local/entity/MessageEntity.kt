package com.nexy.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId"), Index("senderId")]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val serverId: Int? = null,
    val chatId: Int,  // Changed from String to Int
    val senderId: Int,  // Changed from String to Int
    val content: String,
    val type: String,
    val timestamp: Long,
    val status: String,
    val mediaUrl: String?,
    val mediaThumbnail: String?,
    val replyToId: String?,
    val isSyncedToServer: Boolean = false
)
