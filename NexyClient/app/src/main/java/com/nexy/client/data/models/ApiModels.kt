package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("password")
    val password: String
)

data class RegisterRequest(
    @SerializedName("username")
    val username: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("display_name")
    val displayName: String
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    
    @SerializedName("refresh_token")
    val refreshToken: String,
    
    @SerializedName("user")
    val user: User,
    
    @SerializedName("expiresIn")
    val expiresIn: Long? = null
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class InviteLink(
    @SerializedName("code")
    val code: String,
    
    @SerializedName("chat_id")  // Server uses snake_case
    val chatId: Int,
    
    @SerializedName("expires_at")  // Server uses snake_case
    val expiresAt: Long? = null,
    
    @SerializedName("max_uses")  // Server uses snake_case
    val maxUses: Int? = null,
    
    @SerializedName("creator_id")  // Server uses snake_case and Int
    val createdBy: Int
)

data class SearchResult(
    @SerializedName("users")
    val users: List<User>
)

// Invite request models (for server compatibility)
data class CreateInviteRequest(
    @SerializedName("chat_id")
    val chatId: Int? = null,  // Nullable if server generates chat on join
    
    @SerializedName("max_uses")
    val maxUses: Int = 1,
    
    @SerializedName("expires_at")
    val expiresAt: Long? = null
)

data class ValidateInviteRequest(
    @SerializedName("code")
    val code: String
)

data class UseInviteRequest(
    @SerializedName("code")
    val code: String
)

data class JoinChatResponse(
    @SerializedName("chat_id")
    val chatId: Int,
    
    @SerializedName("chat")
    val chat: Chat? = null
)

data class CreateGroupRequest(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("username")
    val username: String? = null,
    
    @SerializedName("members")
    val members: List<Int>? = null,

    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)

data class UpdateGroupRequest(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("username")
    val username: String? = null,
    
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)

data class CreateInviteLinkRequest(
    @SerializedName("usage_limit")
    val usageLimit: Int? = null,
    
    @SerializedName("expires_in")
    val expiresIn: Int? = null
)

data class AddMemberRequest(
    @SerializedName("user_id")
    val userId: Int
)

data class DeleteMessageRequest(
    @SerializedName("message_id")
    val messageId: String
)

data class UpdateMessageRequest(
    @SerializedName("content")
    val content: String
)

data class UpdateMemberRoleRequest(
    @SerializedName("role")
    val role: String
)

data class JoinByInviteRequest(
    @SerializedName("invite_code")
    val inviteCode: String
)

