package com.nexy.client.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.ChatType
import com.nexy.client.ui.screens.chat.components.*
import com.nexy.client.ui.screens.chat.effects.rememberAutoScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

import com.nexy.client.ui.theme.ThemeViewModel

import com.nexy.client.data.models.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToGroupSettings: ((Int) -> Unit)? = null,
    onNavigateToGroupInfo: ((Int) -> Unit)? = null,
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
    val listState = rememberLazyListState()
    val hasScrolledToBottom = remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showChatInfoDialog by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    
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
    
    LaunchedEffect(Unit) {
        viewModel.markAsRead()
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
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                chatName = uiState.chatName,
                chatAvatarUrl = uiState.chatAvatarUrl,
                chatType = uiState.chatType,
                isCreator = uiState.isCreator,
                isSearching = uiState.isSearching,
                searchQuery = uiState.searchQuery,
                onSearchClick = viewModel::toggleSearch,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onNavigateBack = onNavigateBack,
                onClearChat = viewModel::clearChat,
                onDeleteChat = {
                    viewModel.deleteChat()
                    onNavigateBack()
                },
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
                                onDeleteMessage = {},
                                onReplyMessage = {},
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
                            chatType = uiState.chatType,
                            fontScale = fontScale,
                            incomingTextColor = incomingTextColor,
                            outgoingTextColor = outgoingTextColor,
                            onDeleteMessage = viewModel::deleteMessage,
                            onReplyMessage = { message -> replyToMessage = message },
                            onDownloadFile = { fileId, fileName ->
                                viewModel.downloadFile(context, fileId, fileName)
                            },
                            onOpenFile = { fileName ->
                                viewModel.openFile(context, fileName)
                            },
                            onSaveFile = { fileName ->
                                viewModel.saveFile(context, fileName)
                            }
                        )
                    }
                    
                    MessageInput(
                        text = uiState.messageText,
                        onTextChange = viewModel::onMessageTextChange,
                        onSend = {
                            viewModel.sendMessage(replyToId = replyToMessage?.serverId)
                            replyToMessage = null
                        },
                        onSendFile = { fileUri, fileName ->
                            viewModel.sendFileMessage(context, fileUri, fileName)
                        },
                        showEmojiPicker = showEmojiPicker,
                        onToggleEmojiPicker = { showEmojiPicker = !showEmojiPicker },
                        replyToMessage = replyToMessage,
                        onCancelReply = { replyToMessage = null }
                    )
                }
            }
        }
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
