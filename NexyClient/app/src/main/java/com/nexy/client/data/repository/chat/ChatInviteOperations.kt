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
import com.nexy.client.data.models.InvitePreviewResponse
import com.nexy.client.data.models.JoinByInviteRequest
import com.nexy.client.data.models.JoinChatResponse
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
    
    suspend fun validateGroupInvite(code: String): Result<InvitePreviewResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = JoinByInviteRequest(code)
                val response = apiService.validateGroupInvite(request)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to validate invite"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun joinByInviteCode(code: String): Result<JoinChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = JoinByInviteRequest(code)
                val response = apiService.joinGroupByInvite(request)
                if (response.isSuccessful && response.body() != null) {
                    val chat = response.body()!!
                    // Save chat to local database
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
                    Result.success(JoinChatResponse(chatId = chat.id, chat = chat))
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Failed to join group"
                    Result.failure(Exception(errorBody))
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
    
    suspend fun kickMember(groupId: Int, userId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.kickMember(groupId, userId)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to kick member"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun banMember(groupId: Int, userId: Int, reason: String? = null): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = com.nexy.client.data.api.BanMemberRequest(reason)
                val response = apiService.banMember(groupId, userId, request)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to ban member"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun unbanMember(groupId: Int, userId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.unbanMember(groupId, userId)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to unban member"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getBannedMembers(groupId: Int): Result<List<com.nexy.client.data.api.GroupBan>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getBannedMembers(groupId)
                if (response.isSuccessful) {
                    Result.success(response.body() ?: emptyList())
                } else {
                    Result.failure(Exception("Failed to get banned members"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
