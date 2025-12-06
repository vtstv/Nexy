package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatReadReceiptOperations @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val tokenManager: AuthTokenManager,
    private val webSocketClient: NexyWebSocketClient
) {
    companion object {
        private const val TAG = "ChatReadReceiptOps"
    }

    private val lastSentReadReceiptId = mutableMapOf<Int, String>()

    suspend fun markChatAsRead(chatId: Int) {
        withContext(Dispatchers.IO) {
            val currentUserId = tokenManager.getUserId()
            Log.d(TAG, "markChatAsRead: chatId=$chatId, currentUserId=$currentUserId")

            if (currentUserId == null) {
                Log.e(TAG, "markChatAsRead: currentUserId is null, cannot send read receipt")
                return@withContext
            }

            val lastIncomingMessage = messageDao.getLastIncomingMessage(chatId, currentUserId)
            Log.d(TAG, "markChatAsRead: lastIncomingMessage=${lastIncomingMessage?.id}, senderId=${lastIncomingMessage?.senderId}")

            if (lastIncomingMessage == null) {
                Log.d(TAG, "markChatAsRead: No incoming messages to mark as read")
                chatDao.markChatAsRead(chatId)
                return@withContext
            }

            val lastSentId = lastSentReadReceiptId[chatId]
            if (lastSentId == lastIncomingMessage.id) {
                Log.d(TAG, "markChatAsRead: Already sent read receipt for message ${lastIncomingMessage.id}, skipping")
                return@withContext
            }

            chatDao.markChatAsRead(chatId)

            Log.d(TAG, "markChatAsRead: Sending read receipt for message ${lastIncomingMessage.id}")
            webSocketClient.sendReadReceipt(lastIncomingMessage.id, chatId, currentUserId)

            lastSentReadReceiptId[chatId] = lastIncomingMessage.id
        }
    }
}
