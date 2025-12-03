package com.nexy.client.data.models.nexy

import com.google.gson.annotations.SerializedName

data class NexyMessage(
    @SerializedName("header")
    val header: NexyHeader,
    
    @SerializedName("body")
    val body: Map<String, Any>? = null
)

data class NexyHeader(
    @SerializedName("version")
    val version: String = "1.0",
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("message_id")
    val messageId: String,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("sender_id")
    val senderId: Int? = null,
    
    @SerializedName("recipient_id")
    val recipientId: Int? = null,
    
    @SerializedName("chat_id")
    val chatId: Int? = null
)

data class NexyTextMessage(
    val messageId: String,
    val chatId: Int,
    val senderId: Int,
    val content: String,
    val timestamp: Long,
    val replyToId: Int? = null
)

data class NexyMediaMessage(
    @SerializedName("type")
    val type: String = "media",
    
    @SerializedName("messageId")
    val messageId: String,
    
    @SerializedName("chatId")
    val chatId: Int,
    
    @SerializedName("senderId")
    val senderId: Int,
    
    @SerializedName("mediaType")
    val mediaType: String,
    
    @SerializedName("mediaUrl")
    val mediaUrl: String,
    
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    
    @SerializedName("caption")
    val caption: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: Long
)

data class NexyTypingIndicator(
    @SerializedName("type")
    val type: String = "typing",
    
    @SerializedName("chatId")
    val chatId: Int,
    
    @SerializedName("userId")
    val userId: Int,
    
    @SerializedName("isTyping")
    val isTyping: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class NexyReadReceipt(
    @SerializedName("type")
    val type: String = "read_receipt",
    
    @SerializedName("messageId")
    val messageId: String,
    
    @SerializedName("chatId")
    val chatId: Int,
    
    @SerializedName("userId")
    val userId: Int,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class NexyDeliveryReceipt(
    @SerializedName("type")
    val type: String = "delivery_receipt",
    
    @SerializedName("messageId")
    val messageId: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class NexyStatusUpdate(
    @SerializedName("type")
    val type: String = "status",
    
    @SerializedName("userId")
    val userId: Int,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class NexyHeartbeat(
    @SerializedName("type")
    val type: String = "heartbeat",
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class NexyAck(
    @SerializedName("type")
    val type: String = "ack",
    
    @SerializedName("messageId")
    val messageId: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class NexySignalingMessage(
    @SerializedName("type")
    val type: String = "signaling",
    
    @SerializedName("callId")
    val callId: String,
    
    @SerializedName("from")
    val from: Int,
    
    @SerializedName("to")
    val to: Int,
    
    @SerializedName("signalType")
    val signalType: String,
    
    @SerializedName("payload")
    val payload: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
