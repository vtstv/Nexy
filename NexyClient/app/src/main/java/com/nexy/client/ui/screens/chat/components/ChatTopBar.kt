package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nexy.client.ServerConfig
import com.nexy.client.data.models.ChatType
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    chatName: String,
    chatAvatarUrl: String? = null,
    chatType: ChatType = ChatType.PRIVATE,
    isCreator: Boolean = false,
    isSearching: Boolean = false,
    searchQuery: String = "",
    isTyping: Boolean = false,
    onSearchClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onNavigateBack: () -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit,
    onChatInfoClick: () -> Unit,
    onCallClick: (() -> Unit)? = null,
    onGroupSettingsClick: (() -> Unit)? = null,
    showBackButton: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    
    TopAppBar(
        title = { 
            if (isSearching) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChatInfoClick)
                ) {
                    // Avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarUrl = ServerConfig.getFileUrl(chatAvatarUrl)
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = chatName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = chatName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isTyping) {
                            Text(
                                text = "Typing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            Text(
                                text = "Tap for info",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = {
                    if (isSearching) onSearchClick() else onNavigateBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        },
        actions = {
            if (!isSearching) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            
            if (onCallClick != null && !isSearching) {
                IconButton(onClick = onCallClick) {
                    Icon(Icons.Default.Call, contentDescription = "Call")
                }
            }
            
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, "Menu")
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // Group Settings (only for group chats)
                if (chatType == ChatType.GROUP && onGroupSettingsClick != null) {
                    DropdownMenuItem(
                        text = { Text("Group Settings") },
                        onClick = {
                            showMenu = false
                            onGroupSettingsClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Settings, null)
                        }
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text("Chat Info") },
                    onClick = {
                        showMenu = false
                        onChatInfoClick()
                    }
                )
                
                // Only show Clear/Delete for private chats or if user is creator of group
                if (chatType == ChatType.PRIVATE || isCreator) {
                    DropdownMenuItem(
                        text = { Text("Clear chat") },
                        onClick = {
                            showMenu = false
                            showClearDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete chat") },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    )
    
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat") },
            text = { Text("Are you sure you want to clear all messages from this chat?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearChat()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteChat()
                    }
                ) {
                    Text("Delete")
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

