package com.nexy.client.ui.screens.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
                    // Avatar Picker
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (groupAvatarUri != null) {
                                AsyncImage(
                                    model = groupAvatarUri,
                                    contentDescription = "Group Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Add Photo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { viewModel.setGroupName(it) },
                        label = { Text("Group Name") },
                        placeholder = { Text("Enter group name") },
                        leadingIcon = { Icon(Icons.Default.Group, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = groupDescription,
                        onValueChange = { viewModel.setGroupDescription(it) },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("Enter group description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = state.contacts,
                            key = { it.id }
                        ) { contact ->
                            ContactSelectionItem(
                                contact = contact,
                                isSelected = selectedMembers.contains(contact.contactUser.id),
                                onToggle = { viewModel.toggleMemberSelection(contact.contactUser.id) }
                            )
                        }
                    }
                }
            }
            
            is CreateGroupUiState.Error -> {
                // Show error but keep form visible if possible? 
                // For now, just show error screen or snackbar
                // But since we handle error in actions, we might want to show the form again
                // Let's just show the error message
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
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadContacts() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactSelectionItem(
    contact: ContactWithUser,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(contact.contactUser.displayName ?: contact.contactUser.username) },
        supportingContent = { Text("@${contact.contactUser.username}") },
        leadingContent = {
            if (contact.contactUser.avatarUrl != null) {
                AsyncImage(
                    model = contact.contactUser.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (contact.contactUser.displayName ?: contact.contactUser.username).take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        },
        modifier = Modifier.clickable { onToggle() }
    )
}
