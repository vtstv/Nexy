/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.main.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexy.client.ui.screens.chat.ChatScreen
import com.nexy.client.ui.screens.contacts.ContactsScreen
import com.nexy.client.ui.screens.group.CreateGroupScreen
import com.nexy.client.ui.screens.group.GroupSettingsScreen
import com.nexy.client.ui.screens.group.SearchGroupsScreen
import com.nexy.client.ui.screens.search.SearchScreen

@Composable
fun MainScreenDialogs(
    screenConfig: ScreenConfig,
    showContactsDialog: Boolean,
    showSearchDialog: Boolean,
    showCreateGroupDialog: Boolean,
    showSearchGroupsDialog: Boolean,
    showGroupSettingsDialog: Int?,
    onContactsDialogDismiss: () -> Unit,
    onSearchDialogDismiss: () -> Unit,
    onCreateGroupDialogDismiss: () -> Unit,
    onSearchGroupsDialogDismiss: () -> Unit,
    onGroupSettingsDialogDismiss: () -> Unit,
    onChatSelected: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToEditGroup: (Int) -> Unit,
    onRefreshChats: () -> Unit
) {
    val dialogShape = if (screenConfig.useSplitScreen) MaterialTheme.shapes.large else RectangleShape
    
    // Contacts Dialog
    if (showContactsDialog) {
        Dialog(
            onDismissRequest = onContactsDialogDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(screenConfig.dialogWidthFraction)
                    .padding(vertical = screenConfig.dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                ContactsScreen(
                    onNavigateBack = onContactsDialogDismiss,
                    onStartChat = { chatId ->
                        if (chatId > 0) {
                            onChatSelected(chatId)
                            onContactsDialogDismiss()
                        }
                    },
                    onNavigateToSearch = {
                        onContactsDialogDismiss()
                        onNavigateToSearch()
                    }
                )
            }
        }
    }
    
    // Search Dialog
    if (showSearchDialog) {
        Dialog(
            onDismissRequest = onSearchDialogDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(screenConfig.dialogWidthFraction)
                    .padding(vertical = screenConfig.dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                SearchScreen(
                    onNavigateBack = onSearchDialogDismiss,
                    onUserClick = { },
                    onNavigateToQRScanner = { },
                    onChatCreated = { chatId ->
                        if (chatId > 0) {
                            onChatSelected(chatId)
                            onSearchDialogDismiss()
                        }
                    }
                )
            }
        }
    }
    
    // Create Group Dialog
    if (showCreateGroupDialog) {
        Dialog(
            onDismissRequest = onCreateGroupDialogDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (screenConfig.useSplitScreen) 0.3f else 1.0f)
                    .padding(vertical = screenConfig.dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                CreateGroupScreen(
                    onNavigateBack = onCreateGroupDialogDismiss,
                    onGroupCreated = { chatId ->
                        if (chatId > 0) {
                            onRefreshChats()
                            onChatSelected(chatId)
                            onCreateGroupDialogDismiss()
                        }
                    }
                )
            }
        }
    }
    
    // Search Groups Dialog
    if (showSearchGroupsDialog) {
        Dialog(
            onDismissRequest = onSearchGroupsDialogDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(screenConfig.dialogWidthFraction)
                    .padding(vertical = screenConfig.dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                SearchGroupsScreen(
                    onNavigateBack = onSearchGroupsDialogDismiss,
                    onGroupClick = { chatId ->
                        if (chatId > 0) {
                            onChatSelected(chatId)
                            onSearchGroupsDialogDismiss()
                        }
                    }
                )
            }
        }
    }
    
    // Group Settings Dialog
    if (showGroupSettingsDialog != null) {
        val groupIdToShow = showGroupSettingsDialog
        Dialog(
            onDismissRequest = onGroupSettingsDialogDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(screenConfig.dialogWidthFraction)
                    .padding(vertical = screenConfig.dialogPadding),
                shape = dialogShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                GroupSettingsScreen(
                    groupId = groupIdToShow,
                    onNavigateBack = onGroupSettingsDialogDismiss,
                    onEditGroup = { groupId ->
                        android.util.Log.d("MainScreen", "Edit group: $groupId")
                        onGroupSettingsDialogDismiss()
                        onNavigateToEditGroup(groupId)
                    }
                )
            }
        }
    }
}
