package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

data class Chat(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("type")
    val type: ChatType,
    
    @SerializedName("group_type")
    val groupType: GroupType? = null,
    
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("username")
    val username: String? = null,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerializedName("created_by")
    val createdBy: Int? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    
    @SerializedName("participant_ids")
    val participantIds: List<Int>? = emptyList(),
    
    @SerializedName("default_permissions")
    val defaultPermissions: ChatPermissions? = null,
    
    val lastMessage: Message? = null,
    
    @SerializedName("unread_count")
    val unreadCount: Int = 0,
    
    val muted: Boolean = false,

    @SerializedName("member_count")
    val memberCount: Int = 0,

    @SerializedName("is_member")
    val isMember: Boolean = false,

    @SerializedName("muted_until")
    val mutedUntil: String? = null
)

enum class ChatType {
    @SerializedName("private")
    PRIVATE,
    
    @SerializedName("group")
    GROUP
}

enum class GroupType {
    @SerializedName("private_group")
    PRIVATE_GROUP,
    
    @SerializedName("public_group")
    PUBLIC_GROUP
}

data class ChatPermissions(
    @SerializedName("send_messages")
    val sendMessages: Boolean = true,
    
    @SerializedName("send_media")
    val sendMedia: Boolean = true,
    
    @SerializedName("add_users")
    val addUsers: Boolean = true,
    
    @SerializedName("pin_messages")
    val pinMessages: Boolean = false,
    
    @SerializedName("change_info")
    val changeInfo: Boolean = false
)

data class ChatMember(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("chat_id")
    val chatId: Int,
    
    @SerializedName("user_id")
    val userId: Int,
    
    @SerializedName("role")
    val role: MemberRole,
    
    @SerializedName("permissions")
    val permissions: ChatPermissions? = null,
    
    @SerializedName("joined_at")
    val joinedAt: String,
    
    @SerializedName("user")
    val user: User? = null
)

enum class MemberRole {
    @SerializedName("owner")
    OWNER,
    
    @SerializedName("admin")
    ADMIN,
    
    @SerializedName("member")
    MEMBER
}

data class ChatInviteLink(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("chat_id")
    val chatId: Int,
    
    @SerializedName("creator_id")
    val creatorId: Int,
    
    @SerializedName("code")
    val code: String,
    
    @SerializedName("is_revoked")
    val isRevoked: Boolean = false,
    
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    
    @SerializedName("usage_limit")
    val usageLimit: Int? = null,
    
    @SerializedName("usage_count")
    val usageCount: Int = 0,
    
    @SerializedName("created_at")
    val createdAt: String
)

data class MuteChatRequest(
    @SerializedName("duration")
    val duration: String? = null,
    
    @SerializedName("until")
    val until: String? = null
)
