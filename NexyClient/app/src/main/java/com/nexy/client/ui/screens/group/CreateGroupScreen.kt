package com.nexy.client.ui.screens.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.ui.screens.group.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: (Int) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val selectedMembers by viewModel.selectedMembers.collectAsState()
    val groupAvatarUri by viewModel.groupAvatarUri.collectAsState()
    
    val context = LocalContext.current
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.setGroupAvatar(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState is CreateGroupUiState.Success || uiState is CreateGroupUiState.Error) {
                        TextButton(
                            onClick = {
                                viewModel.createGroup(context) { chatId ->
                                    onGroupCreated(chatId)
                                }
                            },
                            enabled = groupName.isNotBlank()
                        ) {
                            Text("Create")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is CreateGroupUiState.Loading -> {
                LoadingState(modifier = Modifier.padding(padding))
            }

            is CreateGroupUiState.Creating -> {
                CreatingState(modifier = Modifier.padding(padding))
            }

            is CreateGroupUiState.Empty -> {
                EmptyContactsState(modifier = Modifier.padding(padding))
            }

            is CreateGroupUiState.Success -> {
                GroupCreationForm(
                    viewModel = viewModel,
                    contacts = state.contacts,
                    groupName = groupName,
                    groupAvatarUri = groupAvatarUri,
                    selectedMembers = selectedMembers,
                    onPickAvatar = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.padding(padding)
                )
            }

            is CreateGroupUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.loadContacts() },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun GroupCreationForm(
    viewModel: CreateGroupViewModel,
    contacts: List<com.nexy.client.data.models.ContactWithUser>,
    groupName: String,
    groupAvatarUri: android.net.Uri?,
    selectedMembers: Set<Int>,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groupDescription by viewModel.groupDescription.collectAsState()
    val groupUsername by viewModel.groupUsername.collectAsState()
    val isPublic by viewModel.isPublic.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        GroupAvatarPicker(
            avatarUri = groupAvatarUri,
            onPickAvatar = onPickAvatar
        )

        GroupInfoFields(
            groupName = groupName,
            onNameChange = { viewModel.setGroupName(it) },
            groupDescription = groupDescription,
            onDescriptionChange = { viewModel.setGroupDescription(it) }
        )

        PublicGroupSettings(
            isPublic = isPublic,
            onPublicChange = { viewModel.setIsPublic(it) },
            groupUsername = groupUsername,
            onUsernameChange = { viewModel.setGroupUsername(it) }
        )

        HorizontalDivider()

        MemberSelectionHeader(selectedCount = selectedMembers.size)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = contacts, key = { it.id }) { contact ->
                ContactSelectionItem(
                    contact = contact,
                    isSelected = selectedMembers.contains(contact.contactUser.id),
                    onToggle = { viewModel.toggleMemberSelection(contact.contactUser.id) }
                )
            }
        }
    }
}

@Composable
private fun MemberSelectionHeader(selectedCount: Int) {
    val text = if (selectedCount > 0) {
        "$selectedCount member${if (selectedCount != 1) "s" else ""} selected"
    } else {
        "Add members (Optional)"
    }
    
    val color = if (selectedCount > 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Text(
        text = text,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}
