/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Telegram-style folder colors
private val FolderColors = listOf(
    Color(0xFF3390EC), // Blue (default)
    Color(0xFFEB7B39), // Orange
    Color(0xFFDD4B4E), // Red
    Color(0xFF9D5BD0), // Purple
    Color(0xFF00A884), // Green
    Color(0xFFDB437E), // Pink
    Color(0xFF3FAAE2), // Cyan
    Color(0xFFCCA336)  // Yellow
)

// Get folder color by index or custom color
private fun getFolderColor(folder: ApiFolderModel, index: Int): Color {
    return if (folder.color.isNotBlank()) {
        try {
            Color(android.graphics.Color.parseColor(folder.color))
        } catch (e: Exception) {
            FolderColors[index % FolderColors.size]
        }
    } else {
        FolderColors[index % FolderColors.size]
    }
}

// Get folder icon based on type
private fun getFolderIcon(folder: ApiFolderModel): ImageVector {
    return when {
        folder.icon.isNotBlank() -> {
            // Try to match icon name to Material icon
            when (folder.icon.lowercase()) {
                "person", "user", "contact" -> Icons.Default.Person
                "people", "group", "groups" -> Icons.Default.Groups
                "chat", "message" -> Icons.Default.Chat
                "work", "business" -> Icons.Default.Work
                "star", "favorite" -> Icons.Default.Star
                "bookmark" -> Icons.Default.Bookmark
                "label" -> Icons.Default.Label
                "robot", "bot" -> Icons.Default.SmartToy
                "channel" -> Icons.Default.Campaign
                "unread" -> Icons.Default.MarkUnreadChatAlt
                else -> Icons.Default.Folder
            }
        }
        folder.includeGroups && !folder.includeContacts -> Icons.Default.Groups
        folder.includeContacts && !folder.includeGroups -> Icons.Default.Person
        folder.includeBots -> Icons.Default.SmartToy
        folder.includeChannels -> Icons.Default.Campaign
        else -> Icons.Default.Folder
    }
}

// Parse timestamp string to Long for proper sorting
private fun parseTimestampForSort(timestamp: String?): Long {
    if (timestamp == null) return 0L
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val withoutMillis = timestamp.substringBefore('.')
        sdf.parse(withoutMillis)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

// Count chats that belong to a folder
private fun countChatsInFolder(chats: List<ChatWithInfo>, folder: ApiFolderModel): Int {
    return chats.count { chatWithInfo ->
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

// Telegram-style folder counter badge
@Composable
private fun FolderCountBadge(
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = color
            )
        }
    }
}

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
                // Check if all selected chats are pinned
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
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    val selectedTab = folderTabs.getOrNull(selectedTabIndex)
                    val indicatorColor = when (selectedTab) {
                        is FolderTab.All -> MaterialTheme.colorScheme.primary
                        is FolderTab.Custom -> getFolderColor(selectedTab.folder, selectedTabIndex - 1)
                        null -> MaterialTheme.colorScheme.primary
                    }
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = indicatorColor
                    )
                }
            },
            divider = {} 
        ) {
            folderTabs.forEachIndexed { index, tab ->
                val isDraggingFolder = dragState.isDragging && dragState.draggedFolderIndex == index - 1

                val backgroundColor by animateColorAsState(
                    targetValue = if (isDraggingFolder) 
                        MaterialTheme.colorScheme.surfaceContainerHigh 
                    else 
                        Color.Transparent,
                    label = "tabBg"
                )
                
                // Get folder color (for custom folders)
                val folderColor = when (tab) {
                    is FolderTab.All -> MaterialTheme.colorScheme.primary
                    is FolderTab.Custom -> getFolderColor(tab.folder, index - 1)
                }
                
                // Content color based on selection
                val contentColor = if (selectedTabIndex == index) folderColor else MaterialTheme.colorScheme.onSurfaceVariant
                
                // Badge color - use folder color only when selected, otherwise use muted color
                val badgeColor = if (selectedTabIndex == index) folderColor else MaterialTheme.colorScheme.onSurfaceVariant

                // Custom tab without ripple/indicator effects
                Box(
                    modifier = Modifier
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // No ripple
                        ) {
                            if (!dragState.isDragging) {
                                selectedTabIndex = index
                            }
                        }
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
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Tab content with icon, text and counter badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (tab) {
                            is FolderTab.All -> {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = contentColor
                                )
                                Text(
                                    text = stringResource(R.string.folder_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = contentColor
                                )
                                // Show total chat count for "All" tab
                                FolderCountBadge(
                                    count = chats.size,
                                    color = badgeColor
                                )
                            }
                            is FolderTab.Custom -> {
                                // Show emoji if icon starts with emoji, otherwise show Material icon
                                val iconText = tab.folder.icon
                                if (iconText.isNotBlank() && !iconText.matches(Regex("^[a-zA-Z_]+$"))) {
                                    // It's likely an emoji
                                    Text(
                                        text = iconText,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                } else {
                                    Icon(
                                        imageVector = getFolderIcon(tab.folder),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = contentColor
                                    )
                                }
                                Text(
                                    text = tab.folder.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = contentColor
                                )
                                // Show chat count in this folder
                                val folderChatCount = countChatsInFolder(chats, tab.folder)
                                FolderCountBadge(
                                    count = folderChatCount,
                                    color = badgeColor
                                )
                            }
                        }
                    }
                }
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
                    // Sort: pinned chats first (stable), then by updatedAt desc
                    .sortedWith(
                        compareByDescending<ChatWithInfo> { it.chat.isPinned }
                            .thenByDescending { parseTimestampForSort(it.chat.pinnedAt) }
                            .thenByDescending { parseTimestampForSort(it.chat.updatedAt) }
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
