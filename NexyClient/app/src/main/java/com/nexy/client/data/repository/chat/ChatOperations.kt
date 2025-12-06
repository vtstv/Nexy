package com.nexy.client.data.repository.chat

import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.repository.ChatInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatOperations @Inject constructor(
    private val chatSyncOperations: ChatSyncOperations,
    private val chatInviteOperations: ChatInviteOperations,
    private val chatInfoProvider: ChatInfoProvider,
    private val chatReadReceiptOperations: ChatReadReceiptOperations,
    private val chatMuteOperations: ChatMuteOperations,
    private val chatCrudOperations: ChatCrudOperations,
    private val chatSearchOperations: ChatSearchOperations
) {
    // region Sync Operations - delegated
    fun getAllChats(): Flow<List<Chat>> = chatSyncOperations.getAllChats()
    suspend fun refreshChats(): Result<List<Chat>> = chatSyncOperations.refreshChats()
    suspend fun getChatById(chatId: Int): Result<Chat> = chatSyncOperations.getChatById(chatId)
    // endregion

    // region Info Provider - delegated
    suspend fun getChatInfo(chatId: Int): ChatInfo? = chatInfoProvider.getChatInfo(chatId)
    // endregion

    // region Read Receipt - delegated
    suspend fun markChatAsRead(chatId: Int) = chatReadReceiptOperations.markChatAsRead(chatId)
    // endregion

    // region CRUD Operations - delegated
    suspend fun clearChatMessages(chatId: Int): Result<Unit> = chatCrudOperations.clearChatMessages(chatId)
    suspend fun deleteChat(chatId: Int): Result<Unit> = chatCrudOperations.deleteChat(chatId)
    suspend fun leaveGroup(chatId: Int): Result<Unit> = chatCrudOperations.leaveGroup(chatId)
    suspend fun getGroupMembers(chatId: Int, query: String? = null): Result<List<ChatMember>> =
        chatCrudOperations.getGroupMembers(chatId, query)
    suspend fun getOrCreateSavedMessages(): Result<Chat> = chatCrudOperations.getOrCreateSavedMessages()
    suspend fun createPrivateChat(userId: Int): Result<Chat> = chatCrudOperations.createPrivateChat(userId)
    // endregion

    // region Mute Operations - delegated
    suspend fun muteChat(chatId: Int, duration: String?, until: String?): Result<Unit> =
        chatMuteOperations.muteChat(chatId, duration, until)
    suspend fun unmuteChat(chatId: Int): Result<Unit> = chatMuteOperations.unmuteChat(chatId)
    suspend fun pinChat(chatId: Int): Result<Unit> = chatMuteOperations.pinChat(chatId)
    suspend fun unpinChat(chatId: Int): Result<Unit> = chatMuteOperations.unpinChat(chatId)
    suspend fun hideChat(chatId: Int): Result<Unit> = chatMuteOperations.hideChat(chatId)
    suspend fun unhideChat(chatId: Int): Result<Unit> = chatMuteOperations.unhideChat(chatId)
    // endregion

    // region Search Operations - delegated
    suspend fun searchPublicGroups(query: String): Result<List<Chat>> =
        chatSearchOperations.searchPublicGroups(query)
    // endregion

    // region Invite Operations - delegated
    suspend fun createInviteLink(chatId: Int, maxUses: Int = 1, expiresAt: Long? = null) =
        chatInviteOperations.createInviteLink(chatId, maxUses, expiresAt)
    suspend fun validateInviteCode(code: String) = chatInviteOperations.validateInviteCode(code)
    suspend fun validateGroupInvite(code: String) = chatInviteOperations.validateGroupInvite(code)
    suspend fun joinByInviteCode(code: String) = chatInviteOperations.joinByInviteCode(code)
    suspend fun removeMember(groupId: Int, userId: Int) = chatInviteOperations.removeMember(groupId, userId)
    suspend fun addGroupMember(groupId: Int, userId: Int) = chatInviteOperations.addGroupMember(groupId, userId)
    suspend fun createGroupInviteLink(groupId: Int, usageLimit: Int? = null, expiresIn: Int? = null) =
        chatInviteOperations.createGroupInviteLink(groupId, usageLimit, expiresIn)
    suspend fun joinPublicGroup(groupId: Int): Result<Chat> = chatInviteOperations.joinPublicGroup(groupId)
    suspend fun transferOwnership(groupId: Int, newOwnerId: Int) =
        chatInviteOperations.transferOwnership(groupId, newOwnerId)
    suspend fun kickMember(groupId: Int, userId: Int) = chatInviteOperations.kickMember(groupId, userId)
    suspend fun banMember(groupId: Int, userId: Int, reason: String? = null) =
        chatInviteOperations.banMember(groupId, userId, reason)
    suspend fun unbanMember(groupId: Int, userId: Int) = chatInviteOperations.unbanMember(groupId, userId)
    suspend fun getBannedMembers(groupId: Int) = chatInviteOperations.getBannedMembers(groupId)
    // endregion
}
