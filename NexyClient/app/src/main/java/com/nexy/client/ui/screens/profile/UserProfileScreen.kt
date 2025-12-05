package com.nexy.client.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.models.User
import com.nexy.client.ui.screens.profile.userprofile.*

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
