/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.ui.screens.chat.ChatScreen
import com.nexy.client.ui.screens.chat.list.ChatListScreen

@Composable
fun SplitScreenLayout(
    selectedChatId: Int?,
    onChatSelected: (Int) -> Unit,
    onNavigateToChatWithMessage: (Int, String) -> Unit,
    targetMessageId: String? = null,
    onConsumeTargetMessage: () -> Unit = {},
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGroupInfo: (Int) -> Unit,
    onNavigateToFolders: () -> Unit = {},
    onLogout: () -> Unit,
    dialogState: DialogState
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel - Chat list (30% width)
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.3f)
                .widthIn(min = 280.dp, max = 400.dp),
            tonalElevation = 1.dp
        ) {
            ChatListScreen(
                onChatClick = onChatSelected,
                onNavigateToSearch = dialogState::openSearch,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToContacts = dialogState::openContacts,
                onNavigateToCreateGroup = dialogState::openCreateGroup,
                onNavigateToSearchGroups = dialogState::openSearchGroups,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToFolders = onNavigateToFolders,
                onLogout = onLogout,
                refreshTrigger = dialogState.refreshTrigger
            )
        }
        
        // Vertical divider
        HorizontalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
        )
        
        // Right panel - Chat content (70% width)
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.7f)
        ) {
            if (selectedChatId != null) {
                key(selectedChatId) {
                    ChatScreen(
                        chatId = selectedChatId,
                        onNavigateBack = { },
                        onNavigateToGroupSettings = dialogState::openGroupSettings,
                        onNavigateToGroupInfo = onNavigateToGroupInfo,
                        onNavigateToChat = onChatSelected,
                        onNavigateToChatWithMessage = onNavigateToChatWithMessage,
                        initialTargetMessageId = targetMessageId,
                        onConsumeTargetMessage = onConsumeTargetMessage,
                        showBackButton = false
                    )
                }
            } else {
                // Empty state when no chat is selected
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a chat to start messaging",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SinglePaneLayout(
    selectedChatId: Int?,
    onChatSelected: (Int) -> Unit,
    onNavigateToChatWithMessage: (Int, String) -> Unit,
    targetMessageId: String? = null,
    onConsumeTargetMessage: () -> Unit = {},
    onClearSelection: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGroupInfo: (Int) -> Unit,
    onNavigateToFolders: () -> Unit = {},
    onLogout: () -> Unit,
    dialogState: DialogState
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        if (selectedChatId != null) {
            ChatScreen(
                chatId = selectedChatId,
                onNavigateBack = onClearSelection,
                onNavigateToGroupSettings = dialogState::openGroupSettings,
                onNavigateToGroupInfo = onNavigateToGroupInfo,
                onNavigateToChat = onChatSelected,
                onNavigateToChatWithMessage = onNavigateToChatWithMessage,
                initialTargetMessageId = targetMessageId,
                onConsumeTargetMessage = onConsumeTargetMessage
            )
        } else {
            ChatListScreen(
                onChatClick = onChatSelected,
                onNavigateToSearch = dialogState::openSearch,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToContacts = dialogState::openContacts,
                onNavigateToCreateGroup = dialogState::openCreateGroup,
                onNavigateToSearchGroups = dialogState::openSearchGroups,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToFolders = onNavigateToFolders,
                onLogout = onLogout,
                refreshTrigger = dialogState.refreshTrigger
            )
        }
    }
}
