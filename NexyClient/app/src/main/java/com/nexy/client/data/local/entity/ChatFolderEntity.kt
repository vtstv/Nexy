package com.nexy.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_folders")
data class ChatFolderEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val name: String,
    val icon: String,
    val color: String,
    val position: Int,
    val includeContacts: Boolean,
    val includeNonContacts: Boolean,
    val includeGroups: Boolean,
    val includeChannels: Boolean,
    val includeBots: Boolean,
    val includedChatIds: List<Int>?,
    val excludedChatIds: List<Int>?,
    val createdAt: String?,
    val updatedAt: String?
)
