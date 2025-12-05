/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.ChatFolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    onNavigateBack: () -> Unit,
    onCreateFolder: () -> Unit,
    onEditFolder: (Int) -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var folderToDelete by remember { mutableStateOf<ChatFolder?>(null) }
    
    // Drag and drop state
    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var draggedOverIndex by remember { mutableIntStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    
    val scope = rememberCoroutineScope()
    var reorderJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }
    
    // Save order when dragging ends
    LaunchedEffect(isDragging) {
        if (!isDragging && draggedItemIndex != -1) {
            // Debounce the save
            reorderJob?.cancel()
            reorderJob = scope.launch {
                delay(300)
                viewModel.saveFolderOrder()
            }
            draggedItemIndex = -1
            draggedOverIndex = -1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Folders") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateFolder) {
                Icon(Icons.Default.Add, contentDescription = "Create folder")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Description
            item {
                Text(
                    text = "Create folders for different groups of chats and quickly switch between them.\nLong press and drag to reorder folders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            // Folders header
            item {
                Text(
                    text = "Your Folders",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // "All Chats" - always first, non-editable
            item {
                ListItem(
                    headlineContent = { Text("All Chats") },
                    supportingContent = { Text("All your chats") },
                    leadingContent = {
                        Icon(Icons.Default.Forum, contentDescription = null)
                    }
                )
                HorizontalDivider()
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // User folders
            if (folders.isEmpty() && !isLoading) {
                item {
                    Text(
                        text = "No folders yet. Create your first folder!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = folders,
                    key = { _, folder -> folder.id }
                ) { index, folder ->
                    val isBeingDragged = draggedItemIndex == index
                    val elevation by animateDpAsState(
                        targetValue = if (isBeingDragged) 8.dp else 0.dp,
                        label = "elevation"
                    )
                    
                    DraggableFolderItem(
                        folder = folder,
                        index = index,
                        isDragging = isBeingDragged,
                        dragOffset = if (isBeingDragged) dragOffset else 0f,
                        isDropTarget = draggedOverIndex == index && !isBeingDragged,
                        onClick = { onEditFolder(folder.id) },
                        onDelete = { folderToDelete = folder },
                        onDragStart = { idx ->
                            draggedItemIndex = idx
                            isDragging = true
                        },
                        onDrag = { offset, idx ->
                            dragOffset = offset
                            // Calculate which item we're over
                            val itemHeight = 72f // approximate height of list item
                            val targetIndex = (idx + (offset / itemHeight).toInt()).coerceIn(0, folders.size - 1)
                            if (targetIndex != draggedOverIndex && targetIndex != draggedItemIndex) {
                                draggedOverIndex = targetIndex
                                // Immediately swap items for visual feedback
                                viewModel.moveFolderLocally(draggedItemIndex, targetIndex)
                                draggedItemIndex = targetIndex
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            dragOffset = 0f
                        },
                        modifier = Modifier
                            .shadow(elevation)
                            .zIndex(if (isBeingDragged) 1f else 0f)
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Delete confirmation dialog
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder?") },
            text = { 
                Text("Are you sure you want to delete \"${folder.name}\"? Your chats will not be deleted.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        folderToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DraggableFolderItem(
    folder: ChatFolder,
    index: Int,
    isDragging: Boolean,
    dragOffset: Float,
    isDropTarget: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Float, Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
        isDropTarget -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
    
    ListItem(
        headlineContent = { Text(folder.name) },
        supportingContent = {
            val chatCount = folder.includedChatIds?.size ?: 0
            val filters = mutableListOf<String>()
            if (folder.includeContacts == true) filters.add("Private chats")
            if (folder.includeGroups == true) filters.add("Groups")
            
            val description = if (filters.isNotEmpty()) {
                filters.joinToString(" • ") + if (chatCount > 0) " + $chatCount chats" else ""
            } else {
                "$chatCount chats"
            }
            Text(description)
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = folder.icon,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
        },
        modifier = modifier
            .background(backgroundColor)
            .graphicsLayer {
                translationY = if (isDragging) dragOffset else 0f
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart(index) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y, index)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .clickable { onClick() }
    )
}
