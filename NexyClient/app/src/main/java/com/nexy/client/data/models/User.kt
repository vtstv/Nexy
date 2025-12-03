package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("display_name")
    val displayName: String? = null,
    
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerializedName("status")
    val status: UserStatus? = UserStatus.OFFLINE,
    
    @SerializedName("bio")
    val bio: String? = null,

    @SerializedName("read_receipts_enabled")
    val readReceiptsEnabled: Boolean = true,
    
    @SerializedName("public_key")
    val publicKey: String? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

enum class UserStatus {
    @SerializedName("online")
    ONLINE,
    
    @SerializedName("offline")
    OFFLINE,
    
    @SerializedName("away")
    AWAY
}
