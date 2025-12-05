/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexy.client.R
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.ChatFolder as ApiFolderModel
import com.nexy.client.ui.screens.chat.ChatWithInfo
import com.nexy.client.ui.screens.chat.list.components.DragState
import com.nexy.client.ui.screens.chat.list.components.DraggableChatListItem
import com.nexy.client.ui.screens.chat.list.components.FolderTab

private const val TAG = "ChatListContent"

@Composable
fun ChatListContent(
    padding: PaddingValues,
    chats: List<ChatWithInfo>,
    isLoading: Boolean,
    folders: List<ApiFolderModel> = emptyList(),
    onChatClick: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToFolders: () -> Unit = {},
    onAddChatToFolder: (chatId: Int, folderId: Int) -> Unit = { _, _ -> },
    onMoveFolderLocally: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onSaveFolderOrder: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    // Drag state
    var dragState by remember { mutableStateOf(DragState()) }
    var folderTabPositions by remember { mutableStateOf<Map<Int, Pair<Float, Float>>>(emptyMap()) }
    var accumulatedFolderOffset by remember { mutableFloatStateOf(0f) }
    
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tabWidthPx = with(density) { 100.dp.toPx() }

    // Create folder tabs: All + user folders
    val folderTabs = remember(folders) {
        listOf(FolderTab.All) + folders.map { FolderTab.Custom(it) }
    }
    
    // Find which folder tab is under the drag position
    fun findFolderUnderDrag(position: Offset): Int? {
        folderTabPositions.forEach { (index, bounds) ->
            val (x, width) = bounds
            if (position.x >= x && position.x <= x + width && index > 0) {
                return index
            }
        }
        return null
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Search bar
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

        // Dynamic folder tabs with drag support
        Box {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp
            ) {
                folderTabs.forEachIndexed { index, tab ->
                    val isDropTarget = dragState.isDragging && 
                        dragState.draggedChatId != null && 
                        findFolderUnderDrag(dragState.dragOffset + dragState.startPosition) == index
                    
                    val isDraggingFolder = dragState.draggedFolderIndex == index
                    
                    val backgroundColor by animateColorAsState(
                        targetValue = when {
                            isDropTarget -> MaterialTheme.colorScheme.primaryContainer
                            isDraggingFolder -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surface
                        },
                        label = "tabBg"
                    )
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isDropTarget) 1.1f else 1f,
                        label = "tabScale"
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
                                },
                                modifier = Modifier.scale(scale)
                            )
                        },
                        modifier = Modifier
                            .background(backgroundColor)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                folderTabPositions = folderTabPositions + (index to Pair(pos.x, coords.size.width.toFloat()))
                            }
                            .then(
                                if (tab is FolderTab.Custom) {
                                    Modifier.pointerInput(index, folders.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                dragState = dragState.copy(
                                                    isDragging = true,
                                                    draggedFolderIndex = index - 1, // -1 because "All" is at 0
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
                
                // Add folder tab (icon only)
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
            
            // Drop indicator when dragging chat
            if (dragState.isDragging && dragState.draggedChatId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                )
            }
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
                                
                                // Check if explicitly excluded
                                if (folder.excludedChatIds?.contains(chatId) == true) {
                                    false
                                }
                                // Check if explicitly included
                                else if (folder.includedChatIds?.contains(chatId) == true) {
                                    true
                                }
                                // Check by chat type filters
                                else {
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
                    .sortedByDescending { it.chat.updatedAt }
                
                LazyColumn {
                    items(filteredChats, key = { it.chat.id }) { chatWithInfo ->
                        val isDragging = dragState.draggedChatId == chatWithInfo.chat.id
                        
                        DraggableChatListItem(
                            chatWithInfo = chatWithInfo,
                            isDragging = isDragging,
                            dragOffset = if (isDragging) dragState.dragOffset else Offset.Zero,
                            onClick = { 
                                if (!dragState.isDragging) {
                                    onChatClick(chatWithInfo.chat.id) 
                                }
                            },
                            onDragStart = { startPos ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragState = DragState(
                                    isDragging = true,
                                    draggedChatId = chatWithInfo.chat.id,
                                    startPosition = startPos,
                                    dragOffset = Offset.Zero
                                )
                            },
                            onDrag = { offset ->
                                dragState = dragState.copy(
                                    dragOffset = dragState.dragOffset + offset
                                )
                            },
                            onDragEnd = {
                                val targetFolder = findFolderUnderDrag(dragState.dragOffset + dragState.startPosition)
                                if (targetFolder != null && targetFolder > 0) {
                                    val folderTab = folderTabs[targetFolder]
                                    if (folderTab is FolderTab.Custom) {
                                        Log.d(TAG, "Adding chat ${chatWithInfo.chat.id} to folder ${folderTab.folder.id}")
                                        onAddChatToFolder(chatWithInfo.chat.id, folderTab.folder.id)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                dragState = DragState()
                            },
                            onDragCancel = {
                                dragState = DragState()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
