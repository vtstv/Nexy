package com.nexy.client.ui.screens.chat.delegates

import com.nexy.client.data.models.InvitePreviewResponse
import com.nexy.client.ui.screens.chat.handlers.ChatMembershipHandler
import com.nexy.client.ui.screens.chat.handlers.ChatStateManager
import com.nexy.client.ui.screens.chat.state.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class MembershipDelegate @Inject constructor(
    private val membershipHandler: ChatMembershipHandler,
    private val stateManager: ChatStateManager
) : ChatViewModelDelegate {

    private lateinit var scope: CoroutineScope
    private lateinit var uiState: MutableStateFlow<ChatUiState>
    private lateinit var getChatId: () -> Int
    private var onChatInitialized: (() -> Unit)? = null

    override fun initialize(
        scope: CoroutineScope,
        uiState: MutableStateFlow<ChatUiState>,
        getChatId: () -> Int
    ) {
        this.scope = scope
        this.uiState = uiState
        this.getChatId = getChatId
    }

    fun setOnChatInitialized(callback: () -> Unit) {
        this.onChatInitialized = callback
    }

    fun clearChat() {
        scope.launch {
            membershipHandler.clearChat(getChatId()).onFailure { error ->
                uiState.value = uiState.value.copy(
                    error = error.message ?: "Failed to clear chat"
                )
            }
        }
    }

    fun deleteChat() {
        scope.launch {
            membershipHandler.deleteChat(getChatId()).onFailure { error ->
                uiState.value = uiState.value.copy(
                    error = error.message ?: "Failed to delete chat"
                )
            }
        }
    }

    fun joinGroup() {
        scope.launch {
            uiState.value = uiState.value.copy(isLoading = true)
            membershipHandler.joinGroup(getChatId())
                .onSuccess {
                    uiState.value = uiState.value.copy(isLoading = false, isMember = true)
                    onChatInitialized?.invoke()
                }
                .onFailure { error ->
                    uiState.value = uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to join group"
                    )
                }
        }
    }

    fun muteChat(duration: String?, until: String?) {
        scope.launch {
            membershipHandler.muteChat(getChatId(), duration, until)
                .onSuccess { refreshChatInfo() }
                .onFailure { e -> uiState.value = uiState.value.copy(error = e.message) }
        }
    }

    fun unmuteChat() {
        scope.launch {
            membershipHandler.unmuteChat(getChatId())
                .onSuccess { refreshChatInfo() }
                .onFailure { e -> uiState.value = uiState.value.copy(error = e.message) }
        }
    }

    suspend fun validateGroupInvite(code: String) = membershipHandler.validateGroupInvite(code)

    suspend fun joinByInviteCode(code: String): Result<Int> {
        return membershipHandler.joinByInviteCode(code).map { response ->
            response.chat?.id ?: throw Exception("No chat returned")
        }
    }

    fun loadInvitePreview(code: String) {
        if (uiState.value.invitePreviews.containsKey(code) ||
            uiState.value.loadingInviteCodes.contains(code)) {
            return
        }

        scope.launch {
            uiState.value = uiState.value.copy(
                loadingInviteCodes = uiState.value.loadingInviteCodes + code
            )

            membershipHandler.validateGroupInvite(code)
                .onSuccess { preview ->
                    uiState.value = uiState.value.copy(
                        invitePreviews = uiState.value.invitePreviews + (code to preview),
                        loadingInviteCodes = uiState.value.loadingInviteCodes - code
                    )
                }
                .onFailure {
                    uiState.value = uiState.value.copy(
                        loadingInviteCodes = uiState.value.loadingInviteCodes - code
                    )
                }
        }
    }

    fun getInvitePreview(code: String): InvitePreviewResponse? {
        loadInvitePreview(code)
        return uiState.value.invitePreviews[code]
    }

    fun isLoadingInvitePreview(code: String): Boolean {
        return uiState.value.loadingInviteCodes.contains(code)
    }

    private fun refreshChatInfo() {
        scope.launch {
            val currentUserId = stateManager.loadCurrentUserId()
            stateManager.loadChatInfo(getChatId(), currentUserId)?.let { chatInfo ->
                uiState.value = uiState.value.copy(
                    chatName = chatInfo.chatName,
                    chatAvatarUrl = chatInfo.chatAvatarUrl,
                    chatType = chatInfo.chatType,
                    groupType = chatInfo.groupType,
                    participantIds = chatInfo.participantIds,
                    isSelfChat = chatInfo.isSelfChat,
                    isCreator = chatInfo.isCreator,
                    isMember = chatInfo.isMember,
                    mutedUntil = chatInfo.mutedUntil,
                    otherUserOnlineStatus = chatInfo.otherUserOnlineStatus
                )
            }
        }
    }
}
