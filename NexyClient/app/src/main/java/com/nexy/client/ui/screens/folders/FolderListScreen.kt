/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.ChatFolder

private const val TAG = "FolderListScreen"

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
    
    // Drag state - keep track of folder ID instead of index
    var draggedFolderId by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var accumulatedOffset by remember { mutableFloatStateOf(0f) }
    var hasMoved by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val itemHeightPx = with(LocalDensity.current) { 72.dp.toPx() }

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    // Function to get current index of dragged folder
    fun getCurrentDragIndex(): Int {
        return folders.indexOfFirst { it.id == draggedFolderId }
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Description
            item(key = "description") {
                Text(
                    text = "Create folders for different groups of chats and quickly switch between them.\n\nHold ≡ and drag to reorder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            // Folders header
            item(key = "header") {
                Text(
                    text = "Your Folders",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // "All Chats" - always first, non-editable
            item(key = "all_chats") {
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
                item(key = "loading") {
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
                item(key = "empty") {
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
                    key = { _, folder -> "folder_${folder.id}" }
                ) { index, folder ->
                    val isDragging = draggedFolderId == folder.id
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.02f else 1f,
                        label = "scale"
                    )
                    
                    val elevation = if (isDragging) 8.dp else 0.dp
                    val zIndex = if (isDragging) 1f else 0f
                    val translationY = if (isDragging) dragOffsetY else 0f
                    
                    val backgroundColor = when {
                        isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Box(
                        modifier = Modifier
                            .zIndex(zIndex)
                            .graphicsLayer {
                                this.translationY = translationY
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
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
                                    // Drag handle
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isDragging) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(32.dp)
                                            .pointerInput(folder.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        Log.d(TAG, "Drag start: folder ${folder.id} at index $index")
                                                        draggedFolderId = folder.id
                                                        accumulatedOffset = 0f
                                                        dragOffsetY = 0f
                                                        hasMoved = false
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        accumulatedOffset += dragAmount.y
                                                        dragOffsetY = accumulatedOffset
                                                        
                                                        val currentIndex = getCurrentDragIndex()
                                                        if (currentIndex >= 0) {
                                                            val threshold = itemHeightPx * 0.5f
                                                            
                                                            when {
                                                                accumulatedOffset > threshold && currentIndex < folders.size - 1 -> {
                                                                    Log.d(TAG, "Moving down: $currentIndex -> ${currentIndex + 1}")
                                                                    viewModel.moveFolderLocally(currentIndex, currentIndex + 1)
                                                                    accumulatedOffset -= itemHeightPx
                                                                    dragOffsetY = accumulatedOffset
                                                                    hasMoved = true
                                                                }
                                                                accumulatedOffset < -threshold && currentIndex > 0 -> {
                                                                    Log.d(TAG, "Moving up: $currentIndex -> ${currentIndex - 1}")
                                                                    viewModel.moveFolderLocally(currentIndex, currentIndex - 1)
                                                                    accumulatedOffset += itemHeightPx
                                                                    dragOffsetY = accumulatedOffset
                                                                    hasMoved = true
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        Log.d(TAG, "Drag end: folder ${folder.id}, hasMoved=$hasMoved")
                                                        draggedFolderId = -1
                                                        dragOffsetY = 0f
                                                        accumulatedOffset = 0f
                                                        if (hasMoved) {
                                                            viewModel.saveFolderOrder()
                                                        }
                                                        hasMoved = false
                                                    },
                                                    onDragCancel = {
                                                        Log.d(TAG, "Drag cancel")
                                                        draggedFolderId = -1
                                                        dragOffsetY = 0f
                                                        accumulatedOffset = 0f
                                                        hasMoved = false
                                                    }
                                                )
                                            }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = folder.icon,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onEditFolder(folder.id) }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit"
                                        )
                                    }
                                    IconButton(onClick = { folderToDelete = folder }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .shadow(elevation)
                                .background(backgroundColor)
                        )
                    }
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
