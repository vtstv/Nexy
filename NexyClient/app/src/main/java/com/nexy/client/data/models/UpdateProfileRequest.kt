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
    val password: String?,

    @SerializedName("read_receipts_enabled")
    val readReceiptsEnabled: Boolean? = null,

    @SerializedName("typing_indicators_enabled")
    val typingIndicatorsEnabled: Boolean? = null,

    @SerializedName("voice_messages_enabled")
    val voiceMessagesEnabled: Boolean? = null,

    @SerializedName("show_online_status")
    val showOnlineStatus: Boolean? = null
)
