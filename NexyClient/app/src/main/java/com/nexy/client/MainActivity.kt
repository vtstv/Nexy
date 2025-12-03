/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexy.client.ui.NavigationViewModel
import com.nexy.client.ui.navigation.Screen
import com.nexy.client.ui.screens.auth.AuthViewModel
import com.nexy.client.ui.screens.auth.LoginScreen
import com.nexy.client.ui.screens.auth.RegisterScreen
import com.nexy.client.ui.screens.main.MainScreen
import com.nexy.client.ui.screens.qr.QRScannerScreen
import com.nexy.client.ui.screens.search.SearchScreen
import com.nexy.client.ui.screens.settings.SettingsScreen
import com.nexy.client.ui.theme.NexyClientTheme
import com.nexy.client.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

import com.nexy.client.ui.screens.profile.ProfileScreen
import com.nexy.client.ui.screens.profile.UserProfileScreen
import com.nexy.client.ui.screens.group.CreateGroupScreen
import com.nexy.client.ui.screens.group.GroupSettingsScreen
import com.nexy.client.ui.screens.group.EditGroupScreen
import com.nexy.client.ui.screens.group.SearchGroupsScreen
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nexy.client.utils.PinManager
import com.nexy.client.ui.screens.auth.PinLockScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import javax.inject.Inject
import com.nexy.client.data.webrtc.WebRTCClient
import com.nexy.client.data.webrtc.CallState
import com.nexy.client.ui.screens.call.CallScreen
import com.nexy.client.data.local.AuthTokenManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nexy.client.data.websocket.NexyWebSocketClient
import com.nexy.client.data.websocket.WebSocketMessageHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.nexy.client.utils.BiometricHelper
import com.nexy.client.services.KeepAliveService

import com.nexy.client.ui.screens.group.GroupInfoScreen

import com.nexy.client.ui.screens.group.InviteMembersScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var pinManager: PinManager
    
    @Inject
    lateinit var webRTCClient: WebRTCClient
    
    @Inject
    lateinit var tokenManager: AuthTokenManager
    
    @Inject
    lateinit var webSocketClient: NexyWebSocketClient
    
    @Inject
    lateinit var messageHandler: WebSocketMessageHandler
    
    @Inject
    lateinit var biometricHelper: BiometricHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start/Stop foreground service based on settings
        CoroutineScope(Dispatchers.Main).launch {
            tokenManager.getBackgroundServiceEnabledFlow().collect { enabled ->
                val serviceIntent = Intent(this@MainActivity, KeepAliveService::class.java)
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } else {
                    stopService(serviceIntent)
                }
            }
        }
        
        // Initialize WebSocket message handler globally
        webSocketClient.setMessageCallback { message ->
            messageHandler.handleIncomingMessage(message)
        }
        
        // Connect if we have a token
        CoroutineScope(Dispatchers.IO).launch {
            tokenManager.getAccessToken()?.let { token ->
                webSocketClient.connect(token)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }
            
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
            } else {
                // Permissions already granted
                webRTCClient.initialize()
            }
        } else {
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 102)
            } else {
                // Permissions already granted
                webRTCClient.initialize()
            }
        }
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                pinManager.onAppForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                pinManager.onAppBackground()
            }
        })

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val settingsViewModel: com.nexy.client.ui.screens.settings.SettingsViewModel = viewModel()
            
            val isDarkTheme by themeViewModel.isDarkTheme.collectAsState()
            val themeStyle by themeViewModel.themeStyle.collectAsState()
            
            val pinCode by settingsViewModel.pinCode.collectAsState()
            val isLocked by pinManager.isLocked.collectAsState()
            
            // Update PinManager with enabled state
            LaunchedEffect(pinCode) {
                pinManager.setPinEnabled(pinCode != null)
            }
            
            NexyClientTheme(
                darkTheme = isDarkTheme,
                themeStyle = themeStyle
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val callState by webRTCClient.callState.collectAsState()
                    var currentUserId by remember { mutableStateOf<Int?>(null) }
                    
                    LaunchedEffect(Unit) {
                        currentUserId = tokenManager.getUserId()
                    }

                    if (isLocked && pinCode != null) {
                        PinLockScreen(
                            correctPin = pinCode!!,
                            onUnlock = { pinManager.unlock() },
                            isBiometricAvailable = biometricHelper.isBiometricAvailable(),
                            onBiometricClick = {
                                biometricHelper.authenticate(
                                    activity = this@MainActivity,
                                    onSuccess = { pinManager.unlock() },
                                    onError = { /* Handle error if needed */ },
                                    onFailed = { /* Handle failure if needed */ }
                                )
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            NexyApp()
                            
                            // Overlay CallScreen if there is an active call state
                            if (callState !is CallState.Idle && currentUserId != null) {
                                CallScreen(
                                    webRTCClient = webRTCClient,
                                    currentUserId = currentUserId!!,
                                    onDismiss = { /* Handled by state change in WebRTCClient */ }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 || requestCode == 102) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                webRTCClient.initialize()
            }
        }
    }
}

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
                    android.util.Log.d("MainActivity", "Navigating to EditGroup: $id")
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
                onStartChat = { id ->
                    // Logic to start/find chat with user
                    // For now, just go back to chat list or search
                    // Ideally, we should create a chat and navigate to it
                    // We can use NavigationViewModel to select chat if it exists
                    // But we need to create it first if it doesn't.
                    // Let's assume SearchScreen logic handles this, we can reuse it or add it here.
                    // For simplicity, let's pop back for now or implement create chat logic in UserProfileViewModel
                    navController.popBackStack()
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
    }
}
