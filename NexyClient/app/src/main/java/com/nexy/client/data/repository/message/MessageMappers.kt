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
        chatId = message.chatId,
        senderId = message.senderId,
        content = message.content,
        type = message.type.name,
        timestamp = parseTimestamp(message.timestamp),
        status = message.status?.name ?: MessageStatus.SENT.name,
        mediaUrl = message.mediaUrl,
        mediaThumbnail = message.mediaType,
        replyToId = message.replyToId?.toString(),
        isSyncedToServer = true
    )

    fun entityToModel(entity: MessageEntity) = Message(
        id = entity.id,
        chatId = entity.chatId,
        senderId = entity.senderId,
        content = entity.content,
        type = MessageType.valueOf(entity.type.uppercase()),
        timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(entity.timestamp)),
        status = MessageStatus.valueOf(entity.status.uppercase()),
        mediaUrl = entity.mediaUrl,
        mediaType = entity.mediaThumbnail,
        replyToId = entity.replyToId?.toIntOrNull(),
        isEdited = false,
        encrypted = false,
        encryptionAlgorithm = null
    )

    fun messageWithSenderToModel(messageWithSender: MessageWithSender): Message {
        val message = entityToModel(messageWithSender.message)
        return message.copy(
            sender = messageWithSender.sender?.toModel()
        )
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
            val withoutMillis = timestamp.substringBefore('.')
            sdf.parse(withoutMillis)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
