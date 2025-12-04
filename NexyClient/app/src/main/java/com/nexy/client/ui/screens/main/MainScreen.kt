/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.ui.screens.chat.list.ChatListScreen
import com.nexy.client.ui.screens.chat.ChatScreen
import com.nexy.client.ui.screens.main.components.*
import androidx.activity.compose.BackHandler

/**
 * Main screen with adaptive layout
 * Tablet (Landscape): Split panel (Chat list + Active chat)
 * Phone / Portrait: Single panel 
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGroupSettings: (Int) -> Unit = {},
    onNavigateToEditGroup: (Int) -> Unit = {},
    onNavigateToGroupInfo: (Int) -> Unit = {},
    onLogout: () -> Unit,
    onChatSelected: (Int?) -> Unit = {},
    initialChatId: Int? = null
) {
    val screenConfig = rememberScreenConfig()
    val dialogState = rememberDialogState()
    var selectedChatId by remember { mutableStateOf(initialChatId) }

    // Update selected chat when initialChatId changes
    LaunchedEffect(initialChatId) {
        if (initialChatId != selectedChatId) {
            selectedChatId = initialChatId
        }
    }
    
    // Notify parent when selection changes
    LaunchedEffect(selectedChatId) {
        onChatSelected(selectedChatId)
    }
    
    // Handle Back Press in Single Pane mode
    if (!screenConfig.useSplitScreen && selectedChatId != null) {
        BackHandler {
            selectedChatId = null
        }
    }
    
    if (screenConfig.useSplitScreen) {
        SplitScreenLayout(
            selectedChatId = selectedChatId,
            onChatSelected = { chatId -> if (chatId > 0) selectedChatId = chatId },
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToGroupInfo = onNavigateToGroupInfo,
            onLogout = onLogout,
            dialogState = dialogState
        )
    } else {
        SinglePaneLayout(
            selectedChatId = selectedChatId,
            onChatSelected = { chatId -> if (chatId > 0) selectedChatId = chatId },
            onClearSelection = { selectedChatId = null },
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToGroupInfo = onNavigateToGroupInfo,
            onLogout = onLogout,
            dialogState = dialogState
        )
    }
    
    // All dialogs
    MainScreenDialogs(
        screenConfig = screenConfig,
        showContactsDialog = dialogState.showContacts,
        showSearchDialog = dialogState.showSearch,
        showCreateGroupDialog = dialogState.showCreateGroup,
        showSearchGroupsDialog = dialogState.showSearchGroups,
        showGroupSettingsDialog = dialogState.showGroupSettings,
        onContactsDialogDismiss = dialogState::closeContacts,
        onSearchDialogDismiss = dialogState::closeSearch,
        onCreateGroupDialogDismiss = dialogState::closeCreateGroup,
        onSearchGroupsDialogDismiss = dialogState::closeSearchGroups,
        onGroupSettingsDialogDismiss = dialogState::closeGroupSettings,
        onChatSelected = { chatId -> selectedChatId = chatId },
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToEditGroup = onNavigateToEditGroup
    )
}
