/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.nexy.client.data.models.GroupType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSelectorScreen(
    folderId: Int,
    onNavigateBack: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val allChats by viewModel.allChats.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LaunchedEffect(folderId) {
        viewModel.loadFolderForEdit(folderId)
        viewModel.loadAllChats()
    }
    
    val initialSelection = remember(currentFolder) {
        currentFolder?.includedChatIds?.toSet() ?: emptySet()
    }
    var selection by remember(initialSelection) { mutableStateOf(initialSelection) }
    
    val groupedChats = remember(allChats) {
        allChats.groupBy { chat ->
            when (chat.type) {
                ChatType.GROUP -> "Groups"
                ChatType.PRIVATE -> "Chats"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Add Chats")
                        Text(
                            "${selection.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.updateFolderChats(folderId, selection.toList())
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done")
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
                // Selected chips
                if (selection.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selection.toList()) { chatId ->
                                val chat = allChats.find { it.id == chatId }
                                if (chat != null) {
                                    SelectedChatChip(
                                        chat = chat,
                                        onRemove = { selection = selection - chatId }
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }

                // Chat types section
                item {
                    Text(
                        text = "Chat types",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Quick filter buttons for chat types
                item {
                    ChatTypeQuickFilter(
                        label = "All Private Chats",
                        icon = Icons.Default.Person,
                        iconColor = Color(0xFF2196F3),
                        isSelected = false,
                        onClick = { 
                            val privateChats = allChats.filter { 
                                it.type == ChatType.PRIVATE 
                            }.map { it.id }
                            selection = selection + privateChats
                        }
                    )
                }

                item {
                    ChatTypeQuickFilter(
                        label = "All Groups",
                        icon = Icons.Default.Group,
                        iconColor = Color(0xFF4CAF50),
                        isSelected = false,
                        onClick = { 
                            val groupChats = allChats.filter { 
                                it.type == ChatType.GROUP
                            }.map { it.id }
                            selection = selection + groupChats
                        }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Chats section
                groupedChats.forEach { (groupName, chats) ->
                    item {
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(chats) { chat ->
                        SelectableChatItem(
                            chat = chat,
                            isSelected = chat.id in selection,
                            onClick = {
                                selection = if (chat.id in selection) {
                                    selection - chat.id
                                } else {
                                    selection + chat.id
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedChatChip(
    chat: Chat,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (chat.name ?: chat.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = (chat.name ?: chat.username ?: "Chat").take(12),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatTypeQuickFilter(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun SelectableChatItem(
    chat: Chat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = chat.name ?: chat.username ?: "Chat",
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        },
        supportingContent = {
            val typeLabel = when (chat.type) {
                ChatType.GROUP -> "Group"
                ChatType.PRIVATE -> "Chat"
            }
            Text(typeLabel, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (chat.name ?: chat.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
