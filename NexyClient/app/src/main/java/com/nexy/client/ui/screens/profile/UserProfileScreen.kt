package com.nexy.client.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nexy.client.ServerConfig
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.User
import com.nexy.client.data.models.UserStatus
import com.nexy.client.data.repository.ContactRepository
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val webRTCClient: WebRTCClient,
    private val authTokenManager: AuthTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    fun loadUser(userId: Int) {
        viewModelScope.launch {
            _uiState.value = UserProfileUiState(isLoading = true)
            try {
                val userResult = userRepository.getUserById(userId, forceRefresh = true)
                val contactResult = contactRepository.checkContactStatus(userId)
                
                if (userResult.isSuccess) {
                    val isContact = contactResult.getOrNull()?.exists == true
                    val contactStatus = contactResult.getOrNull()?.status
                    _uiState.value = UserProfileUiState(
                        user = userResult.getOrNull(),
                        isContact = isContact,
                        isBlocked = contactStatus == "blocked"
                    )
                } else {
                    _uiState.value = UserProfileUiState(error = "Failed to load user")
                }
            } catch (e: Exception) {
                _uiState.value = UserProfileUiState(error = e.message ?: "Unknown error")
            }
        }
    }
    
    fun addContact(userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isContactActionLoading = true) }
            contactRepository.addContact(userId)
                .onSuccess {
                    _uiState.update { it.copy(isContact = true, isContactActionLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        error = error.message ?: "Failed to add contact",
                        isContactActionLoading = false
                    )}
                }
        }
    }
    
    fun removeContact(userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isContactActionLoading = true) }
            contactRepository.deleteContact(userId)
                .onSuccess {
                    _uiState.update { it.copy(isContact = false, isContactActionLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        error = error.message ?: "Failed to remove contact",
                        isContactActionLoading = false
                    )}
                }
        }
    }
    
    fun createChat(userId: Int, onChatCreated: (Int) -> Unit) {
        viewModelScope.launch {
            contactRepository.createPrivateChat(userId)
                .onSuccess { chat ->
                    onChatCreated(chat.id)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to create chat") }
                }
        }
    }
    
    fun startCall(userId: Int) {
        viewModelScope.launch {
            val currentUserId = authTokenManager.getUserId()
            if (currentUserId == null) {
                _uiState.update { it.copy(error = "Not authenticated") }
                return@launch
            }
            
            try {
                webRTCClient.startCall(userId, currentUserId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to start call: ${e.message}") }
            }
        }
    }
    
    fun showMessage(message: String) {
        _uiState.update { it.copy(error = message) }
    }
    
    fun toggleQrDialog() {
        _uiState.update { it.copy(showQrDialog = !it.showQrDialog) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class UserProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isContact: Boolean = false,
    val isBlocked: Boolean = false,
    val isContactActionLoading: Boolean = false,
    val showQrDialog: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: Int,
    onNavigateBack: () -> Unit,
    onStartChat: (Int) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) {
        viewModel.loadUser(userId)
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.user != null -> {
                UserProfileContent(
                    user = uiState.user!!,
                    isContact = uiState.isContact,
                    isBlocked = uiState.isBlocked,
                    isContactActionLoading = uiState.isContactActionLoading,
                    onAddContact = { viewModel.addContact(userId) },
                    onRemoveContact = { viewModel.removeContact(userId) },
                    onStartChat = { 
                        viewModel.createChat(userId) { chatId ->
                            onStartChat(chatId)
                        }
                    },
                    onStartCall = {
                        viewModel.startCall(userId)
                    },
                    onMute = {
                        viewModel.showMessage("Mute is available from chat")
                    },
                    onShowQr = { viewModel.toggleQrDialog() },
                    modifier = Modifier.padding(padding)
                )
                
                if (uiState.showQrDialog) {
                    QrCodeDialog(
                        username = uiState.user!!.username,
                        userId = userId,
                        onDismiss = { viewModel.toggleQrDialog() }
                    )
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "User not found", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun UserProfileContent(
    user: User,
    isContact: Boolean,
    isBlocked: Boolean,
    isContactActionLoading: Boolean,
    onAddContact: () -> Unit,
    onRemoveContact: () -> Unit,
    onStartChat: () -> Unit,
    onStartCall: () -> Unit,
    onMute: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileHeader(user = user)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        QuickActionButtons(
            onStartChat = onStartChat,
            onMute = onMute,
            onStartCall = onStartCall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        UsernameSection(
            username = user.username,
            onShowQr = onShowQr
        )
        
        AddToContactsRow(
            isContact = isContact,
            isLoading = isContactActionLoading,
            onAddContact = onAddContact,
            onRemoveContact = onRemoveContact
        )
        
        if (!user.bio.isNullOrEmpty()) {
            BioSection(bio = user.bio)
        }
    }
}

@Composable
private fun ProfileHeader(user: User) {
    val context = LocalContext.current
    val avatarUrl = ServerConfig.getFileUrl(user.avatarUrl)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 24.dp)
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
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
                        text = (user.displayName?.firstOrNull() ?: user.username.firstOrNull() ?: '?')
                            .toString().uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = user.displayName?.takeIf { it.isNotBlank() } ?: user.username,
            style = MaterialTheme.typography.headlineSmall
        )
        
        val lastSeenText = when (user.status) {
            UserStatus.ONLINE -> "online"
            UserStatus.AWAY -> "away"
            else -> user.lastSeen?.let { "last seen ${formatLastSeen(it)}" } ?: "last seen recently"
        }
        
        Text(
            text = lastSeenText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickActionButtons(
    onStartChat: () -> Unit,
    onMute: () -> Unit,
    onStartCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "Message",
            onClick = onStartChat
        )
        QuickActionButton(
            icon = Icons.Default.NotificationsOff,
            label = "Mute",
            onClick = onMute
        )
        QuickActionButton(
            icon = Icons.Default.Call,
            label = "Call",
            onClick = onStartCall
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(width = 80.dp, height = 64.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UsernameSection(
    username: String,
    onShowQr: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Username",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShowQr) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = "QR Code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun AddToContactsRow(
    isContact: Boolean,
    isLoading: Boolean,
    onAddContact: () -> Unit,
    onRemoveContact: () -> Unit
) {
    Surface(
        onClick = if (isContact) onRemoveContact else onAddContact,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isContact) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = if (isContact) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (isContact) "Remove from contacts" else "Add to contacts",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isContact) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun BioSection(bio: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Bio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun formatLastSeen(dateString: String): String {
    return try {
        val parsedDate = ZonedDateTime.parse(dateString)
        val now = ZonedDateTime.now()
        val minutes = ChronoUnit.MINUTES.between(parsedDate, now)
        
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 10080 -> "${minutes / 1440}d ago"
            else -> "recently"
        }
    } catch (e: DateTimeParseException) {
        "recently"
    }
}

@Composable
private fun QrCodeDialog(
    username: String,
    userId: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "@$username",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(200.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "nexy://user/$userId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scan this code to add user",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
