/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexy.client.ui.NavigationViewModel
import com.nexy.client.ui.screens.auth.AuthViewModel
import com.nexy.client.ui.screens.auth.LoginScreen
import com.nexy.client.ui.screens.auth.RegisterScreen
import com.nexy.client.ui.screens.call.CallScreen
import com.nexy.client.ui.screens.folders.ChatSelectorScreen
import com.nexy.client.ui.screens.folders.FolderEditorScreen
import com.nexy.client.ui.screens.folders.FolderListScreen
import com.nexy.client.ui.screens.group.CreateGroupScreen
import com.nexy.client.ui.screens.group.EditGroupScreen
import com.nexy.client.ui.screens.group.GroupInfoScreen
import com.nexy.client.ui.screens.group.GroupSettingsScreen
import com.nexy.client.ui.screens.group.InviteMembersScreen
import com.nexy.client.ui.screens.group.SearchGroupsScreen
import com.nexy.client.ui.screens.main.MainScreen
import com.nexy.client.ui.screens.profile.ProfileScreen
import com.nexy.client.ui.screens.profile.UserProfileScreen
import com.nexy.client.ui.screens.qr.QRScannerScreen
import com.nexy.client.ui.screens.search.SearchScreen
import com.nexy.client.ui.screens.settings.SettingsScreen

@Composable
fun NexyApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val navigationViewModel: NavigationViewModel = viewModel()
    val selectedChatId by navigationViewModel.selectedChatId.collectAsState()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.ChatList.route) {
            MainScreen(
                initialChatId = selectedChatId,
                onChatSelected = { chatId ->
                    if (chatId != null) {
                        navigationViewModel.selectChat(chatId)
                    }
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToEditGroup = { groupId ->
                    navController.navigate(Screen.EditGroup.createRoute(groupId))
                },
                onNavigateToGroupInfo = { chatId ->
                    navController.navigate(Screen.GroupInfo.createRoute(chatId))
                },
                onNavigateToFolders = {
                    navController.navigate(Screen.Folders.route)
                },
                onLogout = {
                    authViewModel.logout(clearCredentials = false)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable("profile") {
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onUserClick = { userId ->
                },
                onNavigateToQRScanner = {
                    navController.navigate(Screen.QRScanner.route)
                },
                onChatCreated = { chatId ->
                    navigationViewModel.selectChat(chatId)
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.Invite.route,
            arguments = listOf(navArgument("chatId") { type = NavType.IntType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getInt("chatId") ?: return@composable
            InviteMembersScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.QRScanner.route) {
            QRScannerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onQRCodeScanned = { code ->
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onGroupCreated = { chatId ->
                    navigationViewModel.selectChat(chatId)
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.GroupSettings.route,
            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            GroupSettingsScreen(
                groupId = groupId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onEditGroup = { id ->
                    android.util.Log.d("NexyApp", "Navigating to EditGroup: $id")
                    navController.navigate(Screen.EditGroup.createRoute(id))
                }
            )
        }
        
        composable(
            route = Screen.EditGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getInt("groupId") ?: return@composable
            EditGroupScreen(
                groupId = groupId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.GroupInfo.route,
            arguments = listOf(navArgument("chatId") { type = NavType.IntType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getInt("chatId") ?: return@composable
            GroupInfoScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onGroupLeft = {
                    // Navigate back to ChatList and clear back stack to avoid returning to the left group
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.ChatList.route) { inclusive = true }
                    }
                },
                onAddParticipant = { id ->
                    navController.navigate(Screen.Invite.createRoute(id))
                },
                onParticipantClick = { userId ->
                    navController.navigate("user_profile/$userId")
                }
            )
        }
        
        composable(
            route = "user_profile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: return@composable
            UserProfileScreen(
                userId = userId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStartChat = { chatId ->
                    navigationViewModel.selectChat(chatId)
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.ChatList.route) { inclusive = false }
                    }
                }
            )
        }
        
        composable(Screen.SearchGroups.route) {
            SearchGroupsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onGroupClick = { groupId ->
                    navController.navigate(Screen.GroupSettings.createRoute(groupId))
                }
            )
        }
        
        // Folder screens
        composable(Screen.Folders.route) {
            FolderListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCreateFolder = {
                    navController.navigate(Screen.FolderEditor.createRoute())
                },
                onEditFolder = { folderId ->
                    navController.navigate(Screen.FolderEditor.createRoute(folderId))
                }
            )
        }
        
        composable(
            route = Screen.FolderEditor.route,
            arguments = listOf(
                navArgument("folderId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val folderIdStr = backStackEntry.arguments?.getString("folderId")
            val folderId = folderIdStr?.toIntOrNull()
            FolderEditorScreen(
                folderId = folderId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAddChats = { id ->
                    navController.navigate(Screen.ChatSelector.createRoute(id))
                }
            )
        }
        
        composable(
            route = Screen.ChatSelector.route,
            arguments = listOf(navArgument("folderId") { type = NavType.IntType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getInt("folderId") ?: return@composable
            ChatSelectorScreen(
                folderId = folderId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
