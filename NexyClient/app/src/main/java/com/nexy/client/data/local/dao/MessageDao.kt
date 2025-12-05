package com.nexy.client.data.local.dao

import androidx.room.*
import com.nexy.client.data.local.entity.MessageEntity
import com.nexy.client.data.local.models.MessageWithSender
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: Int): Flow<List<MessageWithSender>>
    
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isSyncedToServer = 0")
    suspend fun getUnsyncedMessages(chatId: Int): List<MessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)
    
    @Query("UPDATE messages SET status = 'READ' WHERE chatId = :chatId AND timestamp <= :timestamp AND status != 'READ' AND senderId = :currentUserId")
    suspend fun markMessagesAsReadUpTo(chatId: Int, timestamp: Long, currentUserId: Int): Int

    @Query("UPDATE messages SET content = :content, isEdited = :isEdited WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, content: String, isEdited: Boolean)

    @Query("UPDATE messages SET isSyncedToServer = 1 WHERE id = :messageId")
    suspend fun markMessageAsSynced(messageId: String)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)
    
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: Int)
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatId: Int): MessageEntity?
    
    // Get last incoming (not from current user) message - for read receipts
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderId != :currentUserId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastIncomingMessage(chatId: Int, currentUserId: Int): MessageEntity?
}
