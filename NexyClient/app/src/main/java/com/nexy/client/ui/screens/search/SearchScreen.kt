package com.nexy.client.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.User
import com.nexy.client.ui.screens.search.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onNavigateToQRScanner: () -> Unit,
    onChatCreated: (Int) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedUserForPreview by remember { mutableStateOf<User?>(null) }
    val tabs = listOf("Chats", "Media", "Links", "Files", "Music", "Voice")
    
    // Show snackbar for messages
    LaunchedEffect(uiState.error, uiState.successMessage) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    
    if (selectedUserForPreview != null) {
        UserProfileDialog(
            user = selectedUserForPreview!!,
            onDismiss = { selectedUserForPreview = null },
            onStartChat = {
                selectedUserForPreview?.let { user ->
                    val userId = user.id
                    selectedUserForPreview = null
                    viewModel.saveCurrentQuery()
                    viewModel.createChat(userId) { chatId ->
                        onChatCreated(chatId)
                    }
                }
            }
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SearchTopBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onNavigateBack = onNavigateBack,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                tabs = tabs
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.users.isNotEmpty() || uiState.groups.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.users.isNotEmpty()) {
                        item {
                            Text(
                                text = "Users",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(uiState.users) { user ->
                            UserListItem(
                                user = user,
                                onAddContact = { 
                                    viewModel.saveCurrentQuery()
                                    viewModel.addContact(user.id) 
                                },
                                onStartChat = { 
                                    viewModel.saveCurrentQuery()
                                    viewModel.createChat(user.id) { chatId ->
                                        onChatCreated(chatId)
                                    }
                                },
                                onViewProfile = { 
                                    selectedUserForPreview = user
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    
                    if (uiState.groups.isNotEmpty()) {
                        item {
                            Text(
                                text = "Groups",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(uiState.groups) { chat ->
                            GroupListItem(
                                chat = chat,
                                onClick = { 
                                    viewModel.saveCurrentQuery()
                                    onChatCreated(chat.id) 
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else if (uiState.query.isEmpty()) {
                // Recent searches
                if (uiState.recentSearches.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                TextButton(onClick = { viewModel.clearHistory() }) {
                                    Text("Clear All")
                                }
                            }
                        }
                        
                        items(uiState.recentSearches) { query ->
                            RecentSearchItem(
                                query = query,
                                onClick = { viewModel.onQueryChange(query) },
                                onDelete = { viewModel.deleteHistoryItem(query) }
                            )
                        }
                    }
                } else {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent searches",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (uiState.query.length >= 2) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No results found")
                }
            }
        }
    }
}
