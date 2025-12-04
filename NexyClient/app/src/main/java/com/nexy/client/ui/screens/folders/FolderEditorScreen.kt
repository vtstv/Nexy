/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditorScreen(
    folderId: Int?,
    onNavigateBack: () -> Unit,
    onAddChats: (Int) -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val currentFolder by viewModel.currentFolder.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val savedFolderId by viewModel.savedFolderId.collectAsState()
    
    // Load folder if editing
    LaunchedEffect(folderId) {
        if (folderId != null) {
            viewModel.loadFolderForEdit(folderId)
        } else {
            viewModel.clearCurrentFolder()
        }
        viewModel.loadAllChats()
    }
    
    // Navigate back after save
    LaunchedEffect(savedFolderId) {
        if (savedFolderId != null) {
            onNavigateBack()
        }
    }
    
    var name by remember(currentFolder) { mutableStateOf(currentFolder?.name ?: "") }
    var icon by remember(currentFolder) { mutableStateOf(currentFolder?.icon ?: "📁") }
    var selectedColor by remember(currentFolder) { mutableStateOf(currentFolder?.color ?: "#2196F3") }
    
    var showIconPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isNewFolder = folderId == null
    val canSave = name.isNotBlank()
    
    val includedChats = remember(currentFolder, allChats) {
        val ids = currentFolder?.includedChatIds ?: emptyList()
        allChats.filter { it.id in ids }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewFolder) "New Folder" else "Edit Folder") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNewFolder) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(
                        onClick = {
                            if (canSave) {
                                if (isNewFolder) {
                                    viewModel.createFolder(name, icon, selectedColor)
                                } else {
                                    viewModel.updateFolder(folderId!!, name, icon, selectedColor)
                                }
                            }
                        },
                        enabled = canSave
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Folder name input
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                
                // Icon and color row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Icon picker button
                        OutlinedCard(
                            onClick = { showIconPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = icon,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Icon")
                            }
                        }
                        
                        // Color picker button
                        OutlinedCard(
                            onClick = { showColorPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(parseColor(selectedColor))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Color")
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                // Included chats section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Included Chats",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!isNewFolder && folderId != null) {
                            TextButton(onClick = { onAddChats(folderId) }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }
                }
                
                if (isNewFolder) {
                    item {
                        Text(
                            text = "Save folder first to add chats",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else if (includedChats.isEmpty()) {
                    item {
                        Text(
                            text = "No chats added yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(includedChats) { chat ->
                        IncludedChatItem(
                            chat = chat,
                            onRemove = {
                                viewModel.removeChatFromFolder(folderId!!, chat.id)
                            }
                        )
                    }
                }
            }
        }
        
        // Icon picker dialog
        if (showIconPicker) {
            IconPickerDialog(
                selectedIcon = icon,
                onIconSelected = { 
                    icon = it
                    showIconPicker = false
                },
                onDismiss = { showIconPicker = false }
            )
        }
        
        // Color picker dialog
        if (showColorPicker) {
            ColorPickerDialog(
                selectedColor = selectedColor,
                onColorSelected = {
                    selectedColor = it
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog && folderId != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Folder") },
                text = { Text("Are you sure you want to delete this folder? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteFolder(folderId)
                            onNavigateBack()
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun IconPickerDialog(
    selectedIcon: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val icons = listOf(
        "📁", "📂", "📌", "⭐", "❤️", "💼", "🎯", "🎮",
        "🎵", "📷", "🎬", "📚", "✈️", "🏠", "💰", "🛒",
        "👥", "👤", "💬", "📱", "💻", "🔒", "🔑", "⚙️",
        "🎁", "🎉", "🌟", "🔥", "💡", "📝", "✅", "❌"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.height(200.dp)
            ) {
                items(icons) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (emoji == selectedIcon) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else Color.Transparent
                            )
                            .clickable { onIconSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorPickerDialog(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(colors) { color ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(parseColor(color))
                            .then(
                                if (color == selectedColor) {
                                    Modifier.border(3.dp, Color.White, CircleShape)
                                } else Modifier
                            )
                            .clickable { onColorSelected(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (color == selectedColor) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IncludedChatItem(
    chat: Chat,
    onRemove: () -> Unit
) {
    ListItem(
        headlineContent = { Text(chat.name ?: chat.username ?: "Chat") },
        supportingContent = {
            val typeLabel = when (chat.type) {
                ChatType.GROUP -> "Group"
                ChatType.PRIVATE -> "Contact"
            }
            Text(typeLabel, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (chat.name ?: chat.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove")
            }
        }
    )
}

private fun parseColor(hexColor: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexColor))
    } catch (e: Exception) {
        Color(0xFF2196F3)
    }
}
