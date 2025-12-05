package com.nexy.client.data.repository.chat

import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMappers @Inject constructor() {

    fun modelToEntity(chat: Chat) = ChatEntity(
        id = chat.id,
        type = chat.type.name,
        name = chat.name,
        avatarUrl = chat.avatarUrl,
        participantIds = chat.participantIds?.joinToString(",") ?: "",
        lastMessageId = chat.lastMessage?.id,
        unreadCount = chat.unreadCount,
        createdAt = parseTimestamp(chat.createdAt),
        updatedAt = parseTimestamp(chat.updatedAt),
        muted = chat.muted || isMuted(chat.mutedUntil),
        lastReadMessageId = chat.lastReadMessageId,
        firstUnreadMessageId = chat.firstUnreadMessageId
    )

    fun entityToModel(entity: ChatEntity) = Chat(
        id = entity.id,
        type = ChatType.valueOf(entity.type.uppercase()),
        name = entity.name,
        avatarUrl = entity.avatarUrl,
        participantIds = if (entity.participantIds.isEmpty()) emptyList() else entity.participantIds.split(",").mapNotNull { it.toIntOrNull() },
        lastMessage = null,
        unreadCount = entity.unreadCount,
        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(entity.createdAt)),
        updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(entity.updatedAt)),
        muted = entity.muted,
        lastReadMessageId = entity.lastReadMessageId,
        firstUnreadMessageId = entity.firstUnreadMessageId
    )

    private fun parseTimestamp(timestamp: String?): Long {
        return try {
            if (timestamp == null) return System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val withoutMillis = timestamp.substringBefore('.')
            sdf.parse(withoutMillis)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    private fun isMuted(mutedUntil: String?): Boolean {
        if (mutedUntil == null) return false
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val withoutMillis = mutedUntil.substringBefore('.')
            val cleanTime = withoutMillis.removeSuffix("Z")
            val mutedUntilTime = sdf.parse(cleanTime)?.time ?: return false
            System.currentTimeMillis() < mutedUntilTime
        } catch (e: Exception) {
            false
        }
    }
}
