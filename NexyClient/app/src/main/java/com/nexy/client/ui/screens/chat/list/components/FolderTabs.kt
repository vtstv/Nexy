/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexy.client.R
import com.nexy.client.data.models.ChatFolder as ApiFolderModel
import com.nexy.client.ui.screens.chat.list.state.ChatWithInfo

@Composable
fun FolderCountBadge(
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
fun FolderTabRow(
    folderTabs: List<FolderTab>,
    selectedTabIndex: Int,
    chats: List<ChatWithInfo>,
    onTabSelected: (Int) -> Unit,
    onNavigateToFolders: () -> Unit,
    onMoveFolderLocally: (fromIndex: Int, toIndex: Int) -> Unit,
    onSaveFolderOrder: () -> Unit
) {
    var dragState by remember { mutableStateOf(DragState()) }
    var accumulatedFolderOffset by remember { mutableFloatStateOf(0f) }
    
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tabWidthPx = with(density) { 100.dp.toPx() }
    
    val folders = folderTabs.filterIsInstance<FolderTab.Custom>().map { it.folder }

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
            FolderTabItem(
                tab = tab,
                index = index,
                isSelected = selectedTabIndex == index,
                isDragging = dragState.isDragging && dragState.draggedFolderIndex == index - 1,
                chats = chats,
                onClick = { if (!dragState.isDragging) onTabSelected(index) },
                onDragStart = { folderIndex ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragState = dragState.copy(
                        isDragging = true,
                        draggedFolderIndex = folderIndex,
                        dragOffset = Offset.Zero
                    )
                    accumulatedFolderOffset = 0f
                },
                onDrag = { dragAmount ->
                    accumulatedFolderOffset += dragAmount
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
}

@Composable
private fun FolderTabItem(
    tab: FolderTab,
    index: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    chats: List<ChatWithInfo>,
    onClick: () -> Unit,
    onDragStart: (folderIndex: Int) -> Unit,
    onDrag: (dragAmountX: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isDragging) 
            MaterialTheme.colorScheme.surfaceContainerHigh 
        else 
            Color.Transparent,
        label = "tabBg"
    )
    
    val folderColor = when (tab) {
        is FolderTab.All -> MaterialTheme.colorScheme.primary
        is FolderTab.Custom -> getFolderColor(tab.folder, index - 1)
    }
    
    val contentColor = if (isSelected) folderColor else MaterialTheme.colorScheme.onSurfaceVariant
    val badgeColor = if (isSelected) folderColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .then(
                if (tab is FolderTab.Custom) {
                    Modifier.pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(index - 1) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel
                        )
                    }
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
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
                    FolderCountBadge(count = chats.size, color = badgeColor)
                }
                is FolderTab.Custom -> {
                    val iconText = tab.folder.icon
                    if (iconText.isNotBlank() && !iconText.matches(Regex("^[a-zA-Z_]+$"))) {
                        Text(text = iconText, style = MaterialTheme.typography.labelLarge)
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
                    val folderChatCount = countChatsInFolder(chats, tab.folder)
                    FolderCountBadge(count = folderChatCount, color = badgeColor)
                }
            }
        }
    }
}
