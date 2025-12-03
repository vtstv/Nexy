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
import com.nexy.client.ui.screens.contacts.ContactsScreen
import com.nexy.client.ui.screens.group.CreateGroupScreen
import com.nexy.client.ui.screens.group.GroupSettingsScreen
import com.nexy.client.ui.screens.group.SearchGroupsScreen
import com.nexy.client.ui.screens.search.SearchScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.RectangleShape

/**
 * Main screen with adaptive layout
 * Tablet (Landscape): Split panel (Chat list + Active chat)
 * Phone / Portrait: Single panel (Telegram-like navigation)
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
    initialChatId: Int? = null
) {
    var selectedChatId by remember { mutableStateOf(initialChatId) }
    var showContactsDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showSearchGroupsDialog by remember { mutableStateOf(false) }
    var showGroupSettingsDialog by remember { mutableStateOf<Int?>(null) }
    
    // Detect device configuration
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Define tablet as width >= 600dp
    val isTablet = screenWidth >= 600.dp
    
    // Split screen only for Tablet AND Landscape
    val useSplitScreen = isTablet && isLandscape

    // Update selected chat when initialChatId changes
    LaunchedEffect(initialChatId) {
        if (initialChatId != null) {
            selectedChatId = initialChatId
        }
    }
    
    // Handle Back Press in Single Pane mode
    if (!useSplitScreen && selectedChatId != null) {
        BackHandler {
            selectedChatId = null
        }
    }
    
    if (useSplitScreen) {
        // Split Screen Layout (Tablet Landscape)
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
                    onChatClick = { chatId -> 
                        if (chatId > 0) selectedChatId = chatId 
                    },
                    onNavigateToSearch = { showSearchDialog = true },
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToContacts = { showContactsDialog = true },
                    onNavigateToCreateGroup = { showCreateGroupDialog = true },
                    onNavigateToSearchGroups = { showSearchGroupsDialog = true },
                    onNavigateToSettings = onNavigateToSettings,
                    onLogout = onLogout
                )
            }
            
            // Vertical divider
            VerticalDivider()
            
            // Right panel - Chat content (70% width)
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.7f)
            ) {
                if (selectedChatId != null) {
                    key(selectedChatId) {
                        ChatScreen(
                            chatId = selectedChatId!!,
                            onNavigateBack = { selectedChatId = null },
                            onNavigateToGroupSettings = { chatId -> 
                                showGroupSettingsDialog = chatId
                            },
                            onNavigateToGroupInfo = onNavigateToGroupInfo,
                            showBackButton = false
                        )
                    }
                } else {
                    // Empty state when no chat is selected
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
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
    } else {
        // Single Pane Layout (Phone / Portrait)
        Surface(modifier = Modifier.fillMaxSize()) {
            if (selectedChatId != null) {
                ChatScreen(
                    chatId = selectedChatId!!,
                    onNavigateBack = { selectedChatId = null },
                    onNavigateToGroupSettings = { chatId -> 
                        showGroupSettingsDialog = chatId
                    },
                    onNavigateToGroupInfo = onNavigateToGroupInfo
                )
            } else {
                ChatListScreen(
                    onChatClick = { chatId -> 
                        if (chatId > 0) selectedChatId = chatId 
                    },
                    onNavigateToSearch = { showSearchDialog = true },
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToContacts = { showContactsDialog = true },
                    onNavigateToCreateGroup = { showCreateGroupDialog = true },
                    onNavigateToSearchGroups = { showSearchGroupsDialog = true },
                    onNavigateToSettings = onNavigateToSettings,
                    onLogout = onLogout
                )
            }
        }
    }
    
    // Dialog Configuration based on screen mode
    val dialogWidthFraction = if (useSplitScreen) 0.375f else 1.0f
    val dialogPadding = if (useSplitScreen) 16.dp else 0.dp
    val dialogShape = if (useSplitScreen) MaterialTheme.shapes.large else RectangleShape

    // Contacts Dialog
    if (showContactsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showContactsDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(dialogWidthFraction)
                    .padding(vertical = dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                ContactsScreen(
                    onNavigateBack = { showContactsDialog = false },
                    onStartChat = { chatId ->
                        if (chatId > 0) {
                            selectedChatId = chatId
                            showContactsDialog = false
                        }
                    },
                    onNavigateToSearch = {
                        showContactsDialog = false
                        onNavigateToSearch()
                    }
                )
            }
        }
    }
    
    // Search Dialog
    if (showSearchDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSearchDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(dialogWidthFraction)
                    .padding(vertical = dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                SearchScreen(
                    onNavigateBack = { showSearchDialog = false },
                    onUserClick = { },
                    onNavigateToQRScanner = { },
                    onChatCreated = { chatId ->
                        if (chatId > 0) {
                            selectedChatId = chatId
                            showSearchDialog = false
                        }
                    }
                )
            }
        }
    }
    
    // Create Group Dialog
    if (showCreateGroupDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreateGroupDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (useSplitScreen) 0.3f else 1.0f)
                    .padding(vertical = dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                CreateGroupScreen(
                    onNavigateBack = { showCreateGroupDialog = false },
                    onGroupCreated = { chatId ->
                        if (chatId > 0) {
                            selectedChatId = chatId
                            showCreateGroupDialog = false
                        }
                    }
                )
            }
        }
    }
    
    // Search Groups Dialog
    if (showSearchGroupsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSearchGroupsDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(dialogWidthFraction)
                    .padding(vertical = dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                SearchGroupsScreen(
                    onNavigateBack = { showSearchGroupsDialog = false },
                    onGroupClick = { chatId ->
                        if (chatId > 0) {
                            selectedChatId = chatId
                            showSearchGroupsDialog = false
                        }
                    }
                )
            }
        }
    }
    
    // Group Settings Dialog
    if (showGroupSettingsDialog != null) {
        val groupIdToShow = showGroupSettingsDialog!!
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showGroupSettingsDialog = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(dialogWidthFraction)
                    .padding(vertical = dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                GroupSettingsScreen(
                    groupId = groupIdToShow,
                    onNavigateBack = { showGroupSettingsDialog = null },
                    onEditGroup = { groupId ->
                        android.util.Log.d("MainScreen", "Edit group: $groupId")
                        showGroupSettingsDialog = null // Close the dialog
                        onNavigateToEditGroup(groupId)
                    }
                )
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
    )
}
