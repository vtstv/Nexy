/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.ChatFolder

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

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
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
                    text = "Create folders for different groups of chats and quickly switch between them.",
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
                itemsIndexed(folders) { _, folder ->
                    FolderListItem(
                        folder = folder,
                        onClick = { onEditFolder(folder.id) },
                        onDelete = { folderToDelete = folder }
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
private fun FolderListItem(
    folder: ChatFolder,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(folder.name) },
        supportingContent = {
            val chatCount = folder.includedChatIds?.size ?: 0
            Text("$chatCount chats")
        },
        leadingContent = {
            Text(
                text = folder.icon,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
