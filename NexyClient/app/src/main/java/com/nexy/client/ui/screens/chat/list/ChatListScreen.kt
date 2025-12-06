/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.R
import com.nexy.client.ui.screens.chat.ChatListViewModel
import com.nexy.client.ui.screens.chat.list.selection.FolderPickerDialog
import com.nexy.client.ui.screens.chat.list.selection.MuteDuration
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
    onNavigateToFolders: () -> Unit = {},
    onLogout: () -> Unit = {},
    refreshTrigger: Long = 0L,
    viewModel: ChatListViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    settingsViewModel: com.nexy.client.ui.screens.settings.SettingsViewModel = hiltViewModel()
) {
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            viewModel.refreshChats()
        }
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val selectionState by viewModel.selectionState.collectAsState()
    val isDarkTheme by themeViewModel.isDarkTheme.collectAsState()
    val pinCode by settingsViewModel.pinCode.collectAsState()
    val folders by viewModel.folders.collectAsState()
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    val userName = uiState.currentUser?.displayName?.ifBlank { uiState.currentUser?.username } 
        ?: uiState.currentUser?.username 
        ?: stringResource(R.string.username)
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = selectionState.isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !selectionState.isSelectionMode,
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
                // Always show the normal top bar - selection is in the search area
                ChatListTopBar(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSearchClick = onNavigateToSearch
                )
            }
        ) { padding ->
            ChatListContent(
                padding = padding,
                chats = uiState.chats,
                isLoading = uiState.isLoading,
                folders = folders,
                selectionState = selectionState,
                onChatClick = onChatClick,
                onChatLongClick = { chatWithInfo ->
                    // Long press enters selection mode
                    viewModel.enterSelectionMode(chatWithInfo.chat.id)
                },
                onToggleSelection = { chatId ->
                    viewModel.toggleChatSelection(chatId)
                },
                onClearSelection = {
                    viewModel.clearSelection()
                },
                onPinSelected = {
                    viewModel.pinSelectedChats()
                },
                onUnpinSelected = {
                    viewModel.unpinSelectedChats()
                },
                onMuteSelected = { duration ->
                    viewModel.muteSelectedChats(duration)
                },
                onAddToFolderSelected = {
                    showFolderPicker = true
                },
                onDeleteSelected = {
                    showDeleteConfirmDialog = true
                },
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFolders = onNavigateToFolders,
                onAddChatToFolder = { chatId, folderId -> 
                    viewModel.addChatToFolder(chatId, folderId)
                },
                onMoveFolderLocally = { from, to ->
                    viewModel.moveFolderLocally(from, to)
                },
                onSaveFolderOrder = {
                    viewModel.saveFolderOrder()
                }
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
        
        if (showFolderPicker) {
            FolderPickerDialog(
                folders = folders,
                onDismiss = { 
                    showFolderPicker = false
                },
                onFolderSelected = { folder ->
                    viewModel.addSelectedChatsToFolder(folder.id)
                    scope.launch {
                        snackbarHostState.showSnackbar("${selectionState.selectedCount} chats added to ${folder.name}")
                    }
                }
            )
        }
        
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteConfirmDialog = false
                },
                title = { Text("Delete chats?") },
                text = { 
                    Text("Hide ${selectionState.selectedCount} chat(s) from your list? You can find them again by searching or receiving a new message.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.hideSelectedChats()
                            showDeleteConfirmDialog = false
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showDeleteConfirmDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
