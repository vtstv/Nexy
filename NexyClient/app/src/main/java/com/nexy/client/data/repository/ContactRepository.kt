package com.nexy.client.data.repository

import android.util.Log
import com.nexy.client.data.api.CreateChatRequest
import com.nexy.client.data.api.CreateGroupChatRequest
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao
) {
    private val _contactsUpdateTrigger = MutableSharedFlow<Unit>(replay = 0)
    val contactsUpdateTrigger = _contactsUpdateTrigger.asSharedFlow()
    
    /**
     * Add a user to contacts
     */
    suspend fun addContact(userId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.addContact(AddContactRequest(userId))
            if (response.isSuccessful) {
                _contactsUpdateTrigger.emit(Unit)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add contact: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get list of all contacts
     */
    suspend fun getContacts(): Result<List<ContactWithUser>> = withContext(Dispatchers.IO) {
        try {
            Log.d("ContactRepository", "Fetching contacts from API...")
            val response = apiService.getContacts()
            Log.d("ContactRepository", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
            Log.d("ContactRepository", "Response body is null: ${response.body() == null}")
            
            if (response.isSuccessful) {
                val contacts = response.body() ?: emptyList()
                Log.d("ContactRepository", "Received ${contacts.size} contacts")
                contacts.forEachIndexed { index, contact ->
                    Log.d("ContactRepository", "Contact $index: id=${contact.id}, userId=${contact.userId}, contactUserId=${contact.contactUserId}, username=${contact.contactUser.username}")
                }
                Result.success(contacts)
            } else {
                val errorMsg = "Failed to get contacts: ${response.code()} - ${response.errorBody()?.string()}"
                Log.e("ContactRepository", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("ContactRepository", "Exception getting contacts", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if a user is in contacts
     */
    suspend fun checkContactStatus(userId: Int): Result<ContactStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkContactStatus(userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to check contact status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update contact status (block/unblock)
     */
    suspend fun updateContactStatus(contactId: Int, status: ContactStatus): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.updateContactStatus(
                contactId,
                UpdateContactStatusRequest(status)
            )
            if (response.isSuccessful) {
                _contactsUpdateTrigger.emit(Unit)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update contact: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove a contact
     */
    suspend fun deleteContact(contactUserId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deleteContact(contactUserId)
            if (response.isSuccessful) {
                _contactsUpdateTrigger.emit(Unit)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete contact: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create or get existing private chat with user
     */
    suspend fun createPrivateChat(recipientId: Int): Result<Chat> = withContext(Dispatchers.IO) {
        try {
            Log.d("ContactRepository", "Creating private chat with user: $recipientId")
            val response = apiService.createPrivateChat(CreateChatRequest(recipientId))
            Log.d("ContactRepository", "Create chat response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val chat = response.body()!!
                Log.d("ContactRepository", "Chat created: id=${chat.id}, type=${chat.type}, createdAt=${chat.createdAt}")
                
                // Save chat to local database
                chatDao.insertChat(chat.toEntity())
                Log.d("ContactRepository", "Chat saved to local DB with id=${chat.id}")
                
                Result.success(chat)
            } else {
                val errorMsg = "Failed to create chat: ${response.code()} - ${response.errorBody()?.string()}"
                Log.e("ContactRepository", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("ContactRepository", "Exception creating chat", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create group chat with multiple members
     */
    suspend fun createGroupChat(name: String, memberIds: List<Int>): Result<Chat> = withContext(Dispatchers.IO) {
        try {
            Log.d("ContactRepository", "Creating group chat: $name with ${memberIds.size} members")
            val response = apiService.createGroupChat(CreateGroupChatRequest(name, memberIds))
            Log.d("ContactRepository", "Create group response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val chat = response.body()!!
                Log.d("ContactRepository", "Group created: id=${chat.id}, name=${chat.name}, type=${chat.type}")
                
                // Save chat to local database
                chatDao.insertChat(chat.toEntity())
                Log.d("ContactRepository", "Group saved to local DB with id=${chat.id}")
                
                Result.success(chat)
            } else {
                val errorMsg = "Failed to create group: ${response.code()} - ${response.errorBody()?.string()}"
                Log.e("ContactRepository", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("ContactRepository", "Exception creating group", e)
            Result.failure(e)
        }
    }
    
    private fun Chat.toEntity() = ChatEntity(
        id = id,
        type = type.name,
        name = name,
        avatarUrl = avatarUrl,
        participantIds = participantIds?.joinToString(",") ?: "",
        lastMessageId = lastMessage?.id,
        unreadCount = unreadCount,
        createdAt = parseTimestamp(createdAt),
        updatedAt = parseTimestamp(updatedAt),
        muted = muted
    )
    
    private fun parseTimestamp(timestamp: String?): Long {
        return try {
            timestamp?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                    .parse(it)?.time
            } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
