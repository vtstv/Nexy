/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexy.client.R
import com.nexy.client.data.models.ChatType
import com.nexy.client.data.models.ChatFolder as ApiFolderModel
import com.nexy.client.ui.screens.chat.ChatWithInfo

// Built-in folder type for filtering
sealed class FolderTab {
    object All : FolderTab()
    data class Custom(val folder: ApiFolderModel) : FolderTab()
}

@Composable
fun ChatListContent(
    padding: PaddingValues,
    chats: List<ChatWithInfo>,
    isLoading: Boolean,
    folders: List<ApiFolderModel> = emptyList(),
    onChatClick: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToFolders: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Create folder tabs: All + user folders
    val folderTabs = remember(folders) {
        listOf(FolderTab.All) + folders.map { FolderTab.Custom(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Search bar
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

        // Dynamic folder tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 8.dp
        ) {
            folderTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { 
                        Text(
                            when (tab) {
                                is FolderTab.All -> stringResource(R.string.folder_all)
                                is FolderTab.Custom -> tab.folder.name
                            }
                        )
                    }
                )
            }
            
            // Add folder tab (icon only)
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
                                
                                // Check if explicitly excluded
                                if (folder.excludedChatIds?.contains(chatId) == true) {
                                    false
                                }
                                // Check if explicitly included
                                else if (folder.includedChatIds?.contains(chatId) == true) {
                                    true
                                }
                                // Check by chat type filters
                                else {
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
                    .sortedByDescending { it.chat.updatedAt }
                
                LazyColumn {
                    items(filteredChats) { chatWithInfo ->
                        ChatListItem(
                            chatWithInfo = chatWithInfo,
                            onClick = { onChatClick(chatWithInfo.chat.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
