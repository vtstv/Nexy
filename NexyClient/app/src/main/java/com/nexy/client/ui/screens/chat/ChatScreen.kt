package com.nexy.client.ui.screens.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nexy.client.data.models.ChatType
import com.nexy.client.ui.screens.chat.components.*
import com.nexy.client.ui.screens.chat.effects.rememberAutoScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

import com.nexy.client.ui.theme.ThemeViewModel

import com.nexy.client.data.models.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToGroupSettings: ((Int) -> Unit)? = null,
    onNavigateToGroupInfo: ((Int) -> Unit)? = null,
    onNavigateToChat: ((Int) -> Unit)? = null,
    onNavigateToChatWithMessage: ((Int, String) -> Unit)? = null,
    initialTargetMessageId: String? = null,
    onConsumeTargetMessage: (() -> Unit)? = null,
    onNavigateToUserProfile: ((Int) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(key = "chat_$chatId"),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val fontScale by themeViewModel.fontScale.collectAsState()
    val incomingTextColor by themeViewModel.incomingTextColor.collectAsState()
    val outgoingTextColor by themeViewModel.outgoingTextColor.collectAsState()
    val avatarSize by themeViewModel.avatarSize.collectAsState()
    val listState = rememberLazyListState()
    val hasScrolledToBottom = remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showChatInfoDialog by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    var pendingInviteCode by remember { mutableStateOf<String?>(null) }
    var isJoiningGroup by remember { mutableStateOf(false) }
    
    // Show error as snackbar
    LaunchedEffect(uiState.error) {
        if (uiState.error != null && uiState.chatName != "Chat") {
            snackbarHostState.showSnackbar(
                message = uiState.error ?: "An error occurred",
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    // Initialize chat
    LaunchedEffect(chatId) {
        viewModel.initializeChatId(chatId)
        hasScrolledToBottom.value = false // Reset scroll flag when changing chat
    }

    // Handle pending deep link target when opening chat from another screen
    LaunchedEffect(chatId, initialTargetMessageId) {
        if (initialTargetMessageId != null) {
            android.util.Log.d("ChatScreen", "Pending message deep link for chat $chatId: $initialTargetMessageId")
            viewModel.navigateToMessage(initialTargetMessageId)
            onConsumeTargetMessage?.invoke()
        }
    }
    
    // Use LifecycleEventObserver for reliable pause/resume detection
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    android.util.Log.d("ChatScreen", "ON_RESUME - chat is now visible")
                    viewModel.onChatOpened()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    android.util.Log.d("ChatScreen", "ON_PAUSE - chat is no longer visible")
                    viewModel.onChatClosed()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Initial scroll to bottom when messages are first loaded
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty() && !hasScrolledToBottom.value) {
            android.util.Log.d("ChatScreen", "Scrolling to bottom (index 0), messages count: ${uiState.messages.size}")
            // With reverseLayout = true, index 0 is the bottom (newest message)
            listState.scrollToItem(0)
            hasScrolledToBottom.value = true
        }
    }
    
    // Scroll to target message when it's found
    LaunchedEffect(uiState.targetMessageId, uiState.messages.size) {
        val targetId = uiState.targetMessageId
        if (targetId != null && uiState.messages.isNotEmpty()) {
            val targetIndexInOriginal = uiState.messages.indexOfFirst { it.id == targetId }
            if (targetIndexInOriginal >= 0) {
                // MessageList uses reversedMessages with reverseLayout=true, so translate index
                val reversedIndex = uiState.messages.size - 1 - targetIndexInOriginal
                android.util.Log.d(
                    "ChatScreen",
                    "Found target message: $targetId, originalIndex=$targetIndexInOriginal, reversedIndex=$reversedIndex, total=${uiState.messages.size}"
                )
                // Jump directly to the target item to avoid long scrolls on large histories
                listState.scrollToItem(reversedIndex)

                // Keep target highlighted briefly
                kotlinx.coroutines.delay(1200)
                viewModel.clearTargetMessage()
            } else {
                android.util.Log.d("ChatScreen", "Target message $targetId not found in list of ${uiState.messages.size} messages")
            }
        }
    }
    
    // Track when user is at bottom of list (seeing newest messages) 
    // With reverseLayout=true, firstVisibleItemIndex=0 means user is at the bottom (newest messages)
    val isAtBottom = remember { derivedStateOf { listState.firstVisibleItemIndex <= 2 } }
    
    // When new messages arrive and user is at bottom, mark as read
    LaunchedEffect(uiState.messages.size, isAtBottom.value) {
        if (uiState.messages.isNotEmpty() && isAtBottom.value) {
            viewModel.onUserSawNewMessages()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                chatName = uiState.chatName,
                chatAvatarUrl = uiState.chatAvatarUrl,
                chatType = uiState.chatType,
                isSelfChat = uiState.isSelfChat,
                isCreator = uiState.isCreator,
                isSearching = uiState.isSearching,
                searchQuery = uiState.searchQuery,
                isTyping = uiState.isTyping,
                typingUser = uiState.typingUser,
                mutedUntil = uiState.mutedUntil,
                otherUserOnlineStatus = uiState.otherUserOnlineStatus,
                onSearchClick = viewModel::toggleSearch,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onNavigateBack = onNavigateBack,
                onClearChat = viewModel::clearChat,
                onDeleteChat = {
                    viewModel.deleteChat()
                    onNavigateBack()
                },
                onMuteClick = { showMuteDialog = true },
                onUnmuteClick = { viewModel.unmuteChat() },
                onChatInfoClick = { 
                    if (uiState.chatType == ChatType.GROUP && onNavigateToGroupInfo != null) {
                        onNavigateToGroupInfo(chatId)
                    } else {
                        showChatInfoDialog = true 
                    }
                },
                onCallClick = if (uiState.chatType == ChatType.PRIVATE && !uiState.isSelfChat) {
                    { viewModel.startCall() }
                } else null,
                onGroupSettingsClick = if (onNavigateToGroupSettings != null) {
                    { onNavigateToGroupSettings(chatId) }
                } else null,
                showBackButton = showBackButton
            )
        }
    ) { padding ->
        if (showChatInfoDialog) {
            AlertDialog(
                onDismissRequest = { showChatInfoDialog = false },
                title = { Text("Chat Info") },
                text = { 
                    Column {
                        Text("Name: ${uiState.chatName}")
                        Spacer(Modifier.height(8.dp))
                        Text("Notifications: On")
                        Spacer(Modifier.height(8.dp))
                        Text("Theme: Default")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChatInfoDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (uiState.error != null && uiState.chatName == "Chat") {
            ChatErrorView(
                error = uiState.error ?: "Failed to load chat",
                onNavigateBack = onNavigateBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            showEmojiPicker = false
                        })
                    }
            ) {
                ConnectionStatusBanner(
                    isConnected = uiState.isConnected,
                    pendingMessageCount = uiState.pendingMessageCount
                )
                
                if (uiState.isSearching) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.searchResults.isEmpty() && uiState.searchQuery.length > 2) {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                 Text("No results found")
                             }
                        } else {
                            MessageList(
                                messages = uiState.searchResults,
                                currentUserId = uiState.currentUserId,
                                listState = rememberLazyListState(),
                                chatType = uiState.chatType,
                                fontScale = fontScale,
                                incomingTextColor = incomingTextColor,
                                outgoingTextColor = outgoingTextColor,
                                avatarSize = avatarSize,
                                onDeleteMessage = {},
                                onReplyMessage = {},
                                onEditMessage = {},
                                onDownloadFile = { _, _ -> },
                                onOpenFile = {},
                                onSaveFile = {}
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        MessageList(
                            messages = uiState.messages,
                            currentUserId = uiState.currentUserId,
                            listState = listState,
                            highlightMessageId = uiState.targetMessageId,
                            chatType = uiState.chatType,
                            fontScale = fontScale,
                            incomingTextColor = incomingTextColor,
                            outgoingTextColor = outgoingTextColor,
                            avatarSize = avatarSize,
                            firstUnreadMessageId = uiState.firstUnreadMessageId,
                            userRole = uiState.userRole,
                            participants = uiState.participants,
                            onDeleteMessage = { messageId ->
                                viewModel.deleteMessage(messageId)
                            },
                            onReplyMessage = { message -> replyToMessage = message },
                            onEditMessage = viewModel::startEditing,
                            onDownloadFile = { fileId, fileName ->
                                viewModel.downloadFile(context, fileId, fileName)
                            },
                            onOpenFile = { fileName ->
                                viewModel.openFile(context, fileName)
                            },
                            onSaveFile = { fileName ->
                                viewModel.saveFile(context, fileName)
                            },
                            onInviteLinkClick = { code ->
                                pendingInviteCode = code
                            },
                            onUserLinkClick = { userId ->
                                userId.toIntOrNull()?.let { id ->
                                    onNavigateToUserProfile?.invoke(id)
                                }
                            },
                            onReactionClick = { messageId, emoji ->
                                viewModel.toggleReaction(messageId, emoji)
                            },
                            invitePreviewProvider = { code -> viewModel.getInvitePreview(code) },
                            isLoadingInvitePreview = { code -> viewModel.isLoadingInvitePreview(code) },
                            messageLinkPreviewProvider = { chatId, messageId -> 
                                viewModel.getMessagePreview(chatId, messageId)
                            },
                            isLoadingMessagePreview = { chatId, messageId -> 
                                viewModel.isLoadingMessagePreview(chatId, messageId)
                            },
                            onLoadMessagePreview = { chatId, messageId ->
                                viewModel.loadMessagePreview(chatId, messageId)
                            },
                            onNavigateToMessage = { targetChatId, messageId ->
                                android.util.Log.d("ChatScreen", "onNavigateToMessage clicked: chat=$targetChatId message=$messageId current=$chatId")
                                val parsedChatId = targetChatId.toIntOrNull()
                                if (parsedChatId != null && parsedChatId != chatId) {
                                    onNavigateToChatWithMessage?.invoke(parsedChatId, messageId)
                                } else {
                                    viewModel.navigateToMessage(messageId)
                                }
                            }
                        )
                        
                        // Show loading indicator when loading to target message
                        if (uiState.isLoadingToMessage) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                    .clickable(enabled = false) {}, // Block clicks while loading
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "Loading message...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    
                    if (!uiState.isMember && uiState.groupType == com.nexy.client.data.models.GroupType.PUBLIC_GROUP) {
                        Button(
                            onClick = viewModel::joinGroup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("Join Group")
                        }
                    } else {
                        MessageInput(
                            text = uiState.messageText,
                            onTextChange = viewModel::onMessageTextChanged,
                            onSend = {
                                viewModel.sendMessage(replyToId = replyToMessage?.serverId)
                                replyToMessage = null
                            },
                            onSendFile = { fileUri, fileName ->
                                viewModel.sendFileMessage(context, fileUri, fileName)
                            },
                            onSendVoice = { audioFile, durationMs ->
                                viewModel.sendVoiceMessage(audioFile, durationMs.toLong())
                            },
                            showEmojiPicker = showEmojiPicker,
                            onToggleEmojiPicker = { showEmojiPicker = !showEmojiPicker },
                            replyToMessage = replyToMessage,
                            onCancelReply = { replyToMessage = null },
                            editingMessage = uiState.editingMessage,
                            onCancelEdit = viewModel::cancelEditing,
                            voiceMessagesEnabled = uiState.voiceMessagesEnabled,
                            recipientVoiceMessagesEnabled = uiState.recipientVoiceMessagesEnabled
                        )
                    }
                }
            }
        }
    }

    if (showMuteDialog) {
        MuteDialog(
            onDismiss = { showMuteDialog = false },
            onMute = { duration, until ->
                viewModel.muteChat(duration, until)
                showMuteDialog = false
            }
        )
    }

    if (pendingInviteCode != null) {
        val scope = rememberCoroutineScope()
        var invitePreview by remember { mutableStateOf<com.nexy.client.data.models.InvitePreviewResponse?>(null) }
        var isLoadingPreview by remember { mutableStateOf(true) }
        
        // Load preview when dialog opens
        LaunchedEffect(pendingInviteCode) {
            isLoadingPreview = true
            invitePreview = null
            viewModel.validateGroupInvite(pendingInviteCode!!)
                .onSuccess { preview ->
                    invitePreview = preview
                    isLoadingPreview = false
                }
                .onFailure {
                    invitePreview = null
                    isLoadingPreview = false
                }
        }
        
        JoinGroupByInviteDialog(
            inviteCode = pendingInviteCode!!,
            isLoadingPreview = isLoadingPreview,
            isJoining = isJoiningGroup,
            preview = invitePreview,
            onConfirm = {
                scope.launch {
                    isJoiningGroup = true
                    viewModel.joinByInviteCode(pendingInviteCode!!)
                        .onSuccess { newChatId ->
                            isJoiningGroup = false
                            pendingInviteCode = null
                            onNavigateToChat?.invoke(newChatId)
                        }
                        .onFailure { error ->
                            isJoiningGroup = false
                            pendingInviteCode = null
                            snackbarHostState.showSnackbar(
                                message = error.message ?: "Failed to join group"
                            )
                        }
                }
            },
            onDismiss = {
                if (!isJoiningGroup) {
                    pendingInviteCode = null
                }
            }
        )
    }
}

@Composable
private fun ChatErrorView(
    error: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Failed to load chat",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateBack) {
                Text("Go Back")
            }
        }
    }
}
