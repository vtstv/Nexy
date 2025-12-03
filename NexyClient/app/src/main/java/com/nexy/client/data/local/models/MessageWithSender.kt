package com.nexy.client.data.local.models

import androidx.room.Embedded
import androidx.room.Relation
import com.nexy.client.data.local.entity.MessageEntity
import com.nexy.client.data.local.entity.UserEntity

data class MessageWithSender(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "senderId",
        entityColumn = "id"
    )
    val sender: UserEntity?
)
