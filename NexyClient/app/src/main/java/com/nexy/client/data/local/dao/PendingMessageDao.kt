package com.nexy.client.data.local.dao

import androidx.room.*
import com.nexy.client.data.local.entity.PendingMessageEntity
import com.nexy.client.data.local.entity.SendState
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages ORDER BY createdAt ASC")
    fun getAllPending(): Flow<List<PendingMessageEntity>>
    
    @Query("SELECT * FROM pending_messages WHERE sendState = :state ORDER BY createdAt ASC")
    suspend fun getByState(state: String): List<PendingMessageEntity>
    
    // Include SENDING state in case app was killed during send
    @Query("SELECT * FROM pending_messages WHERE sendState IN ('QUEUED', 'SENDING', 'ERROR') AND retryCount < maxRetries ORDER BY createdAt ASC")
    suspend fun getReadyToSend(): List<PendingMessageEntity>
    
    @Query("SELECT * FROM pending_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getByChatId(chatId: Int): List<PendingMessageEntity>
    
    @Query("SELECT * FROM pending_messages WHERE messageId = :messageId")
    suspend fun getByMessageId(messageId: String): PendingMessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingMessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<PendingMessageEntity>)
    
    @Update
    suspend fun update(message: PendingMessageEntity)
    
    @Query("UPDATE pending_messages SET sendState = :state, lastAttemptAt = :timestamp WHERE messageId = :messageId")
    suspend fun updateState(messageId: String, state: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE pending_messages SET sendState = :state, retryCount = retryCount + 1, lastAttemptAt = :timestamp, errorMessage = :error WHERE messageId = :messageId")
    suspend fun markFailed(messageId: String, state: String = SendState.ERROR.name, timestamp: Long = System.currentTimeMillis(), error: String?)
    
    @Query("DELETE FROM pending_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: String)
    
    @Query("DELETE FROM pending_messages WHERE chatId = :chatId")
    suspend fun deleteByChatId(chatId: Int)
    
    @Query("DELETE FROM pending_messages WHERE sendState = 'SENT'")
    suspend fun clearSent()
    
    @Query("SELECT COUNT(*) FROM pending_messages WHERE sendState IN ('QUEUED', 'SENDING')")
    fun getPendingCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM pending_messages WHERE sendState = 'ERROR'")
    fun getFailedCount(): Flow<Int>
}
