/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.utils.initialization

import android.content.Context
import android.content.Intent
import android.os.Build
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.websocket.NexyWebSocketClient
import com.nexy.client.data.websocket.WebSocketMessageHandler
import com.nexy.client.services.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitializer @Inject constructor(
    private val tokenManager: AuthTokenManager,
    private val webSocketClient: NexyWebSocketClient,
    private val messageHandler: WebSocketMessageHandler
) {
    
    fun initializeApp(context: Context) {
        setupBackgroundService(context)
        setupWebSocket()
        connectWebSocketIfAuthenticated()
    }
    
    private fun setupBackgroundService(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            tokenManager.getBackgroundServiceEnabledFlow().collect { enabled ->
                val serviceIntent = Intent(context, KeepAliveService::class.java)
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    context.stopService(serviceIntent)
                }
            }
        }
    }
    
    private fun setupWebSocket() {
        webSocketClient.setMessageCallback { message ->
            messageHandler.handleIncomingMessage(message)
        }
    }
    
    private fun connectWebSocketIfAuthenticated() {
        CoroutineScope(Dispatchers.IO).launch {
            tokenManager.getAccessToken()?.let { token ->
                webSocketClient.connect(token)
            }
        }
    }
}
