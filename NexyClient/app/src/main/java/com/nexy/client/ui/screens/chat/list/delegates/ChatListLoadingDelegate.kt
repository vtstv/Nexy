package com.nexy.client.ui.screens.chat.list.delegates

import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.ui.screens.chat.list.state.ChatListUiState
import com.nexy.client.ui.screens.chat.list.state.ChatWithInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class ChatListLoadingDelegate @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val tokenManager: AuthTokenManager
) {
    private lateinit var scope: CoroutineScope
    private lateinit var uiState: MutableStateFlow<ChatListUiState>
    private lateinit var refreshTrigger: MutableStateFlow<Long>

    fun initialize(
        scope: CoroutineScope,
        uiState: MutableStateFlow<ChatListUiState>,
        refreshTrigger: MutableStateFlow<Long>
    ) {
        this.scope = scope
        this.uiState = uiState
        this.refreshTrigger = refreshTrigger
    }

    fun loadCurrentUser() {
        scope.launch {
            tokenManager.getUserId()?.let { userId ->
                launch {
                    userRepository.getUserById(userId, forceRefresh = true)
                }

                userRepository.getUserByIdFlow(userId).collect { user ->
                    if (user != null) {
                        uiState.update { it.copy(currentUser = user) }
                    }
                }
            }
        }
    }

    fun loadChats() {
        scope.launch {
            combine(
                chatRepository.getAllChats(),
                refreshTrigger
            ) { chats, _ -> chats }
                .collect { chats ->
                    val chatsWithInfo = ArrayList<ChatWithInfo>()
                    for (chat in chats) {
                        val (displayName, avatarUrl, isSelfChat) = when (chat.type) {
                            ChatType.PRIVATE -> {
                                val currentUserId = tokenManager.getUserId()
                                val otherUserId = chat.participantIds?.firstOrNull { it != currentUserId }

                                if (otherUserId != null) {
                                    val userResult = userRepository.getUserById(otherUserId)
                                    val user = userResult.getOrNull()
                                    val name = user?.displayName?.takeIf { it.isNotBlank() }
                                        ?: user?.username?.takeIf { it.isNotBlank() }
                                        ?: "User $otherUserId"
                                    Triple(name, user?.avatarUrl, false)
                                } else {
                                    Triple("Notepad", null, true)
                                }
                            }
                            else -> Triple(
                                chat.name?.takeIf { it.isNotBlank() } ?: "Unknown",
                                chat.avatarUrl,
                                false
                            )
                        }

                        val lastMessage = chatRepository.getLastMessageForChat(chat.id)
                        val preview = when {
                            lastMessage == null -> "No messages"
                            lastMessage.mediaUrl != null -> {
                                val prefix = if (chat.type == ChatType.GROUP && lastMessage.sender != null) {
                                    val senderName = lastMessage.sender.displayName?.takeIf { it.isNotBlank() }
                                        ?: lastMessage.sender.username?.takeIf { it.isNotBlank() }
                                        ?: "User ${lastMessage.senderId}"
                                    "$senderName: "
                                } else ""
                                "${prefix}📎 ${lastMessage.content ?: "Attachment"}"
                            }
                            else -> {
                                val prefix = if (chat.type == ChatType.GROUP && lastMessage.sender != null) {
                                    val senderName = lastMessage.sender.displayName?.takeIf { it.isNotBlank() }
                                        ?: lastMessage.sender.username?.takeIf { it.isNotBlank() }
                                        ?: "User ${lastMessage.senderId}"
                                    "$senderName: "
                                } else ""
                                prefix + (lastMessage.content ?: "")
                            }
                        }
                        val timeStr = lastMessage?.timestamp?.let { formatMessageTime(it) }

                        chatsWithInfo.add(
                            ChatWithInfo(
                                chat = chat.copy(avatarUrl = avatarUrl),
                                displayName = displayName,
                                lastMessagePreview = preview,
                                lastMessageTime = timeStr,
                                unreadCount = chat.unreadCount,
                                isSelfChat = isSelfChat
                            )
                        )
                    }
                    uiState.value = uiState.value.copy(chats = chatsWithInfo)
                }
        }

        refreshChats()
    }

    fun refreshChats() {
        scope.launch {
            uiState.value = uiState.value.copy(isLoading = true, error = null)

            chatRepository.refreshChats().fold(
                onSuccess = { chats ->
                    uiState.value = uiState.value.copy(isLoading = false)
                    refreshTrigger.value = System.currentTimeMillis()

                    chats.forEach { chat ->
                        launch {
                            try {
                                if (chat.lastMessage != null) {
                                    val lastMsg = chatRepository.getLastMessageForChat(chat.id)

                                    if (lastMsg == null || lastMsg.id != chat.lastMessage.id) {
                                        android.util.Log.d(
                                            "ChatListLoadingDelegate",
                                            "Syncing chat ${chat.id}: Local=${lastMsg?.id}, Server=${chat.lastMessage.id}"
                                        )
                                        chatRepository.loadMessages(chat.id, limit = 20, offset = 0)
                                        refreshTrigger.value = System.currentTimeMillis()
                                    } else {
                                        android.util.Log.d(
                                            "ChatListLoadingDelegate",
                                            "Chat ${chat.id} is up to date"
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "ChatListLoadingDelegate",
                                    "Failed to sync chat ${chat.id}",
                                    e
                                )
                            }
                        }
                    }
                },
                onFailure = { error ->
                    uiState.value = uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }

    fun openSavedMessages(onChatClick: (Int) -> Unit) {
        scope.launch {
            chatRepository.getOrCreateSavedMessages().fold(
                onSuccess = { chat ->
                    onChatClick(chat.id)
                },
                onFailure = { error ->
                    uiState.value = uiState.value.copy(
                        error = error.message ?: "Failed to open Notepad"
                    )
                }
            )
        }
    }

    fun triggerRefresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    private fun formatMessageTime(timestamp: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(timestamp.substringBefore('.')) ?: return ""
            val now = Calendar.getInstance()
            val msgCal = Calendar.getInstance()
            msgCal.time = date

            when {
                now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) -> {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                }
                now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 -> {
                    "Yesterday"
                }
                now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
                }
                else -> {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
