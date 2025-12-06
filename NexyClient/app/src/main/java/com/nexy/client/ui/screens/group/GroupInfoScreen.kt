package com.nexy.client.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.MemberRole
import com.nexy.client.ui.screens.group.components.*

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

    var showBanDialog by remember { mutableStateOf<Int?>(null) }
    var banReason by remember { mutableStateOf("") }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showTopBarMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(chatId) {
        viewModel.loadGroupInfo(chatId)
    }

    LaunchedEffect(uiState.isLeftGroup) {
        if (uiState.isLeftGroup) {
            viewModel.clearLeftGroupState()
            onGroupLeft()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

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

    if (showLeaveDialog) {
        LeaveGroupDialog(
            onConfirm = {
                showLeaveDialog = false
                viewModel.leaveGroup()
            },
            onDismiss = { showLeaveDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GroupInfoTopBar(
                isSearching = uiState.isSearching,
                searchQuery = uiState.searchQuery,
                isMember = uiState.isMember,
                showTopBarMenu = showTopBarMenu,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onToggleSearch = viewModel::toggleSearch,
                onNavigateBack = onNavigateBack,
                onShowTopBarMenu = { showTopBarMenu = true },
                onDismissTopBarMenu = { showTopBarMenu = false },
                onLeaveGroup = { showLeaveDialog = true }
            )
        }
    ) { padding ->
        GroupInfoContent(
            uiState = uiState,
            padding = padding,
            chatId = chatId,
            viewModel = viewModel,
            onAddParticipant = onAddParticipant,
            onParticipantClick = onParticipantClick,
            onShowBanDialog = { userId -> showBanDialog = userId }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoTopBar(
    isSearching: Boolean,
    searchQuery: String,
    isMember: Boolean,
    showTopBarMenu: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onNavigateBack: () -> Unit,
    onShowTopBarMenu: () -> Unit,
    onDismissTopBarMenu: () -> Unit,
    onLeaveGroup: () -> Unit
) {
    TopAppBar(
        title = {
            if (isSearching) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
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
            IconButton(onClick = { if (isSearching) onToggleSearch() else onNavigateBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (!isSearching) {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }

                if (isMember) {
                    Box {
                        IconButton(onClick = onShowTopBarMenu) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showTopBarMenu,
                            onDismissRequest = onDismissTopBarMenu
                        ) {
                            DropdownMenuItem(
                                text = { Text("Leave Group", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    onDismissTopBarMenu()
                                    onLeaveGroup()
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

@Composable
private fun GroupInfoContent(
    uiState: GroupInfoUiState,
    padding: PaddingValues,
    chatId: Int,
    viewModel: GroupInfoViewModel,
    onAddParticipant: (Int) -> Unit,
    onParticipantClick: (Int) -> Unit,
    onShowBanDialog: (Int) -> Unit
) {
    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.chat != null -> {
            GroupInfoList(
                uiState = uiState,
                padding = padding,
                chatId = chatId,
                viewModel = viewModel,
                onAddParticipant = onAddParticipant,
                onParticipantClick = onParticipantClick,
                onShowBanDialog = onShowBanDialog
            )
        }
        uiState.error != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun GroupInfoList(
    uiState: GroupInfoUiState,
    padding: PaddingValues,
    chatId: Int,
    viewModel: GroupInfoViewModel,
    onAddParticipant: (Int) -> Unit,
    onParticipantClick: (Int) -> Unit,
    onShowBanDialog: (Int) -> Unit
) {
    val currentUserRole = viewModel.getCurrentUserRole()
    val canModerate = currentUserRole == MemberRole.OWNER || currentUserRole == MemberRole.ADMIN

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        if (!uiState.isSearching) {
            item {
                GroupInfoHeader(
                    chat = uiState.chat,
                    members = uiState.members
                )
            }

            item {
                GroupInfoActions(
                    isMember = uiState.isMember,
                    groupType = uiState.chat?.groupType,
                    currentUserRole = currentUserRole,
                    showBannedMembers = uiState.showBannedMembers,
                    bannedMembersCount = uiState.bannedMembers.size,
                    onAddParticipant = { onAddParticipant(chatId) },
                    onToggleBannedMembers = viewModel::toggleBannedMembersView,
                    onJoinGroup = viewModel::joinGroup
                )
            }

            item {
                Text(
                    text = if (uiState.showBannedMembers) "Banned Members" else "Participants",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (uiState.showBannedMembers) {
            when {
                uiState.isLoadingBans -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                uiState.bannedMembers.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No banned members", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    items(uiState.bannedMembers) { ban ->
                        BannedMemberItem(
                            ban = ban,
                            onUnban = { viewModel.unbanMember(ban.userId) }
                        )
                    }
                }
            }
        } else {
            val displayMembers = if (uiState.isSearching) uiState.searchResults else uiState.members

            if (uiState.isSearching && displayMembers.isEmpty() && uiState.searchQuery.length > 2) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No members found")
                    }
                }
            }

            items(displayMembers) { member ->
                ParticipantItem(
                    member = member,
                    currentUserId = uiState.currentUserId,
                    currentUserRole = currentUserRole,
                    canModerate = canModerate,
                    onClick = { onParticipantClick(member.userId) },
                    onKick = { viewModel.kickMember(member.userId) },
                    onBan = { onShowBanDialog(member.userId) }
                )
            }
        }
    }
}
