/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.nexy.client.ui.screens.chat.ChatWithInfo

@Composable
fun ChatListContent(
    padding: PaddingValues,
    chats: List<ChatWithInfo>,
    isLoading: Boolean,
    onChatClick: (Int) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf(ChatFolder.ALL) }

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

        // Folders
        TabRow(selectedTabIndex = selectedFolder.ordinal) {
            ChatFolder.values().forEach { folder ->
                Tab(
                    selected = selectedFolder == folder,
                    onClick = { selectedFolder = folder },
                    text = { Text(stringResource(folder.titleRes)) }
                )
            }
        }

        //  Chat list
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
                val filteredChats = chats
                    .filter { chatWithInfo ->
                        val matchesSearch = if (searchQuery.isEmpty()) true
                        else chatWithInfo.displayName.contains(searchQuery, ignoreCase = true) ||
                                chatWithInfo.chat.lastMessage?.content?.contains(searchQuery, ignoreCase = true) == true
                        
                        val matchesFolder = when(selectedFolder) {
                            ChatFolder.ALL -> true
                            ChatFolder.PRIVATE -> chatWithInfo.chat.type == ChatType.PRIVATE
                            ChatFolder.GROUPS -> chatWithInfo.chat.type == ChatType.GROUP
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
