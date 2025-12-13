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
    val displayName: String,
    
    @SerializedName("phone_number")
    val phoneNumber: String? = null
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

data class InvitePreviewResponse(
    @SerializedName("valid")
    val valid: Boolean,
    
    @SerializedName("chat_id")
    val chatId: Int? = null,
    
    @SerializedName("chat_name")
    val chatName: String? = null,
    
    @SerializedName("chat_type")
    val chatType: String? = null,
    
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerializedName("member_count")
    val memberCount: Int? = null,
    
    @SerializedName("error_message")
    val errorMessage: String? = null
)

data class UserSession(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("user_id")
    val userId: Int,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("device_name")
    val deviceName: String,
    
    @SerializedName("device_type")
    val deviceType: String,
    
    @SerializedName("ip_address")
    val ipAddress: String,
    
    @SerializedName("last_active")
    val lastActive: String,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("is_current")
    val isCurrent: Boolean,
    
    @SerializedName("accept_secret_chats")
    val acceptSecretChats: Boolean = true,
    
    @SerializedName("accept_calls")
    val acceptCalls: Boolean = true
)

// Chat Folders
data class ChatFolder(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("user_id")
    val userId: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("icon")
    val icon: String,
    
    @SerializedName("color")
    val color: String,
    
    @SerializedName("position")
    val position: Int,
    
    @SerializedName("include_contacts")
    val includeContacts: Boolean = false,
    
    @SerializedName("include_non_contacts")
    val includeNonContacts: Boolean = false,
    
    @SerializedName("include_groups")
    val includeGroups: Boolean = false,
    
    @SerializedName("include_channels")
    val includeChannels: Boolean = false,
    
    @SerializedName("include_bots")
    val includeBots: Boolean = false,
    
    @SerializedName("included_chats")
    val includedChatIds: List<Int>? = null,
    
    @SerializedName("excluded_chats")
    val excludedChatIds: List<Int>? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

data class CreateFolderRequest(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("icon")
    val icon: String = "📁",
    
    @SerializedName("color")
    val color: String = "#2196F3",
    
    @SerializedName("include_contacts")
    val includeContacts: Boolean = false,
    
    @SerializedName("include_non_contacts")
    val includeNonContacts: Boolean = false,
    
    @SerializedName("include_groups")
    val includeGroups: Boolean = false,
    
    @SerializedName("include_channels")
    val includeChannels: Boolean = false,
    
    @SerializedName("include_bots")
    val includeBots: Boolean = false
)

data class UpdateFolderRequest(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("icon")
    val icon: String,
    
    @SerializedName("color")
    val color: String,
    
    @SerializedName("include_contacts")
    val includeContacts: Boolean? = null,
    
    @SerializedName("include_non_contacts")
    val includeNonContacts: Boolean? = null,
    
    @SerializedName("include_groups")
    val includeGroups: Boolean? = null,
    
    @SerializedName("include_channels")
    val includeChannels: Boolean? = null,
    
    @SerializedName("include_bots")
    val includeBots: Boolean? = null
)

data class AddChatsToFolderRequest(
    @SerializedName("chat_ids")
    val chatIds: List<Int>
)

data class ReorderFoldersRequest(
    @SerializedName("positions")
    val positions: Map<Int, Int>
)

data class UpdateSessionSettingsRequest(
    @SerializedName("accept_secret_chats")
    val acceptSecretChats: Boolean? = null,
    
    @SerializedName("accept_calls")
    val acceptCalls: Boolean? = null
)

