package com.nexy.client.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.models.MemberRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: Int,
    onNavigateBack: () -> Unit,
    onEditGroup: (Int) -> Unit,
    viewModel: GroupSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(groupId) {
        viewModel.loadGroupSettings(groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState is GroupSettingsUiState.Success) {
                        val state = uiState as GroupSettingsUiState.Success
                        if (state.canManageMembers) {
                            IconButton(onClick = { 
                                android.util.Log.d("GroupSettings", "Edit button clicked for groupId: $groupId")
                                onEditGroup(groupId) 
                            }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is GroupSettingsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is GroupSettingsUiState.Success -> {
                GroupSettingsContent(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onRemoveMember = { memberId ->
                        viewModel.removeMember(groupId, memberId)
                    },
                    onUpdateRole = { memberId, role ->
                        viewModel.updateMemberRole(groupId, memberId, role)
                    },
                    onCreateInviteLink = {
                        viewModel.createInviteLink(groupId)
                    },
                    onTransferOwnership = { memberId ->
                        viewModel.transferOwnership(groupId, memberId)
                    }
                )
            }
            
            is GroupSettingsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(state.message)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadGroupSettings(groupId) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupSettingsContent(
    state: GroupSettingsUiState.Success,
    modifier: Modifier = Modifier,
    onRemoveMember: (Int) -> Unit,
    onUpdateRole: (Int, String) -> Unit,
    onCreateInviteLink: () -> Unit,
    onTransferOwnership: (Int) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = state.chat.name ?: "Group",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (!state.chat.description.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.chat.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.chat.username != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "@${state.chat.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        item {
            Text(
                text = "Members (${state.members.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        items(state.members) { member ->
            MemberItem(
                member = member,
                canManage = state.canManageMembers,
                isOwner = state.chat.createdBy == member.userId,
                currentUserIsOwner = state.chat.createdBy == state.members.find { it.userId == state.chat.createdBy }?.userId, // Simplified check, ideally pass currentUserId
                onRemove = { onRemoveMember(member.userId) },
                onUpdateRole = { role -> onUpdateRole(member.userId, role) },
                onTransferOwnership = { onTransferOwnership(member.userId) }
            )
        }
        
        if (state.canCreateInvite) {
            item {
                OutlinedButton(
                    onClick = onCreateInviteLink,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Invite Link")
                }
            }
        }
    }
}

@Composable
fun MemberItem(
    member: ChatMember,
    canManage: Boolean,
    isOwner: Boolean,
    currentUserIsOwner: Boolean,
    onRemove: () -> Unit,
    onUpdateRole: (String) -> Unit,
    onTransferOwnership: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    
    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer Ownership") },
            text = { Text("Are you sure you want to transfer ownership to ${member.user?.displayName ?: member.user?.username}? You will become an admin.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTransferOwnership()
                        showTransferDialog = false
                    }
                ) {
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
    
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // User avatar
                if (member.user?.avatarUrl != null) {
                    AsyncImage(
                        model = member.user.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (member.user?.displayName?.firstOrNull()?.uppercaseChar()
                                    ?: member.user?.username?.firstOrNull()?.uppercaseChar()
                                    ?: '?').toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = member.user?.displayName ?: member.user?.username ?: "User ${member.userId}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = member.role.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (canManage && !isOwner) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Options")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (member.role != MemberRole.ADMIN) {
                        DropdownMenuItem(
                            text = { Text("Make Admin") },
                            onClick = {
                                onUpdateRole("admin")
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Shield, contentDescription = null)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Remove Admin") },
                            onClick = {
                                onUpdateRole("member")
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Shield, contentDescription = null)
                            }
                        )
                    }
                    
                    if (currentUserIsOwner) {
                         DropdownMenuItem(
                            text = { Text("Transfer Ownership") },
                            onClick = {
                                showTransferDialog = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                            }
                        )
                    }
                    
                    DropdownMenuItem(
                        text = { Text("Remove from Group") },
                        onClick = {
                            onRemove()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PersonRemove, contentDescription = null)
                        }
                    )
                }
            }
        }

    }
}
