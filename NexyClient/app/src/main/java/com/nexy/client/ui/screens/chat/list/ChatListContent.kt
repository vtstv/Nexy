/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexy.client.R
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.ChatFolder as ApiFolderModel
import com.nexy.client.ui.screens.chat.ChatWithInfo
import com.nexy.client.ui.screens.chat.list.components.DragState
import com.nexy.client.ui.screens.chat.list.components.FolderTab
import com.nexy.client.ui.screens.chat.list.components.SelectableChatListItem
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
    onMuteSelected: (MuteDuration) -> Unit = {},
    onAddToFolderSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onNavigateToSearch: () -> Unit,
    onNavigateToFolders: () -> Unit = {},
    onAddChatToFolder: (chatId: Int, folderId: Int) -> Unit = { _, _ -> },
    onMoveFolderLocally: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onSaveFolderOrder: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    var dragState by remember { mutableStateOf(DragState()) }
    var accumulatedFolderOffset by remember { mutableFloatStateOf(0f) }
    
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tabWidthPx = with(density) { 100.dp.toPx() }

    val folderTabs = remember(folders) {
        listOf(FolderTab.All) + folders.map { FolderTab.Custom(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Animated switch between Search field and Selection Action Bar
        AnimatedContent(
            targetState = selectionState.isSelectionMode,
            transitionSpec = {
                fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically()
            },
            label = "searchToSelection"
        ) { isSelectionMode ->
            if (isSelectionMode) {
                SelectionActionBar(
                    selectedCount = selectionState.selectedCount,
                    onClose = onClearSelection,
                    onPin = onPinSelected,
                    onMute = onMuteSelected,
                    onAddToFolder = onAddToFolderSelected,
                    onDelete = onDeleteSelected
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, stringResource(R.string.search))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }

        // Folder tabs with drag support for reordering
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 8.dp
        ) {
            folderTabs.forEachIndexed { index, tab ->
                val isDraggingFolder = dragState.draggedFolderIndex == index - 1

                val backgroundColor by animateColorAsState(
                    targetValue = if (isDraggingFolder) 
                        MaterialTheme.colorScheme.surfaceContainerHigh 
                    else 
                        MaterialTheme.colorScheme.surface,
                    label = "tabBg"
                )

                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { 
                        if (!dragState.isDragging) {
                            selectedTabIndex = index 
                        }
                    },
                    text = { 
                        Text(
                            when (tab) {
                                is FolderTab.All -> stringResource(R.string.folder_all)
                                is FolderTab.Custom -> tab.folder.name
                            }
                        )
                    },
                    modifier = Modifier
                        .background(backgroundColor)
                        .then(
                            if (tab is FolderTab.Custom) {
                                Modifier.pointerInput(index, folders.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragState = dragState.copy(
                                                isDragging = true,
                                                draggedFolderIndex = index - 1,
                                                dragOffset = Offset.Zero
                                            )
                                            accumulatedFolderOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            accumulatedFolderOffset += dragAmount.x

                                            val currentIdx = dragState.draggedFolderIndex
                                            if (currentIdx >= 0) {
                                                val threshold = tabWidthPx * 0.5f
                                                when {
                                                    accumulatedFolderOffset > threshold && currentIdx < folders.size - 1 -> {
                                                        onMoveFolderLocally(currentIdx, currentIdx + 1)
                                                        dragState = dragState.copy(draggedFolderIndex = currentIdx + 1)
                                                        accumulatedFolderOffset -= tabWidthPx
                                                    }
                                                    accumulatedFolderOffset < -threshold && currentIdx > 0 -> {
                                                        onMoveFolderLocally(currentIdx, currentIdx - 1)
                                                        dragState = dragState.copy(draggedFolderIndex = currentIdx - 1)
                                                        accumulatedFolderOffset += tabWidthPx
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            onSaveFolderOrder()
                                            dragState = DragState()
                                            accumulatedFolderOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragState = DragState()
                                            accumulatedFolderOffset = 0f
                                        }
                                    )
                                }
                            } else Modifier
                        )
                )
            }

            Tab(
                selected = false,
                onClick = onNavigateToFolders,
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Edit folders",
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }

        // Chat list
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && chats.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (chats.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.no_chats))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onNavigateToSearch) {
                        Text(stringResource(R.string.start_conversation))
                    }
                }
            } else {
                val selectedTab = folderTabs.getOrNull(selectedTabIndex) ?: FolderTab.All
                
                val filteredChats = chats
                    .filter { chatWithInfo ->
                        val matchesSearch = if (searchQuery.isEmpty()) true
                        else chatWithInfo.displayName.contains(searchQuery, ignoreCase = true) ||
                                chatWithInfo.chat.lastMessage?.content?.contains(searchQuery, ignoreCase = true) == true
                        
                        val matchesFolder = when (selectedTab) {
                            is FolderTab.All -> true
                            is FolderTab.Custom -> {
                                val folder = selectedTab.folder
                                val chatId = chatWithInfo.chat.id
                                val chatType = chatWithInfo.chat.type
                                
                                if (folder.excludedChatIds?.contains(chatId) == true) {
                                    false
                                } else if (folder.includedChatIds?.contains(chatId) == true) {
                                    true
                                } else {
                                    val isGroup = chatType == ChatType.GROUP
                                    val isPrivate = chatType == ChatType.PRIVATE
                                    
                                    (folder.includeGroups && isGroup) ||
                                    (folder.includeContacts && isPrivate) ||
                                    (folder.includeNonContacts && isPrivate)
                                }
                            }
                        }
                        
                        matchesSearch && matchesFolder
                    }
                    // Sort: pinned chats first (by pinnedAt desc), then by updatedAt desc
                    .sortedWith(
                        compareByDescending<ChatWithInfo> { it.chat.isPinned }
                            .thenByDescending { if (it.chat.isPinned) it.chat.pinnedAt else 0L }
                            .thenByDescending { it.chat.updatedAt }
                    )
                
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
}
