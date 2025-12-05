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
    val selectableChats by viewModel.selectableChats.collectAsState()
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
    
    // Filter flags state
    var includeAllPrivate by remember(currentFolder) { 
        mutableStateOf(currentFolder?.includeContacts == true || currentFolder?.includeNonContacts == true) 
    }
    var includeAllGroups by remember(currentFolder) { 
        mutableStateOf(currentFolder?.includeGroups == true) 
    }
    
    val groupedChats = remember(selectableChats) {
        selectableChats.groupBy { it.chat.type }
            .mapKeys { (type, _) ->
                when (type) {
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
                        val filterInfo = buildString {
                            if (includeAllPrivate) append("All Private")
                            if (includeAllGroups) {
                                if (isNotEmpty()) append(" + ")
                                append("All Groups")
                            }
                            if (selection.isNotEmpty()) {
                                if (isNotEmpty()) append(" + ")
                                append("${selection.size} chats")
                            }
                            if (isEmpty()) append("No selection")
                        }
                        Text(
                            filterInfo,
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
                            viewModel.updateFolder(
                                folderId = folderId,
                                name = currentFolder?.name ?: "",
                                icon = currentFolder?.icon ?: "📁",
                                color = currentFolder?.color ?: "#2196F3",
                                includeContacts = includeAllPrivate,
                                includeNonContacts = includeAllPrivate,
                                includeGroups = includeAllGroups
                            )
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
                                val item = selectableChats.find { it.chat.id == chatId }
                                if (item != null) {
                                    SelectedChatChip(
                                        item = item,
                                        onRemove = { selection = selection - chatId }
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }

                // Auto-include filters section
                item {
                    Text(
                        text = "Auto-include filters",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Toggle for All Private Chats
                item {
                    ChatTypeToggle(
                        label = "All Private Chats",
                        description = "Automatically include all private chats",
                        icon = Icons.Default.Person,
                        iconColor = Color(0xFF2196F3),
                        isChecked = includeAllPrivate,
                        onCheckedChange = { includeAllPrivate = it }
                    )
                }

                // Toggle for All Groups
                item {
                    ChatTypeToggle(
                        label = "All Groups",
                        description = "Automatically include all groups",
                        icon = Icons.Default.Group,
                        iconColor = Color(0xFF4CAF50),
                        isChecked = includeAllGroups,
                        onCheckedChange = { includeAllGroups = it }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Quick selection buttons
                item {
                    Text(
                        text = "Quick selection",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    ChatTypeQuickFilter(
                        label = "Select all Private Chats",
                        icon = Icons.Default.Person,
                        iconColor = Color(0xFF2196F3),
                        isSelected = false,
                        onClick = { 
                            val privateChats = selectableChats.filter { 
                                it.chat.type == ChatType.PRIVATE 
                            }.map { it.chat.id }
                            selection = selection + privateChats
                        }
                    )
                }

                item {
                    ChatTypeQuickFilter(
                        label = "Select all Groups",
                        icon = Icons.Default.Group,
                        iconColor = Color(0xFF4CAF50),
                        isSelected = false,
                        onClick = { 
                            val groupChats = selectableChats.filter { 
                                it.chat.type == ChatType.GROUP
                            }.map { it.chat.id }
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

                    items(chats) { item ->
                        SelectableChatItem(
                            item = item,
                            isSelected = item.chat.id in selection,
                            onClick = {
                                selection = if (item.chat.id in selection) {
                                    selection - item.chat.id
                                } else {
                                    selection + item.chat.id
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
    item: SelectableChat,
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
                    text = item.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = item.displayName.take(12),
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
private fun ChatTypeToggle(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { 
            Text(
                description, 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
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
            }
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!isChecked) }
    )
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
        trailingContent = {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add all",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun SelectableChatItem(
    item: SelectableChat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = item.displayName,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        },
        supportingContent = {
            val typeLabel = when (item.chat.type) {
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
                    text = item.displayName.take(1).uppercase(),
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
