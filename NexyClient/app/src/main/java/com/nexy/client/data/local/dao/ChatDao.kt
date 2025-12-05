package com.nexy.client.data.local.dao

import androidx.room.*
import com.nexy.client.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE isHidden = 0 ORDER BY isPinned DESC, pinnedAt DESC, updatedAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>
    
    @Query("SELECT * FROM chats WHERE isHidden = 0 ORDER BY isPinned DESC, pinnedAt DESC, updatedAt DESC")
    suspend fun getAllChatsSync(): List<ChatEntity>
    
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: Int): ChatEntity?
    
    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun getChatByIdFlow(chatId: Int): Flow<ChatEntity?>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChat(chat: ChatEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChats(chats: List<ChatEntity>): List<Long>
    
    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Update
    suspend fun updateChats(chats: List<ChatEntity>)
    
    @Query("UPDATE chats SET unreadCount = :count WHERE id = :chatId")
    suspend fun updateUnreadCount(chatId: Int, count: Int)
    
    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markChatAsRead(chatId: Int)
    
    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE id = :chatId")
    suspend fun incrementUnreadCount(chatId: Int)

    @Query("UPDATE chats SET lastMessageId = :messageId, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: Int, messageId: String, timestamp: Long)
    
    @Query("UPDATE chats SET isPinned = :isPinned, pinnedAt = :pinnedAt WHERE id = :chatId")
    suspend fun setPinned(chatId: Int, isPinned: Boolean, pinnedAt: Long)
    
    @Query("UPDATE chats SET isHidden = :isHidden WHERE id = :chatId")
    suspend fun setHidden(chatId: Int, isHidden: Boolean)
    
    @Delete
    suspend fun deleteChat(chat: ChatEntity)
    
    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Int)
}
