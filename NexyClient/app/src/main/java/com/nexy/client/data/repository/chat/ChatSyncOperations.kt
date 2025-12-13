/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.data.repository.chat

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.models.Chat
import com.nexy.client.data.network.NetworkMonitor
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.websocket.ConnectionState
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSyncOperations @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao,
    private val chatMappers: ChatMappers,
    private val userRepository: UserRepository,
    private val networkMonitor: NetworkMonitor,
    private val webSocketClient: NexyWebSocketClient
) {
    companion object {
        private const val TAG = "ChatSyncOperations"
        // If server was unreachable, skip API calls for this duration
        private const val SERVER_UNREACHABLE_BACKOFF_MS = 15_000L
    }
    
    // Track when server became unreachable to avoid repeated timeout waits
    @Volatile
    private var serverUnreachableUntil: Long = 0L

    fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { chatMappers.entityToModel(it) }
        }
    }
    
    /**
     * Check if there are any chats in local database (synchronous).
     * Used to determine whether to show loading indicator.
     */
    suspend fun hasLocalChats(): Boolean {
        return chatDao.getAllChatsSync().isNotEmpty()
    }
    
    private fun isServerLikelyAvailable(): Boolean {
        // 1. Check if we have network connectivity
        if (!networkMonitor.isConnected.value) {
            Log.d(TAG, "No network connectivity, server unavailable")
            return false
        }
        // 2. Check if WebSocket is connected (best indicator of server availability)
        val wsState = webSocketClient.connectionState.value
        if (wsState == ConnectionState.CONNECTED) {
            // Server is definitely reachable, clear any backoff
            serverUnreachableUntil = 0L
            return true
        }
        // 3. Check if we're in backoff period from previous failure
        if (System.currentTimeMillis() < serverUnreachableUntil) {
            Log.d(TAG, "Server unreachable backoff active (${(serverUnreachableUntil - System.currentTimeMillis())/1000}s remaining)")
            return false
        }
        // 4. If WebSocket is connecting, server might be available soon
        if (wsState == ConnectionState.CONNECTING) {
            Log.d(TAG, "WebSocket connecting, will try API")
            return true
        }
        // 5. WebSocket disconnected but network available - likely server down
        Log.d(TAG, "WebSocket disconnected, assuming server unavailable")
        return false
    }
    
    private fun markServerUnreachable() {
        serverUnreachableUntil = System.currentTimeMillis() + SERVER_UNREACHABLE_BACKOFF_MS
        Log.w(TAG, "Marked server as unreachable for ${SERVER_UNREACHABLE_BACKOFF_MS/1000}s")
    }

    suspend fun refreshChats(): Result<List<Chat>> {
        return withContext(Dispatchers.IO) {
            // Quick check if server is likely available
            if (!isServerLikelyAvailable()) {
                Log.w(TAG, "refreshChats: server likely unavailable, returning local chats")
                val localChats = chatDao.getAllChatsSync().map { chatMappers.entityToModel(it) }
                return@withContext Result.success(localChats)
            }
            
            try {
                val response = apiService.getChats()
                if (response.isSuccessful && response.body() != null) {
                    val chats = response.body()!!
                    
                    val existingChatsMap = chatDao.getAllChatsSync().associateBy { it.id }
                    val serverChatIds = chats.map { it.id }.toSet()
                    
                    // Delete chats that exist locally but not on server
                    val chatsToDelete = existingChatsMap.keys.filter { !serverChatIds.contains(it) }
                    chatsToDelete.forEach { chatId ->
                        chatDao.deleteChatById(chatId)
                    }
                    
                    val updates = ArrayList<ChatEntity>()
                    val inserts = ArrayList<ChatEntity>()
                    
                    chats.forEach { chat ->
                        val existingEntity = existingChatsMap[chat.id]
                        // Pass existingEntity to preserve local-only field (isHidden)
                        val newEntity = chatMappers.modelToEntity(chat, existingEntity)
                        
                        if (existingEntity != null) {
                            val mergedLastMessageId = chat.lastMessage?.id ?: existingEntity.lastMessageId
                            // Use server's values - they are the source of truth
                            Log.w(TAG, "refreshChats: chat ${chat.id} server unreadCount=${chat.unreadCount}, isPinned=${chat.isPinned}, firstUnreadMessageId=${chat.firstUnreadMessageId}")
                            
                            updates.add(newEntity.copy(
                                lastMessageId = mergedLastMessageId,
                                unreadCount = chat.unreadCount,
                                lastReadMessageId = chat.lastReadMessageId,
                                firstUnreadMessageId = chat.firstUnreadMessageId,
                                muted = newEntity.muted,
                                // isPinned and pinnedAt now come from server
                                // Only preserve isHidden as local-only field
                                isHidden = existingEntity.isHidden
                            ))
                        } else {
                            inserts.add(newEntity)
                        }
                    }
                    
                    if (updates.isNotEmpty()) {
                        chatDao.updateChats(updates)
                    }
                    if (inserts.isNotEmpty()) {
                        chatDao.insertChats(inserts)
                    }
                    
                    // Pre-fetch participants for private chats to ensure names are available
                    // We do this after updating chats so the UI can show the list immediately, 
                    // even if names are temporarily missing (though they will update as users are fetched)
                    try {
                        val participantIds = chats
                            .filter { it.type == com.nexy.client.data.models.ChatType.PRIVATE }
                            .flatMap { it.participantIds ?: emptyList() }
                            .toSet()
                        
                        // Use supervisorScope to fetch users in parallel
                        supervisorScope {
                            participantIds.map { userId ->
                                async {
                                    try {
                                        userRepository.getUserById(userId)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to fetch participant $userId", e)
                                    }
                                }
                            }.awaitAll()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pre-fetching participants", e)
                    }

                    Result.success(chats)
                } else {
                    // API failed, return local chats
                    Log.w(TAG, "refreshChats: API failed, returning local chats")
                    val localChats = chatDao.getAllChatsSync().map { chatMappers.entityToModel(it) }
                    Result.success(localChats)
                }
            } catch (e: Exception) {
                // Network error - mark server as unreachable and return local chats
                if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) {
                    markServerUnreachable()
                }
                Log.e(TAG, "refreshChats: exception, returning local chats", e)
                val localChats = chatDao.getAllChatsSync().map { chatMappers.entityToModel(it) }
                Result.success(localChats)
            }
        }
    }
    
    suspend fun getChatById(chatId: Int): Result<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "getChatById: chatId=$chatId, serverAvailable=${isServerLikelyAvailable()}")
                
                // Check local cache first
                val localChat = chatDao.getChatById(chatId)
                
                // If server likely unavailable, return local immediately
                if (!isServerLikelyAvailable()) {
                    return@withContext if (localChat != null) {
                        val chat = chatMappers.entityToModel(localChat)
                        Log.w(TAG, "getChatById: returning cached chat (server unavailable)")
                        Result.success(chat)
                    } else {
                        Result.failure(Exception("Chat not found (offline)"))
                    }
                }
                
                // Online: Try API for fresh data
                val response = apiService.getChatById(chatId)
                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    
                    // Pre-fetch participants to ensure avatars are available
                    chat.participantIds?.forEach { userId ->
                        try {
                            userRepository.getUserById(userId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch participant $userId", e)
                        }
                    }
                    
                    // Use server values - they are the source of truth
                    Log.w(TAG, "getChatById: serverUnreadCount=${chat.unreadCount}, isPinned=${chat.isPinned}, firstUnreadMessageId=${chat.firstUnreadMessageId}")
                    
                    val existingChat = chatDao.getChatById(chatId)
                    val updatedEntity = chatMappers.modelToEntity(chat, existingChat).copy(
                        lastMessageId = existingChat?.lastMessageId ?: chat.lastMessage?.id,
                        // isPinned and pinnedAt now come from server via modelToEntity
                        // Only preserve isHidden as local-only field
                        isHidden = existingChat?.isHidden ?: false
                    )
                    
                    if (existingChat != null) {
                        chatDao.updateChat(updatedEntity)
                    } else {
                        chatDao.insertChat(updatedEntity)
                    }
                    
                    Log.w(TAG, "getChatById: returning chat with unreadCount=${chat.unreadCount}, firstUnreadMessageId=${chat.firstUnreadMessageId}")
                    Result.success(chat)
                } else {
                    // Fallback to local if API fails
                    if (localChat != null) {
                        val chat = chatMappers.entityToModel(localChat)
                        // Ensure participants are cached for avatars even if offline
                        chat.participantIds?.forEach { userId ->
                            try {
                                userRepository.getUserById(userId)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to fetch participant $userId", e)
                            }
                        }
                        Result.success(chat)
                    } else {
                        Result.failure(Exception("Chat not found"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting chat by ID: $chatId", e)
                // Fallback to local on error
                val localChat = chatDao.getChatById(chatId)
                if (localChat != null) {
                    val chat = chatMappers.entityToModel(localChat)
                    Result.success(chat)
                } else {
                    Result.failure(e)
                }
            }
        }
    }
}
