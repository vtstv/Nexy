/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.folders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.ChatType
import com.nexy.client.ui.screens.folders.components.ChatTypeQuickFilter
import com.nexy.client.ui.screens.folders.components.ChatTypeToggle
import com.nexy.client.ui.screens.folders.components.SelectableChatItem
import com.nexy.client.ui.screens.folders.components.SelectedChatChip

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
            ChatSelectorTopBar(
                selection = selection,
                includeAllPrivate = includeAllPrivate,
                includeAllGroups = includeAllGroups,
                onNavigateBack = onNavigateBack,
                onSave = {
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
            )
        }
    ) { paddingValues ->
        ChatSelectorContent(
            paddingValues = paddingValues,
            isLoading = isLoading,
            selection = selection,
            selectableChats = selectableChats,
            groupedChats = groupedChats,
            includeAllPrivate = includeAllPrivate,
            includeAllGroups = includeAllGroups,
            onSelectionChange = { selection = it },
            onIncludeAllPrivateChange = { includeAllPrivate = it },
            onIncludeAllGroupsChange = { includeAllGroups = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSelectorTopBar(
    selection: Set<Int>,
    includeAllPrivate: Boolean,
    includeAllGroups: Boolean,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit
) {
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
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Check, contentDescription = "Done")
            }
        }
    )
}

@Composable
private fun ChatSelectorContent(
    paddingValues: PaddingValues,
    isLoading: Boolean,
    selection: Set<Int>,
    selectableChats: List<SelectableChat>,
    groupedChats: Map<String, List<SelectableChat>>,
    includeAllPrivate: Boolean,
    includeAllGroups: Boolean,
    onSelectionChange: (Set<Int>) -> Unit,
    onIncludeAllPrivateChange: (Boolean) -> Unit,
    onIncludeAllGroupsChange: (Boolean) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (selection.isNotEmpty()) {
            item {
                SelectedChatsRow(
                    selection = selection,
                    selectableChats = selectableChats,
                    onRemove = { chatId -> onSelectionChange(selection - chatId) }
                )
                HorizontalDivider()
            }
        }

        item {
            SectionHeader("Auto-include filters")
        }

        item {
            ChatTypeToggle(
                label = "All Private Chats",
                description = "Automatically include all private chats",
                icon = Icons.Default.Person,
                iconColor = Color(0xFF2196F3),
                isChecked = includeAllPrivate,
                onCheckedChange = onIncludeAllPrivateChange
            )
        }

        item {
            ChatTypeToggle(
                label = "All Groups",
                description = "Automatically include all groups",
                icon = Icons.Default.Group,
                iconColor = Color(0xFF4CAF50),
                isChecked = includeAllGroups,
                onCheckedChange = onIncludeAllGroupsChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            SectionHeader("Quick selection")
        }

        item {
            ChatTypeQuickFilter(
                label = "Select all Private Chats",
                icon = Icons.Default.Person,
                iconColor = Color(0xFF2196F3),
                isSelected = false,
                onClick = { 
                    val privateChats = selectableChats
                        .filter { it.chat.type == ChatType.PRIVATE }
                        .map { it.chat.id }
                    onSelectionChange(selection + privateChats)
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
                    val groupChats = selectableChats
                        .filter { it.chat.type == ChatType.GROUP }
                        .map { it.chat.id }
                    onSelectionChange(selection + groupChats)
                }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        groupedChats.forEach { (groupName, chats) ->
            item {
                SectionHeader(groupName)
            }

            items(chats) { item ->
                SelectableChatItem(
                    item = item,
                    isSelected = item.chat.id in selection,
                    onClick = {
                        onSelectionChange(
                            if (item.chat.id in selection) selection - item.chat.id
                            else selection + item.chat.id
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectedChatsRow(
    selection: Set<Int>,
    selectableChats: List<SelectableChat>,
    onRemove: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(selection.toList()) { chatId ->
            val item = selectableChats.find { it.chat.id == chatId }
            if (item != null) {
                SelectedChatChip(
                    item = item,
                    onRemove = { onRemove(chatId) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
