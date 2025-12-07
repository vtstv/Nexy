package com.nexy.client.data.websocket.handlers

import android.util.Log
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.PendingMessageDao
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.nexy.NexyMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageStateHandler @Inject constructor(
    private val messageDao: MessageDao,
    private val pendingMessageDao: PendingMessageDao,
    private val tokenManager: AuthTokenManager
) {
    companion object {
        private const val TAG = "MessageStateHandler"
    }

    suspend fun handleAck(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["message_id"] as? String ?: return
        val status = body["status"] as? String
        val serverId = (body["server_id"] as? Double)?.toInt()
        
        if (status == "ok") {
            Log.d(TAG, "Received ACK for message: $messageId, serverId=$serverId")
            pendingMessageDao.delete(messageId)
            
            if (serverId != null && serverId > 0) {
                messageDao.updateMessageServerIdAndStatus(messageId, serverId, MessageStatus.SENT.name)
                Log.d(TAG, "Message $messageId marked as SENT with serverId=$serverId")
            } else {
                messageDao.updateMessageStatus(messageId, MessageStatus.SENT.name)
                Log.d(TAG, "Message $messageId marked as SENT (no serverId)")
            }
        }
    }

    suspend fun handleReadReceipt(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = (body["message_id"] as? String) ?: (body["read_message_id"] as? String) ?: return
        
        Log.d(TAG, "Received read receipt for message: $messageId")
        
        val message = messageDao.getMessageById(messageId)
        if (message != null) {
            val currentUserId = tokenManager.getUserId()
            Log.d(TAG, "Processing Read Receipt: msgId=$messageId, chatId=${message.chatId}, timestamp=${message.timestamp}, msgSender=${message.senderId}, currentUserId=$currentUserId")
            
            if (currentUserId != null) {
                if (message.senderId == currentUserId) {
                    Log.d(TAG, "Executing markMessagesAsReadUpTo for user $currentUserId")
                    val updatedCount = messageDao.markMessagesAsReadUpTo(message.chatId, message.timestamp, currentUserId)
                    Log.d(TAG, "Marked $updatedCount messages as READ")
                    
                    if (updatedCount == 0) {
                        Log.w(TAG, "markMessagesAsReadUpTo updated 0 rows! Force updating current message $messageId")
                        messageDao.updateMessageStatus(messageId, MessageStatus.READ.name)
                    }
                } else {
                    Log.d(TAG, "Skipping markMessagesAsReadUpTo: Message sender ${message.senderId} != current user $currentUserId")
                }
            } else {
                Log.e(TAG, "Cannot mark as read: currentUserId is null")
            }
        } else {
            Log.w(TAG, "Message $messageId not found for read receipt, updating status only if exists")
            messageDao.updateMessageStatus(messageId, MessageStatus.READ.name)
        }
    }

    suspend fun handleEditMessage(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["message_id"] as? String ?: return
        val content = body["content"] as? String ?: return
        
        Log.d(TAG, "Handling edit message: messageId=$messageId, newContent=$content")
        messageDao.updateMessageContent(messageId, content, true)
    }

    suspend fun handleDeleteMessage(nexyMessage: NexyMessage) {
        val body = nexyMessage.body ?: return
        val messageId = body["message_id"] as? String ?: return

        Log.d(TAG, "Handling delete message: messageId=$messageId")
        messageDao.deleteMessage(messageId)
    }
}
