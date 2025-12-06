package com.nexy.client.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
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
import com.nexy.client.data.api.GroupBan
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.models.MemberRole
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
    
    // Ban dialog state - must be at top level for AlertDialog to work properly
    var showBanDialog by remember { mutableStateOf<Int?>(null) }
    var banReason by remember { mutableStateOf("") }

    LaunchedEffect(chatId) {
        viewModel.loadGroupInfo(chatId)
    }

    LaunchedEffect(uiState.isLeftGroup) {
        if (uiState.isLeftGroup) {
            viewModel.clearLeftGroupState()
            onGroupLeft()
        }
    }
    
    // Ban confirmation dialog with reason - outside of LazyColumn
    if (showBanDialog != null) {
        BanConfirmationDialog(
            reason = banReason,
            onReasonChange = { banReason = it },
            onConfirm = {
                viewModel.banMember(showBanDialog!!, banReason.ifBlank { null })
                showBanDialog = null
                banReason = ""
            },
            onDismiss = {
                showBanDialog = null
                banReason = ""
            }
        )
    }
    
    // Leave group confirmation dialog
    var showLeaveDialog by remember { mutableStateOf(false) }
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave this group?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leaveGroup()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // TopBar menu state
    var showTopBarMenu by remember { mutableStateOf(false) }
    
    // Snackbar for errors
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        
                        // Menu with Leave Group option
                        if (uiState.isMember) {
                            Box {
                                IconButton(onClick = { showTopBarMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showTopBarMenu,
                                    onDismissRequest = { showTopBarMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Leave Group", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showTopBarMenu = false
                                            showLeaveDialog = true
                                        },
                                        leadingIcon = { 
                                            Icon(
                                                Icons.Default.ExitToApp, 
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            ) 
                                        }
                                    )
                                }
                            }
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
                            
                            // Show "X members, Y online" 
                            val onlineCount = uiState.members.count { it.user?.onlineStatus == "online" }
                            val membersText = if (onlineCount > 0) {
                                "${uiState.members.size} members, $onlineCount online"
                            } else {
                                "${uiState.members.size} members"
                            }
                            Text(
                                text = membersText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                HorizontalDivider()
                                
                                // Banned Members - only for admins and owners
                                val currentUserRole = viewModel.getCurrentUserRole()
                                if (currentUserRole == MemberRole.OWNER || currentUserRole == MemberRole.ADMIN) {
                                    ListItem(
                                        headlineContent = { 
                                            Text(
                                                if (uiState.showBannedMembers) "Show Participants" else "Banned Members"
                                            )
                                        },
                                        leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
                                        trailingContent = {
                                            if (uiState.bannedMembers.isNotEmpty()) {
                                                Badge { Text(uiState.bannedMembers.size.toString()) }
                                            }
                                        },
                                        modifier = Modifier.clickable { viewModel.toggleBannedMembersView() }
                                    )
                                    HorizontalDivider()
                                }
                            } else if (uiState.chat?.groupType == GroupType.PUBLIC_GROUP) {
                                ListItem(
                                    headlineContent = { Text("Join Group", color = MaterialTheme.colorScheme.primary) },
                                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { viewModel.joinGroup() }
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    // Participants List Header
                    item {
                        Text(
                            text = if (uiState.showBannedMembers) "Banned Members" else "Participants",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Show banned members or regular participants
                if (uiState.showBannedMembers) {
                    if (uiState.isLoadingBans) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (uiState.bannedMembers.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No banned members", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(uiState.bannedMembers) { ban ->
                            BannedMemberItem(
                                ban = ban,
                                onUnban = { viewModel.unbanMember(ban.userId) }
                            )
                        }
                    }
                } else {
                    val displayMembers = if (uiState.isSearching) uiState.searchResults else uiState.members
                    
                    if (uiState.isSearching && displayMembers.isEmpty() && uiState.searchQuery.length > 2) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No members found")
                            }
                        }
                    }

                    val currentUserRole = viewModel.getCurrentUserRole()
                    val canModerate = currentUserRole == MemberRole.OWNER || currentUserRole == MemberRole.ADMIN

                    items(displayMembers) { member ->
                        ParticipantItem(
                            member = member,
                            currentUserId = uiState.currentUserId,
                            currentUserRole = currentUserRole,
                            canModerate = canModerate,
                            onClick = { onParticipantClick(member.userId) },
                            onKick = { viewModel.kickMember(member.userId) },
                            onBan = { showBanDialog = member.userId }
                        )
                    }
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
    member: ChatMember,
    currentUserId: Int?,
    currentUserRole: MemberRole?,
    canModerate: Boolean,
    onClick: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    val user = member.user
    // Show displayName if available, otherwise username
    val displayName = if (!user?.displayName.isNullOrEmpty()) user?.displayName!! else user?.username ?: "User"
    val isOnline = user?.onlineStatus == "online"
    
    // Get role badge text (Owner, Admin)
    val roleBadge = when (member.role) {
        MemberRole.OWNER -> "Owner"
        MemberRole.ADMIN -> "Admin"
        else -> null
    }
    
    // Can this user be moderated by current user?
    val canModerateThisMember = canModerate && 
        member.userId != currentUserId && // Can't moderate yourself
        member.role != MemberRole.OWNER && // Can't moderate owner
        (currentUserRole == MemberRole.OWNER || member.role != MemberRole.ADMIN) // Admins can't moderate other admins
    
    var showMenu by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text(displayName) },
        supportingContent = { 
            // Always show online status
            user?.onlineStatus?.let { status ->
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            val avatarUrl = ServerConfig.getFileUrl(user?.avatarUrl)
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
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show role badge (Owner/Admin) on the right 
                roleBadge?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // Show menu for moderation if user has permission
                if (canModerateThisMember) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Kick") },
                                onClick = {
                                    showMenu = false
                                    onKick()
                                },
                                leadingIcon = { Icon(Icons.Default.PersonRemove, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Ban") },
                                onClick = {
                                    showMenu = false
                                    onBan()
                                },
                                leadingIcon = { Icon(Icons.Default.Block, null) },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error,
                                    leadingIconColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun BannedMemberItem(
    ban: GroupBan,
    onUnban: () -> Unit
) {
    val user = ban.user
    val displayName = if (!user?.displayName.isNullOrEmpty()) user?.displayName!! else user?.username ?: "User #${ban.userId}"
    val bannedByName = ban.bannedByUser?.let { 
        if (!it.displayName.isNullOrEmpty()) it.displayName else it.username 
    } ?: "Admin"
    
    ListItem(
        headlineContent = { Text(displayName) },
        supportingContent = {
            Column {
                Text(
                    text = "Banned by $bannedByName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!ban.reason.isNullOrEmpty()) {
                    Text(
                        text = "Reason: ${ban.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        leadingContent = {
            val avatarUrl = ServerConfig.getFileUrl(user?.avatarUrl)
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
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        trailingContent = {
            TextButton(onClick = onUnban) {
                Text("Unban", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanConfirmationDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ban Member") },
        text = {
            Column {
                Text("Are you sure you want to ban this member?")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason (optional)") },
                    placeholder = { Text("Enter ban reason...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Ban")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
