package com.nexy.client.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.local.entity.UserEntity
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.UserStatus
import com.nexy.client.data.repository.message.MessageMappers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message synchronization manager.
 * 
 * Uses pts (points) to track which updates have been received.
 * When a gap is detected or on app startup, getDifference is called
 * to fetch any missed messages.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: NexyApiService,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val messageMappers: MessageMappers
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val PREFS_NAME = "nexy_sync_state"
        private const val KEY_PTS = "pts"
        private const val KEY_DATE = "date"
        private const val KEY_LAST_SYNC = "last_sync"
        
        // Sync if no updates for 15 minutes
        private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get locally stored pts value
     */
    fun getLocalPts(): Int {
        return prefs.getInt(KEY_PTS, 0)
    }
    
    /**
     * Update local pts value
     */
    fun setLocalPts(pts: Int) {
        if (pts > getLocalPts()) {
            prefs.edit().putInt(KEY_PTS, pts).apply()
            Log.d(TAG, "Updated local pts to $pts")
        }
    }
    
    /**
     * Get last sync timestamp
     */
    private fun getLastSync(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0)
    }
    
    /**
     * Update last sync timestamp
     */
    private fun setLastSync() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }
    
    /**
     * Check if sync is needed (no sync for 15+ minutes)
     */
    fun isSyncNeeded(): Boolean {
        val lastSync = getLastSync()
        return System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS
    }
    
    /**
     * Handle incoming pts from WebSocket message.
     * If there's a gap, trigger getDifference.
     */
    suspend fun handleIncomingPts(pts: Int, ptsCount: Int = 1): Boolean {
        val localPts = getLocalPts()
        
        return when {
            // Normal case: pts matches expected
            localPts + ptsCount == pts -> {
                setLocalPts(pts)
                false // No gap
            }
            // Already processed
            localPts + ptsCount > pts -> {
                Log.d(TAG, "Skipping already processed update: localPts=$localPts, pts=$pts")
                false
            }
            // Gap detected!
            else -> {
                Log.w(TAG, "Gap detected! localPts=$localPts, expected=${localPts + ptsCount}, got=$pts")
                true // Gap needs to be filled
            }
        }
    }
    
    /**
     * Fetch updates from server since local pts
     * Called on app startup and when gaps are detected.
     */
    suspend fun syncDifference(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val localPts = getLocalPts()
            Log.d(TAG, "Syncing difference from pts=$localPts")
            
            val response = apiService.getDifference(pts = localPts, limit = 500)
            
            if (!response.isSuccessful || response.body() == null) {
                Log.e(TAG, "getDifference failed: ${response.code()}")
                return@withContext Result.failure(Exception("Sync failed: ${response.code()}"))
            }
            
            val diff = response.body()!!
            var messagesAdded = 0
            
            // Process new messages
            diff.newMessages.forEach { message ->
                // Skip deleted messages
                if (message.isDeleted) {
                    messageDao.deleteMessage(message.id)
                    return@forEach
                }
                
                // Insert sender if available
                message.sender?.let { sender ->
                    userDao.insertUser(UserEntity(
                        id = sender.id,
                        username = sender.username,
                        email = sender.email,
                        displayName = sender.displayName,
                        avatarUrl = sender.avatarUrl,
                        status = sender.status?.name ?: UserStatus.OFFLINE.name,
                        bio = sender.bio,
                        publicKey = sender.publicKey,
                        createdAt = sender.createdAt
                    ))
                }
                
                val entity = messageMappers.modelToEntity(message)
                val existing = messageDao.getMessageById(message.id)
                
                if (existing != null) {
                    // Smart merge: preserve higher status
                    val existingStatus = try { MessageStatus.valueOf(existing.status) } catch (e: Exception) { MessageStatus.SENT }
                    val newStatus = try { MessageStatus.valueOf(entity.status) } catch (e: Exception) { MessageStatus.SENT }
                    
                    val finalStatus = if (existingStatus.ordinal > newStatus.ordinal) existing.status else entity.status
                    messageDao.updateMessage(entity.copy(status = finalStatus))
                } else {
                    messageDao.insertMessage(entity)
                    messagesAdded++
                }
            }
            
            // Handle edited messages
            diff.editedMessages?.forEach { message ->
                val entity = messageMappers.modelToEntity(message)
                messageDao.updateMessage(entity)
            }
            
            // Handle deleted messages
            diff.deletedMessages?.forEach { messageId ->
                messageDao.deleteMessage(messageId)
            }
            
            // Update local state
            setLocalPts(diff.state.pts)
            setLastSync()
            
            Log.d(TAG, "Sync complete: added $messagesAdded messages, new pts=${diff.state.pts}")
            Result.success(messagesAdded)
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Fetch channel-specific updates (for supergroups/channels)
     */
    suspend fun syncChannelDifference(chatId: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val channelPts = getChannelPts(chatId)
            Log.d(TAG, "Syncing channel $chatId from pts=$channelPts")
            
            val response = apiService.getChannelDifference(chatId, pts = channelPts, limit = 100)
            
            if (!response.isSuccessful || response.body() == null) {
                Log.e(TAG, "getChannelDifference failed: ${response.code()}")
                return@withContext Result.failure(Exception("Channel sync failed"))
            }
            
            val diff = response.body()!!
            var messagesAdded = 0
            
            diff.newMessages.forEach { message ->
                if (message.isDeleted) {
                    messageDao.deleteMessage(message.id)
                    return@forEach
                }
                
                message.sender?.let { sender ->
                    userDao.insertUser(UserEntity(
                        id = sender.id,
                        username = sender.username,
                        email = sender.email,
                        displayName = sender.displayName,
                        avatarUrl = sender.avatarUrl,
                        status = sender.status?.name ?: UserStatus.OFFLINE.name,
                        bio = sender.bio,
                        publicKey = sender.publicKey,
                        createdAt = sender.createdAt
                    ))
                }
                
                val entity = messageMappers.modelToEntity(message)
                val existing = messageDao.getMessageById(message.id)
                
                if (existing == null) {
                    messageDao.insertMessage(entity)
                    messagesAdded++
                } else {
                    messageDao.updateMessage(entity)
                }
            }
            
            diff.editedMessages?.forEach { message ->
                val entity = messageMappers.modelToEntity(message)
                messageDao.updateMessage(entity)
            }
            
            diff.deletedMessages?.forEach { messageId ->
                messageDao.deleteMessage(messageId)
            }
            
            setChannelPts(chatId, diff.pts)
            
            Log.d(TAG, "Channel $chatId sync complete: added $messagesAdded, new pts=${diff.pts}")
            
            // If not final, there are more messages
            if (!diff.final) {
                Log.d(TAG, "Channel sync not final, more messages available")
            }
            
            Result.success(messagesAdded)
            
        } catch (e: Exception) {
            Log.e(TAG, "Channel sync failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get channel-specific pts
     */
    private fun getChannelPts(chatId: Int): Int {
        return prefs.getInt("channel_pts_$chatId", 0)
    }
    
    /**
     * Set channel-specific pts
     */
    private fun setChannelPts(chatId: Int, pts: Int) {
        if (pts > getChannelPts(chatId)) {
            prefs.edit().putInt("channel_pts_$chatId", pts).apply()
        }
    }
    
    /**
     * Reset sync state (for logout or clear data)
     */
    fun reset() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Sync state reset")
    }
}
