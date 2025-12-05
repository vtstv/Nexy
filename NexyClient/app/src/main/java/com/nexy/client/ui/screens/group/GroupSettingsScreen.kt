package com.nexy.client.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.models.Chat
import com.nexy.client.ui.screens.group.components.*

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
            GroupSettingsTopBar(
                uiState = uiState,
                groupId = groupId,
                onNavigateBack = onNavigateBack,
                onEditGroup = onEditGroup
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is GroupSettingsUiState.Loading -> {
                LoadingState(modifier = Modifier.padding(padding))
            }

            is GroupSettingsUiState.Success -> {
                GroupSettingsContent(
                    state = state,
                    groupId = groupId,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }

            is GroupSettingsUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.loadGroupSettings(groupId) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSettingsTopBar(
    uiState: GroupSettingsUiState,
    groupId: Int,
    onNavigateBack: () -> Unit,
    onEditGroup: (Int) -> Unit
) {
    TopAppBar(
        title = { Text("Group Settings") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
        },
        actions = {
            if (uiState is GroupSettingsUiState.Success && uiState.canManageMembers) {
                IconButton(onClick = { onEditGroup(groupId) }) {
                    Icon(Icons.Default.Edit, "Edit")
                }
            }
        }
    )
}

@Composable
private fun GroupSettingsContent(
    state: GroupSettingsUiState.Success,
    groupId: Int,
    viewModel: GroupSettingsViewModel,
    modifier: Modifier = Modifier
) {
    var showCreateInviteDialog by remember { mutableStateOf(false) }
    var showInviteLinkDialog by remember { mutableStateOf(false) }
    
    // Show invite link dialog when a new link is created
    LaunchedEffect(state.inviteLink) {
        if (state.inviteLink != null) {
            showInviteLinkDialog = true
        }
    }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GroupInfoCard(chat = state.chat)
        }

        item {
            MembersHeader(memberCount = state.members.size)
        }

        items(state.members) { member ->
            MemberItem(
                member = member,
                canManage = state.canManageMembers,
                isOwner = state.chat.createdBy == member.userId,
                currentUserIsOwner = isCurrentUserOwner(state),
                onRemove = { viewModel.removeMember(groupId, member.userId) },
                onUpdateRole = { role -> viewModel.updateMemberRole(groupId, member.userId, role) },
                onTransferOwnership = { viewModel.transferOwnership(groupId, member.userId) }
            )
        }

        if (state.canCreateInvite) {
            item {
                CreateInviteLinkButton(
                    onClick = { showCreateInviteDialog = true }
                )
            }
        }
    }
    
    if (showCreateInviteDialog) {
        CreateInviteLinkDialog(
            onDismiss = { showCreateInviteDialog = false },
            onCreate = { settings ->
                viewModel.createInviteLink(
                    groupId = groupId,
                    usageLimit = settings.usageLimit,
                    expiresInSeconds = settings.expiresInSeconds
                )
                showCreateInviteDialog = false
            }
        )
    }
    
    if (showInviteLinkDialog && state.inviteLink != null) {
        InviteLinkCreatedDialog(
            inviteLink = state.inviteLink,
            onDismiss = { 
                showInviteLinkDialog = false
                viewModel.clearInviteLink()
            }
        )
    }
}

private fun isCurrentUserOwner(state: GroupSettingsUiState.Success): Boolean {
    return state.chat.createdBy == state.members.find { 
        it.userId == state.chat.createdBy 
    }?.userId
}
