/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.webrtc.CallState
import com.nexy.client.data.webrtc.WebRTCClient
import com.nexy.client.ui.navigation.NexyApp
import com.nexy.client.ui.screens.auth.PinLockScreen
import com.nexy.client.ui.screens.call.CallScreen
import com.nexy.client.ui.screens.settings.SettingsViewModel
import com.nexy.client.ui.theme.NexyClientTheme
import com.nexy.client.ui.theme.ThemeViewModel
import com.nexy.client.utils.BiometricHelper
import com.nexy.client.utils.PinManager
import com.nexy.client.utils.initialization.AppInitializer
import com.nexy.client.utils.lifecycle.AppLifecycleObserver
import com.nexy.client.utils.permissions.PermissionsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var pinManager: PinManager
    
    @Inject
    lateinit var webRTCClient: WebRTCClient
    
    @Inject
    lateinit var tokenManager: AuthTokenManager
    
    @Inject
    lateinit var biometricHelper: BiometricHelper
    
    @Inject
    lateinit var appInitializer: AppInitializer
    
    @Inject
    lateinit var permissionsManager: PermissionsManager
    
    @Inject
    lateinit var lifecycleObserver: AppLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appInitializer.initializeApp(this)
        permissionsManager.requestRequiredPermissions(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()
            
            val isDarkTheme by themeViewModel.isDarkTheme.collectAsState()
            val themeStyle by themeViewModel.themeStyle.collectAsState()
            
            val pinCode by settingsViewModel.pinCode.collectAsState()
            val isLocked by pinManager.isLocked.collectAsState()
            
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
                                    onError = { },
                                    onFailed = { }
                                )
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            NexyApp()
                            
                            if (callState !is CallState.Idle && currentUserId != null) {
                                CallScreen(
                                    currentUserId = currentUserId!!,
                                    onDismiss = { }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.handlePermissionsResult(requestCode, grantResults)
    }
}
