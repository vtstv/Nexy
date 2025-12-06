/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nexy.client.data.models.ChatFolder as ApiFolderModel
import com.nexy.client.ui.screens.chat.list.state.ChatWithInfo
import com.nexy.client.ui.screens.chat.list.components.ChatListBody
import com.nexy.client.ui.screens.chat.list.components.ChatSearchField
import com.nexy.client.ui.screens.chat.list.components.FolderTab
import com.nexy.client.ui.screens.chat.list.components.FolderTabRow
import com.nexy.client.ui.screens.chat.list.selection.ChatSelectionState
import com.nexy.client.ui.screens.chat.list.selection.MuteDuration
import com.nexy.client.ui.screens.chat.list.selection.SelectionActionBar

@Composable
fun ChatListContent(
    padding: PaddingValues,
    chats: List<ChatWithInfo>,
    isLoading: Boolean,
    folders: List<ApiFolderModel> = emptyList(),
    selectionState: ChatSelectionState = ChatSelectionState(),
    onChatClick: (Int) -> Unit,
    onChatLongClick: (ChatWithInfo) -> Unit = {},
    onToggleSelection: (Int) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onPinSelected: () -> Unit = {},
    onUnpinSelected: () -> Unit = {},
    onMuteSelected: (MuteDuration) -> Unit = {},
    onAddToFolderSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onNavigateToSearch: () -> Unit,
    onNavigateToFolders: () -> Unit = {},
    onAddChatToFolder: (chatId: Int, folderId: Int) -> Unit = { _, _ -> },
    onMoveFolderLocally: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onSaveFolderOrder: () -> Unit = {}
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val folderTabs = remember(folders) {
        listOf(FolderTab.All) + folders.map { FolderTab.Custom(it) }
    }
    
    val selectedTab = folderTabs.getOrNull(selectedTabIndex) ?: FolderTab.All

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (selectionState.isSelectionMode) {
            val allSelectedArePinned = selectionState.selectedChatIds.isNotEmpty() &&
                selectionState.selectedChatIds.all { chatId ->
                    chats.find { it.chat.id == chatId }?.chat?.isPinned == true
                }
            
            SelectionActionBar(
                selectedCount = selectionState.selectedCount,
                allSelectedArePinned = allSelectedArePinned,
                onClose = onClearSelection,
                onPin = onPinSelected,
                onUnpin = onUnpinSelected,
                onMute = onMuteSelected,
                onAddToFolder = onAddToFolderSelected,
                onDelete = onDeleteSelected
            )
        }

        FolderTabRow(
            folderTabs = folderTabs,
            selectedTabIndex = selectedTabIndex,
            chats = chats,
            onTabSelected = { selectedTabIndex = it },
            onNavigateToFolders = onNavigateToFolders,
            onMoveFolderLocally = onMoveFolderLocally,
            onSaveFolderOrder = onSaveFolderOrder
        )

        ChatListBody(
            chats = chats,
            searchQuery = "",
            selectedTab = selectedTab,
            isLoading = isLoading,
            selectionState = selectionState,
            onChatClick = onChatClick,
            onChatLongClick = onChatLongClick,
            onToggleSelection = onToggleSelection,
            onNavigateToSearch = onNavigateToSearch
        )
    }
}

@Composable
private fun SearchOrSelectionBar(
    isSelectionMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectionState: ChatSelectionState,
    chats: List<ChatWithInfo>,
    onClearSelection: () -> Unit,
    onPinSelected: () -> Unit,
    onUnpinSelected: () -> Unit,
    onMuteSelected: (MuteDuration) -> Unit,
    onAddToFolderSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    // Deprecated: Logic moved to main content
}

