package com.nexy.client.data.repository

import android.content.Context
import android.net.Uri
import com.nexy.client.data.models.*
import com.nexy.client.data.repository.chat.ChatOperations
import com.nexy.client.data.repository.file.FileOperations
import com.nexy.client.data.repository.message.MessageOperations
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatOperations: ChatOperations,
    private val messageOperations: MessageOperations,
    private val fileOperations: FileOperations
) {
    
    // ===== CHAT OPERATIONS DELEGATION =====
    
    fun getAllChats(): Flow<List<Chat>> = chatOperations.getAllChats()
    
    suspend fun refreshChats(): Result<List<Chat>> = chatOperations.refreshChats()
    
    suspend fun getChatById(chatId: Int): Result<Chat> = chatOperations.getChatById(chatId)
    
    suspend fun getChatInfo(chatId: Int): ChatInfo? = chatOperations.getChatInfo(chatId)
    
    suspend fun getOrCreateSavedMessages(): Result<Chat> = chatOperations.getOrCreateSavedMessages()
    
    suspend fun markChatAsRead(chatId: Int) = chatOperations.markChatAsRead(chatId)
    
    suspend fun clearChatMessages(chatId: Int): Result<Unit> = chatOperations.clearChatMessages(chatId)
    
    suspend fun deleteChat(chatId: Int): Result<Unit> = chatOperations.deleteChat(chatId)
    
    // ===== MESSAGE OPERATIONS DELEGATION =====
    
    fun getMessagesByChatId(chatId: Int): Flow<List<Message>> = messageOperations.getMessagesByChatId(chatId)
    
    suspend fun getLastMessageForChat(chatId: Int): Message? = messageOperations.getLastMessageForChat(chatId)
    
    suspend fun loadMessages(chatId: Int, limit: Int = 50, offset: Int = 0): Result<List<Message>> = 
        messageOperations.loadMessages(chatId, limit, offset)
    
    suspend fun sendMessage(
        chatId: Int, 
        senderId: Int, 
        content: String, 
        type: MessageType = MessageType.TEXT,
        recipientUserId: Int? = null
    ): Result<Message> = messageOperations.sendMessage(chatId, senderId, content, type, recipientUserId)
    
    suspend fun deleteMessage(messageId: String): Result<Unit> = messageOperations.deleteMessage(messageId)
    
    // ===== FILE OPERATIONS DELEGATION =====
    
    suspend fun sendFileMessage(
        chatId: Int,
        senderId: Int,
        context: Context,
        fileUri: Uri,
        fileName: String
    ): Result<Message> = fileOperations.sendFileMessage(chatId, senderId, context, fileUri, fileName)
    
    suspend fun downloadFile(fileId: String, context: Context, fileName: String): Result<Uri> =
        fileOperations.downloadFile(fileId, context, fileName)
    
    // ===== INVITE/JOIN OPERATIONS =====
    // These remain here as they're not yet extracted to separate modules
    
    suspend fun createInviteLink(chatId: Int, maxUses: Int = 1, expiresAt: Long? = null): Result<InviteLink> {
        return chatOperations.createInviteLink(chatId, maxUses, expiresAt)
    }
    
    suspend fun validateInviteCode(code: String): Result<InviteLink> {
        return chatOperations.validateInviteCode(code)
    }
    
    suspend fun joinByInviteCode(code: String): Result<JoinChatResponse> {
        return chatOperations.joinByInviteCode(code)
    }
    
    suspend fun leaveGroup(chatId: Int): Result<Unit> {
        return chatOperations.leaveGroup(chatId)
    }
}

// ChatInfo data class for UI
data class ChatInfo(
    val id: Int,
    val name: String,
    val type: ChatType,
    val avatarUrl: String? = null,
    val participantIds: List<Int>? = null
)
