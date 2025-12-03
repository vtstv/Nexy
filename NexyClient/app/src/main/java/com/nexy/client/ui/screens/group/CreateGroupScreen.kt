package com.nexy.client.ui.screens.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.ContactWithUser

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
                                viewModel.createGroup { chatId ->
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is CreateGroupUiState.Creating -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Creating group...")
                    }
                }
            }

            is CreateGroupUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No contacts yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Add contacts to create groups",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            is CreateGroupUiState.Success -> {
                val groupDescription by viewModel.groupDescription.collectAsState()
                val groupUsername by viewModel.groupUsername.collectAsState()
                val isPublic by viewModel.isPublic.collectAsState()
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { viewModel.setGroupName(it) },
                        label = { Text("Group Name") },
                        placeholder = { Text("Enter group name") },
                        leadingIcon = { Icon(Icons.Default.Group, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = groupDescription,
                        onValueChange = { viewModel.setGroupDescription(it) },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("Enter group description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        minLines = 2,
                        maxLines = 3
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Public Group",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isPublic) "Anyone can find and join" else "Invite only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPublic,
                            onCheckedChange = { viewModel.setIsPublic(it) }
                        )
                    }
                    
                    if (isPublic) {
                        OutlinedTextField(
                            value = groupUsername,
                            onValueChange = { viewModel.setGroupUsername(it) },
                            label = { Text("Username") },
                            placeholder = { Text("groupname") },
                            prefix = { Text("@") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    HorizontalDivider()

                    if (selectedMembers.isNotEmpty()) {
                        Text(
                            "${selectedMembers.size} member${if (selectedMembers.size != 1) "s" else ""} selected",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Add members (Optional)",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = state.contacts,
                            key = { it.contactUserId }
                        ) { contact ->
                            ContactSelectionItem(
                                contact = contact,
                                isSelected = selectedMembers.contains(contact.contactUserId),
                                onToggle = { viewModel.toggleMemberSelection(contact.contactUserId) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            is CreateGroupUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Show error snackbar
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.loadContacts() }) {
                                Text("Retry")
                            }
                        }
                    ) {
                        Text(state.message)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Still show the form for retry
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { viewModel.setGroupName(it) },
                        label = { Text("Group Name") },
                        placeholder = { Text("Enter group name") },
                        leadingIcon = { Icon(Icons.Default.Group, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactSelectionItem(
    contact: ContactWithUser,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )

        Spacer(Modifier.width(16.dp))

        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.contactUser.displayName?.ifBlank { contact.contactUser.username } ?: contact.contactUser.username,
                style = MaterialTheme.typography.bodyLarge
            )
            if (contact.contactUser.displayName?.isNotBlank() == true) {
                Text(
                    text = "@${contact.contactUser.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
