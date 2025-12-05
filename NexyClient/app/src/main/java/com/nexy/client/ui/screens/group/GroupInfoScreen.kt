package com.nexy.client.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.models.User
import com.nexy.client.ServerConfig
import com.nexy.client.ui.components.OnlineStatusIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chatId: Int,
    onNavigateBack: () -> Unit,
    onGroupLeft: () -> Unit = {},
    onAddParticipant: (Int) -> Unit,
    onParticipantClick: (Int) -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(chatId) {
        viewModel.loadGroupInfo(chatId)
    }

    LaunchedEffect(uiState.isLeftGroup) {
        if (uiState.isLeftGroup) {
            onGroupLeft()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (uiState.isSearching) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text("Search members...") },
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
                        Text("Group Info") 
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSearching) viewModel.toggleSearch() else onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.isSearching) {
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.chat != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header
                if (!uiState.isSearching) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (uiState.chat?.avatarUrl != null) {
                                AsyncImage(
                                    model = ServerConfig.getFileUrl(uiState.chat?.avatarUrl),
                                    contentDescription = "Group Avatar",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(100.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = uiState.chat?.name?.firstOrNull()?.toString() ?: "?",
                                            style = MaterialTheme.typography.displayMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = uiState.chat?.name ?: "Unknown Group",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (!uiState.chat?.description.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.chat?.description!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "${uiState.participants.size} participants",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Actions
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (uiState.isMember) {
                                ListItem(
                                    headlineContent = { Text("Add Participants") },
                                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                                    modifier = Modifier.clickable { onAddParticipant(chatId) }
                                )
                                Divider()
                                ListItem(
                                    headlineContent = { Text("Leave Group", color = MaterialTheme.colorScheme.error) },
                                    leadingContent = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    modifier = Modifier.clickable { viewModel.leaveGroup() }
                                )
                                Divider()
                            } else if (uiState.chat?.groupType == GroupType.PUBLIC_GROUP) {
                                ListItem(
                                    headlineContent = { Text("Join Group", color = MaterialTheme.colorScheme.primary) },
                                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { viewModel.joinGroup() }
                                )
                                Divider()
                            }
                        }
                    }

                    // Participants List Header
                    item {
                        Text(
                            text = "Participants",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                val displayParticipants = if (uiState.isSearching) uiState.searchResults else uiState.participants
                
                if (uiState.isSearching && displayParticipants.isEmpty() && uiState.searchQuery.length > 2) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No members found")
                        }
                    }
                }

                items(displayParticipants) { user ->
                    ParticipantItem(
                        user = user,
                        isOwner = uiState.chat?.createdBy == user.id,
                        onClick = { onParticipantClick(user.id) }
                    )
                }
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ParticipantItem(
    user: User,
    isOwner: Boolean = false,
    onClick: () -> Unit
) {
    // Show displayName if available, otherwise username
    val displayName = if (!user.displayName.isNullOrEmpty()) user.displayName else user.username
    
    ListItem(
        headlineContent = { Text(displayName) },
        supportingContent = { 
            if (isOwner) {
                Text("Owner", color = MaterialTheme.colorScheme.primary)
            } else {
                // Show online status component
                user.onlineStatus?.let { status ->
                    if (status.isNotEmpty()) {
                        OnlineStatusIndicator(
                            onlineStatus = status,
                            showDot = true,
                            showText = true
                        )
                    }
                }
            }
        },
        leadingContent = {
            val avatarUrl = ServerConfig.getFileUrl(user.avatarUrl)
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayName.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
