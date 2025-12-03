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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chatId: Int,
    onNavigateBack: () -> Unit,
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
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.chat?.avatarUrl != null) {
                            AsyncImage(
                                model = uiState.chat?.avatarUrl,
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

                // Participants List
                item {
                    Text(
                        text = "Participants",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                items(uiState.participants) { user ->
                    ParticipantItem(
                        user = user,
                        isOwner = uiState.chat?.createdBy == uiState.currentUserId,
                        canTransferOwnership = uiState.chat?.createdBy == uiState.currentUserId && user.id != uiState.currentUserId,
                        onTransferOwnership = { viewModel.transferOwnership(user.id) },
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
    canTransferOwnership: Boolean = false,
    onTransferOwnership: () -> Unit = {},
    onClick: () -> Unit
) {
    var showTransferDialog by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text(user.displayName ?: user.username) },
        supportingContent = { 
            if (user.status != null) {
                Text(user.status.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        },
        leadingContent = {
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
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
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (user.displayName?.firstOrNull() ?: user.username.firstOrNull() ?: '?').toString().uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        trailingContent = if (canTransferOwnership) {
            {
                IconButton(onClick = { showTransferDialog = true }) {
                    Icon(Icons.Default.Person, contentDescription = "Transfer Ownership")
                }
            }
        } else null,
        modifier = Modifier.clickable(onClick = onClick)
    )
    
    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer Ownership") },
            text = { Text("Are you sure you want to transfer group ownership to ${user.displayName ?: user.username}? You will become an admin.") },
            confirmButton = {
                TextButton(onClick = {
                    showTransferDialog = false
                    onTransferOwnership()
                }) {
                    Text("Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
