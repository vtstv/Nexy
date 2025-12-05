/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexy.client.R
import com.nexy.client.data.models.ChatType
import com.nexy.client.ui.screens.chat.ChatWithInfo
import com.nexy.client.ui.screens.chat.list.selection.ChatSelectionState

@Composable
fun ChatListBody(
    chats: List<ChatWithInfo>,
    searchQuery: String,
    selectedTab: FolderTab,
    isLoading: Boolean,
    selectionState: ChatSelectionState,
    onChatClick: (Int) -> Unit,
    onChatLongClick: (ChatWithInfo) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading && chats.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (chats.isEmpty()) {
            EmptyChatsPlaceholder(
                onNavigateToSearch = onNavigateToSearch,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val filteredChats = filterAndSortChats(chats, searchQuery, selectedTab)
            
            LazyColumn {
                items(filteredChats, key = { it.chat.id }) { chatWithInfo ->
                    val isSelected = selectionState.isSelected(chatWithInfo.chat.id)
                    
                    SelectableChatListItem(
                        chatWithInfo = chatWithInfo,
                        isSelected = isSelected,
                        isSelectionMode = selectionState.isSelectionMode,
                        onClick = {
                            if (selectionState.isSelectionMode) {
                                onToggleSelection(chatWithInfo.chat.id)
                            } else {
                                onChatClick(chatWithInfo.chat.id)
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onChatLongClick(chatWithInfo)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsPlaceholder(
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.no_chats))
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onNavigateToSearch) {
            Text(stringResource(R.string.start_conversation))
        }
    }
}

private fun filterAndSortChats(
    chats: List<ChatWithInfo>,
    searchQuery: String,
    selectedTab: FolderTab
): List<ChatWithInfo> {
    return chats
        .filter { chatWithInfo ->
            val matchesSearch = if (searchQuery.isEmpty()) true
            else chatWithInfo.displayName.contains(searchQuery, ignoreCase = true) ||
                    chatWithInfo.chat.lastMessage?.content?.contains(searchQuery, ignoreCase = true) == true
            
            val matchesFolder = when (selectedTab) {
                is FolderTab.All -> true
                is FolderTab.Custom -> chatMatchesFolder(chatWithInfo, selectedTab.folder)
            }
            
            matchesSearch && matchesFolder
        }
        .sortedWith(
            compareByDescending<ChatWithInfo> { it.chat.isPinned }
                .thenByDescending { parseTimestampForSort(it.chat.pinnedAt) }
                .thenByDescending { parseTimestampForSort(it.chat.updatedAt) }
        )
}
