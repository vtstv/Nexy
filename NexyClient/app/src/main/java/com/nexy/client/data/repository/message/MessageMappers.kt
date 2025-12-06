package com.nexy.client.data.repository.message

import com.nexy.client.data.local.entity.MessageEntity
import com.nexy.client.data.local.entity.UserEntity
import com.nexy.client.data.local.models.MessageWithSender
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.models.User
import com.nexy.client.data.models.UserStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageMappers @Inject constructor() {

    fun modelToEntity(message: Message) = MessageEntity(
        id = message.id,
        serverId = message.serverId,
        chatId = message.chatId,
        senderId = message.senderId,
        content = message.content,
        type = message.type.name,
        timestamp = parseTimestamp(message.timestamp),
        status = message.status?.name ?: MessageStatus.SENT.name,
        mediaUrl = message.mediaUrl,
        mediaThumbnail = message.mediaType,
        duration = message.duration,
        replyToId = message.replyToId?.toString(),
        isEdited = message.isEdited,
        isSyncedToServer = true
    )

    fun entityToModel(entity: MessageEntity) = Message(
        id = entity.id,
        serverId = entity.serverId,
        chatId = entity.chatId,
        senderId = entity.senderId,
        content = entity.content,
        type = MessageType.valueOf(entity.type.uppercase()),
        timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(entity.timestamp)),
        status = MessageStatus.valueOf(entity.status.uppercase()),
        mediaUrl = entity.mediaUrl,
        mediaType = entity.mediaThumbnail,
        duration = entity.duration,
        replyToId = entity.replyToId?.toIntOrNull(),
        isEdited = entity.isEdited,
        encrypted = false,
        encryptionAlgorithm = null
    )

    fun messageWithSenderToModel(messageWithSender: MessageWithSender): Message {
        val message = entityToModel(messageWithSender.message)
        // If sender is available in relation, use it.
        // If not, we might want to try to fetch it or it might be null (e.g. system message)
        return if (messageWithSender.sender != null) {
            message.copy(sender = messageWithSender.sender.toModel())
        } else {
            message
        }
    }

    private fun UserEntity.toModel() = User(
        id = id,
        username = username,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        status = UserStatus.valueOf(status),
        bio = bio,
        publicKey = publicKey,
        createdAt = createdAt
    )

    private fun parseTimestamp(timestamp: String?): Long {
        return try {
            if (timestamp == null) return System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val withoutMillis = timestamp.substringBefore('.')
            sdf.parse(withoutMillis)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
