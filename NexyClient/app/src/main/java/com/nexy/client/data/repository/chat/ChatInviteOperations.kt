/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.data.repository.chat

import com.nexy.client.data.api.AddMemberRequest
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.models.CreateInviteLinkRequest
import com.nexy.client.data.models.CreateInviteRequest
import com.nexy.client.data.models.InviteLink
import com.nexy.client.data.models.JoinChatResponse
import com.nexy.client.data.models.UseInviteRequest
import com.nexy.client.data.models.ValidateInviteRequest
import com.nexy.client.data.models.Chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatInviteOperations @Inject constructor(
    private val apiService: NexyApiService,
    private val chatDao: ChatDao,
    private val tokenManager: AuthTokenManager,
    private val chatMappers: ChatMappers
) {
    
    suspend fun createInviteLink(chatId: Int, maxUses: Int = 1, expiresAt: Long? = null): Result<InviteLink> {
        return withContext(Dispatchers.IO) {
            try {
                val request = CreateInviteRequest(chatId, maxUses, expiresAt)
                val response = apiService.createInviteLink(request)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to create invite link"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun validateInviteCode(code: String): Result<InviteLink> {
        return withContext(Dispatchers.IO) {
            try {
                val request = ValidateInviteRequest(code)
                val response = apiService.validateInviteCode(request)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to validate invite code"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun joinByInviteCode(code: String): Result<JoinChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = UseInviteRequest(code)
                val response = apiService.useInviteCode(request)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    result.chat?.let { chat ->
                        val existingChat = chatDao.getChatById(chat.id)
                        val newEntity = chatMappers.modelToEntity(chat)
                        val finalEntity = if (existingChat != null) {
                            newEntity.copy(
                                lastMessageId = existingChat.lastMessageId,
                                unreadCount = existingChat.unreadCount,
                                muted = existingChat.muted
                            )
                        } else {
                            newEntity
                        }
                        chatDao.insertChat(finalEntity)
                    }
                    Result.success(result)
                } else {
                    Result.failure(Exception("Failed to join chat"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun removeMember(groupId: Int, userId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.removeMember(groupId, userId)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to remove member"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addGroupMember(groupId: Int, userId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.addGroupMember(groupId, AddMemberRequest(userId))
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to add member"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createGroupInviteLink(groupId: Int, usageLimit: Int? = null, expiresIn: Int? = null): Result<InviteLink> {
        return withContext(Dispatchers.IO) {
            try {
                val request = CreateInviteLinkRequest(usageLimit, expiresIn)
                val response = apiService.createGroupInviteLink(groupId, request)
                if (response.isSuccessful && response.body() != null) {
                    val chatInviteLink = response.body()!!
                    val inviteLink = InviteLink(
                        code = chatInviteLink.code,
                        chatId = chatInviteLink.chatId,
                        createdBy = chatInviteLink.creatorId,
                        maxUses = chatInviteLink.usageLimit,
                        expiresAt = null
                    )
                    Result.success(inviteLink)
                } else {
                    Result.failure(Exception("Failed to create group invite link"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun joinPublicGroup(groupId: Int): Result<Chat> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.joinPublicGroup(groupId)
                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    chatDao.insertChat(chatMappers.modelToEntity(chat))
                    Result.success(chat)
                } else {
                    Result.failure(Exception("Failed to join group"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun transferOwnership(groupId: Int, newOwnerId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = com.nexy.client.data.api.TransferOwnershipRequest(newOwnerId)
                val response = apiService.transferOwnership(groupId, request)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to transfer ownership"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
