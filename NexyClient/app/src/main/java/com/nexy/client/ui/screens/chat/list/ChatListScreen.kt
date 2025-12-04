/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.R
import com.nexy.client.ui.screens.chat.ChatListViewModel
import com.nexy.client.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(
    onChatClick: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToContacts: () -> Unit = {},
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToSearchGroups: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    settingsViewModel: com.nexy.client.ui.screens.settings.SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme by themeViewModel.isDarkTheme.collectAsState()
    val pinCode by settingsViewModel.pinCode.collectAsState()
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    val userName = uiState.currentUser?.displayName?.ifBlank { uiState.currentUser?.username } 
        ?: uiState.currentUser?.username 
        ?: stringResource(R.string.username)
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            // Optional: clear error in ViewModel
        }
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatListDrawer(
                userName = userName,
                avatarUrl = uiState.currentUser?.avatarUrl,
                isDarkTheme = isDarkTheme,
                isPinSet = pinCode != null,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToCreateGroup = onNavigateToCreateGroup,
                onNavigateToSearchGroups = onNavigateToSearchGroups,
                onNavigateToContacts = onNavigateToContacts,
                onOpenSavedMessages = { viewModel.openSavedMessages(onChatClick) },
                onNavigateToSettings = onNavigateToSettings,
                onLockApp = { viewModel.lockApp() },
                onShowAboutDialog = { showAboutDialog = true },
                onToggleTheme = { themeViewModel.toggleTheme() },
                onShowLogoutDialog = { showLogoutDialog = true },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ChatListTopBar(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToContacts = onNavigateToContacts,
                    onNavigateToProfile = onNavigateToProfile
                )
            }
        ) { padding ->
            ChatListContent(
                padding = padding,
                chats = uiState.chats,
                isLoading = uiState.isLoading,
                onChatClick = onChatClick,
                onNavigateToSearch = onNavigateToSearch
            )
        }
        
        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }

        if (showLogoutDialog) {
            LogoutDialog(
                onDismiss = { showLogoutDialog = false },
                onLogout = onLogout
            )
        }
    }
}
