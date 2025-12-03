package com.nexy.client.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    groupId: Int,
    onNavigateBack: () -> Unit,
    viewModel: EditGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val groupDescription by viewModel.groupDescription.collectAsState()
    val groupUsername by viewModel.groupUsername.collectAsState()
    val isPublic by viewModel.isPublic.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Group") },
            text = { Text("Are you sure you want to delete this group? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(groupId) {
                            onNavigateBack()
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Group") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState is EditGroupUiState.Success) {
                        TextButton(
                            onClick = {
                                viewModel.updateGroup(groupId) {
                                    onNavigateBack()
                                }
                            },
                            enabled = groupName.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is EditGroupUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is EditGroupUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = viewModel::setGroupName,
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = groupDescription,
                        onValueChange = viewModel::setGroupDescription,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    
                    if (isPublic) {
                        OutlinedTextField(
                            value = groupUsername,
                            onValueChange = viewModel::setGroupUsername,
                            label = { Text("Username") },
                            prefix = { Text("@") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = isPublic
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            onCheckedChange = viewModel::setIsPublic
                        )
                    }
                    
                    if (isOwner) {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = {
                                showDeleteDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete Group")
                        }
                    }
                }
            }
            
            is EditGroupUiState.Error -> {
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
                        Button(onClick = { viewModel.loadGroup(groupId) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
