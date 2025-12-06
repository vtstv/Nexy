package com.nexy.client.ui.screens.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.nexy.client.ServerConfig
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.ChatRepository
import com.nexy.client.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteMembersViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteMembersUiState())
    val uiState: StateFlow<InviteMembersUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null

    fun generateInviteLink(chatId: Int) {
        viewModelScope.launch {
            // Don't set loading here to avoid flickering if we are just entering the screen
            // _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Default expiration: 1 day (86400 seconds)
                val result = chatRepository.createGroupInviteLink(chatId, usageLimit = null, expiresIn = 86400)
                if (result.isSuccess) {
                    val inviteLink = result.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        inviteLink = inviteLink?.code
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to generate invite link"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        
        if (query.length > 2) {
            searchJob = viewModelScope.launch {
                delay(500) // Debounce
                _uiState.value = _uiState.value.copy(isSearching = true)
                val result = userRepository.searchUsers(query)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        searchResults = result.getOrDefault(emptyList()),
                        isSearching = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSearching = false)
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    fun addMember(chatId: Int, user: User) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = chatRepository.addGroupMember(chatId, user.id)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    memberAddedMessage = "Added ${user.username} to group",
                    searchQuery = "",
                    searchResults = emptyList()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to add member: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(memberAddedMessage = null, error = null)
    }
}

data class InviteMembersUiState(
    val inviteLink: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val memberAddedMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteMembersScreen(
    chatId: Int,
    onNavigateBack: () -> Unit,
    viewModel: InviteMembersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(chatId) {
        viewModel.generateInviteLink(chatId)
    }
    
    LaunchedEffect(uiState.memberAddedMessage) {
        uiState.memberAddedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Add Members") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Section
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by username") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )
            
            if (uiState.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            if (uiState.searchResults.isNotEmpty()) {
                Text(
                    text = "Search Results",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(uiState.searchResults) { user ->
                        ListItem(
                            headlineContent = { Text(user.displayName?.takeIf { it.isNotBlank() } ?: user.username) },
                            supportingContent = { Text("@${user.username}") },
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
                                                text = (user.displayName?.firstOrNull() ?: user.username.firstOrNull() ?: '?')
                                                    .toString().uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.addMember(chatId, user) }) {
                                    Icon(Icons.Default.PersonAdd, "Add")
                                }
                            }
                        )
                        Divider()
                    }
                }
            } else if (uiState.searchQuery.length > 2 && !uiState.isSearching) {
                 Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Divider()
            
            // Invite Link Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Or share invite link",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (uiState.inviteLink != null) {
                    // Construct full URL (mock for now)
                    val fullLink = "nexy://invite/${uiState.inviteLink}"
                    
                    OutlinedTextField(
                        value = fullLink,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Invite Link", fullLink)
                                clipboard.setPrimaryClip(clip)
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copy")
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Join my group on Nexy: $fullLink")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share Link")
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun rememberVectorPainter(image: androidx.compose.ui.graphics.vector.ImageVector) = 
    androidx.compose.ui.graphics.vector.rememberVectorPainter(image)
