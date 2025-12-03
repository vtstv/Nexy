package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

data class UpdateProfileRequest(
    @SerializedName("display_name")
    val displayName: String,
    
    @SerializedName("bio")
    val bio: String,
    
    @SerializedName("avatar_url")
    val avatarUrl: String?,
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("password")
    val password: String?
)
