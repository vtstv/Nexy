package com.nexy.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: Int,
    val username: String,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val status: String,
    val bio: String?,
    val readReceiptsEnabled: Boolean = true,
    val publicKey: String?,
    val createdAt: String?,
    val lastSeen: Long = System.currentTimeMillis()
)
